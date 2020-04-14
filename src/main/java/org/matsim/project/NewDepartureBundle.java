package org.matsim.project;

import org.matsim.api.core.v01.Id;
import org.matsim.project.pt.MyDepartureImpl;

public class NewDepartureBundle {

	private final int accessIndexAlongRoute;
	private final int egressIndexAlongRoute;
	private final Id<MyDepartureImpl> departureId;
	
	NewDepartureBundle(final Id<MyDepartureImpl> departureId, final int accessIndex, final int egressIndex){
		this.accessIndexAlongRoute = accessIndex;
		this.egressIndexAlongRoute = egressIndex;
		this.departureId = departureId;
	}
	
	public int getAcessIndexAlongRoute() {
		return this.accessIndexAlongRoute;
	}
	
	public int getEgressIndexAlongRoute() {
		return this.egressIndexAlongRoute;
	}
	
	public Id<MyDepartureImpl> getDepartureId() {
		return this.departureId;
	}
	
		
}
