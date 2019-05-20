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
import java.nio.file.Files;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contrib.eventsBasedPTRouter.TransitRouterEventsWSFactory;
import org.matsim.contrib.eventsBasedPTRouter.stopStopTimes.StopStopTimeCalculator;
import org.matsim.contrib.eventsBasedPTRouter.waitTimes.WaitTimeStuckCalculator;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlansConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.population.routes.LinkNetworkRouteFactory;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.router.RoutingModule;
import org.matsim.core.router.TripRouter;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.trafficmonitoring.FreeSpeedTravelTime;
import org.matsim.facilities.ActivityFacilitiesFactoryImpl;
import org.matsim.facilities.Facility;
import org.matsim.pt.router.PreparedTransitSchedule;
import org.matsim.pt.router.TransitRouter;
import org.matsim.pt.router.TransitRouterConfig;
import org.matsim.pt.router.TransitRouterNetworkTravelTimeAndDisutility;
import org.matsim.pt.router.TransitTravelDisutility;
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
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorFactory;

import com.google.inject.Provider;

/**
 * @author nagel
 *
 */
public class RunMatsim {

	// static String INPUT_FOLDER = "c:/workAtHome/PassengerDelay";
	static String INPUT_FOLDER = "/work1/s103232/PassengerDelay";
	static String date = "2014_09_01";
	static ActivityFacilitiesFactoryImpl facFac = new ActivityFacilitiesFactoryImpl();
	static NetworkRoute networkRoute;

