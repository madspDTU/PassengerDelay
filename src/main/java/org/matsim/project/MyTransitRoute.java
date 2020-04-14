package org.matsim.project;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Route;
import org.matsim.core.utils.misc.OptionalTime;
import org.matsim.core.utils.misc.OptionalTimes;
import org.matsim.project.pt.MyDepartureImpl;
import org.matsim.project.pt.MyTransitLineImpl;
import org.matsim.project.pt.MyTransitRouteImpl;
import org.matsim.project.pt.MyTransitStopFacilityImpl;
import org.matsim.pt.routes.ExperimentalTransitRoute;

import ch.sbb.matsim.routing.pt.raptor.MyRaptorRoute;

public class MyTransitRoute implements Route {
	
	private Id<MyTransitStopFacilityImpl> accessStopId;
	private Id<MyTransitStopFacilityImpl> egressStopId;
	private Id<MyDepartureImpl> departureId;
	private int legDepartureTime;
	private Id<MyTransitLineImpl> lineId;
	private int travelTime;
	private int accessIndexAlongRoute;
	private int egressIndexAlongRoute;
	

	public MyTransitRoute(MyRaptorRoute.RoutePart part, Id<MyDepartureImpl> departureId) {
		this.accessStopId = part.fromRouteStop.getStopFacility().getId();
		this.egressStopId = part.toRouteStop.getStopFacility().getId();
		this.lineId = part.line.getId();
		this.departureId = departureId;
		this.travelTime = part.arrivalTime - part.depTime;
		this.legDepartureTime = part.depTime;
		this.accessIndexAlongRoute = part.fromRouteStop.getIndexAlongRoute();
		this.egressIndexAlongRoute = part.toRouteStop.getIndexAlongRoute();
	}
	
	
	public void setDepartureId(Id<MyDepartureImpl> id) {
		this.departureId = id;
	}
	
	public int getLegDepartureTime() {
		return this.legDepartureTime;
	}
	
	public Id<MyDepartureImpl> getDepartureId() {
		return this.departureId;
	}

	@Override
	public double getDistance() {
		return Double.NaN;
	}

	@Override
	public void setDistance(double distance) {
	}


	@Override
	public void setTravelTime(double travelTime) {
	}

	@Override
	public Id<Link> getStartLinkId() {
		return null;
	}

	@Override
	public Id<Link> getEndLinkId() {
		return null;
	}

	@Override
	public void setStartLinkId(Id<Link> linkId) {
	}

	@Override
	public void setEndLinkId(Id<Link> linkId) {
	}

	@Override
	public String getRouteDescription() {
		return "";
	}

	@Override
	public void setRouteDescription(String routeDescription) {
	}

	@Override
	public String getRouteType() {
		return "";
	}

	@Override
	public Route clone() {
		return null;
	}
	
	public Id<MyTransitStopFacilityImpl> getAccessStopId(){
		return this.accessStopId;
	}
	
	public Id<MyTransitStopFacilityImpl> getEgressStopId(){
		return this.egressStopId;
	}

		
	public Id<MyTransitLineImpl> getLineId(){
		return this.lineId;
	}

	public void setLineId(Id<MyTransitLineImpl> id) {
		this.lineId = id;
	}
	
	public int getAccessIndexAlongRoute() {
		return this.accessIndexAlongRoute;
	}
	
	public int getEgressIndexAlongRoute() {
		return this.egressIndexAlongRoute;
	}


	public void setAccessIndexAlongRoute(int accessIndexAlongRoute) {
		this.accessIndexAlongRoute = accessIndexAlongRoute;
	}
	
	public void setEgressIndexAlongRoute(int egressIndexAlongRoute) {
		this.egressIndexAlongRoute = egressIndexAlongRoute;
	}


	@Override
	public OptionalTime getTravelTime() {
		return OptionalTime.defined(travelTime);
	}


	@Override
	public void setTravelTimeUndefined() {
		//Do nothing		
	}

}
