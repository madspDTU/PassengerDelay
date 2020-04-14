/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.routing.pt.raptor;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.utils.collections.QuadTree;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.misc.Time;
import org.matsim.project.RunMatsim;
import org.matsim.project.pt.MyDepartureImpl;
import org.matsim.project.pt.MyTransitLineImpl;
import org.matsim.project.pt.MyTransitRouteImpl;
import org.matsim.project.pt.MyTransitRouteStopImpl;
import org.matsim.project.pt.MyTransitScheduleImpl;
import org.matsim.project.pt.MyTransitStopFacilityImpl;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author mrieser / SBB
 */
public class MySwissRailRaptorData {

	private static final Logger log = Logger.getLogger(MySwissRailRaptorData.class);

	//	private static long counter1;
	//	private static long counter2;

	final RaptorStaticConfig config;
	final int countStops;
	final int countRouteStops;
	final RRoute[] routes;
	final int[] departures; // in the RAPTOR paper, this is usually called "trips", but I stick with the MATSim nomenclature
	final RRouteStop[] routeStops; // list of all route stops
	final RTransfer[] transfers;
	final Map<MyTransitStopFacilityImpl, Integer> stopFacilityIndices;
	final Map<MyTransitStopFacilityImpl, int[]> routeStopsPerStopFacility;
	final QuadTree<MyTransitStopFacilityImpl> stopsQT;
	final Map<String, Map<String, QuadTree<MyTransitStopFacilityImpl>>> stopFilterAttribute2Value2StopsQT;

	private MySwissRailRaptorData(RaptorStaticConfig config, int countStops,
			RRoute[] routes, int[] departures, RRouteStop[] routeStops,
			RTransfer[] transfers, Map<MyTransitStopFacilityImpl, Integer> stopFacilityIndices,
			Map<MyTransitStopFacilityImpl, int[]> routeStopsPerStopFacility, QuadTree<MyTransitStopFacilityImpl> stopsQT) {
		this.config = config;
		this.countStops = countStops;
		this.countRouteStops = routeStops.length;
		this.routes = routes;
		this.departures = departures;
		this.routeStops = routeStops;
		this.transfers = transfers;
		this.stopFacilityIndices = stopFacilityIndices;
		this.routeStopsPerStopFacility = routeStopsPerStopFacility;
		this.stopsQT = stopsQT;
		this.stopFilterAttribute2Value2StopsQT = new HashMap<String, Map<String, QuadTree<MyTransitStopFacilityImpl>>>();
	}

