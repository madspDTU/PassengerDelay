package org.matsim.project;

import org.matsim.api.core.v01.Coord;
import org.matsim.facilities.Facility;
import org.matsim.project.PassengerDelayEvent.EventType;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

public class PassengerDelayEvent {

	enum EventType {
		ACTIVITY_START, WALK_START, WAIT_START, BOARDING, UNBOARDING, SIMULATION_END, PIS_NOTIFICATION  
	}

	EventType type;
	double time;
	Coord fromCoord;
	String fromString;
	Coord toCoord;
	String toString;
	String how;
	String departureId;


	public PassengerDelayEvent(EventType type, double time, Facility fromLocation, Facility toLocation, String how, String departureId) {
		this.type = type;
		this.time = time;
		this.fromCoord = fromLocation.getCoord();
		String fromString;
		if( fromLocation instanceof TransitStopFacility ) {
			fromString = ((TransitStopFacility) fromLocation).getId().toString();
		} else if(how.equals("DURING_WALK")) {
			fromString = "COORDINATE";
		} else {
			fromString = "ACTIVITY";
		}
		this.fromString = fromString;
		this.toCoord = toLocation.getCoord();
		this.toString = toLocation instanceof TransitStopFacility ? 
				((TransitStopFacility) toLocation).getId().toString() : "ACTIVITY";
		this.how = how;
		this.departureId = departureId;
	}
	
	public PassengerDelayEvent(EventType type, double time, Facility fromLocation, Facility toLocation, String how) {
		this(type, time, fromLocation, toLocation, how, "");
	}
}
