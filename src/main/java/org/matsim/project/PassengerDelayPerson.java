package org.matsim.project;

import java.util.HashMap;

import org.apache.log4j.Logger;

import java.util.AbstractMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Route;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.router.StageActivityTypes;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.facilities.Facility;
import org.matsim.pt.router.FakeFacility;
import org.matsim.pt.routes.ExperimentalTransitRoute;
import org.matsim.core.population.routes.GenericRouteImpl;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import ch.sbb.matsim.routing.pt.raptor.LeastCostRaptorRouteSelector;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptor;

public class PassengerDelayPerson {

	Logger logger = Logger.getLogger(PassengerDelayPerson.class);

	private final double WALKING_SPEED = 1.0; //m/s. Assuming a speed of 1.3 and an additional length of 1.3.

	SwissRailRaptor raptor;
	List<Leg> currentRoute;
	Id<Person> id;
	Plan plan;
	private Status status;
	private double nextTimeOfInterest;
	Facility destinationLocation;
	Facility nextLocationOfInterest;
	Facility currentLocation;

	private double currentClock;

	private int stopwatch;

	private Id<Departure> currentDepartureId;

	private double timeForLastShortestPathSearch = -1;

	private double nextEgressTime;

	LinkedList<PassengerDelayEvent> events; 

	public static enum Status 
	{ 
		ACTIVITY, STATION, VEHICLE, WALK,; 
	} 

	PassengerDelayPerson(Id<Person> id, Plan plan){
		this.id = id;
		this.plan = plan;
		this.status = Status.ACTIVITY;
		this.currentLocation = new MyFakeFacility(((Activity) plan.getPlanElements().get(0)).getCoord());
		this.nextTimeOfInterest = ((Activity) plan.getPlanElements().get(0)).getEndTime();
		this.destinationLocation = new MyFakeFacility(((Activity) plan.getPlanElements().get(2)).getCoord());
		this.currentRoute = null;
		this.nextEgressTime = Double.MAX_VALUE;
		this.stopwatch = 0;
		this.events = new LinkedList<PassengerDelayEvent>();
		this.events.add(new PassengerDelayEvent(PassengerDelayEvent.EventType.ACTIVITY_START,
				0.0, this.currentLocation, this.currentLocation, "ACTIVITY"));
	}

	void advance(){	
		if(RunMatsim.elaborateLogging){
			System.out.println(id);
		}		
		switch(this.status){
		case ACTIVITY: 
			activityFunction();
			break;
		case STATION:
			stationFunction();
			break;
		case VEHICLE:
			stillInVehicleFunction();
			break;
		case WALK:
			GenericRouteImpl route = (GenericRouteImpl) currentRoute.get(0).getRoute();
			if(route.getDistance()==0){
				System.err.println("We need to advance in order to fix this stupid problem : (");
			}
			walkFunction();	
			break;
		default:
			System.err.println("Hov hov...");
			// Some exception
			break;
		}	

		if(RunMatsim.elaborateLogging){
			System.out.println("[" + currentLocation.getCoord().getX() + " , " + currentLocation.getCoord().getY() + "]");
			System.out.println(" towards ");
			if(nextLocationOfInterest != null){
				System.out.println("[" + nextLocationOfInterest.getCoord().getX() + " , " + 
						nextLocationOfInterest.getCoord().getY() + "]");
			}
			System.out.println(this.status);
			System.out.println("End");
		}
	}

	void setRaptor(SwissRailRaptor raptor){
		this.raptor = raptor;
	}

