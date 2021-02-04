package org.matsim.project;

import java.util.Map;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.facilities.Facility;

public final class MyFakeFacility implements Facility {
	private Coord coord;
	public MyFakeFacility( Coord coord ) { this.coord = coord ; }
	@Override public Coord getCoord() {
		return this.coord ;
	}

	public Id getId() {
		return null;
	}

	@Override
	public Map<String, Object> getCustomAttributes() {
		// TODO Auto-generated method stub
		throw new RuntimeException("not implemented") ;
	}

	@Override
	public Id getLinkId() {
		return null;
	}
	
}