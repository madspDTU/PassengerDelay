/* *********************************************************************** *
 * project: org.matsim.*
 * TransitRouteStop.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2009 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.project.pt;

/**
 * Describes the stop within a route of a transit line. Specifies also at
 * what time a headway is expected at the stop as offset from the route start.
 *
 * @author mrieser
 */
public class MyTransitRouteStopImpl {

	
	private MyTransitStopFacilityImpl stop;
	private final int departureOffset;
	private final int arrivalOffset;
	private final int indexAlongRoute;
	
	public MyTransitRouteStopImpl(final MyTransitStopFacilityImpl stop, final int arrivalOffset, final int departureOffset, final int indexAlongRoute) {
		this.stop = stop;
		this.departureOffset = departureOffset;
		this.arrivalOffset = arrivalOffset;
		this.indexAlongRoute = indexAlongRoute;
	}

	public MyTransitStopFacilityImpl getStopFacility() {
		return this.stop;
	}

	public int getDepartureOffset() {
		return this.departureOffset;
	}

	public int getArrivalOffset() {
		return this.arrivalOffset;
	}
	
	public int getIndexAlongRoute() {
		return this.indexAlongRoute;
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof MyTransitRouteStopImpl)) {
			return false;
		}
		MyTransitRouteStopImpl other = (MyTransitRouteStopImpl) obj;
		if (this.stop == null) {
			if (other.getStopFacility() != null) {
				return false;
			}
		} else {
			if (!stop.equals(other.getStopFacility())) {
				return false;
			}
		}
		if (this.departureOffset != other.getDepartureOffset()) {
			return false;
		} 
		if (this.arrivalOffset != other.getArrivalOffset()) {
			return false;
		}
		return true;
	}
	
	@Override
	public String toString() {
		return "[TransitRouteStop stop=" + this.stop.getId() + " offset=" + this.departureOffset +" ]";
	}

}
