/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.routing.pt.raptor;

import ch.sbb.matsim.routing.pt.raptor.MySwissRailRaptorData.RRoute;
import ch.sbb.matsim.routing.pt.raptor.MySwissRailRaptorData.RRouteStop;
import ch.sbb.matsim.routing.pt.raptor.MySwissRailRaptorData.RTransfer;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.facilities.Facility;
import org.matsim.project.RunMatsim;
import org.matsim.project.pt.MyDepartureImpl;
import org.matsim.project.pt.MyTransitLineImpl;
import org.matsim.project.pt.MyTransitRouteImpl;
import org.matsim.project.pt.MyTransitStopFacilityImpl;

import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * The actual RAPTOR implementation, based on Delling et al, Round-Based Public Transit Routing.
 *
 * This class is <b>NOT</b> thread-safe due to the use of internal state during the route calculation.
 *
 * @author mrieser / SBB
 */
public class MySwissRailRaptorCore {
	private static final Logger log = Logger.getLogger( MySwissRailRaptorCore.class ) ;

	private final MySwissRailRaptorData data;

	private final RouteStopPathElementExtension[] arrivalPathPerRouteStop;
	// private final double[] egressCostsPerRouteStop;
	//	private final double[] leastArrivalCostAtRouteStop;
	//	private final double[] leastArrivalCostAtStop;
	private final BitSet improvedRouteStopIndices;
	private final BitSet improvedStops;
	// private final BitSet destinationRouteStopIndices;
	private double bestArrivalCost = Double.POSITIVE_INFINITY;
	private final StopPathElementExtension[] arrivalPathPerStop;
	//	private final LinkedList<PathElement>[] tmpArrivalPathPerStop; // only used to ensure parallel update
	private final BitSet tmpImprovedStops; // only used to ensure parallel update

	private PathElement bestArrivalPath;

	private boolean reached = false;
	
	private double[] minCosts = new double[RunMatsim.numberOfTransitStopFacilities];


	public MySwissRailRaptorCore(MySwissRailRaptorData data2) {
		this.data = data2;
		this.arrivalPathPerRouteStop = new RouteStopPathElementExtension[data2.countRouteStops];
		// this.egressCostsPerRouteStop = new double[data2.countRouteStops];
		//	this.leastArrivalCostAtRouteStop = new double[data.countRouteStops];
		//		this.leastArrivalCostAtStop = new double[data.countStops];
		this.improvedRouteStopIndices = new BitSet(this.data.countRouteStops);
		// this.destinationRouteStopIndices = new BitSet(this.data.countRouteStops);
		this.improvedStops = new BitSet(this.data.countStops);
		this.arrivalPathPerStop = new StopPathElementExtension[this.data.countStops];
		//		this.tmpArrivalPathPerStop = new LinkedList[this.data.countStops];
		this.tmpImprovedStops = new BitSet(this.data.countStops);
	}

	private void reset() {
		Arrays.fill(this.arrivalPathPerRouteStop, null);
		//	Arrays.fill(this.egressCostsPerRouteStop, Double.POSITIVE_INFINITY);
		Arrays.fill(this.arrivalPathPerStop, null);
		//		Arrays.fill(this.leastArrivalCostAtRouteStop, Double.POSITIVE_INFINITY);
		//		Arrays.fill(this.leastArrivalCostAtStop, Double.POSITIVE_INFINITY);
		this.improvedStops.clear();
		this.improvedRouteStopIndices.clear();
		//	this.destinationRouteStopIndices.clear();
		this.bestArrivalCost = Double.POSITIVE_INFINITY;
		this.bestArrivalPath = null;
		this.reached = false;
		this.minCosts = new double[RunMatsim.numberOfTransitStopFacilities];
	}

