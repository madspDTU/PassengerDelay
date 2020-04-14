package org.matsim.project;

import org.matsim.api.core.v01.Coord;
import org.matsim.facilities.Facility;
import org.matsim.project.pt.MyTransitStopFacilityImpl;

public class PassengerDelayEvent {

	enum EventType {
		ACTIVITY_START, WALK_START, WAIT_START, BOARDING, UNBOARDING, SIMULATION_END, PIS_NOTIFICATION  
	}

	EventType type;
	int time;
	Coord fromCoord;
	String fromString;
	Coord toCoord;
	String toString;
	String how;
	String departureId;


	public PassengerDelayEvent(EventType type, int time, Facility fromLocation, Facility toLocation, String how, String departureId) {
		this.type = type;
		this.time = time;
		this.fromCoord = fromLocation.getCoord();
		String fromString;
		if( fromLocation instanceof MyTransitStopFacilityImpl ) {
			fromString = ((MyTransitStopFacilityImpl) fromLocation).getId().toString();
		} else if(how.equals("DURING_WALK")) {
			fromString = "COORDINATE";
		} else {
			fromString = "ACTIVITY";
		}
		this.fromString = fromString;
		this.toCoord = toLocation.getCoord();
		this.toString = toLocation instanceof MyTransitStopFacilityImpl ? 
				((MyTransitStopFacilityImpl) toLocation).getId().toString() : "ACTIVITY";
		this.how = how;
		this.departureId = departureId;
	}
	
	public PassengerDelayEvent(EventType type, int time, Facility fromLocation, Facility toLocation, String how) {
		this(type, time, fromLocation, toLocation, how, "");
	}
}
