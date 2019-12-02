package org.matsim.project;

import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Route;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.network.NetworkUtils;
import org.matsim.facilities.Facility;
import org.matsim.project.PassengerDelayEvent.EventType;
import org.matsim.pt.routes.ExperimentalTransitRoute;
import org.matsim.core.population.routes.GenericRouteImpl;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import ch.sbb.matsim.routing.pt.raptor.MySwissRailRaptor;


public class PassengerDelayPerson {

	Logger logger = Logger.getLogger(PassengerDelayPerson.class);

	private final double WALKING_SPEED = 1.0; // m/s. Assuming a speed of 1.3
	private final Id<Person> id;
	private final Plan plan;
	private final LinkedList<PassengerDelayEvent> events;

	// and an additional length of
	// 1.3.

	private MySwissRailRaptor raptor;
	private List<Leg> currentPath;
	private Status status;

	// it should be possible to merge these two...
	private double nextTimeOfInterest;

	private Facility pathDestinationLocation;
	private Facility nextLocationOfInterest;
	private Facility currentLocation;
	private Id<Departure> currentDepartureId;
	private double currentClock;
	private final int timeStep = RunMatsim.TIMESTEP;
	private int stopwatch;
	private int tPSPS = -1; // Time (t) for previous (P) shortest (S) path (P) search (S). 



	public static enum Status {
		ACTIVITY, STATION, VEHICLE, WALK, ;
	}

	PassengerDelayPerson(Id<Person> id, Plan plan) {
		this.id = id;
		this.plan = plan;
		this.status = Status.ACTIVITY;
		this.currentLocation = new MyFakeFacility(((Activity) plan.getPlanElements().get(0)).getCoord());
		this.nextTimeOfInterest = ((Activity) plan.getPlanElements().get(0)).getEndTime();
		this.pathDestinationLocation = new MyFakeFacility(((Activity) plan.getPlanElements().get(2)).getCoord());
		this.currentPath = null;
		this.currentClock = RunMatsim.startTime;
		this.stopwatch = 0;
		this.events = new LinkedList<PassengerDelayEvent>();
		addEvent(PassengerDelayEvent.EventType.ACTIVITY_START, 0.0,	this.currentLocation, this.currentLocation, "ACTIVITY");
	}

	private void addEvent(EventType eventType, double time, Facility fromLocation, Facility toLocation, String descr){
		addEvent(eventType,time,fromLocation,toLocation,descr,"");
	}

	private void addEvent(EventType eventType, double time, Facility fromLocation, Facility toLocation, String descr,
			String departureId){
		PassengerDelayEvent event = new PassengerDelayEvent(eventType, time, fromLocation, toLocation, descr, departureId);
		this.events.add(event);
	}