	private void activityFunction() {	
		if(this.stopwatch  + 300 > this.nextTimeOfInterest){ 
			this.plan.getPlanElements().remove(0); // Removed the activity
			this.currentClock = this.nextTimeOfInterest; // Time is now actEndTime;
			String mode = ((Leg) this.plan.getPlanElements().get(0)).getMode();
			if(mode.equals(TransportMode.pt)){
				if(this.stopwatch  > timeForLastShortestPathSearch ){ // Time for antoher path search.
					currentRoute = calculateShortestPath(currentLocation, destinationLocation, currentClock);
				}
				if(RunMatsim.elaborateLogging){
					System.out.println("### Activity -> Walk");
				}
				this.status = Status.WALK;
				if(((GenericRouteImpl) this.currentRoute.get(0).getRoute()).getDistance() == 0){
					System.err.println("This is acting weirdly");
				}


				events.add(new PassengerDelayEvent(PassengerDelayEvent.EventType.WALK_START, 
						this.currentClock, this.currentLocation, this.nextLocationOfInterest, "WALK"));
				walkFunction();
			} else if(mode.equals("Teleportation")){ //If teleportation: Skip the leg.
				this.plan.getPlanElements().remove(0); 
				Facility oldLocation = this.currentLocation;
				this.currentLocation = this.destinationLocation;
				if(this.plan.getPlanElements().size()==1){
					this.nextTimeOfInterest = Double.MAX_VALUE; // Finished
				} else {
					Activity act = ((Activity) this.plan.getPlanElements().get(0));
					double actEndTime = act.getEndTime();
					if(this.nextTimeOfInterest < actEndTime){ // actEndTime
						this.nextTimeOfInterest = actEndTime;
					}
					act = ((Activity) this.plan.getPlanElements().get(2));
					this.destinationLocation = new MyFakeFacility(act.getCoord()); // Location of next activity
				}	
				if(RunMatsim.elaborateLogging){
					System.out.println("### Activity -> Teleportation -> Activity");
				}
				this.status = Status.ACTIVITY;
				events.add(new PassengerDelayEvent(PassengerDelayEvent.EventType.ACTIVITY_START, 
						this.currentClock, oldLocation, this.currentLocation,"TELEPORTATION"));
				activityFunction();
			}
		} else { // Activity has not finished yet.
			if(RunMatsim.elaborateLogging){
				System.out.println("Waiting until " + nextTimeOfInterest + ". That is " +
						(nextTimeOfInterest - this.stopwatch  - 300)/3600 + " more hours");
			}
			currentClock = this.stopwatch ;

		}
	}

	private List<Leg> calculateShortestPath( Facility fromFacility, Facility toFacility, double time) {		

		this.timeForLastShortestPathSearch = this.stopwatch ; // Updated latest path search clock;

		List<Leg> trip = this.raptor.calcRoute(fromFacility, toFacility, time, null);		
		Leg leg = trip.get(0);
		Route route = leg.getRoute();
		if(route instanceof GenericRouteImpl && ((GenericRouteImpl) route).getDistance() == 0){
			// The route starts with a 0-distance walk-subleg which is removed.
			if(trip.size() == 1){
				System.err.println("Shortest path is a null route " + this.status + " " + this.id);

				System.exit(-1);
			}
			trip.remove(0);
		}
		leg=trip.get(0);
		route=leg.getRoute();

		if(route instanceof ExperimentalTransitRoute){
			//No need to do anything?
			nextLocationOfInterest = RunMatsim.facilities.get(((ExperimentalTransitRoute) route).getAccessStopId());
		} else if(route instanceof GenericRouteImpl){
			// Walk to next stop/act instead of taking pt
			this.nextLocationOfInterest = extractNextLocationOfInterest(trip);
		}
		return trip;
	}
	
	private Facility extractNextLocationOfInterest(List<Leg> trip){
		Facility nextLOI;
		if(trip.size() == 1){ //Walk directly to destination.
			nextLOI = destinationLocation;
		} else {
			Route route = trip.get(1).getRoute();			 // Get next proper pt-leg	
			nextLOI= RunMatsim.facilities.get(((ExperimentalTransitRoute) route).getAccessStopId());
		}
		return nextLOI;
	}

