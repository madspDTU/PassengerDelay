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
import org.matsim.project.RunMatsim.AdaptivenessType;
import org.matsim.pt.routes.ExperimentalTransitRoute;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.routes.GenericRouteImpl;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.utils.objectattributes.attributable.Attributes;

import ch.sbb.matsim.routing.pt.raptor.MySwissRailRaptor;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptor;


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

	private LinkedList<List<Leg>> preLoadedRoutes;



	public static enum Status {
		ACTIVITY, STATION, VEHICLE, WALK, DEAD;
	}

	PassengerDelayPerson(Id<Person> id, Plan plan) {
		this.id = id;
		this.plan = plan;
		this.status = Status.ACTIVITY;
		this.currentLocation = new MyFakeFacility(((Activity) plan.getPlanElements().get(0)).getCoord());
		this.nextTimeOfInterest = ((Activity) plan.getPlanElements().get(0)).getEndTime().seconds();
		if(plan.getPlanElements().size() <= 2){
			System.out.println("Plan is too short");
		}
		this.pathDestinationLocation = new MyFakeFacility(((Activity) plan.getPlanElements().get(2)).getCoord());
		this.currentPath = null;
		this.currentClock = RunMatsim.startTime;
		this.stopwatch = 0;
		this.events = new LinkedList<PassengerDelayEvent>();
		addEvent(PassengerDelayEvent.EventType.ACTIVITY_START, 0.0,	this.currentLocation, this.currentLocation, "ACTIVITY");
	}

	public Id<Person> getId(){
		return id;
	}
	public Plan getPlan(){
		return plan;
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
		int N = plan.getPlanElements().size();
		for(int n = 0; n < N; n++){
			if(plan.getPlanElements().get(n) instanceof Activity){
				Activity act = (Activity) plan.getPlanElements().get(n);
				this.currentLocation = new MyFakeFacility(act.getCoord());
				this.currentClock = Double.max(currentClock, act.getEndTime().seconds());
			} else {
				Leg leg = (Leg) plan.getPlanElements().get(n);
				this.pathDestinationLocation = new MyFakeFacility(((Activity) plan.getPlanElements().get(n + 1)).getCoord());
				if(leg.getMode().equals(TransportMode.pt)){

					this.currentPath = calculateShortestPath(this.currentLocation,
							this.pathDestinationLocation, this.currentClock);
					while(!currentPath.isEmpty()){
						Route route = this.currentPath.get(0).getRoute();
						Leg pathLeg = this.currentPath.get(0);
						if(route instanceof GenericRouteImpl){
							if(this.currentPath.size() > 1){
								this.nextLocationOfInterest = RunMatsim.facilities.get(( (MyTransitRoute) 
										this.currentPath.get(1).getRoute()).getAccessStopId());
							} else {
								this.nextLocationOfInterest = this.pathDestinationLocation;
							}
							addEvent(EventType.WALK_START, this.currentClock, 
									this.currentLocation, this.nextLocationOfInterest, "WALK");
						} else {
							MyTransitRoute ptRoute = (MyTransitRoute) route;
							Facility waitingLocation = this.currentLocation;
							double waitingStartTime = ptRoute.getLegDepartureTime();
							addEvent(EventType.WAIT_START, waitingStartTime, waitingLocation, waitingLocation, "WAIT");
							this.nextLocationOfInterest = RunMatsim.facilities.get(ptRoute.getEgressStopId());
							Id<Departure> departureId = ptRoute.getDepartureId();
							double departureTime = RunMatsim.departureTimeOfDepartureAtStop.get(departureId).get(ptRoute.getAccessStopId());
							addEvent(EventType.BOARDING, departureTime, waitingLocation, this.nextLocationOfInterest,
									ptRoute.getLineId().toString(), ptRoute.getDepartureId().toString());
						}
						this.currentLocation = this.nextLocationOfInterest;
						if(this.currentPath.size() == 1) {
							// To fix a walk trip that was created as a walk to a stop,
							// and subsequently to an activity, i.e. walks in L shapes.
						//	this.currentClock += ((GenericRouteImpl) route).getDistance() / 
						//			RunMatsim.walkSpeed * RunMatsim.walkBeelineDistanceFactor;
						} else {
						//	this.currentClock += pathLeg.getTravelTime();
						}
						this.currentClock += pathLeg.getTravelTime();
						
						this.currentPath.remove(0);
					}
					this.nextLocationOfInterest = this.currentLocation;
					addEvent(EventType.ACTIVITY_START, this.currentClock, this.nextLocationOfInterest, this.currentLocation, "ACTIVITY");
				} else { // Teleportation
					Activity act = (Activity) plan.getPlanElements().get(n + 1);
					this.nextLocationOfInterest = new MyFakeFacility(act.getCoord());
					this.currentLocation = this.nextLocationOfInterest;
					addEvent(EventType.ACTIVITY_START, this.currentClock, this.currentLocation, this.nextLocationOfInterest, "TELEPORTATION");
				}
			}
			if(this.currentClock > RunMatsim.endTime){
				if(plan.getPlanElements().get(n) instanceof Leg){
					for(int i = (N-1); i >= n; i--){
						plan.getPlanElements().remove(i);
					}
					break;
				}
			}
		}
	}

	private void printAllEvents() {
		for(PassengerDelayEvent event : events) {
			System.out.println(event.type + ": Time: " + event.time + " How: " + event.how +
					" From: " + event.fromString + " To:" + event.toString + " FromCoord: " +
					event.fromCoord + " ToCoord: " + event.toCoord +  " DepartureId: " + event.departureId);
		}

	}

	void advance() {
		Gbl.assertIf(this.currentClock == this.stopwatch || this.status == Status.DEAD);
		switch (this.status) {
		case ACTIVITY:
			activityFunction();
			break;
		case STATION:
			stationFunction();
			break;
		case VEHICLE:
			inVehicleFunction();
			break;
		case WALK:
			walkFunction();
			break;
		case DEAD:
			//doNothing();
			break;
		default: 
			break;
		}
	}

	void setRaptor(MySwissRailRaptor raptor) {
		this.raptor = raptor;
	}


	/*
	 * Function handling an agent currently at or entering an activity.
	 */
	private void activityFunction() {

		// If the agent has to leave before next timestep
		if (this.nextTimeOfInterest < this.stopwatch + timeStep) {
			this.plan.getPlanElements().remove(0); // Remove the activity
			this.currentClock = this.nextTimeOfInterest; // Time is now
			// actEndTime;
			String mode = ((Leg) this.plan.getPlanElements().get(0)).getMode();
			if (mode.equals(TransportMode.pt)) {
				if(RunMatsim.adaptivenessType != RunMatsim.AdaptivenessType.RIGID){
					this.currentPath = calculateShortestPath(this.currentLocation, this.pathDestinationLocation,
							this.currentClock);
				} else {
					this.currentPath = preLoadedRoutes.poll();
				}
				this.nextLocationOfInterest = extractNextLocationOfInterestFromCurrentPath(); 
				this.status = Status.WALK;
				addEvent(EventType.WALK_START, this.currentClock, this.currentLocation, this.nextLocationOfInterest, "WALK");
				if(!(this.currentPath.get(0).getRoute() instanceof GenericRouteImpl)){
					System.err.println("Activity does not start walk with genericroute");
					System.err.println("Current location is stop: " + (this.currentLocation instanceof TransitStopFacility));
				}
				walkFunction();
			} else if (mode.equals("Teleportation")) { 
				this.currentLocation = this.pathDestinationLocation;
				arriveAtActivityFacility("TELEPORTATION");
			}
		} else { // Activity has not finished yet.
			this.currentClock = this.stopwatch + this.timeStep;
			return;
		}
	}

	private List<Leg> normalCalcRoute(Facility fromFacility, Facility toFacility, double time){
		List<Leg> path = this.raptor.calcRoute(fromFacility, toFacility, time, null); //, false, null, null);
		if(path == null) {
			path = createDirectWalkPath(fromFacility, toFacility, time);
		}
		return path;
	}

	private List<Leg> onboardCalcRoute(Facility fromFacility, Facility toFacility, double time, Id<TransitRoute> routeId, Id<TransitStopFacility> stopId){
		List<Leg> path = this.raptor.calcRoute(fromFacility, toFacility, time, null); //, true, routeId, stopId);
		if(path == null) {
			path = createDirectWalkPath(fromFacility, toFacility, time);
		}
		return path;
	}

	private List<Leg> calculateShortestPath(Facility fromFacility, Facility toFacility, double time) {
		this.tPSPS = this.stopwatch; 
		List<Leg> path = normalCalcRoute(fromFacility, toFacility, time);
		path = stripPathForZeroWalksAndConvert2MyTransitRoute(path);
		return path;
	}

	private List<Leg> createDirectWalkPath(Facility fromFacility, Facility toFacility, double time) {
		LinkedList<Leg> path = new LinkedList<Leg>();
		Leg leg = PopulationUtils.createLeg(TransportMode.walk);
		leg.setDepartureTime(time);
		double distance = CoordUtils.calcProjectedEuclideanDistance(fromFacility.getCoord(), toFacility.getCoord()) /
				RunMatsim.walkBeelineDistanceFactor;
		double travelTime = distance / RunMatsim.walkSpeed;
		leg.setTravelTime(distance / RunMatsim.walkSpeed);
		GenericRouteImpl route = new GenericRouteImpl(null, null);
		route.setDistance(distance);
		route.setTravelTime(travelTime);
		leg.setRoute(route);
		path.add(leg);
		return path;
	}

	private List<Leg> calculateModifiedShortestPath(Facility fromFacility, Facility toFacility, double time) {
		this.tPSPS = this.stopwatch; 
		Id<TransitRoute> currentRouteId = getCurrentRouteId();
		Id<TransitStopFacility> stopId = ((TransitStopFacility) fromFacility).getId();
		List<Leg> path = onboardCalcRoute(fromFacility, toFacility, time, currentRouteId, stopId);
		path = stripPathForZeroWalksAndConvert2MyTransitRoute(path);
		Id<Departure> shortestPathFirstDeparture = findFirstDepartureIdOfPath(path);
		if( !this.currentDepartureId.equals(shortestPathFirstDeparture) ){
			if( RunMatsim.facilities.get(stopId) != nextLocationOfInterest){
				addEvent(EventType.PIS_NOTIFICATION, time, fromFacility, fromFacility, "IN_VEHICLE");
			}
			this.nextTimeOfInterest = this.currentClock;
			this.nextLocationOfInterest = this.currentLocation;
		} else {
			Route route = path.get(0).getRoute();
			// This happens if the person can reach the next stop on foot, since utility for walk is lower than for wait, 
			// AND the modified search has no penalty on transfering to the same route.
			// We do not want this this to happen, so we ignore this walk leg
			/// Luckily, the other variables are already stored correctly, so we do not have to make further corrections.
			if(route instanceof GenericRouteImpl){
				//	System.err.println("Removing a walk leg from: "  + ((TransitStopFacility) fromFacility).getId() + 
				//			" to " + ((MyTransitRoute) path.get(1).getRoute()).getAccessStopId() + " lasting " + path.get(0).getTravelTime() +
				//			"s over a distance of " + path.get(0).getRoute().getDistance() + "m.");
				path.remove(0); // Removing walk leg.
			}

			TransitStopFacility newEgressStop = RunMatsim.facilities.get(((MyTransitRoute) path.get(0).getRoute()).getEgressStopId());
			if( newEgressStop != nextLocationOfInterest){
				addEvent(EventType.PIS_NOTIFICATION, time, fromFacility, newEgressStop, "IN_VEHICLE");
				this.nextTimeOfInterest = RunMatsim.arrivalTimeOfDepartureAtStop.get(this.currentDepartureId).get(newEgressStop.getId());
				this.nextLocationOfInterest = RunMatsim.facilities.get(newEgressStop.getId());
			}
			path.remove(0);
		}
		return path;
	}

	private Id<TransitRoute> getCurrentRouteId() {
		return RunMatsim.dep2Route.get(this.stopwatch).get(this.currentDepartureId);
	}

	private Id<Departure> findFirstDepartureIdOfPath(List<Leg> path) {
		Id<Departure> shortestPathVehicle = null;
		for(int i = 0; i < path.size(); i++){
			if(path.get(i).getRoute() instanceof MyTransitRoute){
				shortestPathVehicle = ((MyTransitRoute) path.get(i).getRoute()).getDepartureId();
				break;
			}
		}
		return shortestPathVehicle;
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
				nextLOI = RunMatsim.facilities.get(((MyTransitRoute) route).getEgressStopId());
			} else {
				route = this.currentPath.get(1).getRoute(); // Get next proper pt-leg
				nextLOI = RunMatsim.facilities.get(((MyTransitRoute) route).getAccessStopId());
			}
		}
		return nextLOI;
	}





	private void stationFunction() {
		if (this.tPSPS < this.stopwatch && 
				(RunMatsim.adaptivenessType == AdaptivenessType.SEMIADAPTIVE || RunMatsim.adaptivenessType == AdaptivenessType.FULLADAPTIVE)  ) {
			// Time for a new shortest path search
			Id<TransitLine> prevLine = ((MyTransitRoute) this.currentPath.get(0).getRoute()).getLineId();

			this.currentPath = calculateShortestPath(this.currentLocation, this.pathDestinationLocation,
					this.currentClock);
			this.nextLocationOfInterest = extractNextLocationOfInterestFromCurrentPath();

			Route newRoute = this.currentPath.get(0).getRoute();
			if(newRoute instanceof MyTransitRoute && ((MyTransitRoute) newRoute).getLineId() != prevLine){
				addEvent(EventType.PIS_NOTIFICATION,this.currentClock, this.currentLocation, this.nextLocationOfInterest, "NEW_LINE_AT_STATION");			
			}
		} 

		Route route = currentPath.get(0).getRoute();
		if (route instanceof GenericRouteImpl) {
			this.status = Status.WALK;
			addEvent(EventType.WALK_START, this.currentClock, this.currentLocation, this.nextLocationOfInterest, "SUDDEN_WALK");
			walkFunction();
		} else if (route instanceof MyTransitRoute) { 
			MyTransitRoute myRoute = (MyTransitRoute) route;
			Id<TransitStopFacility> fromStopId = myRoute.getAccessStopId();
			Id<TransitStopFacility> toStopId = myRoute.getEgressStopId();
			Id<Departure> departureId = myRoute.getDepartureId();
			double actualBoardingTime = RunMatsim.departureTimeOfDepartureAtStop.get(departureId).get(fromStopId);
			if(actualBoardingTime >= this.stopwatch + this.timeStep){
				this.currentClock = this.stopwatch + this.timeStep;
				return;
			} else if (actualBoardingTime < this.currentClock){

				// It would be better to do this afterwards, but would create bias, such that non-adaptive runs
				// would be better at catching next departure than the adaptive ones.
				this.currentClock = this.stopwatch + this.timeStep;
				if(RunMatsim.adaptivenessType == RunMatsim.AdaptivenessType.RIGID || 
						RunMatsim.adaptivenessType == RunMatsim.AdaptivenessType.NONADAPTIVE){
					Entry<Double, Id<Departure>> entry = findFirstArrivingDepartureServicingStopPair();
					if(entry == null){//This agent cannot be helped, as no more services connect the two stops.
						this.status = Status.DEAD;
					} else {
						myRoute.setDepartureId(entry.getValue());
					}
				}
				return;
			} else {
				this.status = Status.VEHICLE;
				this.currentClock = actualBoardingTime;
				this.nextTimeOfInterest = RunMatsim.arrivalTimeOfDepartureAtStop.get(departureId).get(toStopId);
				this.currentDepartureId = departureId;
				this.currentPath.remove(0);
				addEvent(EventType.BOARDING, this.currentClock, this.currentLocation, this.nextLocationOfInterest,
						myRoute.getLineId().toString(), this.currentDepartureId.toString());
				inVehicleFunction();
			} 
		} 
	}



	private Entry<Double, Id<Departure>> findFirstArrivingDepartureServicingStopPair() {
		MyTransitRoute oldRoute = (MyTransitRoute) this.currentPath.get(0).getRoute();
		Id<TransitStopFacility> fromStopId = oldRoute.getAccessStopId();
		Id<TransitStopFacility> toStopId = oldRoute.getEgressStopId();
		return RunMatsim.stop2StopTreeMap.get(fromStopId).get(toStopId).ceilingEntry(this.currentClock);
	}

	private Id<Departure> determineDepartureId(ExperimentalTransitRoute route, double egressTime) {
		Id<TransitRoute> routeId = route.getRouteId();
		Id<TransitStopFacility> toStopId = route.getEgressStopId();
		double journeyTime = RunMatsim.route2StopArrival.get(this.tPSPS).get(routeId).get(toStopId);
		double departureTime = egressTime - journeyTime;
		return RunMatsim.route2Departure.get(this.tPSPS).get(routeId).get(departureTime);
	}


	private void inVehicleFunction() {
		Entry<Double, Id<TransitStopFacility>> entry = RunMatsim.nextStationOfDepartureAtTime.get(this.currentDepartureId).ceilingEntry(this.currentClock);
		Id<TransitStopFacility> nextStation = entry.getValue();
		double nextStationArrivalTime = entry.getKey();

		if(nextStationArrivalTime < this.stopwatch + this.timeStep){
			this.currentClock = nextStationArrivalTime;
			this.currentLocation = RunMatsim.facilities.get(nextStation);
			if(this.tPSPS < this.stopwatch && RunMatsim.adaptivenessType == AdaptivenessType.FULLADAPTIVE){
				this.currentPath = calculateModifiedShortestPath(this.currentLocation, this.pathDestinationLocation,
						this.currentClock);
			}
		}
		if(this.nextTimeOfInterest >= this.stopwatch + this.timeStep){
			this.currentClock = this.stopwatch + this.timeStep;
			return;
		} else {
			this.currentClock = this.nextTimeOfInterest;
			this.currentLocation = this.nextLocationOfInterest;
			this.nextLocationOfInterest = extractNextLocationOfInterestFromCurrentPath();
			Route route = this.currentPath.get(0).getRoute();
			if (route instanceof GenericRouteImpl) {
				// Walk
				this.status = Status.WALK;
				addEvent(EventType.WALK_START, this.currentClock, this.currentLocation, this.nextLocationOfInterest, "WALK");
				walkFunction();
			} else if (route instanceof MyTransitRoute) {
				this.status = Status.STATION;
				addEvent(EventType.WAIT_START, this.currentClock, this.currentLocation, this.nextLocationOfInterest, "WAIT");
				stationFunction();
			}	
		}
	}


	private void walkFunction() {
		if(!(this.currentPath.get(0).getRoute() instanceof GenericRouteImpl)) {
			System.out.println(this.id);
			System.out.println(this.currentLocation.getCoord());
			System.out.println(RunMatsim.facilities.get(((MyTransitRoute) this.currentPath.get(0).getRoute()).getAccessStopId()).getCoord());
			System.out.println(this.currentClock);
			System.out.println(this.stopwatch);
			System.out.print(this.currentPath.size());
			for(Leg leg : this.currentPath) {
				System.out.println("Leg: " + leg.getMode() + " from " + leg.getDepartureTime() + " to " + (leg.getDepartureTime() + leg.getTravelTime()) + 
						". Distance: " +	leg.getRoute().getDistance());
			}
			printAllEvents();
		}
		Gbl.assertIf(this.currentPath.get(0).getRoute() instanceof GenericRouteImpl);

		if (this.tPSPS < this.stopwatch && RunMatsim.adaptivenessType == AdaptivenessType.FULLADAPTIVE) {
			this.currentPath = calculateShortestPath(this.currentLocation, this.pathDestinationLocation, this.currentClock);
			Facility newNextLocation = extractNextLocationOfInterestFromCurrentPath();
			if(newNextLocation != this.nextLocationOfInterest){
				addEvent(EventType.PIS_NOTIFICATION,this.currentClock, this.currentLocation, newNextLocation, "DURING_WALK");
			}
			this.nextLocationOfInterest = newNextLocation;
		}
	
		double lambda = (this.stopwatch + this.timeStep - this.currentClock) * WALKING_SPEED;
		double d = NetworkUtils.getEuclideanDistance(currentLocation.getCoord(), nextLocationOfInterest.getCoord());
		if(lambda + 0.000001 < d ){ 
			this.currentLocation = updateLocationDuringAWalkLeg(lambda/d);
			this.currentClock = this.stopwatch + this.timeStep;
			return;
		} else {
			this.currentClock +=  d / WALKING_SPEED;
			this.currentLocation = this.nextLocationOfInterest;
			this.currentPath.remove(0);
			if( !this.currentPath.isEmpty() ){
				this.nextLocationOfInterest = extractNextLocationOfInterestFromCurrentPath();
				this.status = Status.STATION;
				addEvent(EventType.WAIT_START, this.currentClock, this.currentLocation, this.nextLocationOfInterest, "WAIT");
				stationFunction();
			} else {
				arriveAtActivityFacility("ACTIVITY");
			}
		}
	}

	private void arriveAtActivityFacility(String how) {
		//Arrived at an activity-facility.
		this.plan.getPlanElements().remove(0);
		addEvent(EventType.ACTIVITY_START, this.currentClock, this.currentLocation, this.currentLocation, how);
		if(this.plan.getPlanElements().size() == 1){
			this.status = Status.DEAD;
			return;		
		} else {
			Activity thisAct = ((Activity) this.plan.getPlanElements().get(0)); 
			this.nextTimeOfInterest = Math.max(thisAct.getEndTime().seconds(), this.currentClock);
			Activity nextAct = ((Activity) this.plan.getPlanElements().get(2)); 
			this.pathDestinationLocation = new MyFakeFacility(nextAct.getCoord());
			this.status = Status.ACTIVITY;
			activityFunction();
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

	public void createAllRoutesOfDay() {
		double tempCurrentClock = RunMatsim.startTime;
		Facility tempLocation = this.currentLocation;
		Facility tempDestination;
		this.preLoadedRoutes = new LinkedList<List<Leg>>();

		int n = 0;
		while(n < plan.getPlanElements().size()){
			if(plan.getPlanElements().get(n) instanceof Activity){
				Activity act = (Activity) plan.getPlanElements().get(n);
				tempLocation = new MyFakeFacility(act.getCoord());
				tempCurrentClock = Double.max(tempCurrentClock, act.getEndTime().seconds());
			} else {
				Leg leg = (Leg) plan.getPlanElements().get(n);
				if(leg.getMode().equals(TransportMode.pt)){
					tempDestination = new MyFakeFacility(((Activity) plan.getPlanElements().get(n+1)).getCoord());
					List<Leg> path = calculateShortestPath(tempLocation, tempDestination, tempCurrentClock);
					preLoadedRoutes.addLast(path);
					for(Leg pathLeg : path){
						tempCurrentClock += pathLeg.getTravelTime();
					}
				} 
				// Teleportation does not alter tempCurrentClock, so no need to do anything in that case.
			}
			n++;
		}
	}
}