	public void createEntireDayEvents(){
		while(!plan.getPlanElements().isEmpty()){
			if(plan.getPlanElements().get(0) instanceof Activity){
				Activity act = (Activity) plan.getPlanElements().get(0);
				this.currentClock = Double.max(currentClock, act.getEndTime());
			} else {
				Leg leg = (Leg) plan.getPlanElements().get(0);
				this.pathDestinationLocation = new MyFakeFacility(((Activity) plan.getPlanElements().get(1)).getCoord());
				if(leg.getMode().equals(TransportMode.pt)){
					this.currentPath = calculateShortestPath(this.currentLocation,
							this.pathDestinationLocation, this.currentClock);
					while(!currentPath.isEmpty()){
						Route route = this.currentPath.get(0).getRoute();
						if(route instanceof GenericRouteImpl){
							if(this.currentPath.size() > 1){
								this.nextLocationOfInterest = 
										RunMatsim.facilities.get(((MyTransitRoute) 
												this.currentPath.get(1).getRoute()).getAccessStopId());
							} else {
								this.nextLocationOfInterest = 
										new MyFakeFacility(
												((Activity) plan.getPlanElements().get(1)).getCoord());
							}
							addEvent(EventType.WALK_START, this.currentClock, 
									this.currentLocation, this.nextLocationOfInterest, "WALK");
							this.currentLocation = this.nextLocationOfInterest;
							this.currentClock = this.currentPath.get(0).getDepartureTime() +
									this.currentPath.get(0).getTravelTime();
						} else {
							MyTransitRoute ptRoute = (MyTransitRoute) route;
							this.currentLocation = this.nextLocationOfInterest;
							this.nextLocationOfInterest = RunMatsim.facilities.get(ptRoute.getEgressStopId());
							this.currentClock = Double.max(this.currentClock, ptRoute.getLegDepartureTime());
							addEvent(EventType.WAIT_START, this.currentClock,this.currentLocation, this.currentLocation,
									"WAIT");
							Id<TransitRoute> routeId = ptRoute.getRouteId();
							Id<Departure> departureId = ptRoute.getDepartureId();
							this.currentClock = RunMatsim.route2StopDeparture.get(stopwatch).get(routeId).
									get(ptRoute.getAccessStopId()) + RunMatsim.route2DepartureTime.
									get(stopwatch).get(routeId).get(departureId);
							addEvent(EventType.BOARDING, this.currentClock, this.currentLocation, this.nextLocationOfInterest,
									ptRoute.getLineId().toString(), ptRoute.getDepartureId().toString());
							this.currentLocation = this.nextLocationOfInterest;
							this.currentClock = ptRoute.getLegDepartureTime() + ptRoute.getTravelTime();
						}
						this.currentPath.remove(0);
					}
					this.nextLocationOfInterest = this.currentLocation;
					addEvent(EventType.ACTIVITY_START, this.currentClock, this.nextLocationOfInterest,
							this.currentLocation, "ACTIVITY");
				} else { // Teleportation
					Activity act = (Activity) plan.getPlanElements().get(1);
					this.currentLocation = new MyFakeFacility(act.getCoord());
					this.nextLocationOfInterest = this.currentLocation;
					this.currentClock = Double.max(this.currentClock, act.getStartTime());
					addEvent(EventType.ACTIVITY_START, this.currentClock, this.currentLocation,
							this.nextLocationOfInterest, "TELEPORTATION");
				}
			}
			plan.getPlanElements().remove(0);
		}
	}

	void advance() {
		Gbl.assertIf(this.currentClock == this.stopwatch);
		switch (this.status) {
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
			walkFunction();
			break;
		default:
			//Not relevant since we switch on an ENUM.
			break;
		}
	}

	void setRaptor(MySwissRailRaptor raptor) {
		this.raptor = raptor;
	}

	void setZeroSearchRadiusRaptor(MySwissRailRaptor raptor_searchRadius0) {
		this.raptor = raptor_searchRadius0;
	}

	/*
	 * Function handling an agent currently at or entering an activity.
	 */
	private void activityFunction() {

		// If the agent has to leave before next timestep
		if (this.stopwatch + timeStep > this.nextTimeOfInterest) {
			this.plan.getPlanElements().remove(0); // Remove the activity
			this.currentClock = this.nextTimeOfInterest; // Time is now
			// actEndTime;
			String mode = ((Leg) this.plan.getPlanElements().get(0)).getMode();
			if (mode.equals(TransportMode.pt)) {
				// We _always_ have to calculate shortest path. Although it
				// might have been calculated at this timestep,
				// it might have been for a different leg of the dayplan!
				this.currentPath = calculateShortestPath(this.currentLocation, this.pathDestinationLocation,
						this.currentClock);
				this.nextLocationOfInterest = extractNextLocationOfInterestFromCurrentPath(); // Can be done here, because it can
				// always be done immediately.
				this.status = Status.WALK;

				addEvent(EventType.WALK_START, this.currentClock, this.currentLocation, this.nextLocationOfInterest, "WALK");
				walkFunction();
			} else if (mode.equals("Teleportation")) { // If teleportation: Skip
				// the leg.
				this.plan.getPlanElements().remove(0);
				Facility oldLocation = this.currentLocation;
				this.currentLocation = this.pathDestinationLocation;
				if (this.plan.getPlanElements().size() == 1) {
					this.nextTimeOfInterest = Double.MAX_VALUE; // Finished
				} else {
					Activity act = ((Activity) this.plan.getPlanElements().get(0));
					double actEndTime = act.getEndTime();
					if (this.nextTimeOfInterest < actEndTime) { // actEndTime
						this.nextTimeOfInterest = actEndTime;
					}
					act = ((Activity) this.plan.getPlanElements().get(2));
					this.pathDestinationLocation = new MyFakeFacility(act.getCoord());
				}

				this.status = Status.ACTIVITY;
				addEvent(EventType.ACTIVITY_START, this.currentClock, oldLocation, this.currentLocation, "TELEPORTATION");
				activityFunction();
			}
		} else { // Activity has not finished yet.
			this.currentClock = this.stopwatch + this.timeStep;
			//Finishes here
		}
	}