	private List<Leg> whereIsDepartureAtTime() {

		//Make sure that you don't overshoot the egress....


		//This cannot happen as long as we include all departures that ended at least 10 (more) minutes before stopwatch.
		try{
			if(!RunMatsim.dep2Route.containsKey(this.currentDepartureId)){
				System.err.println("This is Syria!" + id + " currentDepId: " + this.currentDepartureId + " @" + stopwatch);
				System.exit(-1);
				return currentRoute;
			}
			// Can most likely be solved by increasing the buffer from 10 to 60 minutes...
			// Or doing something smarter / more efficient.
			// But investigate the problem first....
		} catch(Exception e){
			System.out.println("Current departure: " + this.currentDepartureId);
			System.out.println("Current location: " + this.currentLocation.getCoord());
			System.out.println("Current person: " + this.id);
			e.printStackTrace();
			System.exit(-1);
		}

		Id<TransitRoute> routeId = RunMatsim.dep2Route.get(this.currentDepartureId);
		double departureTime = RunMatsim.route2DepartureTime.get(routeId).get(this.currentDepartureId);
		double auxTime =  this.currentClock - departureTime;
		RunMatsim.route2OrderedStopOffset.get(routeId);
		Entry<Double, TransitStopFacility> entry = RunMatsim.route2OrderedStopOffset.get(routeId).ceilingEntry(auxTime);
		if(entry == null){
			//Some kind of exception
			this.nextLocationOfInterest = RunMatsim.route2OrderedStopOffset.get(routeId).lastEntry().getValue();
			//STATION
			//OR WALK
		} else {
			this.nextLocationOfInterest = entry.getValue();
		}
		Id<TransitStopFacility> stopId = ((TransitStopFacility) nextLocationOfInterest).getId();
		double arrivalTime = departureTime + RunMatsim.route2StopArrival.get(routeId).get(stopId);
		this.nextTimeOfInterest = Math.max(arrivalTime, this.currentClock); 


		return calculateShortestPath(this.nextLocationOfInterest, this.destinationLocation,
				this.nextTimeOfInterest);
	}

	private void stationFunction() {
		if(this.stopwatch  > timeForLastShortestPathSearch){ // Time for a new path search
			currentRoute = calculateShortestPath(this.currentLocation, this.destinationLocation, this.currentClock);
		} // Otherwise, it has been done earlier on at this time step, and thus not necessary again.

		Route route = currentRoute.get(0).getRoute();
		if(route instanceof GenericRouteImpl && ((GenericRouteImpl) route).getDistance() == 0){
			currentRoute.remove(0);
			route = currentRoute.get(0).getRoute();
		}
		if(route instanceof GenericRouteImpl){
			// It is faster to walk.
			this.status = Status.WALK;
			if(RunMatsim.elaborateLogging){
				System.out.println("### Station -> Walk");
			}

			events.add(new PassengerDelayEvent(PassengerDelayEvent.EventType.WALK_START, 
					this.currentClock, this.currentLocation, this.nextLocationOfInterest, "SUDDEN_WALK"));
			walkFunction();
		} else if (route instanceof ExperimentalTransitRoute) { // Current fastest path is through boarding a vehicle.
			ExperimentalTransitRoute expRoute = (ExperimentalTransitRoute) route;
			Id<TransitStopFacility> fromStopId = expRoute.getAccessStopId();
			Id<TransitStopFacility> toStopId = expRoute.getEgressStopId();
			nextLocationOfInterest = RunMatsim.facilities.get(toStopId);
			Leg leg = this.currentRoute.get(0);
			double expectedEgressTime = leg.getDepartureTime() + leg.getTravelTime();

			Entry<Id<Departure>,Double> entry =
					determineDepartureId(route, expectedEgressTime, fromStopId, toStopId);
			double boardingTime = entry.getValue();

			if( this.stopwatch  + 300 > boardingTime){ //Boarding happens before next timestep
				this.status = Status.VEHICLE;
				if(RunMatsim.elaborateLogging){
					System.out.println("### Station -> Vehicle");
				}
				this.currentDepartureId = entry.getKey();
				this.currentClock = boardingTime;
				this.nextEgressTime = expectedEgressTime;

				ExperimentalTransitRoute etr = (ExperimentalTransitRoute) route;
				events.add(new PassengerDelayEvent(PassengerDelayEvent.EventType.BOARDING, 
						this.currentClock, this.currentLocation, RunMatsim.facilities.get(etr.getEgressStopId()),
						etr.getLineId().toString()));
				entersVehicleFunction();
			} else {
				//Wait until next time to see if a better opportunity arises.
				this.currentClock = this.stopwatch  + 300;
			}
		} else {
			System.err.println("This really shouldn't happen.. Last line of stationFunction");
			System.exit(-1);
		}

	}