	public MyRaptorRoute calcLeastCostRoute(int depTime, Facility fromFacility, Facility toFacility, List<MyInitialStop> accessStops,
			List<MyInitialStop> egressStops, RaptorParameters parameters, boolean onBoard, 	MyTransitStopFacilityImpl theStop, Id<MyTransitLineImpl> currentLineId) {
		final int maxTransfers = RunMatsim.maxTotalTransfers; // sensible defaults, could be made configurable if there is a need for it.
		final int maxTransfersAfterFirstArrival = RunMatsim.maxTransfersAfterFirstArrival;

		reset();
		this.bestArrivalCost = calculateExactDistanceToTarget(fromFacility, toFacility)	* -RunMatsim.walkTimeUtility;


		Map<Integer, MyInitialStop> destinationStops = new HashMap<>();

		// go through all egressStops; check if already in destinationStops; if so, check if current cost is smaller; if so, then replace.  This can
		// presumably happen when the same stop can be reached at lower cost by a different egress mode. (*)
		//		int closestStationIndex = -1;
		//		double closestStationCost = Double.POSITIVE_INFINITY;

		for (MyInitialStop egressStop : egressStops) {
			int stopIndex= this.data.stopFacilityIndices.get(egressStop.stop);
			MyInitialStop alternative = destinationStops.get(stopIndex);
			if (alternative == null || egressStop.accessCost < alternative.accessCost) {
				destinationStops.put(stopIndex, egressStop);
				//				if( egressStop.accessCost < closestStationCost) {
				//					closestStationCost = egressStop.accessCost;
				//					closestStationIndex = stopIndex;
				//				}
			}
		}


		/*
		// ??:
		for (InitialStop egressStop : destinationStops.values()) {
			int[] routeStopIndices = this.data.routeStopsPerStopFacility.get(egressStop.stop);
			if (routeStopIndices != null) {
				for (int routeStopIndex : routeStopIndices) {
					this.destinationRouteStopIndices.set(routeStopIndex); // set bit at index position to true
			//		this.egressCostsPerRouteStop[routeStopIndex] = egressStop.accessCost; // set egress costs from given stop
					// presumably, the routeStops are the stops for the different routes that stop at the same stopFacility
				}
			}
		}
		 */

		// same as (*) for access stops:
		Map<MyTransitStopFacilityImpl, MyInitialStop> initialStops = new HashMap<>();
		for (MyInitialStop accessStop : accessStops) {
			MyInitialStop alternative = initialStops.get(accessStop.stop);
			if (alternative == null || accessStop.accessCost < alternative.accessCost) {
				initialStops.put(accessStop.stop, accessStop);
			}
		}



		// go through initial stops ...
		for (MyInitialStop stop : initialStops.values()) {

			// ... retrieve all route stops ...
			int[] routeStopIndices = this.data.routeStopsPerStopFacility.get(stop.stop);
			// ... go through them ...
			for (int routeStopIndex : routeStopIndices) {
				// ... set arrival time and arrival cost accordingly ...
				int arrivalTime = depTime + stop.accessTime;
				double arrivalCost = stop.accessCost;

				RRouteStop routeStop = this.data.routeStops[routeStopIndex];
				RRoute route = this.data.routes[routeStop.transitRouteIndex];


				String mode = routeStop.route.getTransportMode();
				int depOffset = routeStop.departureOffset;
				int arrOffset = routeStop.arrivalOffset;


				int departureIndex = findNextDepartureIndex(route, routeStop, arrivalTime);		
				if (departureIndex >= 0) {
					int routeDepTime = this.data.departures[departureIndex];
					int nextDepartureTimeAtStop = routeDepTime + depOffset;
					int vehicleArrivalTime = routeDepTime + arrOffset;
					int boardingTime = (arrivalTime < vehicleArrivalTime) ? vehicleArrivalTime : arrivalTime;
					int waitingTime = boardingTime - arrivalTime;
					if(waitingTime > RunMatsim.maxWait) {
						continue;
					}
					double waitingCost = waitingTime * -parameters.getMarginalUtilityOfWaitingPt_utl_s();

					double inVehicleWaitingTime = (nextDepartureTimeAtStop - boardingTime);
					double inVehicleWaitingCost = inVehicleWaitingTime * -parameters.getMarginalUtilityOfTravelTime_utl_s(mode);

					//madsp
					int transferCount = 1;
					if(onBoard && vehicleArrivalTime == depTime && theStop.getId() == routeStop.routeStop.getStopFacility().getId() &&
							routeStop.line.getId() == currentLineId) {
						transferCount = 0;
					}
					arrivalCost += waitingCost + transferCount * parameters.getTransferPenaltyFixCostPerTransfer() + inVehicleWaitingCost;

					double minCostToTarget = calculateCostlyMinCostToTarget(routeStop, egressStops);
					if(arrivalCost + minCostToTarget  > this.bestArrivalCost) {
						continue;
					}




					// Pre main loop
					//Weird logic taken from the original....
					PathElement pe = new PathElement(null, routeStop, arrivalTime,  nextDepartureTimeAtStop,
							Double.NaN, arrivalCost , stop.distance, transferCount,
							Double.NaN, arrivalCost - nextDepartureTimeAtStop * -parameters.getMarginalUtilityOfWaitingPt_utl_s());

					boolean inserted = true;
					if(this.arrivalPathPerRouteStop[routeStopIndex]== null) {
						this.arrivalPathPerRouteStop[routeStopIndex] = new RouteStopPathElementExtension(pe);
					} else {
						inserted = this.arrivalPathPerRouteStop[routeStopIndex].insertIfNotDominated(pe);
					}
					if(inserted) {
						this.improvedRouteStopIndices.set(routeStopIndex);
					} 
				}
			}
		}

		int allowedTransfersLeft = maxTransfersAfterFirstArrival;
		// the main loop
		for (int k = 0; k <= maxTransfers; k++) {

	//		printInfoOfStopId("2505", k, true);


			// first stage (according to paper) is to set earliestArrivalTime_k(stop) = earliestArrivalTime_k-1(stop)
			// but because we re-use the earliestArrivalTime-array, we don't have to do anything.

			// second stage: process routes	
			exploreRoutes(parameters, toFacility, egressStops);


			//	printHeapOverview(k);	

			if (reached) {
				if (allowedTransfersLeft == 0) {
					break;
				}
				allowedTransfersLeft--;
			}

			if (this.improvedStops.isEmpty()) {
				break;
			}

			// third stage (according to paper): handle footpaths / transfers
			handleTransfers(true, parameters, toFacility, egressStops);

			// final stage: check stop criterion
			if (this.improvedRouteStopIndices.isEmpty()) {
				break;
			}
		}




		PathElement leastCostPath = findLeastCostArrival(toFacility);	

//		System.out.println("Final path has total cost: " + (leastCostPath.costAtArrival));
//		PathElement pe = leastCostPath;
//		while(true) {
//			System.out.println(
//					(pe.isWalkOrWait() ? pe.nextStopDepartureTime : pe.arrivalTime) + " " + 
//							(pe.isWalkOrWait() ? pe.costAtDeparture : pe.costAtArrival) + " " + 
//							((pe.toRouteStop == null) ?  "" : pe.toRouteStop.routeStop.getStopFacility().getId()) + " " +
//							((pe.toRouteStop == null) ?  "" : pe.toRouteStop.route.getId()) + " " +
//							((pe.toRouteStop == null) ?  "" : pe.nextStopDepartureTime - (pe.toRouteStop.departureOffset -
//									pe.toRouteStop.arrivalOffset)) + " " +
//									((pe.toRouteStop == null) ?  "" : pe.nextStopDepartureTime + pe.toRouteStop.departureOffset) );
//			if(pe.comingFrom != null) {
//				pe = pe.comingFrom;
//			} else {
//				break;
//			}
//		}
//		System.out.println(depTime + " " + 0.);
//		System.out.println(this.bestArrivalCost);


		MyRaptorRoute raptorRoute = createRaptorRoute(fromFacility, toFacility, leastCostPath, depTime);

		return raptorRoute;
	}