	public static MySwissRailRaptorData create(MyTransitScheduleImpl schedule, RaptorStaticConfig staticConfig) {
		log.info("Preparing data for SwissRailRaptor...");
		long startMillis = System.currentTimeMillis();

		int countRoutes = 0;
		long countRouteStops = 0;
		long countDepartures = 0;

		for (MyTransitLineImpl line : schedule.getTransitLines().values()) {
			countRoutes += line.getRoutes().size();
			for (MyTransitRouteImpl route : line.getRoutes().values()) {
				countRouteStops += route.getStops().length;
				countDepartures += route.getDepartures().size();
			}
		}

		if (countRouteStops > Integer.MAX_VALUE) {
			throw new RuntimeException("TransitSchedule has too many TransitRouteStops: " + countRouteStops);
		}
		if (countDepartures > Integer.MAX_VALUE) {
			throw new RuntimeException("TransitSchedule has too many Departures: " + countDepartures);
		}

		int[] departures = new int[(int) countDepartures];
		RRoute[] routes = new RRoute[countRoutes];
		RRouteStop[] routeStops = new RRouteStop[(int) countRouteStops];

		int indexRoutes = 0;
		int indexRouteStops = 0;
		int indexDeparture = 0;

		// enumerate TransitStopFacilities along their usage in transit routes to (hopefully) achieve a better memory locality
		// well, I'm not even sure how often we'll need the transit stop facilities, likely we'll use RouteStops more often
		Map<MyTransitStopFacilityImpl, Integer> stopFacilityIndices = new HashMap<>((int) (schedule.getFacilities().size() * 1.5));
		Map<MyTransitStopFacilityImpl, int[]> routeStopsPerStopFacility = new HashMap<>();

		boolean useModeMapping = staticConfig.isUseModeMappingForPassengers();
		for (MyTransitLineImpl line : schedule.getTransitLines().values()) {
			List<MyTransitRouteImpl> transitRoutes = new ArrayList<>(line.getRoutes().values());
			transitRoutes.sort((tr1, tr2) -> Double.compare(getEarliestDeparture(tr1).getDepartureTime(), getEarliestDeparture(tr2).getDepartureTime())); // sort routes by earliest departure for additional performance gains
			for (MyTransitRouteImpl route : transitRoutes) {
				int indexFirstDeparture = indexDeparture;
				String mode = TransportMode.pt;
				if (useModeMapping) {
					mode = staticConfig.getPassengerMode(route.getTransportMode());
				}
				RRoute rroute = new RRoute(indexRouteStops, route.getNumberOfStops(), indexFirstDeparture, route.getDepartures().size());
				routes[indexRoutes] = rroute;
				//				NetworkRoute networkRoute = route.getRoute();
				//				List<Id<Link>> allLinkIds = new ArrayList<>();
				//				allLinkIds.add(networkRoute.getStartLinkId());
				//				allLinkIds.addAll(networkRoute.getLinkIds());
				//				if (allLinkIds.size() > 1 || networkRoute.getStartLinkId() != networkRoute.getEndLinkId()) {
				//					allLinkIds.add(networkRoute.getEndLinkId());
				//				}
				//               Iterator<Id<Link>> linkIdIterator = allLinkIds.iterator();
				//              Id<Link> currentLinkId = linkIdIterator.next(); //madsp
				//double distanceAlongRoute = Double.NaN; //madsp
				MyTransitRouteStopImpl[] stops = route.getStops();
				for (int i = 0; i < stops.length ; i++) {
					//                    while (!routeStop.getStopFacility().getLinkId().equals(currentLinkId)) {
					//                        if (linkIdIterator.hasNext()) {
					//                            currentLinkId = linkIdIterator.next();
					//                            Link link = network.getLinks().get(currentLinkId);
					//                            distanceAlongRoute += link.getLength();
					//                        } else {
					//                            distanceAlongRoute = Double.NaN;
					//                            break;
					//                        }
					//                    }
					MyTransitRouteStopImpl routeStop = stops[i];
					int stopFacilityIndex = stopFacilityIndices.computeIfAbsent(routeStop.getStopFacility(), stop -> stopFacilityIndices.size());
					RRouteStop rRouteStop = new RRouteStop(routeStop, line, route, mode, indexRoutes, stopFacilityIndex);
					final int thisRouteStopIndex = indexRouteStops;
					routeStops[thisRouteStopIndex] = rRouteStop;
					routeStopsPerStopFacility.compute(routeStop.getStopFacility(), (stop, currentRouteStops) -> {
						if (currentRouteStops == null) {
							return new int[] { thisRouteStopIndex };
						}
						int[] tmp = new int[currentRouteStops.length + 1];
						System.arraycopy(currentRouteStops, 0, tmp, 0, currentRouteStops.length);
						tmp[currentRouteStops.length] = thisRouteStopIndex;
						return tmp;
					});
					indexRouteStops++;
				}
				for (MyDepartureImpl dep : route.getDepartures()) {
					departures[indexDeparture] = dep.getDepartureTime();
					indexDeparture++;
				}
				Arrays.sort(departures, indexFirstDeparture, indexDeparture);
				indexRoutes++;
			}
		}

		// only put used transit stops into the quad tree
		Set<MyTransitStopFacilityImpl> stops = routeStopsPerStopFacility.keySet();
		QuadTree<MyTransitStopFacilityImpl> stopsQT = MyTransitScheduleImpl.createQuadTreeOfTransitStopFacilities(stops);
		int countStopFacilities = stops.size();

		Map<Integer, RTransfer[]> allTransfers = calculateRouteStopTransfers(schedule, stopsQT, routeStopsPerStopFacility, routeStops, staticConfig);
		long countTransfers = 0;
		for (RTransfer[] transfers : allTransfers.values()) {
			countTransfers += transfers.length;
		}
		if (countTransfers > Integer.MAX_VALUE) {
			throw new RuntimeException("TransitSchedule has too many Transfers: " + countTransfers);
		}
		RTransfer[] transfers = new RTransfer[(int) countTransfers];
		int indexTransfer = 0;
		for (int routeStopIndex = 0; routeStopIndex < routeStops.length; routeStopIndex++) {
			RTransfer[] stopTransfers = allTransfers.get(routeStopIndex);
			int transferCount = stopTransfers == null ? 0 : stopTransfers.length;
			if (transferCount > 0) {
				RRouteStop routeStop = routeStops[routeStopIndex];
				routeStop.indexFirstTransfer = indexTransfer;
				routeStop.countTransfers = transferCount;
				System.arraycopy(stopTransfers, 0, transfers, indexTransfer, transferCount);
				indexTransfer += transferCount;
			}
		}

		MySwissRailRaptorData data = new MySwissRailRaptorData(staticConfig, countStopFacilities, routes, departures, routeStops, transfers, stopFacilityIndices, routeStopsPerStopFacility, stopsQT);

		long endMillis = System.currentTimeMillis();


		log.info("SwissRailRaptor data preparation done. Took " + (endMillis - startMillis) / 1000 + " seconds.");
		log.info("SwissRailRaptor statistics:  #routes = " + routes.length);
		log.info("SwissRailRaptor statistics:  #departures = " + departures.length);
		log.info("SwissRailRaptor statistics:  #routeStops = " + routeStops.length);
		log.info("SwissRailRaptor statistics:  #stopFacilities = " + countStopFacilities);
		log.info("SwissRailRaptor statistics:  #transfers (between routeStops) = " + transfers.length);

		//System.out.println("New method removes " + counter1 + " transfers. " + counter2 + "  .  " + transfers.length);

/*
		if(RunMatsim.distancesLocked.compareAndSet(false, true)){
			RunMatsim.distances = new double[RunMatsim.distancesMap.size()][RunMatsim.distancesMap.size()];
			for(Entry<MyTransitStopFacilityImpl,Integer> fromEntry : data.stopFacilityIndices.entrySet()){
				int fromInt = fromEntry.getValue();
				Id<MyTransitStopFacilityImpl> fromId = fromEntry.getKey().getId();
				for(Entry<MyTransitStopFacilityImpl,Integer> toEntry : data.stopFacilityIndices.entrySet()){
					RunMatsim.distances[fromInt][toEntry.getValue()] = RunMatsim.distancesMap.get(fromId).get(toEntry.getKey().getId());
				}
			}
			RunMatsim.distancesLock.countDown();
			long veryEndMillis = System.currentTimeMillis();
			System.out.println("Restructuring distances takes " + (veryEndMillis - endMillis) / 1000 + " seconds.");
		} else {
			try {
				RunMatsim.distancesLock.await();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		*/
		


		return data;
	}