	private Entry<Id<Departure>,Double> determineDepartureId(Route route, double egressTime,
			Id<TransitStopFacility> fromStopId, Id<TransitStopFacility> toStopId) {

		Id<Departure> bestDepartureId = null;
		double fromDepartureOffset = -1;
		double toArrivalOffset = -1;

		double departureTime = -1;
		int index = 0;
		Id<TransitRoute> routeId = ((ExperimentalTransitRoute) route).getRouteId();	
		List<TransitRouteStop> stops = RunMatsim.route2Stops.get(routeId);

		while(bestDepartureId == null && index != (stops.size() -1)){ // Until a valid departure has been found;

			fromDepartureOffset = -1;
			toArrivalOffset = -1;

			for(; index < stops.size(); index++){
				TransitRouteStop stop = stops.get(index);
				Id<TransitStopFacility> stopId = stop.getStopFacility().getId();
				if(stopId == fromStopId){ //May happen more than once, but that's okay!
					fromDepartureOffset = stop.getDepartureOffset();
				} else if(fromDepartureOffset >= 0 && stopId == toStopId){
					toArrivalOffset = stop.getArrivalOffset();
					break;
				}
			}

			departureTime = egressTime - toArrivalOffset;
			bestDepartureId = RunMatsim.route2Departure.get(routeId).get(departureTime);
		}

		double accessTime = departureTime + fromDepartureOffset;
		Entry<Id<Departure>, Double> entry = new AbstractMap.SimpleEntry(bestDepartureId, accessTime);
		if(bestDepartureId == null){
			System.err.println("Departure " + bestDepartureId + " of route " + routeId + 
					" not found! " + id + " @" + this.stopwatch );
			System.err.println("Access time: " + accessTime);
			System.err.println("departureTime " + departureTime);
			System.err.println("fromDepartureOffset: " + fromDepartureOffset);
			System.err.println("toArrivalOffset: " + toArrivalOffset);
			System.err.println("EgressTime: " + egressTime);
			System.err.println("The departure we are looking for departs from " +
					fromStopId + " at time >= " + this.currentRoute.get(0).getDepartureTime());
			System.err.println("And terminates at stop " + toStopId + " at time = " +  egressTime);
			System.exit(-1);
		}
		if(RunMatsim.elaborateLogging){
			System.out.println("Best departure match is: " + bestDepartureId + " @" + accessTime);
		}
		return entry;
	}

	private void entersVehicleFunction() {
		if(this.stopwatch  + 300 > this.nextEgressTime){ // Have to get off before next time step
			Leg leg = this.currentRoute.get(0);
			Route route = this.currentRoute.get(0).getRoute();
			Id<TransitStopFacility> stopId = ((ExperimentalTransitRoute) route).getEgressStopId();
			this.currentDepartureId = null;
			this.currentClock = leg.getDepartureTime() + leg.getTravelTime();
			this.currentLocation = RunMatsim.facilities.get(stopId);
			this.currentRoute.remove(0);
			route = this.currentRoute.get(0).getRoute();
			if(route instanceof GenericRouteImpl && ((GenericRouteImpl) route).getDistance() == 0){
				this.currentRoute.remove(0);
				route = this.currentRoute.get(0).getRoute();
			}

			if(route instanceof GenericRouteImpl){
				this.status = Status.WALK;
				if(RunMatsim.elaborateLogging){
					System.out.println("### Vehicle -> Walk");
				}
				if(((GenericRouteImpl) route).getDistance() == 0){
					System.err.println("WHAT THE FUCK!!!!!");
				}
				events.add(new PassengerDelayEvent(PassengerDelayEvent.EventType.WALK_START, 
						this.currentClock, this.currentLocation, this.nextLocationOfInterest, "WALK"));
				walkFunction();
			} else {
				//Either station or walk....
				this.status = Status.STATION;
				if(RunMatsim.elaborateLogging){
					System.out.println("### Vehicle -> Station");
				}
				events.add(new PassengerDelayEvent(PassengerDelayEvent.EventType.WAIT_START, 
						this.currentClock, this.currentLocation, this.currentLocation, "WAIT"));
				stationFunction();
			}
		} else { // Stay inside the same vehicle;	
			this.currentClock = this.stopwatch  + 300;
		}
	}