	private void printInfoOfStopId(String stationOfInterest, int k, boolean includeRouteStops) {
		System.out.println("k: " + k);
		int intVO = -1;
		MyTransitStopFacilityImpl stopVO = null;
		for(Entry<MyTransitStopFacilityImpl, Integer> stop : this.data.stopFacilityIndices.entrySet()) {
			if(stop.getKey().getId().toString().equals(stationOfInterest)) {
				intVO = stop.getValue();
				stopVO = stop.getKey();
				break;
			}
		}
		StopPathElementExtension voStopPathElementExtension = this.arrivalPathPerStop[intVO];
		if(voStopPathElementExtension != null) {
			System.out.println("PEs at " + stationOfInterest + " are: ");
			for(PathElement voPathElement : voStopPathElementExtension.container) {
				System.out.println(voPathElement.arrivalTime + "\t" + voPathElement.costAtArrival + "\t" + voPathElement.potentialAtArrival + "\tFrom: " + 
						(voPathElement.comingFrom == null ? "NULL" : voPathElement.comingFrom.toRouteStop.route.getId()));
			}
		}

		if(includeRouteStops && voStopPathElementExtension != null) {
			int[] routeStopInts = this.data.routeStopsPerStopFacility.get(stopVO);
			for(int routeStopInt : routeStopInts) {
				RRouteStop routeStop = this.data.routeStops[routeStopInt];
				RouteStopPathElementExtension voPathElementExtension = this.arrivalPathPerRouteStop[routeStopInt];
				if(voPathElementExtension != null) {		
					System.out.print("\tPEs for route: " + routeStop.route.getId() + " are:\t\t(Has departures[arrivals]:");
					for(MyDepartureImpl departure : routeStop.route.getDepartures()) {
						System.out.print(" " + departure.getId() + "[" + (departure.getDepartureTime() + routeStop.routeStop.getArrivalOffset())  + "]");
					}
					System.out.print(")\n");
					for(PathElement voPathElement : voPathElementExtension.container) {
						System.out.println("\t\t" + voPathElement.nextStopDepartureTime + "\t" + voPathElement.costAtDeparture +
								"\t" + voPathElement.potentialAtDeparture );
					}
				}
			}
		}
	}

	private void printHeapOverview(int k) {
		long counter0 = 0;
		long counter1 = 0;
		long counter2 = 0;
		long counter3 = 0;
		long counter4 = 0;
		long counter5 = 0;
		long counter6 = 0;
		long counter7 = 0;
		long superCounter = 0;
		int n = this.arrivalPathPerStop.length -1;
		while(n >= 0) {
			StopPathElementExtension peExt = this.arrivalPathPerStop[n];
			if(peExt == null) {
				counter0++;
			} else if(peExt.size() == 1) {
				counter1++;
			} else if(peExt.size() <= 5 ) {
				counter2++;
			} else if(peExt.size() <= 10) {
				counter3++;
			} else if(peExt.size() <= 25) {
				counter4++;
			} else if(peExt.size() <= 50) {
				counter5++;
			} else if(peExt.size() <= 100) {
				counter6++;
			} else if(peExt.size() > 100) {
				counter7++;
			}
			if(peExt != null && this.improvedStops.get(n)) {
				superCounter += peExt.size();
			}
			n--;
		}
		System.out.println(
				"k: " + k + 
				//"\t0\t1\t5\t10\t25\t50\t100\t+\n" + reached +
				//"\t" + counter0 + "\t" + counter1 + "\t" + counter2 + "\t" + counter3 + "\t" + 
				//counter4 + "\t" + counter5 + "\t" + counter6 + "\t" + counter7 + "\n" +
				". Transfer origins to process: " + superCounter);
	}






