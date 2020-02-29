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
	
	private ExperimentalTransitRoute delegate;
	private Id<Departure> departureId;
	private double legDepartureTime;

	public MyTransitRoute(ExperimentalTransitRoute route) {
		this.delegate = route;
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
		return this.delegate;
	}

	@Override
	public double getDistance() {
		return delegate.getDistance();
	}

	@Override
	public void setDistance(double distance) {
		delegate.setDistance(distance);
	}

	@Override
	public double getTravelTime() {
		return delegate.getTravelTime();
	}

	@Override
	public void setTravelTime(double travelTime) {
		delegate.setTravelTime(travelTime);
	}

	@Override
	public Id<Link> getStartLinkId() {
		return delegate.getStartLinkId();
	}

	@Override
	public Id<Link> getEndLinkId() {
		return delegate.getEndLinkId();
	}

	@Override
	public void setStartLinkId(Id<Link> linkId) {
		delegate.setStartLinkId(linkId);
	}

	@Override
	public void setEndLinkId(Id<Link> linkId) {
		delegate.setEndLinkId(linkId);
	}

	@Override
	public String getRouteDescription() {
		return delegate.getRouteDescription();
	}

	@Override
	public void setRouteDescription(String routeDescription) {
		delegate.setRouteDescription(routeDescription);
	}

	@Override
	public String getRouteType() {
		return delegate.getRouteType();
	}

	@Override
	public Route clone() {
		return delegate;
	}
	
	public Id<TransitStopFacility> getAccessStopId(){
		return delegate.getAccessStopId();
	}
	
	public Id<TransitStopFacility> getEgressStopId(){
		return delegate.getEgressStopId();
	}
	
	public Id<TransitRoute> getRouteId(){
		return delegate.getRouteId();
	}
	
	public Id<TransitLine> getLineId(){
		return delegate.getLineId();
	}
}
