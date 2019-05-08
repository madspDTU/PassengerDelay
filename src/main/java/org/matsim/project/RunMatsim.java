/* *********************************************************************** *
 * project: org.matsim.*												   *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2008 by the members listed in the COPYING,        *
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
package org.matsim.project;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.inject.Provider;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlansConfigGroup;
import org.matsim.core.population.routes.LinkNetworkRouteFactory;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.population.routes.RouteFactories;
import org.matsim.core.router.MultiNodeDijkstraFactory;
import org.matsim.core.router.RoutingModule;
import org.matsim.core.router.costcalculators.FreespeedTravelTimeAndDisutility;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.trafficmonitoring.FreeSpeedTravelTime;
import org.matsim.facilities.ActivityFacilitiesFactoryImpl;
import org.matsim.facilities.Facility;
import org.matsim.pt.router.TransitRouter;
import org.matsim.pt.router.TransitRouterConfig;
import org.matsim.pt.router.TransitRouterImplFactory;
import org.matsim.pt.transitSchedule.TransitLineImpl;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import ch.sbb.matsim.routing.pt.raptor.DefaultRaptorParametersForPerson;
import ch.sbb.matsim.routing.pt.raptor.LeastCostRaptorRouteSelector;
import ch.sbb.matsim.routing.pt.raptor.RaptorParametersForPerson;
import ch.sbb.matsim.routing.pt.raptor.RaptorRouteSelector;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptor;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorData;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorFactory;

/**
 * @author nagel
 *
 */
public class RunMatsim{

	//static String INPUT_FOLDER = "c:/workAtHome/PassengerDelay";
	static String INPUT_FOLDER = "/work1/s103232/PassengerDelay";
	static String date = "2014_09_01";
	static ActivityFacilitiesFactoryImpl facFac = new ActivityFacilitiesFactoryImpl();
	static NetworkRoute networkRoute;