	private void exploreRoutes(RaptorParameters parameters, Facility toFacility, List<MyInitialStop> egressStops) {
		this.improvedStops.clear();

		int routeIndex = -1;
		for (int firstRouteStopIndex = this.improvedRouteStopIndices.nextSetBit(0); firstRouteStopIndex >= 0; firstRouteStopIndex = this.improvedRouteStopIndices.nextSetBit(firstRouteStopIndex+1)) {
			RRouteStop firstRouteStop = this.data.routeStops[firstRouteStopIndex];
			if (firstRouteStop.transitRouteIndex == routeIndex) {
				continue; // we've handled this route already
			}
			String mode = firstRouteStop.route.getTransportMode();
			double marginalUtilityOfTravelTime_utl_s = parameters.getMarginalUtilityOfTravelTime_utl_s(mode);

			int tmpRouteIndex = firstRouteStop.transitRouteIndex;

			// for each relevant route, step along route and look for new/improved connections
			RRoute route = this.data.routes[tmpRouteIndex];

			// firstRouteStop is the first RouteStop in the route we can board in this round
			// figure out which departure we can take



			routeIndex = tmpRouteIndex;


			for (int toRouteStopIndex = firstRouteStopIndex + 1; toRouteStopIndex < route.indexFirstRouteStop + route.countRouteStops; toRouteStopIndex++) {
				firstRouteStop = this.data.routeStops[firstRouteStopIndex];
				RRouteStop toRouteStop = this.data.routeStops[toRouteStopIndex];
						
				//double minCostToTarget = calculateMinCostToTarget(toRouteStop, toFacility);
				//connectUnconnectedPathElements(this.arrivalPathPerRouteStop[toRouteStopIndex], parameters, firstRouteStop, route, toRouteStop, 
				//		minCostToTarget);

				RouteStopPathElementExtension peExt = this.arrivalPathPerRouteStop[firstRouteStopIndex];
				if(peExt == null) {
					firstRouteStopIndex = toRouteStopIndex;
					continue;
				} 
				
				double minCostToTarget = calculateCostlyMinCostToTarget(toRouteStop, egressStops);
				

				for(PathElement loopPE : peExt.getPEs()) {
					
					PathElement boardingPE = loopPE.isWalkOrWait() ? loopPE : loopPE.comingFrom;
					double prevCost = boardingPE.costAtDeparture;
					int previousStopDepartureTime = boardingPE.nextStopDepartureTime;
					int currentDepartureTime = previousStopDepartureTime - boardingPE.toRouteStop.departureOffset;
					int vehicleArrivalTime = currentDepartureTime + toRouteStop.arrivalOffset;
					int vehicleDepartureTime = currentDepartureTime + toRouteStop.departureOffset;
					int inVehicleTime = vehicleArrivalTime - previousStopDepartureTime;
					double inVehicleCost = inVehicleTime * -marginalUtilityOfTravelTime_utl_s;
					double arrivalCost = prevCost + inVehicleCost;
					double arrivalPotential = arrivalCost - vehicleArrivalTime * -parameters.getMarginalUtilityOfWaitingPt_utl_s();
					int waitingInVehicleTime = vehicleDepartureTime - vehicleArrivalTime;
					double waitingInVehicleCost = waitingInVehicleTime * -marginalUtilityOfTravelTime_utl_s;
					double costAtDeparture = arrivalCost + waitingInVehicleCost;

					if(arrivalCost + minCostToTarget > this.bestArrivalCost) {
						continue;
					}
					double distance = Double.NaN;

					//Explore routes
					PathElement	pe = new PathElement(boardingPE,  toRouteStop, vehicleArrivalTime, vehicleDepartureTime, 
							arrivalCost, costAtDeparture, distance, boardingPE.transferCount, 
							arrivalPotential,	costAtDeparture - vehicleDepartureTime * -parameters.getMarginalUtilityOfWaitingPt_utl_s());

					if(this.arrivalPathPerRouteStop[toRouteStopIndex] == null || this.arrivalPathPerRouteStop[toRouteStopIndex].size()==0) {
						this.arrivalPathPerRouteStop[toRouteStopIndex] = new RouteStopPathElementExtension(pe);
					} else {
						this.arrivalPathPerRouteStop[toRouteStopIndex].insertIfNotDominated(pe);
					}
					boolean inserted = true;
					if(this.arrivalPathPerStop[toRouteStop.stopFacilityIndex] == null || this.arrivalPathPerStop[toRouteStop.stopFacilityIndex].size()==0) {
						this.arrivalPathPerStop[toRouteStop.stopFacilityIndex] = new StopPathElementExtension(pe);
					} else {
						inserted = this.arrivalPathPerStop[toRouteStop.stopFacilityIndex].insertIfNotDominated(pe);
					}
					if(inserted) {
						this.improvedStops.set(toRouteStop.stopFacilityIndex);
					}
					// It can actually happen, that pe is not inserted, but still has lowest cost!
					checkForBestArrival(arrivalCost, toFacility,  pe);
				}
				firstRouteStopIndex = toRouteStopIndex;
			}
		}
	}

