/* *********************************************************************** *
 * project: org.matsim.*
 * TransitSchedule.java
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

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.trafficmonitoring.FreeSpeedTravelTime;
import org.matsim.core.utils.collections.QuadTree;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.project.RunMatsim;
import org.matsim.utils.leastcostpathtree.LeastCostPathTree;
import org.matsim.utils.leastcostpathtree.LeastCostPathTree.NodeData;


/**
 * Default implementation of {@link TransitSchedule}.
 * 
 * {@inheritDoc}
 * 
 * @author mrieser
 */
public class MyTransitScheduleImpl  {

	private final Map<Id<MyTransitLineImpl>, MyTransitLineImpl> transitLines;
	private final Map<Id<MyTransitStopFacilityImpl>, MyTransitStopFacilityImpl> stopFacilities;

	public MyTransitScheduleImpl() {
		this.transitLines = new HashMap<>();
		this.stopFacilities = new HashMap<>();
	}


	public void addTransitLine(final MyTransitLineImpl line) {
		final Id<MyTransitLineImpl> id = line.getId();
		if (this.transitLines.containsKey(id)) {
			throw new IllegalArgumentException("There is already a transit line with id " + id.toString());
		}
		this.transitLines.put(id, line);
	}


	public boolean removeTransitLine(MyTransitLineImpl line) {
		MyTransitLineImpl oldLine = this.transitLines.remove(line.getId());
		if (oldLine == null) {
			return false;
		}
		if (oldLine != line) {
			this.transitLines.put(oldLine.getId(), oldLine);
			return false;
		}
		return true;
	}


	public void addStopFacility(final MyTransitStopFacilityImpl stop) {
		final Id<MyTransitStopFacilityImpl> id = stop.getId();
		if (this.stopFacilities.containsKey(id)) {
			throw new IllegalArgumentException("There is already a stop facility with id " + id.toString());
		}
		this.stopFacilities.put(id, stop);
	}


	public Map<Id<MyTransitLineImpl>, MyTransitLineImpl> getTransitLines() {
		return this.transitLines;
	}

	public Map<Id<MyTransitStopFacilityImpl>, MyTransitStopFacilityImpl> getFacilities() {
		return this.stopFacilities;
	}

	public void removeStopFacility(Id<MyTransitStopFacilityImpl> stopId) {
		this.stopFacilities.remove(stopId);
	}


	public final static QuadTree<MyTransitStopFacilityImpl> createQuadTreeOfTransitStopFacilities(Collection<MyTransitStopFacilityImpl> transitStopFacilities) {
		double minX = Double.POSITIVE_INFINITY;
		double minY = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY;
		double maxY = Double.NEGATIVE_INFINITY;
		for (MyTransitStopFacilityImpl stopFacility : transitStopFacilities) {
			double x = stopFacility.getCoord().getX();
			double y = stopFacility.getCoord().getY();

			if (x < minX)
				minX = x;
			if (y < minY)
				minY = y;
			if (x > maxX)
				maxX = x;
			if (y > maxY)
				maxY = y;
		}
		QuadTree<MyTransitStopFacilityImpl> stopsQT = new QuadTree<>(minX, minY, maxX, maxY);
		for (MyTransitStopFacilityImpl stopFacility : transitStopFacilities) {
			double x = stopFacility.getCoord().getX();
			double y = stopFacility.getCoord().getY();
			stopsQT.put(x, y, stopFacility);
		}
		return stopsQT;
	}