	// calculate possible transfers between TransitRouteStops
	private static Map<Integer, RTransfer[]> calculateRouteStopTransfers(MyTransitScheduleImpl schedule,
			QuadTree<MyTransitStopFacilityImpl> stopsQT, Map<MyTransitStopFacilityImpl, int[]> routeStopsPerStopFacility,
			RRouteStop[] routeStops, RaptorStaticConfig config) {
		Map<Integer, RTransfer[]> transfers = new HashMap<>(stopsQT.size() * 5);
		double maxBeelineWalkConnectionDistance = config.getBeelineWalkConnectionDistance();
		double beelineWalkSpeed = config.getBeelineWalkSpeed();
		double beelineDistanceFactor = config.getBeelineWalkDistanceFactor();
		//	double minimalTransferTime = config.getMinimalTransferTime();

		Map<MyTransitStopFacilityImpl, List<MyTransitStopFacilityImpl>> stopToStopsTransfers = new HashMap<>();

		// first, add transfers based on distance
		for (MyTransitStopFacilityImpl fromStop : routeStopsPerStopFacility.keySet()) {
			Coord fromCoord = fromStop.getCoord();
			Collection<MyTransitStopFacilityImpl> nearbyStops = stopsQT.getDisk(fromCoord.getX(), fromCoord.getY(), maxBeelineWalkConnectionDistance);
			stopToStopsTransfers.computeIfAbsent(fromStop, stop -> new ArrayList<>(5)).addAll(nearbyStops);
		}

		//        // take the transfers from the schedule into account
		//        MinimalTransferTimes.MinimalTransferTimesIterator iter = schedule.getMinimalTransferTimes().iterator();
		//        while (iter.hasNext()) {
		//            iter.next();
		//            Id<TransitStopFacility> fromStopId = iter.getFromStopId();
		//            TransitStopFacility fromStop = schedule.getFacilities().get(fromStopId);
		//            Id<TransitStopFacility> toStopId = iter.getToStopId();
		//            TransitStopFacility toStop = schedule.getFacilities().get(toStopId);
		//            List<TransitStopFacility> destinationStops = stopToStopsTransfers.computeIfAbsent(fromStop, stop -> new ArrayList<>(5));
		//            if (!destinationStops.contains(toStop)) {
		//                destinationStops.add(toStop);
		//            }
		//        }

		// now calculate the transfers between the route stops
		//        MinimalTransferTimes mtt = schedule.getMinimalTransferTimes();
		ArrayList<RTransfer> stopTransfers = new ArrayList<>();
		for (Map.Entry<MyTransitStopFacilityImpl, List<MyTransitStopFacilityImpl>> e : stopToStopsTransfers.entrySet()) {
			MyTransitStopFacilityImpl fromStop = e.getKey();
			Coord fromCoord = fromStop.getCoord();
			int[] fromRouteStopIndices = routeStopsPerStopFacility.get(fromStop);
			Collection<MyTransitStopFacilityImpl> nearbyStops = e.getValue();
			for (MyTransitStopFacilityImpl toStop : nearbyStops) {
				int[] toRouteStopIndices = routeStopsPerStopFacility.get(toStop);
				double beelineDistance = CoordUtils.calcEuclideanDistance(fromCoord, toStop.getCoord());
				double transferTime = Math.ceil(beelineDistance / beelineWalkSpeed);
				//                if (transferTime < minimalTransferTime) {
				//                    transferTime = minimalTransferTime;
				//                }

				//                transferTime = mtt.get(fromStop.getId(), toStop.getId(), transferTime);

				final int fixedTransferTime = (int) transferTime; // variables must be effective final to be used in lambdas (below)

				for (int fromRouteStopIndex : fromRouteStopIndices) {
					RRouteStop fromRouteStop = routeStops[fromRouteStopIndex];
					stopTransfers.clear();
					for (int toRouteStopIndex : toRouteStopIndices) {
						RRouteStop toRouteStop = routeStops[toRouteStopIndex];
						if (isUsefulTransfer(fromRouteStop, toRouteStop, maxBeelineWalkConnectionDistance, config.getOptimization(), fixedTransferTime)) {
							RTransfer newTransfer = new RTransfer(fromRouteStopIndex, toRouteStopIndex, fixedTransferTime);
							stopTransfers.add(newTransfer);
						}
					}
					RTransfer[] newTransfers = stopTransfers.toArray(new RTransfer[0]);
					transfers.compute(fromRouteStopIndex, (routeStopIndex, currentTransfers) -> {
						if (currentTransfers == null) {
							return newTransfers;
						}
						RTransfer[] tmp = new RTransfer[currentTransfers.length + newTransfers.length];
						System.arraycopy(currentTransfers, 0, tmp, 0, currentTransfers.length);
						System.arraycopy(newTransfers, 0, tmp, currentTransfers.length, newTransfers.length);
						return tmp;
					});
				}
			}
		}
		return transfers;
	}