	/*
	private void connectUnconnectedPathElements( RouteStopPathElementExtension nextPEExt, RaptorParameters parameters, RRouteStop firstRouteStop,
			RRoute route, RRouteStop toRouteStop, double distanceToTarget) {
		double transferCostBase = parameters.getTransferPenaltyFixCostPerTransfer();
		double marginalUtilityOfWaitingPt_utl_s = parameters.getMarginalUtilityOfWaitingPt_utl_s();
		double marginalUtilityOfTravelTime_utl_s = parameters.getMarginalUtilityOfTravelTime_utl_s(toRouteStop.mode);

		if(nextPEExt != null) {
			LinkedList<PathElement> toBeAdded = new LinkedList<PathElement>();
			for(Iterator<PathElement> it = nextPEExt.getPEs().iterator(); it.hasNext(); ) {
				PathElement pe = it.next();
				it.remove();
				double arrivalCost = pe.cost;
				int departureIndex = findNextDepartureIndex(route, firstRouteStop, pe.arrivalTime);
				if(departureIndex >= 0) {
					int currentDepartureTime = this.data.departures[departureIndex];
					int agentFirstArrivalTime = pe.arrivalTime;
					int vehicleArrivalTime = currentDepartureTime + firstRouteStop.arrivalOffset;
					int nextStopDepartureTime = currentDepartureTime + firstRouteStop.departureOffset;
					int boardingTime = (agentFirstArrivalTime < vehicleArrivalTime) ? vehicleArrivalTime : agentFirstArrivalTime;
					int waitingTime = boardingTime - agentFirstArrivalTime;
					if(waitingTime > RunMatsim.maxWait) {
						continue;
					}
					double waitingCost = waitingTime * -marginalUtilityOfWaitingPt_utl_s;
					int inVehicleWaitingTime = nextStopDepartureTime - boardingTime;
					double inVehicleWaitingCost = inVehicleWaitingTime * -marginalUtilityOfTravelTime_utl_s;
					arrivalCost += waitingCost + inVehicleWaitingCost + transferCostBase;
					if(arrivalCost > this.bestArrivalCost) {
						continue;
					} else {
						PathElement newPE = new PathElement(pe, toRouteStop, pe.firstDepartureTime, nextStopDepartureTime,
								agentFirstArrivalTime, arrivalCost, 0., pe.transferCount + 1, true,
								pe.initialStop, currentDepartureTime, ,arrivalCost - nextStopDepartureTime * -marginalUtilityOfWaitingPt_utl_s);
						toBeAdded.add(newPE);
					}
				}
			}
			while(!toBeAdded.isEmpty()) {
				nextPEExt.insertIfNotDominated(toBeAdded.pollLast());
			}
		}
	}
	 */



	private void checkForBestArrival(double arrivalCost, Facility toFacility, PathElement pe) {
		double distanceToTarget = calculateExactDistanceToTarget(pe.toRouteStop.routeStop.getStopFacility(), toFacility );
		if(distanceToTarget <= RunMatsim.reachedDistance) {
			this.reached  = true;
		}
		//double distanceToTarget = MyRaptorUtils.upperBoundOfEuclideanDistance(pe.toRouteStop.routeStop.getStopFacility().getCoord(), toFacility.getCoord());
		double totalCost = arrivalCost + distanceToTarget * -RunMatsim.walkTimeUtility;
		if (totalCost < this.bestArrivalCost) {
			this.bestArrivalCost = totalCost;
			this.bestArrivalPath = pe;
		}
	}

	private double calculateExactDistanceToTarget(Facility fromFacility, Facility toFacility) {
		return CoordUtils.calcProjectedEuclideanDistance(fromFacility.getCoord(), toFacility.getCoord());
	}