	private List<Leg> calculateShortestPath(Facility fromFacility, Facility toFacility, double time) {

		this.tPSPS = this.stopwatch; 
		boolean onBoard = getStatus() == Status.VEHICLE;
		Id<TransitRoute> currentRouteId = null;
		Id<TransitStopFacility> stopId = null;
		if(onBoard) {
			if(stopwatch == RunMatsim.startTime){
				currentRouteId = RunMatsim.dep2Route.get(stopwatch).get(currentDepartureId);
			} else {
				currentRouteId = RunMatsim.dep2Route.get(stopwatch - timeStep).get(currentDepartureId);
			}
			stopId = ((TransitStopFacility) fromFacility).getId();
		}
		List<Leg> path = this.raptor.calcRoute(fromFacility, toFacility, time, null, onBoard, currentRouteId, stopId);
		path = stripPathForZeroWalksAndConvert2MyTransitRoute(path);
		return path;
	}

	private List<Leg> stripPathForZeroWalksAndConvert2MyTransitRoute(List<Leg> path) {
		List<Leg> newPath = new ArrayList<Leg>(path.size());
		for (Leg leg : path) {
			Route route = leg.getRoute();
			if (route instanceof ExperimentalTransitRoute) {
				convert2MyTransitRoute(leg);
				newPath.add(leg);
			} else if (route.getDistance() > 0) {
				newPath.add(leg);
			}
		}
		return newPath;
	}

	private void convert2MyTransitRoute(Leg leg) {
		ExperimentalTransitRoute route = (ExperimentalTransitRoute) leg.getRoute();
		double egressTime = leg.getDepartureTime() + leg.getTravelTime();
		Id<Departure> departureId = determineDepartureId(route, egressTime);
		MyTransitRoute myRoute = new MyTransitRoute(route);
		myRoute.setLegDepartureTime(leg.getDepartureTime());
		myRoute.setTravelTime(leg.getTravelTime());
		myRoute.setDepartureId(departureId);
		leg.setRoute(myRoute);
	}

	private Facility extractNextLocationOfInterestFromCurrentPath() {
		Facility nextLOI;
		if (this.currentPath.size() == 1) { // Walk directly to destination.
			nextLOI = pathDestinationLocation;
		} else {
			Route route = this.currentPath.get(0).getRoute(); // Get next proper pt-leg
			if (route instanceof MyTransitRoute) {
				nextLOI = RunMatsim.facilities.get(((MyTransitRoute) route).getAccessStopId());
			} else {
				route = this.currentPath.get(1).getRoute(); // Get next proper pt-leg
				nextLOI = RunMatsim.facilities.get(((MyTransitRoute) route).getAccessStopId());
			}
		}
		return nextLOI;
	}



	/***
	 * Returns whether or not the agent is still in the vehicle.
	 * Also updates currentLocation and currentClock.
	 ***/
	private boolean whereIsDepartureAtTime() {

		Id<TransitRoute> routeId = RunMatsim.dep2Route.get(this.stopwatch).get(this.currentDepartureId);
		double departureTime = RunMatsim.route2DepartureTime.get(this.stopwatch).get(routeId)
				.get(this.currentDepartureId);
		TransitStopFacility previouslyFoundEgressStop = (TransitStopFacility) this.nextLocationOfInterest;
		double arrivalAtPreviouslyFoundEgressStop = departureTime
				+ RunMatsim.route2StopArrival.get(this.stopwatch).get(routeId).get(previouslyFoundEgressStop.getId());

		if (arrivalAtPreviouslyFoundEgressStop < this.stopwatch) { 
			// The agent has already left the train;
			this.currentLocation = previouslyFoundEgressStop;
			this.nextTimeOfInterest = arrivalAtPreviouslyFoundEgressStop;
			this.currentClock = arrivalAtPreviouslyFoundEgressStop;
			this.currentPath.remove(0);
			return false;
		}

		double auxTime = this.stopwatch - departureTime;
		Entry<Double, TransitStopFacility> entry = 
				RunMatsim.currentRoute2OrderedStopOffset.get(routeId).ceilingEntry(auxTime);
		this.currentLocation = entry.getValue();
		this.currentClock = departureTime
				+ RunMatsim.route2StopArrival.get(this.stopwatch).get(routeId).get(entry.getValue().getId());

		return true;
	}

