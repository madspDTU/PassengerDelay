/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.routing.pt.raptor;

import ch.sbb.matsim.routing.pt.raptor.MyRaptorRoute.RoutePart;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.facilities.Facility;
import org.matsim.project.RunMatsim;
import org.matsim.project.pt.MyTransitLineImpl;
import org.matsim.project.pt.MyTransitRouteImpl;
import org.matsim.project.pt.MyTransitStopFacilityImpl;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Provides public transport route search capabilities using an implementation of the
 * RAPTOR algorithm underneath.
 *
 * @author mrieser / SBB
 */
public class MySwissRailRaptor  {

	private static final Logger log = Logger.getLogger(SwissRailRaptor.class);

	private final MySwissRailRaptorData data;
	//madsp
	private final MySwissRailRaptorCore raptor;
	private final RaptorParametersForPerson parametersForPerson;
	//    private final String subpopulationAttribute;

	
	//    public SwissRailRaptor(final SwissRailRaptorData data, RaptorParametersForPerson parametersForPerson,
	//                           RaptorRouteSelector routeSelector, RaptorStopFinder stopFinder) {
	//        this(data, parametersForPerson, routeSelector, stopFinder );
	//        log.info("SwissRailRaptor was initialized without support for subpopulations or intermodal access/egress legs.");
	//    }

	public MySwissRailRaptor( final MySwissRailRaptorData data, RaptorParametersForPerson parametersForPerson) {
		this.data = data;
		// madsp
		this.raptor = new MySwissRailRaptorCore(data);
		this.parametersForPerson = parametersForPerson;
		//        this.subpopulationAttribute = subpopulationAttribute;
	}

	//madsp
	public List<Leg> calcRoute(Facility fromFacility, Facility toFacility,int departureTime, 
			boolean onBoard, MyTransitStopFacilityImpl theStop, Id<MyTransitLineImpl> currentLineId, double searchRadius) {
		RaptorParameters parameters = this.parametersForPerson.getRaptorParameters(null);
		if (parameters.getConfig().isUseRangeQuery()) {
			//			return this.performRangeQuery(fromFacility, toFacility, departureTime, person, parameters);
		}
		List<MyInitialStop> accessStops = findStops(fromFacility, searchRadius, 0.);
		List<MyInitialStop> egressStops = findStops(toFacility, RunMatsim.reachedDistance, 0.);

		//madsp
		MyRaptorRoute foundRoute = this.raptor.calcLeastCostRoute(departureTime, fromFacility, toFacility, accessStops,
				egressStops, parameters, onBoard, theStop, currentLineId);
		MyRaptorRoute directWalk = createDirectWalk(fromFacility, toFacility, departureTime, parameters);
		
		/*
		 * The pt trip is compared with a direct walk from trip origin to trip destination. This is useful for backwards
		 * compatibility, but leads to many trips with only a single "transit_walk" leg which are then considered pt
		 * trips by the main mode identifier even though they do not contain any pt leg and should rather be considered
		 * "walk" trips.
		 * 
		 * That problem can be avoided by setting a very high direct walk factor in TransitRouterConfigGroup. However
		 * this should be combined with enabling mode choice for pt and walk trips such that slow pt trips can be
		 * replaced by (faster) walk trips by mode choice. Otherwise agents can be stuck with very slow pt trips.
		 * 
		 * Comparison is only made between a pt trip and a direct walk trip, other modes (e.g. intermodal access/egress
		 * modes) are not considered. If they had been considered here, the router would effectively be a mode choice
		 * module although it is supposed not to change mode choice but rather to simply return a route for a given
		 * mode. Furthermore there is the problem that the generalized cost calculated by the router can be different
		 * from the cost the agent will be exposed to in scoring, because the mode performed differently in mobsim than
		 * anticipated by the router (e.g. the drt travel time turns out to be higher than expected, but the router will
		 * always chose a direct drt trip over the pt trip, because the router might consistently underestimate drt
		 * travel time). So it seems a bad idea to compare other modes than walk here. Walk is usually teleported at a
		 * fixed speed, so it is usually completely deterministic whereas other modes are not.
		 * 
		 * Overall enabling mode choice and setting a very high direct walk factor (e.g. Double.POSITIVE_INFINITY which 
		 * effectively excludes all direct walks) seems cleaner and better.
		 * 
		 * vsp-gleich sep'19 (after talking with KN)
		 * 
		 * 
		 * foundRoute.parts.size() == 0 can happen if SwissRasilRaptorCore.createRaptorRoute() finds a trip made up of,
		 * only 2 parts which consists only of an access and an egress leg without any pt leg inbetween.
		 */
		if (foundRoute == null || foundRoute.parts.size() == 0 || hasNoPtLeg(foundRoute.parts)) {
			// madsp
			return MyRaptorUtils.convertRouteToLegs(directWalk); 
		}
		if (directWalk.getTotalCosts() * parameters.getDirectWalkFactor() < foundRoute.getTotalCosts()) {
			foundRoute = directWalk;
		}

		List<Leg> legs = MyRaptorUtils.convertRouteToLegs(foundRoute);

		return legs;
	}

