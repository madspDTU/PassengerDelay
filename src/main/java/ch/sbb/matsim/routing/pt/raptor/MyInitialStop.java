/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.routing.pt.raptor;

import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.project.pt.MyTransitStopFacilityImpl;

import java.util.List;

/**
 * Specifies the access or egress time and costs for a specific TransitStopFacility and a specific mode.
 *
 * @author mrieser / SBB
 */
public class MyInitialStop {

    final MyTransitStopFacilityImpl stop;
    final double accessCost;
    final int accessTime;
    final double distance;
    final String mode;
    final List<? extends PlanElement> planElements;

    public MyInitialStop(MyTransitStopFacilityImpl stop, double accessCost, int accessTime, double distance, String mode) {
        this.stop = stop;
        this.accessCost = accessCost;
        this.accessTime = accessTime;
        this.distance = distance;
        this.mode = mode;
        this.planElements = null;
    }

    public MyInitialStop(MyTransitStopFacilityImpl stop, double accessCost, int accessTime, List<? extends PlanElement> planElements) {
        this.stop = stop;
        this.accessCost = accessCost;
        this.accessTime = accessTime;
        this.distance = Double.NaN;
        this.mode = null;
        this.planElements = planElements;
    }

	@Override
	public String toString() {
		return "[ stopId=" + stop.getId() + " | accessCost=" + accessCost + " | mode=" + mode + " ]" ;
	}
}