	private void stationFunction() {
		if (this.tPSPS < this.stopwatch && this.currentClock >= this.stopwatch) {
			// Time for a new shortest path search
			this.currentPath = calculateShortestPath(this.currentLocation, this.pathDestinationLocation,
					this.currentClock);
			this.nextLocationOfInterest = extractNextLocationOfInterestFromCurrentPath(); // Can be done here, because it can always be
			// done immediately.
		} // Otherwise, it has been done earlier on at this time step, and thus
		// not necessary again.

		Route route = currentPath.get(0).getRoute();

		if (route instanceof GenericRouteImpl) {
			// It is suddenly faster to walk.
			this.status = Status.WALK;
			addEvent(EventType.WALK_START, this.currentClock, this.currentLocation, this.nextLocationOfInterest, "SUDDEN_WALK");
			walkFunction();
		} else if (route instanceof MyTransitRoute) { // Current fastest path is
			// through boarding a
			// vehicle.
			MyTransitRoute myRoute = (MyTransitRoute) route;
			Id<TransitStopFacility> fromStopId = myRoute.getAccessStopId();
			Id<TransitStopFacility> toStopId = myRoute.getEgressStopId();
			Id<Departure> departureId = myRoute.getDepartureId();
			double actualBoardingTime = getActualBoardingTimeOfTransitRoute(myRoute);
			
			if (this.tPSPS < this.stopwatch && actualBoardingTime >= this.stopwatch) { // Can
				this.currentClock = this.stopwatch;
				stationFunction();
			} else if (actualBoardingTime < this.stopwatch + timeStep) {
				double actualEgressTime = getActualEgressTimeOfTransitRoute(myRoute);

				this.status = Status.VEHICLE;

				this.currentDepartureId = departureId;
				this.currentClock = actualBoardingTime;
				this.nextTimeOfInterest = actualEgressTime;
				this.currentLocation = RunMatsim.facilities.get(fromStopId);
				this.nextLocationOfInterest = RunMatsim.facilities.get(toStopId);
				addEvent(EventType.BOARDING, this.currentClock, this.currentLocation, this.nextLocationOfInterest,
						myRoute.getLineId().toString(), this.currentDepartureId.toString());
				entersVehicleFunction();
			} else { // Wait until next time to see if a better opportunity arises.			
				this.currentClock = this.stopwatch + timeStep;
				//Finishes here...
			}
		} 
	}



	private Id<Departure> determineDepartureId(Route route, double egressTime) {
		ExperimentalTransitRoute expRoute = (ExperimentalTransitRoute) route;
		Id<TransitRoute> routeId = expRoute.getRouteId();
		Id<TransitStopFacility> toStopId = expRoute.getEgressStopId();
		double journeyTime = RunMatsim.route2StopArrival.get(this.tPSPS).get(routeId)
				.get(toStopId);
		double departureTime = egressTime - journeyTime;
		return RunMatsim.route2Departure.get(this.tPSPS).get(routeId).get(departureTime);
	}

	private void entersVehicleFunction() {
		if (this.nextTimeOfInterest > this.stopwatch && this.tPSPS < this.stopwatch) {
			this.currentClock = this.stopwatch;
			stillInVehicleFunction();
		} else if (this.nextTimeOfInterest < this.stopwatch + timeStep) {
			Leg leg = this.currentPath.get(0);
			Route route = leg.getRoute();
			this.currentDepartureId = null;
			this.currentClock = this.nextTimeOfInterest;
			this.currentLocation = this.nextLocationOfInterest;
			this.currentPath.remove(0);
			this.nextLocationOfInterest = extractNextLocationOfInterestFromCurrentPath();
			route = this.currentPath.get(0).getRoute();
			if (route instanceof GenericRouteImpl) {
				this.status = Status.WALK;
				addEvent(EventType.WALK_START, this.currentClock, this.currentLocation, this.nextLocationOfInterest, "WALK");
				walkFunction();
			} else {
				// Either station or walk....
				this.status = Status.STATION;
				addEvent(EventType.WAIT_START, this.currentClock, this.currentLocation, this.currentLocation, "WAIT");
				stationFunction();
			}
		} else { // Stay inside the same vehicle;
			this.currentClock = this.stopwatch + timeStep;
			//Finishes here...
		}
	}