	private static boolean isUsefulTransfer(RRouteStop fromRouteStop, RRouteStop toRouteStop, double maxBeelineWalkConnectionDistance, RaptorStaticConfig.RaptorOptimization optimization, int transferTime) {
//		String fromStop = "VPT";
//		String fromRoute = "23139";
//		String toStop = "8960";
//		String toRoute = "80220703";
//		if(fromRouteStop.routeStop.getStopFacility().getId().toString().equals(fromStop) && fromRouteStop.route.getId().toString().equals(fromRoute) &&
//				toRouteStop.routeStop.getStopFacility().getId().toString().equals(toStop) && toRouteStop.route.getId().toString().equals(toRoute) ) {
//			System.out.println("Transfer present. Initial check.");
//		}

		if (fromRouteStop == toRouteStop) { 
			//		counter2++;
			return false;
		}
		// there is no use to transfer away from the first stop in a route
		if (isFirstStopInRoute(fromRouteStop)) {
			//		counter2++;
			return false;
		}
		// there is no use to transfer to the last stop in a route, we can't go anywhere from there
		if (isLastStopInRoute(toRouteStop)) {
			//		counter2++;
			return false;
		}

//		if(fromRouteStop.routeStop.getStopFacility().getId().toString().equals(fromStop) && fromRouteStop.route.getId().toString().equals(fromRoute) &&
//				toRouteStop.routeStop.getStopFacility().getId().toString().equals(toStop) && toRouteStop.route.getId().toString().equals(toRoute) ) {
//			System.out.println("Transfer present. Next check: Has no possible departure");
//		}

		// if the first departure at fromRouteStop arrives after the last departure at toRouteStop,
		// we'll never get any connection here
		if (hasNoPossibleDeparture(fromRouteStop, toRouteStop, transferTime)) {
			//		counter2++;
			return false;
		}

//		if(fromRouteStop.routeStop.getStopFacility().getId().toString().equals(fromStop) && fromRouteStop.route.getId().toString().equals(fromRoute) &&
//				toRouteStop.routeStop.getStopFacility().getId().toString().equals(toStop) && toRouteStop.route.getId().toString().equals(toRoute) ) {
//			System.out.println("Transfer present. Next check: Other transferCombinationsDominate");
//		}

		// something I have to try out
		if (otherLocalTransferCombinationDominates(fromRouteStop, toRouteStop, transferTime)) { //Should run O(1) with a LinkedHashMap
			//		counter1++;
			return false;
		}		

//		if(fromRouteStop.routeStop.getStopFacility().getId().toString().equals(fromStop) && fromRouteStop.route.getId().toString().equals(fromRoute) &&
//				toRouteStop.routeStop.getStopFacility().getId().toString().equals(toStop) && toRouteStop.route.getId().toString().equals(toRoute) ) {
//			System.out.println("Transfer present. Next check: toStop is part of fromRoute");
//		}


		//		 if the stop facilities are different, and the destination stop is part
		//		 of the current route, it does not make sense to transfer here
		if (toStopIsPartOfRouteButNotSame(fromRouteStop, toRouteStop, transferTime)) { //Should run O(1) with a LinkedHashMap
			//		counter2++;
			return false;
		}

		/////		if(fromRouteStop.routeStop.getStopFacility().getId().toString().equals(fromStop) && fromRouteStop.route.getId().toString().equals(fromRoute) &&
		////				toRouteStop.routeStop.getStopFacility().getId().toString().equals(toStop) && toRouteStop.route.getId().toString().equals(toRoute) ) {
		////			System.out.println("Transfer present. Next check: One stop earlier in opposite direction.");
		////		}
		//		if (optimization == RaptorStaticConfig.RaptorOptimization.OneToOneRouting) {
		//			// If one could have transferred to the same route one stop before, it does not make sense
		//			// to transfer here.
		//			// This optimization may lead to unexpected results in the case of OneToAllRouting ("tree"),
		//			// e.g. when starting at a single stop, users would expect that the stop facility
		//			// in the opposite direction could be reached within a minute or so by walk. But the algorithm
		//			// would find this if the transfers are missing.
		//			if (couldHaveTransferredOneStopEarlierInOppositeDirection(fromRouteStop, toRouteStop, transferTime)) {
		//				//			counter2++;
		//				return false;
		//			}
		//		}

		//		if(fromRouteStop.routeStop.getStopFacility().getId().toString().equals(fromStop) && fromRouteStop.route.getId().toString().equals(fromRoute) &&
		//				toRouteStop.routeStop.getStopFacility().getId().toString().equals(toStop) && toRouteStop.route.getId().toString().equals(toRoute) ) {
		//			System.out.println("Transfer present. Next check: Additional stops faster");
		//		}


		if (cannotReachAdditionalEarlierOrWithBetterPotential(fromRouteStop, toRouteStop, transferTime)) {
			//		counter2++;
			return false;
		}

//		if(fromRouteStop.routeStop.getStopFacility().getId().toString().equals(fromStop) && fromRouteStop.route.getId().toString().equals(fromRoute) &&
//				toRouteStop.routeStop.getStopFacility().getId().toString().equals(toStop) && toRouteStop.route.getId().toString().equals(toRoute) ) {
//			System.out.println("Transfer present. Survived everything!");
//		}

		// if we failed all other checks, it looks like this transfer is useful
		return true;
	}