	private int findNextDepartureIndex(RRoute route, RRouteStop routeStop, int time) {
		int depTimeAtRouteStart = time - routeStop.departureOffset;
		int fromIndex = route.indexFirstDeparture;
		int toIndex = fromIndex + route.countDepartures;

		//Often the latest departure is too late. We do a quick skip when that happens.
		if(this.data.departures[toIndex-1] < depTimeAtRouteStart) {
			return -1;
		} else if (this.data.departures[fromIndex] >= depTimeAtRouteStart  ) { // or too early
			return fromIndex;
		}

		if(route.countDepartures <= 6) {
			//We have already checked the first
			for(int i = fromIndex +1 ; i < toIndex; i++) {
				if(this.data.departures[i] >= depTimeAtRouteStart) {
					return i;
				}
			}
			return -1;
		} else {
			//We have already checked the first and last one
			int pos = Arrays.binarySearch(this.data.departures, fromIndex + 1, toIndex - 1, depTimeAtRouteStart);
			if (pos < 0) {
				// binarySearch returns (-(insertion point) - 1) if the element was not found, which will happen most of the times.
				// insertion_point points to the next larger element, which is the next departure in our case
				// This can be transformed as follows:
				// retval = -(insertion point) - 1
				// ==> insertion point = -(retval+1) .
				pos = -(pos + 1);
			}
			if (pos >= toIndex) {
				// there is no later departure time
				return -1;
			}
			return pos;

		} 
	}


	private void handleTransfers(boolean strict, RaptorParameters raptorParams, Facility toFacility, List<MyInitialStop> egressStops) {
		this.improvedRouteStopIndices.clear();
		this.tmpImprovedStops.clear();

		double transferCostBase = raptorParams.getTransferPenaltyFixCostPerTransfer();
		double margUtilityTransitWalk = raptorParams.getMarginalUtilityOfTravelTime_utl_s(TransportMode.walk); // replaced TransportMode.transit_walk with walk


		for (int stopIndex = this.improvedStops.nextSetBit(0); stopIndex >= 0; stopIndex = this.improvedStops.nextSetBit(stopIndex + 1)) {

			for(PathElement fromPE : this.arrivalPathPerStop[stopIndex].getPEs()) {
				RRouteStop fromRouteStop = fromPE.toRouteStop;

				double prevCost = fromPE.costAtArrival;
				double minCostToTarget = calculateCostlyMinCostToTarget(fromRouteStop,egressStops);
				double minArrivalCost = prevCost +  minCostToTarget;
				if (minArrivalCost > this.bestArrivalCost) {
					continue;
				}

				int firstTransferIndex = fromRouteStop.indexFirstTransfer;
				int lastTransferIndex = firstTransferIndex + fromRouteStop.countTransfers;
				for (int transferIndex = firstTransferIndex; transferIndex < lastTransferIndex; transferIndex++) {
					RTransfer transfer = this.data.transfers[transferIndex];
					int toRouteStopIndex = transfer.toRouteStop;
					RRouteStop toRouteStop = this.data.routeStops[toRouteStopIndex];
					int newArrivalTime = fromPE.arrivalTime + transfer.transferTime; 
					double newCostAtDeparture = prevCost + transferCostBase + transfer.transferTime * -margUtilityTransitWalk;			
					minCostToTarget = calculateCostlyMinCostToTarget(toRouteStop, egressStops);
					if (newCostAtDeparture  + minCostToTarget > this.bestArrivalCost) {
						continue;
					}

					//madsp
					RRoute route = this.data.routes[toRouteStop.transitRouteIndex];
					int departureIndex = findNextDepartureIndex(route, toRouteStop, newArrivalTime);
					if(departureIndex >= 0) {
						int routeDepTime = this.data.departures[departureIndex];
						int arrOffset = toRouteStop.routeStop.getArrivalOffset();
						int depOffset = toRouteStop.routeStop.getDepartureOffset();

						int vehicleArrivalTime = routeDepTime + arrOffset;
						int vehicleDepartureTime = routeDepTime + depOffset;

						int boardingTime = (vehicleArrivalTime < newArrivalTime) ? newArrivalTime : vehicleArrivalTime;
						int waitingTime =  boardingTime - newArrivalTime;
						if(waitingTime > RunMatsim.maxWait) {
							continue;
						}
						double waitingCost = waitingTime * -raptorParams.getMarginalUtilityOfWaitingPt_utl_s();
						int inVehicleWaitingTime = vehicleDepartureTime - boardingTime;
						String mode = toRouteStop.route.getTransportMode();
						double inVehicleWaitingCost = inVehicleWaitingTime * -raptorParams.getMarginalUtilityOfTravelTime_utl_s(mode);
						newCostAtDeparture +=  waitingCost + inVehicleWaitingCost;

						if (newCostAtDeparture  + minCostToTarget > this.bestArrivalCost) {
							continue;
						}

						//Handle transfers
						PathElement pe = new PathElement(fromPE, toRouteStop,  newArrivalTime, vehicleDepartureTime,
								Double.NaN, newCostAtDeparture, transfer.transferTime, fromPE.transferCount + 1,  
								Double.NaN, newCostAtDeparture - vehicleDepartureTime * -raptorParams.getMarginalUtilityOfWaitingPt_utl_s());

						boolean inserted = true;
						if(this.arrivalPathPerRouteStop[toRouteStopIndex]  == null) {
							this.arrivalPathPerRouteStop[toRouteStopIndex] = new RouteStopPathElementExtension(pe);
						} else {
							inserted = this.arrivalPathPerRouteStop[toRouteStopIndex].insertIfNotDominated(pe);
						}
						if(inserted) { 
							this.improvedRouteStopIndices.set(toRouteStopIndex);
						} 
					}
				}
			}
		}
		/*
		//"parallel update". now copy over the newly improved data after all transfers were handled
		for (int stopIndex = this.tmpImprovedStops.nextSetBit(0); stopIndex >= 0; stopIndex = this.tmpImprovedStops.nextSetBit(stopIndex + 1)) {
			LinkedList<PathElement> potentiallyImprovingPathElements =  this.tmpArrivalPathPerStop[stopIndex];
			PathElement pe = potentiallyImprovingPathElements.pollFirst();
			while(pe != null) {
				if(this.arrivalPathPerStop[stopIndex] == null) {
					this.arrivalPathPerStop[stopIndex] = new PathElementExtension(pe);
				} else {
					this.arrivalPathPerStop[stopIndex].insertIfNotDominated(pe);
				}
				pe = potentiallyImprovingPathElements.pollFirst();
			}
		}
		 */
	}


	
	private double calculateCostlyMinCostToTarget(RRouteStop routeStop, List<MyInitialStop> egressStops) {
		int fromStopInt = routeStop.routeStop.getStopFacility().getArrayId();
		if(minCosts[fromStopInt] > 0) {
			return minCosts[fromStopInt];
		}
		
		double minCost = Double.POSITIVE_INFINITY;
		for(MyInitialStop toStop : egressStops) {
			double cost = toStop.accessCost + RunMatsim.distances[fromStopInt][toStop.stop.getArrayId()];
			if(cost < minCost) {
				minCost = cost;
			}
		}
		minCosts[fromStopInt] = minCost;
		return minCost;
	}