	public double[][] createShortestPathDistances(boolean calculateProperly ) {
		System.out.print("Calculating minimum stop-to-stop shortest path distances took... ");
		long start = System.currentTimeMillis();
		Network network = NetworkUtils.createNetwork();
		double[][] distances = new double[RunMatsim.numberOfTransitStopFacilities][RunMatsim.numberOfTransitStopFacilities];
		if(!calculateProperly) {
			return distances;
		}
				
		for (double[] row : distances) {
			Arrays.fill(row, Double.POSITIVE_INFINITY);
		}
		double[][] leastCosts = new double[RunMatsim.numberOfTransitStopFacilities][RunMatsim.numberOfTransitStopFacilities];
		for (double[] row : leastCosts) {
			Arrays.fill(row, Double.POSITIVE_INFINITY);
		}
		

		//Add nodes to network
		for(MyTransitStopFacilityImpl stop : this.stopFacilities.values()) {
			network.addNode(NetworkUtils.createNode(Id.create(stop.getArrayId(),Node.class), stop.getCoord()));
		}

		//Compute walk distances
		for(Node node : network.getNodes().values()) {
			int stopInt = Integer.parseInt(String.valueOf(node.getId().toString()));
			double radius = RunMatsim.maxBeelineTransferWalk;
			Collection<Node> nodes = NetworkUtils.getNearestNodes(network, node.getCoord(), radius);
			while(nodes.size() < 5) {
				radius += RunMatsim.extensionRadius / 3.;
				nodes = NetworkUtils.getNearestNodes(network, node.getCoord(), radius);
			}
			for(Node toNode : nodes) {
				int toStopInt = Integer.parseInt(String.valueOf(toNode.getId().toString()));
				double cost = Math.ceil(CoordUtils.calcProjectedEuclideanDistance(node.getCoord(), toNode.getCoord())) * -RunMatsim.walkTimeUtility;
				leastCosts[stopInt][toStopInt] = cost + -RunMatsim.transferUtility;
			}
		}

		//Compute distances
		for(MyTransitLineImpl line : this.transitLines.values()) {
			for(MyTransitRouteImpl route : line.getRoutes().values()) {
				String mode = route.getTransportMode();
				double marginalUtility = 1;
				if(mode.equals(RunMatsim.MODE_BUS)) {
					marginalUtility = RunMatsim.busTimeUtility;
				} else if(mode.equals(RunMatsim.MODE_TRAIN)) {
					marginalUtility = RunMatsim.trainTimeUtility;
				} else if(mode.equals(RunMatsim.MODE_LOCAL_TRAIN)) {
					marginalUtility = RunMatsim.localTrainTimeUtility;
				} else if(mode.equals(RunMatsim.MODE_S_TRAIN)) {
					marginalUtility = RunMatsim.sTrainTimeUtility;
				} else if(mode.equals(RunMatsim.MODE_METRO)) {
					marginalUtility = RunMatsim.metroTimeUtility;
				}
				MyTransitRouteStopImpl fromStop = null;
				int fromInt = -1;
				for(MyTransitRouteStopImpl toStop : route.getStops()) {
					int toInt = toStop.getStopFacility().getArrayId();
					if(fromInt != -1) {
						double cost = (toStop.getArrivalOffset() - fromStop.getDepartureOffset()) * -marginalUtility;
						// TODO Quick and dirty fix...
						if(cost <= 0) {
							cost = 0.0001;
						}
						if(cost < leastCosts[fromInt][toInt]) {
							leastCosts[fromInt][toInt] = cost;
						}
						
					}
					fromStop = toStop;
					fromInt= toInt;
				}
			}
		}

		//Add links to network;
		for(int i = 0; i < RunMatsim.numberOfTransitStopFacilities; i++) {
			Node fromNode = network.getNodes().get(Id.create(i,Node.class));
			for(int j = 0; j < RunMatsim.numberOfTransitStopFacilities; j++) {
				if(leastCosts[i][j] < Double.POSITIVE_INFINITY) {
					Node toNode = network.getNodes().get(Id.create(j,Node.class));
					Id<Link> linkId = Id.create(fromNode.getId()+"_"+toNode.getId(), Link.class);
					Link link = NetworkUtils.createLink(linkId, fromNode, toNode, network, leastCosts[i][j], 1., 1. , 1.);
					network.addLink(link);
				}
			}
		}
		//Create shortestPathTrees;
		TravelTime travelTime = new FreeSpeedTravelTime();
		OnlyTimeDependentTravelDisutility timeAsTravelDisutility = new OnlyTimeDependentTravelDisutility(travelTime);
		for(Node fromNode : network.getNodes().values()) {
			int fromInt = Integer.parseInt(fromNode.getId().toString());
			LeastCostPathTree lcpt = new LeastCostPathTree(travelTime, timeAsTravelDisutility);
			lcpt.calculate(network, fromNode, 0.);
			for(Entry<Id<Node>, NodeData> entry : lcpt.getTree().entrySet()) {
				int toInt = Integer.parseInt(entry.getKey().toString());
				distances[fromInt][toInt] = entry.getValue().getTime();
			}
			distances[fromInt][fromInt] = 0;
		}

		long then  = System.currentTimeMillis();
		System.out.println(((then-start)/1000.) + " seconds.");

		return distances;
	}


}
