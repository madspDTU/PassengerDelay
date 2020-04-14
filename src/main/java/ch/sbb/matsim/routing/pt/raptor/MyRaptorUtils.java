package ch.sbb.matsim.routing.pt.raptor;


import java.util.ArrayList;
import java.util.List;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Route;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.utils.misc.Time;
import org.matsim.project.MyTransitRoute;
import org.matsim.project.RunMatsim;
import org.matsim.project.pt.MyDepartureImpl;


public class MyRaptorUtils {

	private static final double INV_SQRT_2 = 1./Math.sqrt(2);
	
	public static List<Leg> convertRouteToLegs(MyRaptorRoute route) {
		List<Leg> legs = new ArrayList<>(route.parts.size());
		double lastArrivalTime = Time.getUndefinedTime();
		for (MyRaptorRoute.RoutePart part : route.parts) {
			if (part.line != null) {
				// a pt leg
				Leg ptLeg = PopulationUtils.createLeg(part.mode);
				ptLeg.setDepartureTime(part.depTime);
				ptLeg.setTravelTime(part.arrivalTime - part.depTime);
				Id<MyDepartureImpl> departureId = determineDepartureId(part);
				MyTransitRoute myRoute = new MyTransitRoute(part, departureId);
				ptLeg.setRoute(myRoute);	
				legs.add(ptLeg);
				lastArrivalTime = part.arrivalTime;
			} else {
				if(part.distance>0) {
					// a non-pt leg
					Leg walkLeg = PopulationUtils.createLeg(part.mode);
					walkLeg.setDepartureTime(part.depTime);
					walkLeg.setTravelTime(part.arrivalTime - part.depTime);
					Route walkRoute = RouteUtils.createGenericRouteImpl(null, null);
					walkRoute.setTravelTime(part.arrivalTime - part.depTime);
					walkRoute.setDistance(part.distance);
					walkLeg.setRoute(walkRoute);
					legs.add(walkLeg);
					lastArrivalTime = part.arrivalTime;
				}
			}
		}
		return legs;
	}


	private static Id<MyDepartureImpl> determineDepartureId(MyRaptorRoute.RoutePart part) {
		int journeyTime = part.toRouteStop.getArrivalOffset();
		int egressTime = part.arrivalTime;
		int departureTime = egressTime - journeyTime;
		Id<MyDepartureImpl> departureId = part.route.getDepartureIdFromDepartureTime(departureTime);
		return departureId;
	}
	
	
	// Triangle inequality
	public static double upperBoundEuclideanDistance(Coord coord, Coord other) {
		double xDiff = Math.abs(other.getX()-coord.getX());
		double yDiff = Math.abs(other.getY()-coord.getY());
		return xDiff + yDiff;
	}
	
	// Cauchy - Schwarz inequality
	public static double lowerBoundEuclideanDistance(Coord coord, Coord other) {
		double xDiff = Math.abs(other.getX()-coord.getX());
		double yDiff = Math.abs(other.getY()-coord.getY());
		return INV_SQRT_2 * (xDiff + yDiff);
	}

}