	private double getActualEgressTimeOfTransitRoute(MyTransitRoute route) {
		Id<Departure> departureId = route.getDepartureId();
		Id<TransitStopFacility> egressStopId = route.getEgressStopId();
		int counter = 1;
		for (int time = this.stopwatch + this.timeStep; time >= RunMatsim.startTime; time -= this.timeStep) {
			Id<TransitRoute> routeId = RunMatsim.dep2Route.get(time).get(departureId);
			if (routeId != null && RunMatsim.route2StopArrival.containsKey(time) &&
					RunMatsim.route2StopArrival.get(time).containsKey(routeId)) {
				if (counter > 1) {
					System.err.println(counter + " schedules needed to find egress time of departure " + departureId +
							" at time " + this.stopwatch + ". Eventually found at time " + time);
				}
				return (RunMatsim.route2DepartureTime.get(time).get(routeId).get(departureId) + RunMatsim.route2StopArrival
						.get(time).get(routeId).get(egressStopId));
			}
			counter++;
		}
		System.err.println("No valid actual egresstime found for departure " + this.currentDepartureId);
		System.exit(-1);
		return -1;
	}

	private double getActualBoardingTimeOfTransitRoute(MyTransitRoute route) {

		Id<Departure> departureId = route.getDepartureId();
		Id<TransitStopFacility> accessStopId = route.getAccessStopId();
		for (int time = this.stopwatch + this.timeStep; time >= RunMatsim.startTime; time -= this.timeStep) {
			Id<TransitRoute> routeId = RunMatsim.dep2Route.get(time).get(departureId);
			if (routeId != null && RunMatsim.route2StopDeparture.get(time).containsKey(routeId)) {
				return (RunMatsim.route2DepartureTime.get(time).get(routeId).get(departureId) + RunMatsim.route2StopDeparture
						.get(time).get(routeId).get(accessStopId));
			}
		}
		System.err.println("No valid boarding time found for departure " + this.currentDepartureId);
		System.exit(-1);
		return -1;
	}

	private void stillInVehicleFunction() {
		Gbl.assertIf(this.currentPath.size() > 1);

		boolean stillInVehicle = whereIsDepartureAtTime();
		if (stillInVehicle && this.currentClock < this.stopwatch + this.timeStep
				&& this.tPSPS < this.stopwatch) {
			this.currentPath = calculateShortestPath(this.currentLocation, this.pathDestinationLocation,
					this.currentClock);
		}

		Route route = this.currentPath.get(0).getRoute();
		boolean isSameDeparture = stillInVehicle && route instanceof MyTransitRoute
				&& isSameDeparture(((MyTransitRoute) route));
		if (isSameDeparture && this.currentClock < this.stopwatch + this.timeStep) { // The shortest path is to stay on the current
			// departure...
			this.nextTimeOfInterest = getActualEgressTimeOfTransitRoute((MyTransitRoute) route);
			if (this.nextTimeOfInterest < this.stopwatch + timeStep) {
				this.currentPath.remove(0);
				this.currentLocation = this.nextLocationOfInterest;
				route = this.currentPath.get(0).getRoute();
			}
		} else { // It is a walking leg or a pt-leg with a different route, i.e.
			// a walk or a transfer, i.e. get off!
			this.nextTimeOfInterest = this.currentClock;
		}

		// Have to get off before next time step
		if (this.nextTimeOfInterest < this.stopwatch + timeStep) { 
			this.nextLocationOfInterest = extractNextLocationOfInterestFromCurrentPath(); 
			this.currentDepartureId = null;
			this.currentClock = this.nextTimeOfInterest;
			route = this.currentPath.get(0).getRoute();
			if (route instanceof GenericRouteImpl) {
				// Walk
				this.status = Status.WALK;
				this.nextLocationOfInterest = extractNextLocationOfInterestFromCurrentPath();
				addEvent(EventType.WALK_START, this.currentClock, this.currentLocation, this.nextLocationOfInterest, "WALK");
				walkFunction();
			} else if (route instanceof MyTransitRoute) {
				this.status = Status.STATION;
				this.currentClock = this.nextTimeOfInterest;
				this.nextLocationOfInterest = this.currentLocation;
				addEvent(EventType.WAIT_START, this.currentClock, this.currentLocation, this.nextLocationOfInterest, "WAIT");
				stationFunction();
			}
		} else { // Stay inside the same vehicle;
			this.currentClock = this.stopwatch + timeStep;
			//Finishes here...
		}
	}

