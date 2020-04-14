/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.routing.pt.raptor;

import org.matsim.api.core.v01.Id;
import org.matsim.facilities.Facility;
import org.matsim.project.pt.MyTransitLineImpl;
import org.matsim.project.pt.MyTransitRouteImpl;
import org.matsim.project.pt.MyTransitRouteStopImpl;
import org.matsim.project.pt.MyTransitStopFacilityImpl;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author mrieser / SBB
 */
public class MyRaptorRoute {

    final Facility fromFacility;
    final Facility toFacility;
    private final double totalCosts;
    private double departureTime = Double.NaN;
    private double travelTime =  0;
    private int ptLegCount = 0;
    private List<RoutePart> editableParts = new ArrayList<>();
    final List<RoutePart> parts = Collections.unmodifiableList(this.editableParts);

    public MyRaptorRoute(Facility fromFacility, Facility toFacility, double totalCosts) {
        this.fromFacility = fromFacility;
        this.toFacility = toFacility;
        this.totalCosts = totalCosts;
    }

    void addNonPt(MyTransitRouteStopImpl fromStop, MyTransitRouteStopImpl toStop, int depTime, int travelTime, double distance, String mode) {
        this.editableParts.add(new RoutePart(mode, depTime, -1, depTime + travelTime, distance, null, null, fromStop, toStop));
        if (Double.isNaN(this.departureTime)) {
            this.departureTime = depTime;
        }
        this.travelTime += travelTime;
    }

 

    void addPt( MyTransitLineImpl line, MyTransitRouteImpl route, 
    		String mode, int depTime, int boardingTime, int arrivalTime, double distance, MyTransitRouteStopImpl fromRouteStop,
    		MyTransitRouteStopImpl toRouteStop) {
    	RoutePart part = new RoutePart(mode, depTime, boardingTime, arrivalTime, distance, line, route, fromRouteStop, toRouteStop);
        this.editableParts.add(part);
        if (Double.isNaN(this.departureTime)) {
            this.departureTime = depTime;
        }
        this.travelTime += (arrivalTime - depTime);
        this.ptLegCount++;
    }

    public double getTotalCosts() {
        return this.totalCosts;
    }

    public double getDepartureTime() {
        return this.departureTime;
    }

    public double getTravelTime() {
        return this.travelTime;
    }

    public int getNumberOfTransfers() {
        if (this.ptLegCount > 0) {
            return this.ptLegCount - 1;
        }
        return 0;
    }

    public Iterable<RoutePart> getParts() {
        return this.parts;
    }

    public static final class RoutePart {
        public final String mode;
        public final int depTime;
        public final int boardingTime;
        public final int arrivalTime;
        public final double distance;
        public final MyTransitLineImpl line;
        public final MyTransitRouteImpl route;
        public final MyTransitRouteStopImpl fromRouteStop;
        public final MyTransitRouteStopImpl toRouteStop;
        

        RoutePart(String mode, int depTime, int boardingTime, int arrivalTime, double distance, MyTransitLineImpl line, 
        		MyTransitRouteImpl route, MyTransitRouteStopImpl fromRouteStop, MyTransitRouteStopImpl toRouteStop) {
            this.mode = mode;
            this.depTime = depTime;
            this.boardingTime = boardingTime;
            this.arrivalTime = arrivalTime;
            this.distance = distance;
            this.line = line;
            this.route = route;
            this.fromRouteStop = fromRouteStop;
            this.toRouteStop = toRouteStop;
        }
    }
    
}