	private PathElement findLeastCostArrival(Facility toFacility) {
		PathElement pe = this.bestArrivalPath;
		PathElement leastCostPath = null;
		if (pe != null) {
			int lastPartDistance = (int)Math.ceil(calculateExactDistanceToTarget(pe.toRouteStop.routeStop.getStopFacility(), toFacility));
			double lastPartCost = lastPartDistance * -RunMatsim.walkTimeUtility;
			leastCostPath = new PathElement(pe, null, pe.arrivalTime + lastPartDistance , pe.arrivalTime + lastPartDistance,
					pe.costAtArrival + lastPartCost, pe.costAtArrival + lastPartCost, lastPartDistance, pe.transferCount,
					Double.NaN, Double.NaN); // this is the egress leg
		}
		return leastCostPath;
		/*
		double leastCost = Double.POSITIVE_INFINITY;
		PathElement leastCostPath = null;

		for (Map.Entry<TransitStopFacility, InitialStop> e : destinationStops.entrySet()) {
			TransitStopFacility stop = e.getKey();
			int stopIndex = this.data.stopFacilityIndices.get(stop);
			if(this.arrivalPathPerStop[stopIndex] != null && this.arrivalPathPerStop[stopIndex].size() > 0) {
				PathElement pe = this.arrivalPathPerStop[stopIndex].getFirst();
				if (pe != null) {
					InitialStop egressStop = e.getValue();
					double arrivalTime = pe.arrivalTime + egressStop.accessTime;
					double totalCost = pe.auxCost + egressStop.accessCost;
					if ((totalCost < leastCost) || (totalCost == leastCost && pe.transferCount < leastCostPath.transferCount)) {
						leastCost = totalCost;
						leastCostPath = (pe, null, pe.firstDepartureTime, Double.NaN, arrivalTime,
								pe.arrivalTravelCost + egressStop.accessCost, pe.arrivalTransferCost, egressStop.distance, pe.transferCount,
								true, egressStop, Double.NaN, Double.NaN); // this is the egress leg
					}
				}
			}
		}
		return leastCostPath;
		 */
	}

