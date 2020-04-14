package org.matsim.project;

import org.apache.log4j.Logger;

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
import org.matsim.project.pt.MyDepartureImpl;
import org.matsim.project.pt.MyTransitLineImpl;
import org.matsim.project.pt.MyTransitStopFacilityImpl;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.routes.GenericRouteImpl;
import org.matsim.core.utils.geometry.CoordUtils;
import ch.sbb.matsim.routing.pt.raptor.MySwissRailRaptor;


public class PassengerDelayPerson {

	Logger logger = Logger.getLogger(PassengerDelayPerson.class);

	private final double WALKING_SPEED = 1; // m/s. Assuming a speed of 1.3
	private final Id<Person> id;
	private final Plan plan;
	private final LinkedList<PassengerDelayEvent> events;

	// and an additional length of
	// 1.3.

	private MySwissRailRaptor raptor;
	private List<Leg> currentPath;
	private Status status;

	// it should be possible to merge these two...
	private int nextTimeOfInterest;

	private Facility pathDestinationLocation;
	private Facility nextLocationOfInterest;
	private Facility currentLocation;
	private Id<MyDepartureImpl> currentDepartureId;
	private int currentClock;
	private final int timeStep = RunMatsim.TIMESTEP;
	private int stopwatch;
	private int tPSPS = -1; // Time (t) for previous (P) shortest (S) path (P) search (S). 




	public static enum Status {
		ACTIVITY, STATION, VEHICLE, WALK, DEAD;
	}

	PassengerDelayPerson(Id<Person> id, Plan plan) {
		this.id = id;
		this.plan = plan;
		this.status = Status.ACTIVITY;
		this.currentLocation = new MyFakeFacility(((Activity) plan.getPlanElements().get(0)).getCoord());
		this.nextTimeOfInterest = (int) Math.ceil(((Activity) plan.getPlanElements().get(0)).getEndTime().seconds());
		if(plan.getPlanElements().size() <= 2){
			System.out.println("Plan is too short");
		}
		this.pathDestinationLocation = new MyFakeFacility(((Activity) plan.getPlanElements().get(2)).getCoord());
		this.currentPath = null;
		this.currentClock = RunMatsim.startTime;
		this.stopwatch = 0;
		this.events = new LinkedList<PassengerDelayEvent>();
	}

	public Id<Person> getId(){
		return id;
	}
	public Plan getPlan(){
		return plan;
	}

	private void addEvent(EventType eventType, int time, Facility fromLocation, Facility toLocation, String descr){
		addEvent(eventType,time,fromLocation,toLocation,descr,"");
	}

	private void addEvent(EventType eventType, int time, Facility fromLocation, Facility toLocation, String descr,
			String departureId){
		PassengerDelayEvent event = new PassengerDelayEvent(eventType, time, fromLocation, toLocation, descr, departureId);
		this.events.add(event);
	}