	public static void main(String[] args) throws IOException {

		CreateBaseTransitSchedule.main(new String[]{});

		Config config = ConfigUtils.createConfig( ) ;

		// Things only read once

		if(args != null && args.length > 0){
			INPUT_FOLDER = args[0];
			String date = args[1];
		}


		config.network().setInputFile(INPUT_FOLDER + "/OtherInput/network.xml.gz");
		//config.plans().setInputFile(INPUT_FOLDER + "/OtherInput/population.xml.gz");
		config.transit().setTransitScheduleFile(INPUT_FOLDER + "/BaseSchedules/BaseSchedule.xml.gz");

		Scenario scenario = ScenarioUtils.loadScenario(config) ;

		networkRoute = (NetworkRoute) new LinkNetworkRouteFactory().createRoute(
				Id.create("DummyLink",Link.class), Id.create("DummyLink",Link.class));



		Map<Id<TransitLine>, TransitLine> lines = scenario.getTransitSchedule().getTransitLines();
		TransitRouterConfig transitRouterConfig = new TransitRouterConfig(config);


		for(int stopwatch = 3*3600; stopwatch < 27*3600; stopwatch += 300){
			System.out.println(stopwatch);
			BufferedReader br = new BufferedReader(new FileReader(INPUT_FOLDER + "/Disaggregate/DisaggregateSchedule_" + date + 
					"_" + stopwatch + ".csv"));

			String readLine = br.readLine(); //reads the headers;
			Id<TransitRoute> prevRouteId = Id.create("-1",TransitRoute.class);
			Id<TransitRoute> routeId = null;

			String prevStopId = "-1";
			List<TransitRouteStop> stops = new LinkedList<TransitRouteStop>();
			TransitLineImpl line = null;
			double offset = -1;
			double arrival = -1;
			double departure = -1;
		
			while((readLine = br.readLine()) != null){
				String[] splitLine = readLine.split(";");
				Id<TransitLine> lineId;

				routeId = Id.create(splitLine[2], TransitRoute.class);
				if(!routeId.equals(prevRouteId)){
					if(!stops.isEmpty()){
						TransitRoute route = scenario.getTransitSchedule().getFactory().createTransitRoute(prevRouteId,networkRoute,stops, "train");
						route.addDeparture(scenario.getTransitSchedule().getFactory().createDeparture(
								Id.create(String.valueOf(offset), Departure.class), offset));
						line.addRoute(route);
						stops.clear();
					}
					lineId = Id.create(splitLine[0], TransitLine.class);
					offset = Double.valueOf(splitLine[1]);
					line = (TransitLineImpl) lines.get(lineId);
					if(!line.getRoutes().containsKey(routeId)){
						System.err.println(lineId);
						System.err.println(routeId);
						for(TransitRoute route : line.getRoutes().values()){
							System.out.println("  " + route.getId());
						}
					}
					line.removeRoute(line.getRoutes().get(routeId));
				}				
				String stopId = splitLine[3];
				TransitStopFacility stopFacility = scenario.getTransitSchedule().getFacilities().get(Id.create(stopId, TransitStopFacility.class));
				if(stopFacility != null){
					double time = Double.valueOf(splitLine[1]);
					String moveType = splitLine[4];
					if(moveType.equals("I")){
						arrival = time;
						departure = arrival;
					} else {
						departure = time;
						if(arrival == -1){
							arrival = departure;
						}
						if(arrival < offset){
							arrival += 24*3600;
						}
						if(departure < offset){
							departure += 24*3600;
						}
						
						TransitRouteStop stop = scenario.getTransitSchedule().getFactory().createTransitRouteStop(
								stopFacility,arrival - offset, departure - offset);
						stops.add(stop);
						arrival = -1;
					}
				}
				prevRouteId = routeId;
			}
			if(!stops.isEmpty()){
				TransitRoute route = scenario.getTransitSchedule().getFactory().createTransitRoute(
						routeId,networkRoute,stops, "train");
				route.addDeparture(scenario.getTransitSchedule().getFactory().createDeparture(
						Id.create(String.valueOf(offset), Departure.class), offset));
				line.addRoute(route);
			}

			/*
			TransitRouterImplFactory transitRouterFac = new TransitRouterImplFactory(scenario.getTransitSchedule(),
					transitRouterConfig);
			TransitRouter transitRouter = transitRouterFac.get();
			double depTime = stopwatch;
			Coord fromCoord = new Coord(719991.463908,6174840.523082);	
			Coord toCoord = new Coord(723728.644952,6180425.027057);
			Facility<?> fromFacility = facFac.createActivityFacility(null, fromCoord);
			Facility<?> toFacility = facFac.createActivityFacility(null, toCoord);
			transitRouter.calcRoute(fromFacility, toFacility, depTime, null);
			 */



			RaptorParametersForPerson arg3 = new DefaultRaptorParametersForPerson(config);
			RaptorRouteSelector arg4 = new LeastCostRaptorRouteSelector();
			PlansConfigGroup arg5 = config.plans();
			Map<String, Provider<RoutingModule>> arg7 = new HashMap<String, Provider<RoutingModule>>();
			SwissRailRaptorFactory fac = new SwissRailRaptorFactory(scenario.getTransitSchedule(), config, scenario.getNetwork(), arg3, arg4, 
					null, config.plans(), scenario.getPopulation(), arg7);
			SwissRailRaptor raptor = fac.get();
			LeastCostRaptorRouteSelector router = new LeastCostRaptorRouteSelector();


			double depTime = stopwatch;
			Coord fromCoord = new Coord(676568.434869,6147084.136750);	
			Coord toCoord = new Coord(706724.995278,6203018.787283);
			Facility<?> fromFacility = facFac.createActivityFacility(null, fromCoord);
			Facility<?> toFacility = facFac.createActivityFacility(null, toCoord);
		
		//	long before = System.currentTimeMillis();
		//	for(int i = 0; i < 100000; i++){
		//		if(i % 1000 == 0){
		//			System.out.println(i);
		//		}
			List<Leg> solList = raptor.calcRoute(fromFacility, toFacility, depTime, null);
			double travelTime = 0;
			for(Leg leg : solList){
				travelTime += leg.getTravelTime();
			}
			System.out.println("Travel time is " + travelTime);
			
		//	}
		//	long after = System.currentTimeMillis();
		//	System.out.println("Time was " + (after-before)/1000. + " seconds.");
		//	System.out.println((after-before)/100000. + " miliseconds per shortest path");

		}



	}
}
