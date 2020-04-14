/* *********************************************************************** *
 * project: org.matsim.*
 * Departure.java
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

import org.matsim.api.core.v01.Id;


/**
 * Describes a single departure along a route in a transit line.
 *
 * @author mrieser
 */
public class MyDepartureImpl{

	private final Id<MyDepartureImpl> id;
	private final int departureTime;

	public MyDepartureImpl(final Id<MyDepartureImpl> id,   final int departureTime) {
		this.id = id;
		this.departureTime = departureTime;
	}

	public Id<MyDepartureImpl> getId() {
		return this.id;
	}

	public int getDepartureTime() {
		return this.departureTime;
	}

	public String toString() {
		return "[DepartureImpl: id=" + this.id + ", depTime=" + this.departureTime + "]";
	}

}