	private static MyRaptorRoute createRaptorRoute(Facility fromFacility, Facility toFacility, PathElement destinationPathElement, int departureTime) {

		LinkedList<PathElement> pes = new LinkedList<>();
		double arrivalCost = Double.POSITIVE_INFINITY;
		if (destinationPathElement != null) {
			arrivalCost = destinationPathElement.costAtArrival;
			PathElement pe = destinationPathElement;
			while (pe.comingFrom != null) {
				pes.addFirst(pe);
				pe = pe.comingFrom;
			}
			pes.addFirst(pe);
		}
		if (pes.size() == 2 && pes.get(0).isWalkOrWait() && pes.get(1).isWalkOrWait()) {
			// it's only access and egress, no real pt trip
			arrivalCost = Double.POSITIVE_INFINITY;
			pes.clear();
		}

		MyRaptorRoute raptorRoute = new MyRaptorRoute(fromFacility, toFacility, arrivalCost);

		int time = departureTime;
		MyTransitStopFacilityImpl fromStop = null;
		int peCount = pes.size();
		int i = -1;

		//madsp
		PathElement prevPe = null;
		for (PathElement pe : pes) {
			i++;
			MyTransitStopFacilityImpl toStop = pe.toRouteStop == null ? null : pe.toRouteStop.routeStop.getStopFacility();
			int travelTime = pe.arrivalTime - time;
			if (pe.isWalkOrWait()) {
				boolean differentFromTo = (fromStop == null || toStop == null) || (fromStop != toStop);
				// do not create a transfer-leg if we stay at the same stop facility
				if (differentFromTo) {
					// add (peCount > 2 || peCount == 2 && !pes.get(0).isTransfer) && to catch case of only access and egress 
					// legs without a real leg in between which was previously caught above by 
					// pes.size() == 2 && pes.get(0).isTransfer && pes.get(1).isTransfer
					//
					// in case of peCount < 2 there should be no effect, because peCount-2 < 0 and i will be 0, so i!=peCount - 2
					// TODO check
					if ((peCount > 2 || peCount == 2 && !pes.get(0).isWalkOrWait()) && i == peCount - 2 ) {
						// the second last element is a transfer, skip it so it gets merged into the egress_walk
						// but it can only be merged if it is not intermodal...
						continue;
					}

					// Fixing a L issue.
					if(pe.comingFrom != prevPe) {
						Coord fromCoord = fromFacility.getCoord();
						if(pe.comingFrom.comingFrom != null) {
							fromStop = pe.comingFrom.comingFrom.toRouteStop.routeStop.getStopFacility();
							fromCoord = fromStop.getCoord();
						}
						Coord toCoord = toStop == null ? toFacility.getCoord() : toStop.getCoord();	
						pe.distance = CoordUtils.calcProjectedEuclideanDistance(fromCoord, toCoord);
						travelTime = (int) Math.ceil(pe.distance / RunMatsim.walkSpeed * RunMatsim.walkBeelineDistanceFactor);
					}
					String mode = TransportMode.walk;
					raptorRoute.addNonPt(pe.comingFrom == null ? null : pe.comingFrom.toRouteStop.routeStop,
							pe.toRouteStop == null ? null : pe.toRouteStop.routeStop, 
									time, travelTime, pe.distance, mode);
				}
			} else {
				MyTransitLineImpl line = pe.toRouteStop.line;
				MyTransitRouteImpl route = pe.toRouteStop.route;
				raptorRoute.addPt(line, route, pe.toRouteStop.mode, time, pe.nextStopDepartureTime, pe.arrivalTime, pe.distance, 
						pe.comingFrom.toRouteStop.routeStop, pe.toRouteStop.routeStop);
			}

			time = pe.arrivalTime;
			fromStop = toStop;
			prevPe = pe;
		}

		return raptorRoute;
	}


	public static final class TravelInfo {
		public final Id<MyTransitStopFacilityImpl> departureStop;
		public final int transferCount;

		/** The departure time at the first stop */
		public final double ptDepartureTime;
		/** The arrival time at the last stop */
		public final double ptArrivalTime;
		/** The travel time between the first stop (departure) and the last stop (arrival). */
		public final double ptTravelTime;
		/** The cost for travelling from the first stop to the last stop. Not included are accessCost or cost for waiting at the first stop. */
		public final double travelCost;

		/** The time required to travel from the origin to the first stop. Not included in {@link #ptTravelTime}. */
		public final double accessTime;
		public final double accessCost;

		/** the time an agent has to wait at the first stop until the first pt vehicle departs. */
		public final double waitingTime;
		/** the costs an agent accumulates due to waiting at the first stop until the first pt vehicle departs. */
		public final double waitingCost;

		private final PathElement destinationPath;

		TravelInfo(Id<MyTransitStopFacilityImpl> departureStop, double departureTime, double arrivalTime, double travelCost, double accessTime, double accessCost, int transferCount, double waitingTime, double waitingCost, PathElement destinationPath) {
			this.departureStop = departureStop;
			this.ptDepartureTime = departureTime;
			this.ptArrivalTime = arrivalTime;
			this.ptTravelTime = arrivalTime - departureTime;
			this.travelCost = travelCost;
			this.accessTime = accessTime;
			this.accessCost = accessCost;
			this.transferCount = transferCount;
			this.waitingTime = waitingTime;
			this.waitingCost = waitingCost;
			this.destinationPath = destinationPath;
		}

		public MyRaptorRoute getRaptorRoute() {
			PathElement firstPath = this.destinationPath;
			while (firstPath.comingFrom != null) {
				firstPath = firstPath.comingFrom;
			}

			Facility fromFacility = firstPath.toRouteStop.routeStop.getStopFacility();
			Facility toFacility = this.destinationPath.toRouteStop.routeStop.getStopFacility();
			return createRaptorRoute(fromFacility, toFacility, this.destinationPath, firstPath.arrivalTime);
		}

		public boolean isWalkOnly() {
			if (this.destinationPath.comingFrom == null) {
				return true;
			}
			PathElement pe = this.destinationPath;
			while (pe != null) {
				if (!pe.isWalkOrWait()) {
					return false;
				}
				pe = pe.comingFrom;
			}
			return true;
		}
	}

}