	public void createEntireDayEvents(){
		Activity act = (Activity) plan.getPlanElements().get(0);
		this.currentLocation = new MyFakeFacility(act.getCoord());
		this.currentClock = (int) Math.ceil(act.getEndTime().seconds());
		this.pathDestinationLocation = new MyFakeFacility(((Activity) plan.getPlanElements().get(2)).getCoord());
		this.currentPath = calcRouteFromActivity(this.currentLocation, this.pathDestinationLocation, this.currentClock);
		while(!currentPath.isEmpty()){
			Route route = this.currentPath.get(0).getRoute();
			if(route instanceof GenericRouteImpl){
				if(this.currentPath.size() > 1){
					this.nextLocationOfInterest = RunMatsim.facilities.get(( (MyTransitRoute) 
							this.currentPath.get(1).getRoute()).getAccessStopId());
				} else {
					this.nextLocationOfInterest = this.pathDestinationLocation;
				}
				addEvent(EventType.WALK_START, this.currentClock, this.currentLocation, this.nextLocationOfInterest, "WALK");
			} else {
				MyTransitRoute ptRoute = (MyTransitRoute) route;
				Facility waitingLocation = this.currentLocation;
				int waitingStartTime = ptRoute.getLegDepartureTime();
				this.nextLocationOfInterest = RunMatsim.facilities.get(ptRoute.getEgressStopId());
				addEvent(EventType.WAIT_START, waitingStartTime, waitingLocation, this.nextLocationOfInterest, "WAIT");
				Id<MyDepartureImpl> departureId = ptRoute.getDepartureId();
				Gbl.assertNotNull(departureId);
				int vehicleArrivalTime = RunMatsim.arrivalAndDepartureTimesOfDepartureAtStop.get(departureId)[ptRoute.getAccessIndexAlongRoute()].arr;
				int boardingTime = Math.max(waitingStartTime, vehicleArrivalTime);
				addEvent(EventType.BOARDING, boardingTime, waitingLocation, this.nextLocationOfInterest,
						ptRoute.getLineId().toString(), ptRoute.getDepartureId().toString());
			}
			this.currentLocation = this.nextLocationOfInterest;
			this.currentClock += Math.ceil(route.getTravelTime().seconds());
			if(this.currentClock > RunMatsim.endTime){
				return;
			}
			this.currentPath.remove(0);
		}
		this.nextLocationOfInterest = this.currentLocation;
		addEvent(EventType.ACTIVITY_START, this.currentClock, this.nextLocationOfInterest, this.currentLocation, "ACTIVITY");
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
		if (this.nextTimeOfInterest < this.stopwatch + timeStep && this.nextTimeOfInterest > this.stopwatch) { //TODO quick fix. remove last
			this.plan.getPlanElements().remove(0); // Remove the activity
			this.currentClock = this.nextTimeOfInterest; // Time is now
			if(RunMatsim.adaptivenessType != RunMatsim.AdaptivenessType.RIGID){
				this.currentPath = calcRouteFromActivity(this.currentLocation, this.pathDestinationLocation, this.currentClock);
			}
			this.nextLocationOfInterest = extractNextLocationOfInterestFromCurrentPath(); 
			this.status = Status.WALK;
			addEvent(EventType.WALK_START, this.currentClock, this.currentLocation, this.nextLocationOfInterest, "WALK");
			walkFunction();

		} else { // Activity has not finished yet.
			this.currentClock = this.stopwatch + this.timeStep;
			return;
		}
	}

	private List<Leg> calcRouteFromActivity(Facility fromFacility, Facility toFacility, int time){
		this.tPSPS = this.stopwatch; 
		return this.raptor.calcRoute(fromFacility, toFacility, time, RunMatsim.searchRadius);
	}

	private List<Leg> calcRouteFromStop(Facility fromFacility, Facility toFacility, int time){
		this.tPSPS = this.stopwatch; 
		return this.raptor.calcRoute(fromFacility, toFacility, time, RunMatsim.maxBeelineTransferWalk);
	}

	private List<Leg> calcRouteFromWalk(Facility fromFacility, Facility toFacility, int time, double searchRadius){
		this.tPSPS = this.stopwatch; 
		if(searchRadius < RunMatsim.maxBeelineTransferWalk) {
			searchRadius = RunMatsim.maxBeelineTransferWalk;
		}
		return this.raptor.calcRoute(fromFacility, toFacility, time, searchRadius); 
	}

	private List<Leg> onboardCalcRoute(MyTransitStopFacilityImpl fromStop, Facility toFacility, int time){
		Id<MyTransitLineImpl> currentLineId = RunMatsim.dep2Line.get(this.currentDepartureId);
		List<Leg> path = this.raptor.calcRoute(fromStop, toFacility, time, true, fromStop, currentLineId, RunMatsim.maxBeelineTransferWalk);
		if(path == null) {
			path = createDirectWalkPath(fromStop, toFacility, time);
		}
		return path;
	}


