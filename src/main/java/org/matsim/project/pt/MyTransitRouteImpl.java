/* *********************************************************************** *
 * project: org.matsim.*
 * TransitRoute.java
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

import org.matsim.api.core.v01.Id;



/**
 * Describes a route of a transit line, including its stops and the departures along this route.
 *
 * @author mrieser
 */
public class MyTransitRouteImpl {

	private final Id<MyTransitRouteImpl> routeId;
	private final MyTransitRouteStopImpl[] stops;
	private final HashSet<MyTransitRouteStopImpl> stopsHash;
	private final ArrayList<MyDepartureImpl> departures = new ArrayList<MyDepartureImpl>(8);
	private String transportMode;

	public MyTransitRouteImpl(final Id<MyTransitRouteImpl> id,  final Collection<MyTransitRouteStopImpl> stops, final String transportMode) {
		this.routeId = id;
		this.transportMode = transportMode;
		this.stops = new MyTransitRouteStopImpl[stops.size()];
		this.stopsHash = new HashSet<>(8);
		int i = 0;
		for(MyTransitRouteStopImpl stop : stops) {
			this.stopsHash.add(stop);
			this.stops[i] = stop;
			i++;
		}
	}

	public Id<MyTransitRouteImpl> getId() {
		return this.routeId;
	}

	/**
	 * Sets the transport mode with which this transit route is handled, e.g.
	 * <code>bus</code> or <code>train</code>.
	 *
	 * @param mode
	 */
	public void setTransportMode(final String mode) {
		this.transportMode = mode;
	}

	public String getTransportMode() {
		return this.transportMode;
	}

	public boolean addDeparture(final MyDepartureImpl departure) {
		int n = this.departures.size() -1;
		int departureTime = departure.getDepartureTime();
		if(n <= 5) {
			while(n >= 0) {
				double existingDepartureTime = this.departures.get(n).getDepartureTime();
				if(existingDepartureTime == departureTime) {
					//		System.out.println("Departure " + departure.getId() + " rejected because " + this.departures.get(n).getId() + " is the same");
					return false;
				} else if(existingDepartureTime < departureTime) {
					break;
				} else {
					n--;
				}
			}
			this.departures.add(n+1, departure);
		} else {
			n = binarySearch(this.departures, 0, n, departureTime);
			if(n >= 0) { //Already exists!
				//System.out.println("Departure " + departure.getId() + " rejected because " + this.departures.get(n).getId() + " is the same");
				return false;
			} else {
				// When binary search does not find a match, it returns -(insertion point) -1.
				this.departures.add(-n-1, departure);
			}
		}
		return true;
	}

	static int binarySearch(ArrayList<MyDepartureImpl> arr, int l, int r, int x) { 
		if (r >= l) { 
			int mid = l + (r - l) / 2; 
			int value = arr.get(mid).getDepartureTime();
			if (value == x) {
				return mid; 
			}
			if (x < value ) {
				return binarySearch(arr,l, mid - 1, x); 
			}
			return binarySearch(arr,mid + 1, r, x); 
		} 
		return -l - 1; // return -insertation place - 1
	} 

	public boolean removeDeparture(final Id<MyDepartureImpl> departureId) {
		int n = this.departures.size()-1;
		while(n>=0) {
			if(this.departures.get(n).getId().equals(departureId)) {
				this.departures.remove(n);
				return true;
			}
		}
		return false;
	}

	public void removeDeparture(int i) {
		this.departures.remove(i);
	}

	public ArrayList<MyDepartureImpl> getDepartures() {
		return this.departures;
	}


	public boolean containsStop(MyTransitRouteStopImpl stop) {
		return this.stopsHash.contains(stop);
	}


	@Override
	public String toString() {
		return "[TransitRouteImpl: route=" + this.routeId.toString() + ", #departures=" + this.departures.size() + "]";
	}

	public MyDepartureImpl getFirstDeparture() {
		return this.departures.get(0);
	}

	public MyDepartureImpl getLastDeparture() {
		return this.departures.get(this.departures.size()-1);
	}

	public MyTransitRouteStopImpl getFirstStop() {
		return this.stops[0];
	}

	public MyTransitRouteStopImpl getLastStop() {
		return this.stops[this.stops.length-1];
	}

	public MyTransitRouteStopImpl getStop(int index) {
		return this.stops[index];
	}

	public MyTransitRouteStopImpl[] getStops() {
		return this.stops;
	}

	public int getNumberOfStops() {
		return this.stops.length;
	}

	public Id<MyDepartureImpl> getDepartureIdFromDepartureTime(int departureTime) {
		int index =  binarySearch(this.departures,0,this.departures.size()-1,departureTime);
		if(index < 0) {
			System.out.println("Huge error in internal binary search for determination of departure time");
			return null;
		}
		return this.departures.get(index).getId();
	}

}
