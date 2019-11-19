package org.matsim.project;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Route;
import org.matsim.pt.routes.ExperimentalTransitRoute;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

public class MyTransitRoute implements Route {
	
	private ExperimentalTransitRoute route;
	private Id<Departure> departureId;
	private double legDepartureTime;

	public MyTransitRoute(ExperimentalTransitRoute route) {
		this.route = route;
	}
	
	public void setDepartureId(Id<Departure> departureId){
		this.departureId = departureId;
	}
	
	public Id<Departure> getDepartureId(){
		return this.departureId;
	}
	
	public void setLegDepartureTime(double legStartTime){
		this.legDepartureTime = legStartTime;
	}
	
	public double getLegDepartureTime(){
		return this.legDepartureTime;
	}
	
	public ExperimentalTransitRoute getTransitRoute(){
		return this.route;
	}

	@Override
	public double getDistance() {
		return route.getDistance();
	}

	@Override
	public void setDistance(double distance) {
		route.setDistance(distance);
	}

	@Override
	public double getTravelTime() {
		return route.getTravelTime();
	}

	@Override
	public void setTravelTime(double travelTime) {
		route.setTravelTime(travelTime);
	}

	@Override
	public Id<Link> getStartLinkId() {
		return route.getStartLinkId();
	}

	@Override
	public Id<Link> getEndLinkId() {
		return route.getEndLinkId();
	}

	@Override
	public void setStartLinkId(Id<Link> linkId) {
		route.setStartLinkId(linkId);
	}

	@Override
	public void setEndLinkId(Id<Link> linkId) {
		route.setEndLinkId(linkId);
	}

	@Override
	public String getRouteDescription() {
		return route.getRouteDescription();
	}

	@Override
	public void setRouteDescription(String routeDescription) {
		route.setRouteDescription(routeDescription);
	}

	@Override
	public String getRouteType() {
		return route.getRouteType();
	}

	@Override
	public Route clone() {
		return route;
	}
	
	public Id<TransitStopFacility> getAccessStopId(){
		return route.getAccessStopId();
	}
	
	public Id<TransitStopFacility> getEgressStopId(){
		return route.getEgressStopId();
	}
	
	public Id<TransitRoute> getRouteId(){
		return route.getRouteId();
	}
	
	public Id<TransitLine> getLineId(){
		return route.getLineId();
	}
}