	private static boolean isFirstStopInRoute(RRouteStop routeStop) {
		MyTransitRouteStopImpl firstRouteStop = routeStop.route.getFirstStop();
		return routeStop.routeStop == firstRouteStop;
	}

	private static boolean isLastStopInRoute(RRouteStop routeStop) {
		MyTransitRouteStopImpl lastRouteStop = routeStop.route.getLastStop();
		return routeStop.routeStop == lastRouteStop;
	}

	private static boolean otherLocalTransferCombinationDominates(RRouteStop fromRouteStop, RRouteStop toRouteStop, int transferTime) {
		double toUtility = getRouteUtility(toRouteStop.route.getTransportMode());

		MyTransitRouteStopImpl fromStop = fromRouteStop.routeStop;
		MyTransitRouteStopImpl toStop = toRouteStop.routeStop;
		
		// from -> PrevTo.   Time savings is not a sufficient attribute, but some time savings will dominate.
		if(toRouteStop.indexInRoute > 0) {
			MyTransitRouteStopImpl prevToStop = toRouteStop.route.getStop(toRouteStop.indexInRoute-1);
			int transferToPrev = (int) Math.ceil(CoordUtils.calcEuclideanDistance(fromStop.getStopFacility().getCoord(), prevToStop.getStopFacility().getCoord()));
			int inVehicleTime = toStop.getArrivalOffset() - prevToStop.getDepartureOffset();
			int timeSavings =  transferTime - (transferToPrev + inVehicleTime);
			double costSavings = transferTime * -RunMatsim.walkTimeUtility - (transferToPrev * -RunMatsim.walkTimeUtility + (inVehicleTime) * -toUtility);
			double gainedPotential = costSavings - timeSavings * -RunMatsim.waitTimeUtility;
			if(timeSavings >= 0 && gainedPotential >= 0) {
				return true;
			}
		}

	

		// From -> NextTo
		// Mathematically impossible: If you save time, then this time might be converted to waiting time instead of in-vehicle time.
//		MyTransitRouteStopImpl nextToStop = toRouteStop.route.getStop(toRouteStop.indexInRoute+1);
//		int transferToNext = (int) Math.ceil(CoordUtils.calcEuclideanDistance(fromStop.getStopFacility().getCoord(), nextToStop.getStopFacility().getCoord()));
//		//It is better to transfer fo the previous stop inestead
//		if(transferToNext < RunMatsim.maxBeelineTransferWalk) {
//			int timeSavings =  transferTime + nextToStop.getArrivalOffset() - toStop.getDepartureOffset() - transferToNext;
//			double costSavings = transferTime * -RunMatsim.walkTimeUtility + (nextToStop.getArrivalOffset() - toStop.getDepartureOffset()) * -toUtility 
//					- transferToNext * -RunMatsim.walkTimeUtility ;
//			double gainedPotential = costSavings - timeSavings * -RunMatsim.waitTimeUtility;
//
//			
//			if( transferToNext  < transferTime + nextToStop.getArrivalOffset() - toStop.getDepartureOffset()) {
//				return true;
//			}
//		}

		//PrevFrom -> To 
		//THIS ONE IS MATHEMATICALLY IMPOSSIBLE: Either you gain time, but lose potential, or you lose time. In neither case you dominate.
//		MyTransitRouteStopImpl prevFromStop = fromRouteStop.route.getStop(fromRouteStop.indexInRoute-1);
//		int transferFromPrev = (int) Math.ceil(CoordUtils.calcEuclideanDistance(prevFromStop.getStopFacility().getCoord(), toStop.getStopFacility().getCoord()));
//		//Would have been better to transfer one stop earlier. 
//		if(transferFromPrev < RunMatsim.maxBeelineTransferWalk) {
//			int timeSavings =  transferTime + fromStop.getArrivalOffset() - prevFromStop.getArrivalOffset() - transferFromPrev;
//			double costSavings = transferTime * -RunMatsim.walkTimeUtility + (fromStop.getArrivalOffset() - prevFromStop.getArrivalOffset()) * -fromUtility 
//					- transferFromPrev * -RunMatsim.walkTimeUtility ;
//			double gainedPotential = costSavings - timeSavings * -RunMatsim.waitTimeUtility;
//			System.out.println("timeSavings: " + timeSavings + " costSavings: " + costSavings + " gainedPotential: " + gainedPotential);
//			if(timeSavings >= 0 && gainedPotential >= 0) {
//				System.out.println("Mathematically impossible...");
//			}
//
//			if( transferFromPrev  < transferTime + fromStop.getArrivalOffset() - prevFromStop.getArrivalOffset()) {
//				return true;
//			}
//		}

//		if(fromRouteStop.routeStop.getStopFacility().getId().toString().equals("VPT") && fromRouteStop.route.getId().toString().equals("23139") &&
//				toRouteStop.routeStop.getStopFacility().getId().toString().equals("8960") && toRouteStop.route.getId().toString().equals("80220703") ) {
//			System.out.println("Transfer present. Next check: Other transferCombinationsDominate");
//		}
		
		// nextFrom -> to
		if(fromRouteStop.indexInRoute < fromRouteStop.route.getStops().length -1) {
			double fromUtility = getRouteUtility(fromRouteStop.route.getTransportMode());
			MyTransitRouteStopImpl nextFromStop = fromRouteStop.route.getStop(fromRouteStop.indexInRoute+1);
			int transferFromNext = (int) Math.ceil(CoordUtils.calcEuclideanDistance(nextFromStop.getStopFacility().getCoord(), toStop.getStopFacility().getCoord()));
			int inVehicleTime = nextFromStop.getArrivalOffset() - fromStop.getArrivalOffset();
			int timeSavings = transferTime - (transferFromNext + inVehicleTime);
			double costSavings =  transferTime * -RunMatsim.walkTimeUtility - (transferFromNext * -RunMatsim.walkTimeUtility + inVehicleTime * -fromUtility);
			double gainedPotential = costSavings - timeSavings * -RunMatsim.waitTimeUtility;		
			if(timeSavings >= 0 && gainedPotential >=0) {
				return true;
			}
		}
		return false;
	}