	private void stillInVehicleFunction() {
		currentRoute = whereIsDepartureAtTime();
		if(RunMatsim.elaborateLogging){
			System.out.println(nextTimeOfInterest);
		}
		Route route = this.currentRoute.get(0).getRoute();
		if(route instanceof ExperimentalTransitRoute && 
				isSameDeparture( ((ExperimentalTransitRoute) route).getRouteId() ) ){
			Leg leg = this.currentRoute.get(0);
			Id<TransitStopFacility> stopId = ((ExperimentalTransitRoute) route).getEgressStopId();
			this.nextLocationOfInterest = RunMatsim.facilities.get(stopId);
			this.nextEgressTime = leg.getDepartureTime() + leg.getDepartureTime();
		} else { //It is a walking leg or a pt-leg with a different route, i.e. a walk or a transfer, i.e. get off!
			this.nextEgressTime = this.nextTimeOfInterest;
		}


		if(this.stopwatch  + 300 > this.nextEgressTime){ // Have to get off before next time step
			route = this.currentRoute.get(0).getRoute();
			this.currentDepartureId = null;
			this.currentClock = this.nextEgressTime;
			this.currentLocation = this.nextLocationOfInterest;
			if(route instanceof GenericRouteImpl){
				if(((GenericRouteImpl) route).getDistance() == 0){ // Plain transfer
					System.err.println("Does this ever happen!?!?!?!?!?!?!? Might be impossible" +
							".... and is equivalent to that in line 328, so if impossible it would be nice");
					this.currentRoute.remove(0);
					this.status = Status.STATION;
					this.nextLocationOfInterest = this.currentLocation;
					if(RunMatsim.elaborateLogging){
						System.out.println("### Vehicle -> Station");
					}
					events.add(new PassengerDelayEvent(PassengerDelayEvent.EventType.WAIT_START, 
							this.currentClock, this.currentLocation, 
							this.nextLocationOfInterest,	"WAIT"));
					stationFunction();
				} else {
					// Proper walk
					this.status = Status.WALK;
					this.nextLocationOfInterest = extractNextLocationOfInterest(this.currentRoute);
					if(RunMatsim.elaborateLogging){
						System.out.println("### Vehicle -> Walk");
					}
					events.add(new PassengerDelayEvent(PassengerDelayEvent.EventType.WALK_START, 
							this.currentClock, this.currentLocation,
							this.nextLocationOfInterest, "WALK"));
					walkFunction();
				} 
			} else {
				if(((ExperimentalTransitRoute)route).getEgressStopId().equals(
						((TransitStopFacility) currentLocation).getId())){ // A planned egress station
					this.currentRoute.remove(0);
					route = this.currentRoute.get(0).getRoute();
					if(route instanceof GenericRouteImpl && ((GenericRouteImpl) route).getDistance() == 0){
						this.currentRoute.get(0).getRoute();
					}
				} // If not, then this has already been done, as the currentRoute is searched from this particular stop.
				this.status = Status.STATION;
				if(RunMatsim.elaborateLogging){
					System.out.println("### Vehicle -> Station");
				}
				events.add(new PassengerDelayEvent(PassengerDelayEvent.EventType.WAIT_START, 
						this.currentClock, this.currentLocation, this.currentLocation, "WAIT"));
				stationFunction();
			}
		} else { // Stay inside the same vehicle;	
			this.currentClock = this.stopwatch  + 300;
		}
	}



