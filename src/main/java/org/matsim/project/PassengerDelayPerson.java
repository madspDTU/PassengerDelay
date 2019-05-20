package org.matsim.project;

import java.util.LinkedList;
import java.util.List;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.pt.router.FakeFacility;

import ch.sbb.matsim.routing.pt.raptor.LeastCostRaptorRouteSelector;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptor;

public class PassengerDelayPerson {


	List<Leg> legsToDo;
	Id<Person> id;
	Plan plan;
	private Status status;
	private double endTimeWalkLeg;
	private double endTimeActivity;

	enum Status 
	{ 
		ACTIVITY, STATION, VEHICLE, WALK,; 
	} 

	PassengerDelayPerson(Id<Person> id, Plan plan){
		this.id = id;
		this.plan = plan;
		this.status = Status.ACTIVITY;
		this.legsToDo = new LinkedList<Leg>();
	}

	Coord locatePerson(double time, LeastCostRaptorRouteSelector router){
		switch(this.status){
		case ACTIVITY: 
			if(time + 5*3600 > endTimeActivity){
				calculateShortestPath(time,router);
				if(time > endTimeActivity){
					updateWalkPosition(time);
				}
			} else {
				//do nothing;
			}
			break;
		case STATION:
			stationFunction(time);
			break;
		case VEHICLE:
			vehicleFunction(time);
			break;
		case WALK:
			if(endTimeWalkLeg > time){
				updateWalkPosition(time);
				calculateShortestPath();
			} else {
				if(nextDestinationIsStation){
					stationFunction(time);
				} else {
					activityFunction(time);
				}
			}
			break;
		default:
			System.err.println("Hov hov...");
			// Some exception
			break;
		}


		return null;

	}

	private double getExpectedBoardingTime() {
		// TODO Auto-generated method stub
		return 0;
	}

	private void calculateShortestPath( Coord fromCoord, Coord toCoord, double time, SwissRailRaptor raptor) {
		
		FakeFacility fromFacility = new FakeFacility(fromCoord);
		FakeFacility toFacility = new FakeFacility(toCoord);
		legsToDo = raptor.calcRoute(fromFacility, toFacility, time, null);

	}

	private void stationFunction(double time) {
		double expectedBoardingTime = getExpectedBoardingTime();
		if(expectedBoardingTime > time){
			// Has not boarded yet
			calculateShortestPath();
		} else {
			// Has already boarded
			vehicleFunction(time);
		}
	}

	private void vehicleFunction(double time) {
		// TODO Auto-generated method stub

	}

	private void updateWalkPosition(double time) {
		// TODO Auto-generated method stub

	}
}