	private static double getRouteUtility(String mode) {
		double utility = RunMatsim.localTrainTimeUtility;
		if(mode.equals(RunMatsim.MODE_BUS)) {
			utility = RunMatsim.busTimeUtility;
		} else if(mode.equals(RunMatsim.MODE_METRO)) {
			utility = RunMatsim.metroTimeUtility;
		} else if(mode.equals(RunMatsim.MODE_S_TRAIN)) {
			utility = RunMatsim.sTrainTimeUtility;
		} else if(mode.equals(RunMatsim.MODE_TRAIN)) {
			utility = RunMatsim.trainTimeUtility;
		}
		return utility;
	}

	private static boolean hasNoPossibleDeparture(RRouteStop fromRouteStop, RRouteStop toRouteStop, double transferTime) {
		MyDepartureImpl earliestFromDep = fromRouteStop.route.getFirstDeparture();
		MyDepartureImpl latestToDep = toRouteStop.route.getLastDeparture();

		double earliestArrival = earliestFromDep.getDepartureTime() + fromRouteStop.arrivalOffset;
		double latestDeparture = latestToDep.getDepartureTime() + toRouteStop.departureOffset;
		if(earliestArrival + transferTime > latestDeparture) {
			return true;
		}

		int maxWait = RunMatsim.maxWait;
		MyDepartureImpl latestFromDep = fromRouteStop.route.getLastDeparture();
		MyDepartureImpl earliestToDep = toRouteStop.route.getFirstDeparture();
		double latestArrival = latestFromDep.getDepartureTime() + fromRouteStop.arrivalOffset;
		double earliestDeparture = earliestToDep.getDepartureTime() + toRouteStop.departureOffset;	
		if(latestArrival + transferTime + maxWait < earliestDeparture) {
			return true;
		}

		return false;
	}