	private boolean isSameDeparture(Id<TransitRoute> routeId) {
		// If they have the same route, they also have the same time pattern. As such no overtaking can occur.
		// This also means, that if the route is the same before and after, it is also the same departure
		return RunMatsim.dep2Route.get(currentDepartureId) == routeId;	
	}

	private void walkFunction() {
		if(this.stopwatch  > timeForLastShortestPathSearch){ // TIme fo another path search;
			currentRoute = calculateShortestPath(currentLocation, destinationLocation,currentClock);
		}

		double d = NetworkUtils.getEuclideanDistance(currentLocation.getCoord(), nextLocationOfInterest.getCoord());
		double timeDuration = this.stopwatch  + 300 - currentClock;
		double rho = (d == 0) ? Double.POSITIVE_INFINITY : (timeDuration * WALKING_SPEED) / d;
		if(RunMatsim.elaborateLogging){
			System.out.println("Rho: " + rho);
		}
		if( rho <= 0.9999 ){
			double currentX = currentLocation.getCoord().getX();
			double currentY = currentLocation.getCoord().getY();
			double nextX = nextLocationOfInterest.getCoord().getX();
			double nextY = nextLocationOfInterest.getCoord().getY();
			this.currentLocation = new MyFakeFacility(	new Coord(currentX + rho * (nextX - currentX), 
					currentY + rho * (nextY - currentY)));
			this.currentClock = this.stopwatch  + 300;
		} else {
			this.currentLocation = nextLocationOfInterest;
			if(Double.isFinite(rho)){
				this.currentClock += timeDuration / rho;
			}
			if(currentRoute.size()>1){
				currentRoute.remove(0);
				Route route = currentRoute.get(0).getRoute();
				if( route instanceof ExperimentalTransitRoute){
					//Arrived at a station
					this.status = Status.STATION;
					if(RunMatsim.elaborateLogging){
						System.out.println("### Walk -> Station");
					}
					events.add(new PassengerDelayEvent(PassengerDelayEvent.EventType.WAIT_START, 
							this.currentClock, this.currentLocation, 
							this.nextLocationOfInterest, "WALK"));
					stationFunction();
				} else { // route must be a GenericRouteImpl (a walk leg).
					//TTHIS SHOULD NOT BE POSSIBLE.
					/*
					System.err.println("This SHOULD NOT BE POSSIBLE " + id + " @" + this.stopwatch + " " + rho +
							" " + currentLocation.getCoord());
					System.err.println("### Walk -> Walk");
					this.status = Status.WALK;
					if(((GenericRouteImpl) route).getDistance() == 0){
						System.err.println("THIS IS WALKKLY WEIRD");
					}
					walkFunction();
					 */
				}
			} else { // Arrived at an activity
				this.plan.getPlanElements().remove(0); // Remove leg from plan
				this.currentLocation = this.destinationLocation; // Update location
				if(this.plan.getPlanElements().size()==1){ // Finished!
					this.nextTimeOfInterest = Double.MAX_VALUE;
				} else {
					Activity act = ((Activity) this.plan.getPlanElements().get(0)); // Notice the 0
					this.nextTimeOfInterest = act.getEndTime(); 
					if(this.currentClock > this.nextTimeOfInterest){
						this.nextTimeOfInterest = this.currentClock;
					}
					act = ((Activity) this.plan.getPlanElements().get(2));          // Notice the 2
					this.destinationLocation = new MyFakeFacility(act.getCoord());
				}	
				if(RunMatsim.elaborateLogging){
					System.out.println("### Walk -> Activity");
				}
				this.status = Status.ACTIVITY;
				events.add(new PassengerDelayEvent(PassengerDelayEvent.EventType.ACTIVITY_START, 
						this.currentClock, this.currentLocation, 
						this.currentLocation,  "ACTIVITY"));
				activityFunction();

			}
		}
	}

	void setStopwatch(int stopwatch){
		this.stopwatch = stopwatch;
	}

	Status getStatus(){
		return this.status;
	}
}