	private List<Leg> createDirectWalkPath(Facility fromFacility, Facility toFacility, int time) {
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

	private List<Leg> calculateModifiedShortestPath(MyTransitStopFacilityImpl fromStation, Facility toFacility, int time) {
		this.tPSPS = this.stopwatch; 
		List<Leg> path = onboardCalcRoute(fromStation, toFacility, time);
		Id<MyDepartureImpl> shortestPathFirstDeparture = findFirstDepartureIdOfPath(path);
		if( !this.currentDepartureId.equals(shortestPathFirstDeparture) ){
			if( fromStation != nextLocationOfInterest){
				addEvent(EventType.PIS_NOTIFICATION, time, fromStation, fromStation, "IN_VEHICLE");
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

			MyTransitRoute myTransitRoute = ((MyTransitRoute) path.get(0).getRoute());
			MyTransitStopFacilityImpl newEgressStop = RunMatsim.facilities.get(myTransitRoute.getEgressStopId());
			if( newEgressStop != nextLocationOfInterest){
				addEvent(EventType.PIS_NOTIFICATION, time, fromStation, newEgressStop, "IN_VEHICLE");
				this.nextTimeOfInterest = RunMatsim.arrivalAndDepartureTimesOfDepartureAtStop.get(
						this.currentDepartureId)[myTransitRoute.getEgressIndexAlongRoute()].arr;
				this.nextLocationOfInterest = RunMatsim.facilities.get(newEgressStop.getId());
			}
			path.remove(0);
		}
		return path;
	}


	private Id<MyDepartureImpl> findFirstDepartureIdOfPath(List<Leg> path) {
		Id<MyDepartureImpl> shortestPathVehicle = null;
		for(int i = 0; i < path.size(); i++){
			if(path.get(i).getRoute() instanceof MyTransitRoute){
				shortestPathVehicle = ((MyTransitRoute) path.get(i).getRoute()).getDepartureId();
				break;
			}
		}
		return shortestPathVehicle;
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
			Id<MyTransitLineImpl> prevLine = ((MyTransitRoute) this.currentPath.get(0).getRoute()).getLineId();

			this.currentPath = calcRouteFromStop(this.currentLocation, this.pathDestinationLocation, this.currentClock);
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
			Id<MyDepartureImpl> departureId = myRoute.getDepartureId();
			ArrDep arrDep = null;
			if(RunMatsim.arrivalAndDepartureTimesOfDepartureAtStop.containsKey(departureId)) {
				// Not required to happen -- e.g. if the departure is cancelled in the realised scenario!
				arrDep = RunMatsim.arrivalAndDepartureTimesOfDepartureAtStop.get(departureId)[myRoute.getAccessIndexAlongRoute()];
			}
			if(arrDep !=  null && arrDep.arr >= this.stopwatch + this.timeStep){
				this.currentClock = this.stopwatch + this.timeStep;
				return;
			} else if (arrDep == null || arrDep.dep < this.currentClock){

				// It would be better to do this afterwards, but would create bias, such that non-adaptive runs
				// would be better at catching next departure than the adaptive ones.
				this.currentClock = this.stopwatch + this.timeStep;
				if(RunMatsim.adaptivenessType == RunMatsim.AdaptivenessType.RIGID || 
						RunMatsim.adaptivenessType == RunMatsim.AdaptivenessType.NONADAPTIVE){
					Entry<Integer, NewDepartureBundle> entry = findFirstArrivingDepartureServicingStopPair();
					if(entry == null){//This agent cannot be helped, as no more services connect the two stops.
						this.status = Status.DEAD;
					} else {
						Id<MyDepartureImpl> newDepartureId = entry.getValue().getDepartureId();
						myRoute.setDepartureId(newDepartureId);
						myRoute.setLineId(RunMatsim.dep2Line.get(newDepartureId));
						myRoute.setTravelTime(Double.NaN); //We never use it, anyway
						myRoute.setAccessIndexAlongRoute(entry.getValue().getAcessIndexAlongRoute());
						myRoute.setEgressIndexAlongRoute(entry.getValue().getEgressIndexAlongRoute());
					}
				}
				return;
			} else { // arrDep != null && arrDep.dep >= this.currentClock
				this.status = Status.VEHICLE;
				if(currentClock < arrDep.arr) {
					this.currentClock = arrDep.arr;
				}
				this.nextTimeOfInterest = RunMatsim.arrivalAndDepartureTimesOfDepartureAtStop.get(departureId)[myRoute.getEgressIndexAlongRoute()].arr;
				this.currentDepartureId = departureId;
				this.currentPath.remove(0);
				addEvent(EventType.BOARDING, this.currentClock, this.currentLocation, this.nextLocationOfInterest,
						myRoute.getLineId().toString(), this.currentDepartureId.toString());
				inVehicleFunction();
			} 
		} 
	}



	private Entry<Integer, NewDepartureBundle> findFirstArrivingDepartureServicingStopPair() {
		MyTransitRoute oldRoute = (MyTransitRoute) this.currentPath.get(0).getRoute();
		Id<MyTransitStopFacilityImpl> fromStopId = oldRoute.getAccessStopId();
		Id<MyTransitStopFacilityImpl> toStopId = oldRoute.getEgressStopId();
		if(RunMatsim.stop2StopTreeMap.containsKey(fromStopId) && 
				RunMatsim.stop2StopTreeMap.get(fromStopId).containsKey(toStopId)) {
			return RunMatsim.stop2StopTreeMap.get(fromStopId).get(toStopId).ceilingEntry(this.currentClock);
		} else {
			return null;
		}
	}


	private void inVehicleFunction() {
		Entry<Integer, Id<MyTransitStopFacilityImpl>> entry = 
				RunMatsim.nextStationOfDepartureAtTime.get(this.currentDepartureId).ceilingEntry(this.currentClock);
		Id<MyTransitStopFacilityImpl> nextStation = entry.getValue();
		int nextStationArrivalTime = entry.getKey();

		if(nextStationArrivalTime < this.stopwatch + this.timeStep){
			this.currentClock = nextStationArrivalTime;
			this.currentLocation = RunMatsim.facilities.get(nextStation);
			if(this.tPSPS < this.stopwatch && RunMatsim.adaptivenessType == AdaptivenessType.FULLADAPTIVE){
				this.currentPath = calculateModifiedShortestPath((MyTransitStopFacilityImpl) this.currentLocation,
						this.pathDestinationLocation, this.currentClock);
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
		if (this.tPSPS < this.stopwatch && RunMatsim.adaptivenessType == AdaptivenessType.FULLADAPTIVE) {
			double searchRadius = Math.ceil(CoordUtils.calcProjectedEuclideanDistance(currentLocation.getCoord(), nextLocationOfInterest.getCoord()));
			this.currentPath = calcRouteFromWalk(this.currentLocation, this.pathDestinationLocation, this.currentClock, searchRadius);
			Facility newNextLocation = extractNextLocationOfInterestFromCurrentPath();
			if(newNextLocation != this.nextLocationOfInterest){
				addEvent(EventType.PIS_NOTIFICATION,this.currentClock, this.currentLocation, newNextLocation, "DURING_WALK");
			}
			this.nextLocationOfInterest = newNextLocation;
		}

		double lambda = (int) (this.stopwatch + this.timeStep - this.currentClock) * WALKING_SPEED;
		double d = CoordUtils.calcProjectedEuclideanDistance(currentLocation.getCoord(), nextLocationOfInterest.getCoord());
		if(lambda + 0.000001 < d ){ 
			this.currentLocation = updateLocationDuringAWalkLeg(lambda/d);
			this.currentClock = this.stopwatch + this.timeStep;
			return;
		} else {
			this.currentClock +=  Math.ceil(d / WALKING_SPEED);
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
		this.status = Status.DEAD;
		return;		
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
		StringBuilder s = new StringBuilder(64);
		for(PassengerDelayEvent event : events){
			s.append(id + ";" + event.type + ";" + event.time + ";" + 
					event.fromCoord.getX() + ";" + event.fromCoord.getY() + ";" + event.fromString + ";" +
					event.toCoord.getX() + ";" + event.toCoord.getY() + ";" + event.toString + ";" + 
					event.how + ";" + event.departureId + "\n");
		}
		return s.toString();
	}

	public void createAllRoutesOfDay() {
		Activity act = (Activity) plan.getPlanElements().get(0);
		Facility origin = new MyFakeFacility(act.getCoord());
		int departureTime = (int) Math.ceil(act.getEndTime().seconds());
		Facility destination = new MyFakeFacility(((Activity) plan.getPlanElements().get(2)).getCoord());
		this.currentPath = calcRouteFromActivity(origin, destination, departureTime);
	}
}