	private static MyDepartureImpl getEarliestDeparture(MyTransitRouteImpl route) {
		return route.getFirstDeparture();
	}


	//We add a clause here... If the walk can take you to the stop faster, we allow it.
	private static boolean toStopIsPartOfRouteButNotSame(RRouteStop fromRouteStop, RRouteStop toRouteStop, int transferTime) {
		if(!fromRouteStop.route.containsStop(toRouteStop.routeStop)) {
			return false;
		}
		if(transferTime < toRouteStop.routeStop.getDepartureOffset() - fromRouteStop.routeStop.getArrivalOffset()) {
			return false; 
		} else {
			return true;
		}
	}


	private static boolean cannotReachAdditionalEarlierOrWithBetterPotential(RRouteStop fromRouteStop, RRouteStop toRouteStop, int transferTime) {

		double fromUtility = getRouteUtility(fromRouteStop.route.getTransportMode());
		double toUtility = getRouteUtility(toRouteStop.route.getTransportMode());
		
		int sizeOfFromRoute = fromRouteStop.route.getStops().length;
		int indexOfFromRouteStop = fromRouteStop.indexInRoute + 1;
		int sizeOfToRoute = toRouteStop.route.getStops().length;
		int indexOfToRouteStop = toRouteStop.indexInRoute + 1;

		int fromTimeZero = fromRouteStop.route.getStop(fromRouteStop.indexInRoute).getArrivalOffset();
		int toTimeZero = toRouteStop.route.getStop(toRouteStop.indexInRoute).getDepartureOffset();


		//If there are more stops in to, then at least one of those stops are new.
		if(sizeOfToRoute - indexOfToRouteStop > sizeOfFromRoute - indexOfFromRouteStop) {
			return false;
		} 
		while (indexOfToRouteStop < sizeOfToRoute && indexOfFromRouteStop < sizeOfFromRoute) {
			MyTransitRouteStopImpl fromStop = fromRouteStop.route.getStop(indexOfFromRouteStop);
			MyTransitRouteStopImpl toStop = toRouteStop.route.getStop(indexOfToRouteStop);
			if(toStop.getStopFacility() == fromStop.getStopFacility()) {
				int fromInVehicleTime = fromStop.getArrivalOffset() - fromTimeZero;
				int toInVehicleTime = toStop.getArrivalOffset() - toTimeZero;
				int timeSavings = fromInVehicleTime - (toInVehicleTime + transferTime);
				if(timeSavings >= 0) {
					return false;
				}
				double costSavings = fromInVehicleTime * -fromUtility -
						(-RunMatsim.transferUtility + transferTime * -RunMatsim.walkTimeUtility + toInVehicleTime * -toUtility);
				double gainedPotential = costSavings - timeSavings * RunMatsim.walkTimeUtility;
				if(gainedPotential >= 0) {
					return false;
				}
				indexOfToRouteStop++;
				indexOfFromRouteStop++;
			} else if (!fromRouteStop.route.containsStop(toStop)) {
				// No matter how far we increment indexOfFromRouteStop, we won't find it....
				return false;
			} else {
				//TODO it might be faster to implement this with a binary search in the above clause,
				// potentially skipping many in-between stops... But since it would most often be the case
				// that they do not share stops, this may be fine...
				indexOfFromRouteStop++;
			}
		}


		// If true, then we did not find any suitable new stops. If not, then we ran out of from stops - > new has something new to offer.
		return indexOfToRouteStop == sizeOfToRoute;
	}

