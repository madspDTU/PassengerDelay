package ch.sbb.matsim.routing.pt.raptor;

import ch.sbb.matsim.routing.pt.raptor.MySwissRailRaptorData.RRouteStop;


public class PathElement {
	final PathElement comingFrom;
	final RRouteStop toRouteStop;
	final int nextStopDepartureTime;
	final int arrivalTime;
	final double costAtArrival;
	final double costAtDeparture;
	double distance;  //madsp: Removed a "final"
	final int transferCount;
	final double potentialAtArrival;
	final double potentialAtDeparture;
	
	PathElement(PathElement comingFrom, RRouteStop toRouteStop, int arrivalTime, int nextStopDepartureTime,
			double costAtArrival, double costAtDeparture, double distance, int transferCount, 
			double potentialAtArrival, double potentialAtDeparture) {
		this.comingFrom = comingFrom;
		this.toRouteStop = toRouteStop;
		this.nextStopDepartureTime = nextStopDepartureTime;
		this.arrivalTime = arrivalTime;
		this.costAtArrival = costAtArrival;
		this.costAtDeparture = costAtDeparture;
		this.distance = distance;
		this.transferCount = transferCount;
		this.potentialAtArrival = potentialAtArrival;
		this.potentialAtDeparture = potentialAtDeparture;
	}

	public boolean isWalkOrWait() {
		return Double.isNaN(this.potentialAtArrival);
	}
}

