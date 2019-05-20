package org.matsim.project;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Route;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.router.StageActivityTypes;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.facilities.Facility;
import org.matsim.pt.router.FakeFacility;
import org.matsim.pt.routes.ExperimentalTransitRoute;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import ch.sbb.matsim.routing.pt.raptor.LeastCostRaptorRouteSelector;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptor;

public class PassengerDelayPerson {

	private final double WALKING_SPEED = 1;

	List<Leg> currentRoute;
	Id<Person> id;
	Plan plan;
	private Status status;
	private double nextTimeOfInterest;
	Facility destinationLocation;
	Facility nextLocationOfInterest;
	Facility currentLocation;
	private HashMap<Id<Facility>, Facility> facilities = new HashMap<Id<Facility>, Facility>();

	private double currentClock;

	enum Status 
	{ 
		ACTIVITY, STATION, VEHICLE, WALK,; 
	} 

	PassengerDelayPerson(Id<Person> id, Plan plan, SwissRailRaptor raptor){
		this.id = id;
		this.plan = plan;
		this.status = Status.ACTIVITY;
		this.currentLocation = new FakeFacility(((Activity) plan.getPlanElements().get(0)).getCoord());
		this.nextTimeOfInterest = TripStructureUtils.getLegs(plan.getPlanElements()).get(0).getDepartureTime();
		this.destinationLocation = new FakeFacility(((Activity) plan.getPlanElements().get(2)).getCoord());
		this.currentRoute = calculateShortestPath(currentLocation, destinationLocation, nextTimeOfInterest, raptor);
	}

	void advance(double time, SwissRailRaptor router){
		switch(this.status){
		case ACTIVITY: 
			activityFunction(time, router);
			break;
		case STATION:
			stationFunction(time, router);
			break;
		case VEHICLE:
			vehicleFunction(time, router);
			break;
		case WALK:
			walkFunction(time, router);	
			break;
		default:
			System.err.println("Hov hov...");
			// Some exception
			break;
		}	

	}


	private void activityFunction(double time, SwissRailRaptor router) {
		if(time > nextTimeOfInterest - 5*60){
			currentRoute = calculateShortestPath(currentLocation, destinationLocation, time, router);
			if(time > nextTimeOfInterest){
				this.status = Status.WALK;
				walkFunction(time, router);
			}
		} else {
			currentClock = time;
		}
	}

	private List<Leg> calculateShortestPath( Facility fromFacility, Facility toFacility, double time, SwissRailRaptor raptor) {

		List<Leg> trip = raptor.calcRoute(fromFacility, toFacility, time, null);
		Leg leg = trip.get(0);
		Route route = leg.getRoute();
		if(route instanceof ExperimentalTransitRoute){
			nextLocationOfInterest = facilities.get(((ExperimentalTransitRoute) route).getAccessStopId());
		}
		return trip;
	}

	private void stationFunction(double time, SwissRailRaptor router) {
		nextLocationOfInterest =
				facilities.get(((ExperimentalTransitRoute) currentRoute.get(0).getRoute()).getEgressStopId());
		if( currentRoute.get(0).getDepartureTime() < time && currentRoute.get(0).getDepartureTime() >= currentClock){
			this.status = Status.VEHICLE;
			currentClock = currentRoute.get(0).getDepartureTime();
			vehicleFunction(time, router);
		} else {
			currentRoute = router.calcRoute(currentLocation, destinationLocation, time, null);
			currentClock = time;
		}

	}

	private void vehicleFunction(double time, SwissRailRaptor router) {
		double egressTime = currentRoute.get(0).getDepartureTime() + currentRoute.get(0).getTravelTime();
		if(time > egressTime){
			// Has already left train.
			currentClock = time;
			currentLocation = nextLocationOfInterest;
			currentRoute.remove(0);
			if(currentRoute.get(0).getMode().equals(TransportMode.transit_walk)){
				this.status = Status.WALK;
				if(currentRoute.size() == 1){
					// Next location is activity
					nextLocationOfInterest = destinationLocation;
				} else {
					// Next location is station
					nextLocationOfInterest = facilities.get(((ExperimentalTransitRoute) currentRoute.get(1).getRoute()).getAccessStopId());
				}
				walkFunction(time,router);
			} else {
				stationFunction(time, router);
			}
		}


	}

	private void walkFunction(double time, SwissRailRaptor router) {
		double d = NetworkUtils.getEuclideanDistance(currentLocation.getCoord(), nextLocationOfInterest.getCoord());
		double timeDuration = time - currentClock;
		double rho = d / (timeDuration * WALKING_SPEED);
		if( rho < 1 ){
			double currentX = currentLocation.getCoord().getX();
			double currentY = currentLocation.getCoord().getY();
			double nextX = currentLocation.getCoord().getX();
			double nextY = currentLocation.getCoord().getY();
			currentLocation = new FakeFacility(	new Coord(currentX + rho * (nextX - currentX), 
					currentY + rho * (nextY - currentY)));
			currentClock = time;
			currentRoute = router.calcRoute(currentLocation, destinationLocation, time, null);
		} else {
			currentLocation = nextLocationOfInterest;
			currentClock += timeDuration / rho;
			if(currentLocation instanceof TransitStopFacility){
				//Arrived at a station
				this.status = Status.STATION;
				currentRoute.remove(0);
				stationFunction(time, router);
			} else {
				// Arrived at an activity
				this.status = Status.ACTIVITY;
				currentClock = time;
			}
		}
	}
}