	private static boolean couldHaveTransferredOneStopEarlierInOppositeDirection(RRouteStop fromRouteStop, RRouteStop toRouteStop, int transferDistance) {
		MyTransitRouteStopImpl previousFromRouteStop = fromRouteStop.route.getStop(fromRouteStop.indexInRoute-1);
		MyTransitRouteStopImpl nextToRouteStop = toRouteStop.route.getStop(toRouteStop.indexInRoute+1);

		//They are the same, so only keep the other one...
		if (previousFromRouteStop.getStopFacility() == nextToRouteStop.getStopFacility()) {
			return true;
		}

		int earlierDistance = (int) Math.ceil(CoordUtils.calcProjectedEuclideanDistance(previousFromRouteStop.getStopFacility().getCoord(), 
				nextToRouteStop.getStopFacility().getCoord()));

		//If distance of transfer is shorter (or equal) than current transfer
		return earlierDistance <= transferDistance;
	}

	public Collection<MyTransitStopFacilityImpl> findNearbyStops(double x, double y, double distance) {
		return this.stopsQT.getDisk(x, y, distance);
	}

	public MyTransitStopFacilityImpl findNearestStop(double x, double y) {
		return this.stopsQT.getClosest(x, y);
	}

	static final class RRoute {
		final int indexFirstRouteStop;
		final int countRouteStops;
		final int indexFirstDeparture;
		final int countDepartures;

		RRoute(int indexFirstRouteStop, int countRouteStops, int indexFirstDeparture, int countDepartures) {
			this.indexFirstRouteStop = indexFirstRouteStop;
			this.countRouteStops = countRouteStops;
			this.indexFirstDeparture = indexFirstDeparture;
			this.countDepartures = countDepartures;
		}
	}

	static final class RRouteStop {
		final MyTransitRouteStopImpl routeStop;
		final MyTransitLineImpl line;
		final MyTransitRouteImpl route;
		final String mode;
		final int transitRouteIndex;
		final int stopFacilityIndex;
		final int arrivalOffset;
		final int departureOffset;
		int indexFirstTransfer = -1;
		int countTransfers = 0;
		int indexInRoute;

		RRouteStop(MyTransitRouteStopImpl routeStop, MyTransitLineImpl line, MyTransitRouteImpl route, String mode, 
				int transitRouteIndex, int stopFacilityIndex) {
			this.routeStop = routeStop;
			this.line = line;
			this.route = route;
			this.mode = mode;
			this.transitRouteIndex = transitRouteIndex;
			this.stopFacilityIndex = stopFacilityIndex;
			// "normalize" the arrival and departure offsets, make sure they are always well defined.
			this.arrivalOffset = routeStop.getArrivalOffset();
			this.departureOffset =  routeStop.getDepartureOffset();
			this.indexInRoute = routeStop.getIndexAlongRoute();
		}

		private static boolean isUndefinedTime(double time) {
			return Time.isUndefinedTime(time) || Double.isNaN(time);
		}
	}

	static final class RTransfer {
		final int fromRouteStop;
		final int toRouteStop;
		final int transferTime;

		RTransfer(int fromRouteStop, int toRouteStop, int transferTime) {
			this.fromRouteStop = fromRouteStop;
			this.toRouteStop = toRouteStop;
			this.transferTime = transferTime;
		}
	}

	/*
	 * synchronized in order to avoid that multiple quad trees for the very same stop filter attribute/value combination are prepared at the same time 
	 */
	public synchronized void prepareStopFilterQuadTreeIfNotExistent(String stopFilterAttribute, String stopFilterValue) {
		if (!stopFilterAttribute2Value2StopsQT.containsKey(stopFilterAttribute)) {
			stopFilterAttribute2Value2StopsQT.put(stopFilterAttribute, new HashMap<>());
		}
		QuadTree<MyTransitStopFacilityImpl> stopsQTFiltered = new QuadTree<>(stopsQT.getMinEasting(), stopsQT.getMinNorthing(), stopsQT.getMaxEasting(), stopsQT.getMaxNorthing());
		stopFilterAttribute2Value2StopsQT.get(stopFilterAttribute).put(stopFilterValue, stopsQTFiltered);
	}
}