	private boolean hasNoPtLeg(List<RoutePart> parts) {
		for (RoutePart part : parts) {
			// if the route part has a TransitLine, it must be a real pt leg
			if (part.line != null) {
				return false;
			}
		}
		return true;
	}

	public MySwissRailRaptorData getUnderlyingData() {
		return this.data;
	}

	private List<MyInitialStop> findStops(Facility facility, double searchRadius, double extensionRadius) {
		List<MyTransitStopFacilityImpl> stops = findNearbyStops(facility, searchRadius, extensionRadius, data);
		List<MyInitialStop> initialStops = stops.stream().map(stop -> {
			double beelineDistance = CoordUtils.calcProjectedEuclideanDistance(stop.getCoord(), facility.getCoord());
			int travelTime = (int) Math.ceil(beelineDistance);
			double disutility = travelTime * -RunMatsim.walkTimeUtility;
			return new MyInitialStop(stop, disutility, travelTime, beelineDistance , TransportMode.walk);
		}).collect(Collectors.toList());
		return initialStops;
	}


	private MyRaptorRoute createDirectWalk(Facility fromFacility, Facility toFacility, int departureTime, RaptorParameters parameters) {
		double beelineDistance = CoordUtils.calcProjectedEuclideanDistance(fromFacility.getCoord(), toFacility.getCoord());
		int walkTime = (int) Math.ceil(beelineDistance);
		double walkCost_per_s = -parameters.getMarginalUtilityOfTravelTime_utl_s(TransportMode.walk);
		double walkCost = walkTime * walkCost_per_s;
		MyRaptorRoute route = new MyRaptorRoute(fromFacility, toFacility, walkCost);
		route.addNonPt(null, null, departureTime, walkTime, beelineDistance , TransportMode.walk);
		return route;
	}


	//madsp
	public List<Leg> calcRoute(Facility fromFacility, Facility toFacility, int departureTime, double searchRadius) {
		return calcRoute(fromFacility, toFacility, departureTime, false, null, null, searchRadius);
	}




	private List<MyTransitStopFacilityImpl> findNearbyStops(Facility facility, double searchRadius, double extensionRadius,  MySwissRailRaptorData data) {
		double x = facility.getCoord().getX();
		double y = facility.getCoord().getY();
		Collection<MyTransitStopFacilityImpl> stopFacilities = data.stopsQT.getDisk(x, y, searchRadius);
//		if (stopFacilities.size() < 2) {
//			MyTransitStopFacilityImpl  nearestStop = data.stopsQT.getClosest(x, y);
//			double nearestDistance = CoordUtils.calcEuclideanDistance(facility.getCoord(), nearestStop.getCoord());
//			stopFacilities = data.stopsQT.getDisk(x, y, nearestDistance + extensionRadius);
//		}
		if (stopFacilities instanceof List) {
			return (List<MyTransitStopFacilityImpl>) stopFacilities;
		}
		return new ArrayList<>(stopFacilities);
	}

}