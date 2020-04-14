package org.matsim.project.pt;

import java.util.Map;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.facilities.Facility;

public class MyTransitStopFacilityImpl implements Facility {

	public Coord coord;
	public Id<MyTransitStopFacilityImpl> id;
	private int arrayId;
	
	public MyTransitStopFacilityImpl(Id<MyTransitStopFacilityImpl> id, Coord coord, int arrayId) {
		this.id = id;
		this.coord = coord;
		this.arrayId = arrayId;
	}
	
	@Override
	public Coord getCoord() {
		return coord;
	}

	@Override
	public Map<String, Object> getCustomAttributes() {
		return null;
	}

	@Override
	public Id<Link> getLinkId() {
		return null;
	}
	
	public Id<MyTransitStopFacilityImpl> getId() {
		return this.id;
	}
	
	public int getArrayId() {
		return this.arrayId;
	}
	
	//TODO You could actually include all transfer stops here....

}
