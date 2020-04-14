/* *********************************************************************** *
 * project: org.matsim.*
 * TransitLine.java
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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.matsim.api.core.v01.Id;


/**
 * Description of a single transit line. Can have multiple routes (e.g. from A to B and from B to A).
 *
 * @author mrieser
 */
public class MyTransitLineImpl  {

	private final Id<MyTransitLineImpl> lineId;
	private final Map<Id<MyTransitRouteImpl>, MyTransitRouteImpl> transitRoutes = new LinkedHashMap<>(5);
	
	public MyTransitLineImpl(final Id<MyTransitLineImpl> id) {
		this.lineId = id;
	}

	public Id<MyTransitLineImpl> getId() {
		return this.lineId;
	}
	
	
	
	public void addRoute(final MyTransitRouteImpl transitRoute) {
		final Id<MyTransitRouteImpl> id = transitRoute.getId();
		if (this.transitRoutes.containsKey(id)) {
			throw new IllegalArgumentException("There is already a transit route with id " + id.toString() + " with line " + this.lineId);
		}
		this.transitRoutes.put(id, transitRoute);
	}

	public Map<Id<MyTransitRouteImpl>, MyTransitRouteImpl> getRoutes() {
		return Collections.unmodifiableMap(this.transitRoutes);
	}

	public boolean removeRoute(final MyTransitRouteImpl route) {
		return null != this.transitRoutes.remove(route.getId());
	}

	
	public String toString() {
		return "[TransitLineImpl: line=" + this.lineId.toString() + ", #routes=" + this.transitRoutes.size() + "]";
	}
	
}