	@SuppressWarnings("static-access")
	public static void main(String[] args) throws IOException {

		CreateBaseTransitSchedule.main(new String[]{});

		//Config config = ConfigUtils.loadConfig("/zhome/81/e/64390/git/matsim-example-project/input/1percent/config_eventPTRouter.xml");
		Config config = ConfigUtils.createConfig();


		// Things only read once

		if(args != null && args.length > 0){
			INPUT_FOLDER = args[0];
			date = args[1];
		}


		config.network().setInputFile(INPUT_FOLDER + "/OtherInput/network.xml.gz");
		//config.plans().setInputFile(INPUT_FOLDER + "/OtherInput/population.xml.gz");
		config.transit().setTransitScheduleFile(INPUT_FOLDER + "/BaseSchedules/BaseSchedule.xml.gz");
		//config.transit().setTransitScheduleFile("/zhome/81/e/64390/git/matsim-example-project/input/full/schedule_CPH.xml.gz");



		//Implement the events based PT router instead - it uses less transfer links.

		config.transitRouter().setMaxBeelineWalkConnectionDistance(467.7);
		config.transitRouter().setSearchRadius(3000.);
		config.transitRouter().setAdditionalTransferTime(120.);
		config.transitRouter().setExtensionRadius(5000.);
		config.transitRouter().setDirectWalkFactor(1.);

		config.travelTimeCalculator().setTraveltimeBinSize(15*3600);
		config.qsim().setStartTime(0);
		config.qsim().setEndTime(27*3600);



		Scenario scenario = ScenarioUtils.loadScenario(config) ;

		Coord fromCoord = new Coord(676568.434869,6147084.136750);	
		Coord toCoord = new Coord(706724.995278,6203018.787283);
		Facility<?> fromFacility = facFac.createActivityFacility(null, fromCoord);
		Facility<?> toFacility = facFac.createActivityFacility(null, toCoord);

		/*
		Person person = scenario.getPopulation().getFactory().createPerson(Id.create("Person",Person.class));
		Plan plan = scenario.getPopulation().getFactory().createPlan();
		plan.addActivity(scenario.getPopulation().getFactory().createActivityFromCoord("h", fromCoord));
		plan.addLeg(scenario.getPopulation().getFactory().createLeg("pt"));
		plan.addActivity(scenario.getPopulation().getFactory().createActivityFromCoord("w", toCoord));
		person.addPlan(plan);
		scenario.getPopulation().addPerson(person);*/

		PopulationReader populationReader = new PopulationReader(scenario);
		populationReader.readFile("/work1/s103232/PassengerDelay/OtherInput/PTPlans_CPH.xml.gz");


		RaptorParametersForPerson arg3 = new DefaultRaptorParametersForPerson(config);
		RaptorRouteSelector arg4 = new LeastCostRaptorRouteSelector();
		PlansConfigGroup arg5 = config.plans();
		Map<String, javax.inject.Provider<RoutingModule>> arg7 = new HashMap<String, javax.inject.Provider<RoutingModule>>();

		SwissRailRaptorFactory fac = new SwissRailRaptorFactory(scenario.getTransitSchedule(), config, scenario.getNetwork(), arg3, arg4, 
				null, config.plans(), scenario.getPopulation(), arg7);
		SwissRailRaptor raptor = fac.get();


		PassengerDelayPerson[] passengerDelayPersons = new PassengerDelayPerson[scenario.getPopulation().getPersons().size()];
		int i = 0;
		for(Person person : scenario.getPopulation().getPersons().values()){
			passengerDelayPersons[i] = new PassengerDelayPerson(person.getId(), person.getPlans().get(0), raptor);
			i++;
		}





		networkRoute = (NetworkRoute) new LinkNetworkRouteFactory().createRoute(
				Id.create("DummyLink",Link.class), Id.create("DummyLink",Link.class));



		Map<Id<TransitLine>, TransitLine> lines = scenario.getTransitSchedule().getTransitLines();
		TransitRouterConfig transitRouterConfig = new TransitRouterConfig(config);

		/// R script still needs to run for part 9...




		for(int stopwatch = 3*3600; stopwatch < 27*3600; stopwatch += 300){
			System.out.println(stopwatch);


			LinkedList<TransitLine> linesToRemove = new LinkedList<TransitLine>();
			for(TransitLine transitLine : scenario.getTransitSchedule().getTransitLines().values()){
				linesToRemove.add(transitLine);
			}
			for (TransitLine transitLine : linesToRemove){
				scenario.getTransitSchedule().removeTransitLine(transitLine);
			}	

			//Have to modify these two - they do not work without minor(/major) changes. Most likely due to changes in column names.
			scenario = CreateBaseTransitSchedule.addTrainSchedule(scenario, 
					INPUT_FOLDER + "/Disaggregate/Train/DisaggregateSchedule_" + date + "_" + stopwatch + ".csv");
			scenario = CreateBaseTransitSchedule.addBusSchedule(scenario,
					INPUT_FOLDER + "/Disaggregate/Bus/DisaggregateBusSchedule_" + date + "_" + stopwatch + ".csv");





			/*
			{
				BufferedReader br = new BufferedReader(new FileReader());

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
			}
			 */

			// Standard MATSim
			/*
			TransitRouterImplFactory transitRouterFac = new TransitRouterImplFactory(scenario.getTransitSchedule(),
					transitRouterConfig);
			TransitRouter transitRouter = transitRouterFac.get();
			double depTime = stopwatch;
			Coord fromCoord = new Coord(719991.463908,6174840.523082);	
			Coord toCoord = new Coord(723728.644952,6180425.027057);
			Facility<?> fromFacility = facFac.createActivityFacility(null, fromCoord);
			Facility<?> toFacility = facFac.createActivityFacility(null, toCoord);
			List<Leg> solList = transitRouter.calcRoute(fromFacility, toFacility, depTime, null);
			 */



			// Events Based PT Router
			/*
				TransitRouterNetworkWW routingNetworkTemp = new TransitRouterNetworkWW();
				routingNetworkTemp.createFromSchedule(scenario.getNetwork(), scenario.getTransitSchedule(),
						467.7);

			PreparedTransitSchedule preparedTransitSchedule = new PreparedTransitSchedule(scenario.getTransitSchedule());
			TravelTime travelTime = new FreeSpeedTravelTime();
			TransitTravelDisutility travelDisutility = new TransitRouterNetworkTravelTimeAndDisutility(
					transitRouterConfig, preparedTransitSchedule);			

			Controler controler = new Controler( scenario ) ;
			controler.getConfig().controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.overwriteExistingFiles);
			WaitTimeStuckCalculator waitTimeCalculator = new WaitTimeStuckCalculator(controler.getScenario().getPopulation(), controler.getScenario().getTransitSchedule(), controler.getConfig().travelTimeCalculator().getTraveltimeBinSize(), (int) (controler.getConfig().qsim().getEndTime()-controler.getConfig().qsim().getStartTime()));
			controler.getEvents().addHandler(waitTimeCalculator);
			StopStopTimeCalculator stopStopTimeCalculator = new StopStopTimeCalculator(controler.getScenario().getTransitSchedule(), controler.getConfig().travelTimeCalculator().getTraveltimeBinSize(), (int) (controler.getConfig().qsim().getEndTime()-controler.getConfig().qsim().getStartTime()));
			controler.getEvents().addHandler(stopStopTimeCalculator);
			controler.addOverridingModule(new AbstractModule() {
				@Override
				public void install() {
					bind(TransitRouter.class).toProvider(new TransitRouterEventsWSFactory(scenario,
							waitTimeCalculator.get(), stopStopTimeCalculator.get()));
				}
			});
			TransitRouterEventsWSFactory transitRouterEventsFactory = new TransitRouterEventsWSFactory(scenario,
					waitTimeCalculator.get(), stopStopTimeCalculator.get());
			TransitRouter tripRouter = transitRouterEventsFactory.get();
			controler.getConfig().controler().setLastIteration(0);
			//	controler.run();

			//		Provider<TransitRouter> routerProvider = controler.getInjector().getProvider(TransitRouter.class);
			//TransitRouter tripRouter = routerProvider.get();




			//		com.google.inject.Injector injector = controler.getInjector();
			//	 com.google.inject.Provider<TripRouter> tripRouterProvider = injector.getProvider(TripRouter.class);
			//	 TripRouter tripRouter = tripRouterProvider.get();


			//	TransitRouterEventsWSFactory routerFactory = new TransitRouterEventsWSFactory(scenario, waitTime,stopStopTime);
			//	TransitRouter router = routerFactory.get();


			double depTime = stopwatch;


			List<? extends PlanElement> solList = null;
			long before = System.currentTimeMillis();
			for(int i = 0; i < 1000; i++){
				if(i % 1000 == 0){
					System.out.println(i);
				}
				solList = tripRouter.calcRoute(fromFacility, toFacility, depTime, null);

			}
			long after = System.currentTimeMillis();
			System.out.println("Time was " + (after-before)/1000. + " seconds.");
			System.out.println((after-before)/1000. + " miliseconds per shortest path");

			double tt = 0;
			for(Leg leg : TripStructureUtils.getLegs(solList)){
				tt += leg.getTravelTime();
			}
			System.out.println("Travel time is " + tt);
			 */



			// RAPTOR

			fac = new SwissRailRaptorFactory(scenario.getTransitSchedule(), config, scenario.getNetwork(), arg3, arg4, 
					null, config.plans(), scenario.getPopulation(), arg7);
			raptor = fac.get();
 
			for(PassengerDelayPerson person : passengerDelayPersons){
				person.advance(stopwatch, raptor);
			}





		}



	}
}