	private boolean isSameDeparture(MyTransitRoute route) {
		return this.currentDepartureId.equals(route.getDepartureId());
	}

	private void walkFunction() {
		Gbl.assertIf(this.currentPath.get(0).getRoute() instanceof GenericRouteImpl);

		if (currentClock >= this.stopwatch && tPSPS < this.stopwatch) {
			// Time for another shortest path search
			currentPath = calculateShortestPath(currentLocation, pathDestinationLocation, this.currentClock);
			this.nextLocationOfInterest = extractNextLocationOfInterestFromCurrentPath(); // Can be done here, because it can always be
			// done immediately.
		}

		double d = NetworkUtils.getEuclideanDistance(currentLocation.getCoord(), nextLocationOfInterest.getCoord());
		double timeDuration = this.stopwatch - this.currentClock;
		if (this.currentClock >= this.stopwatch) {
			timeDuration += this.timeStep;
		}
		double rho = (d == 0) ? Double.POSITIVE_INFINITY : (timeDuration * WALKING_SPEED) / d;
		if (rho <= 0.9999) {
			this.currentLocation = updateLocationDuringAWalkLeg(rho);
			if (this.currentClock < this.stopwatch) {
				// Run the walkFunction() with currentClock = stopwatch.
				this.currentClock = this.stopwatch;
				walkFunction();
			} else {
				this.currentClock = this.stopwatch + timeStep;
				//Finishes here...
			}
		} else {
			this.currentLocation = nextLocationOfInterest;
			this.currentClock += d / WALKING_SPEED;
			if (currentPath.size() > 1) {
				currentPath.remove(0);
				this.status = Status.STATION;
				addEvent(EventType.WAIT_START, this.currentClock, this.currentLocation, this.nextLocationOfInterest, "WAIT");
				stationFunction();
			} else { // Arrived at an activity
				this.plan.getPlanElements().remove(0);
				this.currentLocation = this.pathDestinationLocation;   // May be unnecessary, since it should be the same as 9 lines above (nextlocofint)
				if (this.plan.getPlanElements().size() == 1) { 
					// The agent has completed the game - roll the credits! Sleep tight... ZzzZz
					this.nextTimeOfInterest = Double.MAX_VALUE;
				} else {
					Activity thisAct = ((Activity) this.plan.getPlanElements().get(0)); 
					this.nextTimeOfInterest = Math.max(thisAct.getEndTime(), this.currentClock);
					Activity nextAct = ((Activity) this.plan.getPlanElements().get(2)); 
					this.pathDestinationLocation = new MyFakeFacility(nextAct.getCoord());
				}
				this.status = Status.ACTIVITY;
				addEvent(EventType.ACTIVITY_START, this.currentClock,
						this.currentLocation, this.currentLocation, "ACTIVITY");
				activityFunction();
			}
		}
	}

	private Facility updateLocationDuringAWalkLeg(double rho) {
		double currentX = currentLocation.getCoord().getX();
		double currentY = currentLocation.getCoord().getY();
		double nextX = nextLocationOfInterest.getCoord().getX();
		double nextY = nextLocationOfInterest.getCoord().getY();
		return new MyFakeFacility(new Coord(currentX + rho * (nextX - currentX),
				currentY + rho * (nextY - currentY)));
	}

	void setStopwatch(int stopwatch) {
		this.stopwatch = stopwatch;
	}

	Status getStatus() {
		return this.status;
	}

	public String eventsToString(){
		int tripId = 0;
		StringBuilder s = new StringBuilder(64);
		for(PassengerDelayEvent event : events){
			s.append(id + ";" + tripId + ";" + 
					event.type + ";" + event.time + ";" + event.fromCoord.getX() + ";"
					+ event.fromCoord.getY() + ";" + event.fromString + ";" +
					+ event.toCoord.getX() + ";" + event.toCoord.getY() + ";"+
					event.toString + ";" + event.how + ";" + event.departureId + "\n");
			if(event.type.equals(PassengerDelayEvent.EventType.ACTIVITY_START)){
				tripId++;
			}
		}
		return s.toString();
	}
}
