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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.population.routes.LinkNetworkRouteFactory;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.router.RoutingModule;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.facilities.ActivityFacilitiesFactoryImpl;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import ch.sbb.matsim.routing.pt.raptor.DefaultRaptorIntermodalAccessEgress;
import ch.sbb.matsim.routing.pt.raptor.DefaultRaptorParametersForPerson;
import ch.sbb.matsim.routing.pt.raptor.DefaultRaptorStopFinder;
import ch.sbb.matsim.routing.pt.raptor.LeastCostRaptorRouteSelector;
import ch.sbb.matsim.routing.pt.raptor.RaptorIntermodalAccessEgress;
import ch.sbb.matsim.routing.pt.raptor.RaptorParametersForPerson;
import ch.sbb.matsim.routing.pt.raptor.RaptorRouteSelector;
import ch.sbb.matsim.routing.pt.raptor.RaptorStaticConfig;
import ch.sbb.matsim.routing.pt.raptor.RaptorStaticConfig.RaptorOptimization;
import ch.sbb.matsim.routing.pt.raptor.RaptorStopFinder;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptor;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorData;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorModule;

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
	static ConcurrentHashMap<Id<TransitStopFacility>, TransitStopFacility> facilities = 
			new ConcurrentHashMap<Id<TransitStopFacility>, TransitStopFacility>();
	private static int cores = 2;
	static ConcurrentHashMap<Id<Departure>, Id<TransitRoute>> dep2Route;
	static ConcurrentHashMap<Id<Departure>, Id<TransitLine>> dep2Line;
	
	static ConcurrentHashMap<Id<TransitRoute>, ConcurrentSkipListMap<Double, TransitStopFacility>> route2OrderedStopOffset;
	static ConcurrentHashMap<Id<TransitRoute>, ConcurrentHashMap<Id<TransitStopFacility>, Double>> route2StopDeparture;
	static ConcurrentHashMap<Id<TransitRoute>, ConcurrentHashMap<Id<Departure>, Double>> route2DepartureTime;
	static ConcurrentHashMap<Id<TransitRoute>, ConcurrentHashMap<Id<TransitStopFacility>,Double>> route2StopArrival;
	static ConcurrentHashMap<Id<TransitRoute>, ConcurrentHashMap<Double,Id<Departure>>> route2Departure;

	static int startTime = 3*3600;
	static int endTime = 27*3600;



	public static boolean elaborateLogging = false;
	public static boolean aggregateLogging = true;
	public static boolean runParallelThreads = true;

	public static SwissRailRaptor raptor;

	static ConcurrentHashMap<Id<TransitRoute>, LinkedList<TransitRouteStop>> route2Stops;





	public static void main(String[] args){

		CreateBaseTransitSchedule.main(new String[]{});

		//Config config = ConfigUtils.loadConfig("/zhome/81/e/64390/git/matsim-example-project/input/1percent/config_eventPTRouter.xml");
		Config config = ConfigUtils.createConfig();


		// Things only read once

		if(args != null && args.length > 0){
			INPUT_FOLDER = args[0];
			date = args[1];
			cores = Integer.valueOf(args[2]);
		}
	

		config.network().setInputFile(INPUT_FOLDER + "/OtherInput/network.xml.gz");
		config.transit().setTransitScheduleFile(INPUT_FOLDER + "/BaseSchedules/BaseSchedule_InfrastructureOnly.xml.gz");
		if(!runParallelThreads){
			config.transit().setTransitScheduleFile("/zhome/81/e/64390/git/matsim-example-project/input/full/schedule_CPH.xml.gz");
		}

		//Implement the events based PT router instead - it uses less transfer links.

		config.transitRouter().setMaxBeelineWalkConnectionDistance(500.);
		config.transitRouter().setSearchRadius(3000.);
		config.transitRouter().setAdditionalTransferTime(0.);
		config.transitRouter().setExtensionRadius(5000.);
		config.transitRouter().setDirectWalkFactor(1.);
		config.plansCalcRoute().getOrCreateModeRoutingParams(TransportMode.walk).setBeelineDistanceFactor(
				1.);
		config.plansCalcRoute().getOrCreateModeRoutingParams(TransportMode.walk).setTeleportedModeSpeed(
				1.);




		config.travelTimeCalculator().setTraveltimeBinSize(15*3600);
		config.qsim().setStartTime(0);
		config.qsim().setEndTime(27*3600);



		Scenario scenario = ScenarioUtils.loadScenario(config) ;


		Controler controler = new Controler(scenario);

		// To use the fast pt router (Part 1 of 1)
		controler.addOverridingModule(new SwissRailRaptorModule());



		Population fakePopulation = ScenarioUtils.createScenario(ConfigUtils.createConfig()).getPopulation();

		RaptorParametersForPerson arg3 = new DefaultRaptorParametersForPerson(config);
		RaptorRouteSelector arg4 = new LeastCostRaptorRouteSelector();

		RaptorIntermodalAccessEgress iae = new DefaultRaptorIntermodalAccessEgress();
		Map<String, RoutingModule> routingModuleMap = new HashMap<String, RoutingModule>();

		RaptorStopFinder stopFinder = new DefaultRaptorStopFinder(fakePopulation, iae, routingModuleMap);

		RaptorStaticConfig staticConfig = new RaptorStaticConfig();
		staticConfig.setBeelineWalkConnectionDistance(
				config.transitRouter().getMaxBeelineWalkConnectionDistance());
		staticConfig.setBeelineWalkDistanceFactor(
				config.transitRouter().getDirectWalkFactor());
		staticConfig.setBeelineWalkSpeed(
				config.plansCalcRoute().getOrCreateModeRoutingParams(
						TransportMode.walk).getTeleportedModeSpeed());
		staticConfig.setMinimalTransferTime(
				config.transitRouter().getAdditionalTransferTime());
		staticConfig.setOptimization(RaptorOptimization.OneToOneRouting);
		staticConfig.setUseModeMappingForPassengers(false);



		//	SwissRailRaptorFactory fac = new SwissRailRaptorFactory(scenario.getTransitSchedule(), config, scenario.getNetwork(), arg3, arg4, 
		//			null, config.plans(), fakePopulation, arg7);
		//	SwissRailRaptor raptor = fac.get();


		PopulationReader populationReader = new PopulationReader(scenario);
		populationReader.readFile("/work1/s103232/PassengerDelay/OtherInput/PTPlans_CPH.xml.gz");


		PassengerDelayPerson[] passengerDelayPersons = new PassengerDelayPerson[scenario.getPopulation().getPersons().size()];
		int i = 0;
		for(Person person : scenario.getPopulation().getPersons().values()){
			passengerDelayPersons[i] = new PassengerDelayPerson(person.getId(), person.getPlans().get(0));
			i++;
		}

		LinkedList<PassengerDelayPerson>[] persons = new LinkedList[cores];

		for(i = 0; i < passengerDelayPersons.length; i++){
			int r = i % cores;
			if( persons[r] == null){
				persons[r] = new LinkedList<PassengerDelayPerson>();
			}
			persons[r].addLast(passengerDelayPersons[i]);
		}

		Scenario[] scenarios = new Scenario[cores];
		for(i = 0; i < cores; i++){
			scenarios[i] = ScenarioUtils.loadScenario(createConfig());
		}


		for(TransitStopFacility stopFacility : scenario.getTransitSchedule().getFacilities().values()){
			facilities.put(stopFacility.getId(), stopFacility);
		}




		networkRoute = (NetworkRoute) new LinkNetworkRouteFactory().createRoute(
				Id.create("DummyLink",Link.class), Id.create("DummyLink",Link.class));


		System.out.println("Using " + cores + " cores");	

		for(int stopwatch = startTime; stopwatch < endTime; stopwatch += 300){

			long backThen = System.currentTimeMillis();
			scenario = CreateBaseTransitSchedule.clearTransitSchedule(scenario);

			//Have to modify these two - they do not work without minor(/major) changes. Most likely due to changes in column names.
			scenario = CreateBaseTransitSchedule.addTrainSchedule(scenario, 
					INPUT_FOLDER + "/Disaggregate/Train/" + date + "/DisaggregateSchedule_" + date + "_" + stopwatch + ".csv");
			scenario = CreateBaseTransitSchedule.addBusSchedule(scenario,
					INPUT_FOLDER + "/Disaggregate/Bus/" + date + "/DisaggregateBusSchedule_" + date + "_" + stopwatch + ".csv");

			createDepMaps(scenario.getTransitSchedule().getTransitLines().values());

			System.out.println("Stopwatch is now: " + intToTimeString(stopwatch));

			// RAPTOR
			//		ThreadPoolExecutor executor = null;
			//		executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(cores);
			//		for(LinkedList<PassengerDelayPerson> pList: persons){
			//			executor.execute(new AdvanceJob(stopwatch, pList));		
			//		}
			//i = 0;
			//for(LinkedList<PassengerDelayPerson> pList: persons){
			//	executor.execute(advanceJobs[i]);		
			//	}
			//	executor.shutdown();

			if(runParallelThreads){
				AdvanceJob[] advanceJobs = new AdvanceJob[cores];
				for(i = 0; i < cores; i++){
					advanceJobs[i] = new AdvanceJob(stopwatch, persons[i], scenarios[i]);		
				}

				i=0;
				Thread[] threads = new Thread[cores];
				for(int j = 0; j < cores; j++){
					threads[j] = new Thread(advanceJobs[j]);
				}
				for(int j = 0; j < cores; j++){
					threads[j].start();
				}


				for(int j = 0; j < cores; j++){
					try {
						threads[j].join();
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}

			}




			SwissRailRaptorData data;
			if(!runParallelThreads){
				data = SwissRailRaptorData.create(scenario.getTransitSchedule(), staticConfig , scenario.getNetwork());
				raptor = new SwissRailRaptor(data, arg3, arg4, stopFinder);
			}
			long backNow = System.currentTimeMillis();



			// Processing the population
			i = 0;
			ThreadPoolExecutor executor = null;
			if(runParallelThreads){
				executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(cores);
			}
			long then = System.currentTimeMillis(); 


			if(!runParallelThreads){
				for(PassengerDelayPerson person : passengerDelayPersons){
					person.setStopwatch(stopwatch);
					//if(person.id.toString().equals("393188_2_Person")){
					person.advance();
					//}
				}
			}

			long now = System.currentTimeMillis(); 
			if(!runParallelThreads){
				System.out.println("real/sim (Total): " + 300./(now-backThen)*1000.  );
			}


			if(aggregateLogging){
				//		System.out.println("real/sim (Graph): " + 300./(backNow-backThen)*1000.  );
				//		System.out.println("real/sim (Simulation): " + 300./(now-then)*1000.  );
				System.out.println("Status:");
				int activity = 0, station = 0, vehicle = 0, walk = 0;	
				for(PassengerDelayPerson person : passengerDelayPersons){
					switch(person.getStatus()){
					case ACTIVITY: 				activity++;			break;
					case STATION:				station++;			break;
					case VEHICLE:				vehicle++;			break;
					case WALK:					walk++;			break;
					default:	System.err.println("Something's terribly wrong with the statuses");		break;
					}
				}
				System.out.println("  - Activity: " + activity);
				System.out.println("  - Station: " + station);
				System.out.println("  - Vehicle: " + vehicle);
				System.out.println("  - Walk: " + walk);
			}
		}

		try {
			FileWriter writer = new FileWriter(new File("/work1/s103232/PassengerDelay/Output/Events_" +
		date + ".csv"));
			writer.append("AgentId;TripId;Type;Time;FromX;FromY;FromString;ToX;ToY;ToString;How\n");
			for(PassengerDelayPerson person :passengerDelayPersons){
				int tripId = 0;
				for(PassengerDelayEvent event : person.events){
					writer.append(person.id + ";" + tripId + ";" + 
							event.type + ";" + event.time + ";" + event.fromCoord.getX() + ";"
							+ event.fromCoord.getY() + ";" + event.fromString + ";" +
							+ event.toCoord.getX() + ";" + event.toCoord.getY() + ";"+
							event.toString + ";" + event.how + "\n");
					if(event.type.equals(PassengerDelayEvent.EventType.ACTIVITY_START)){
						tripId++;
					}
				}
			}
			writer.flush();
			writer.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}



	}



	private static String intToTimeString(int n) {
		int h = n / 3600;
		n -= h*3600;
		int m = n / 60;
		n -= m * 60;
		return ((h >= 10) ? h : ("0" + h)) + ":" +  
		((m >= 10) ? m : ("0" + m)) + ":" + 
		((n >= 10) ? n : ("0" + n));
	}



	private static void createDepMaps(Collection<TransitLine> transitLines) {
		dep2Route = new ConcurrentHashMap<Id<Departure>,Id<TransitRoute>>();
		dep2Line = new ConcurrentHashMap<Id<Departure>,Id<TransitLine>>();
		
		route2OrderedStopOffset = 
				new ConcurrentHashMap<Id<TransitRoute>, ConcurrentSkipListMap<Double,TransitStopFacility>>();
		route2StopArrival = 
				new ConcurrentHashMap<Id<TransitRoute>, ConcurrentHashMap<Id<TransitStopFacility>,Double>>();

		route2DepartureTime = new ConcurrentHashMap<Id<TransitRoute>, ConcurrentHashMap<Id<Departure>,Double>>();
		route2StopDeparture = new ConcurrentHashMap<Id<TransitRoute>, ConcurrentHashMap<Id<TransitStopFacility>,Double>>();
		route2StopArrival = new ConcurrentHashMap<Id<TransitRoute>, ConcurrentHashMap<Id<TransitStopFacility>, Double>>();
		route2Departure = new ConcurrentHashMap<Id<TransitRoute>, ConcurrentHashMap<Double, Id<Departure>>>();
		route2Stops = new ConcurrentHashMap<Id<TransitRoute>, LinkedList<TransitRouteStop>>();


		for(TransitLine line : transitLines){
			for(TransitRoute route : line.getRoutes().values()){
				ConcurrentSkipListMap<Double,TransitStopFacility> skipListMap =
						new ConcurrentSkipListMap<Double,TransitStopFacility>();
				ConcurrentHashMap<Id<TransitStopFacility>,Double> hashMap = 
						new ConcurrentHashMap<Id<TransitStopFacility>,Double>();
				ConcurrentHashMap<Id<TransitStopFacility>,Double> hashMap2 = 
						new ConcurrentHashMap<Id<TransitStopFacility>,Double>();
				LinkedList<TransitRouteStop> linkedList = new LinkedList<TransitRouteStop>();

				for(TransitRouteStop stop : route.getStops()){
					skipListMap.putIfAbsent(stop.getDepartureOffset(), stop.getStopFacility());
					hashMap.put(stop.getStopFacility().getId(), stop.getDepartureOffset());
					hashMap2.put(stop.getStopFacility().getId(), stop.getArrivalOffset());
					linkedList.addLast(stop);
				}
				route2OrderedStopOffset.put(route.getId(), skipListMap);
				route2StopDeparture.put(route.getId(), hashMap);
				route2StopArrival.put(route.getId(), hashMap2);
				route2Stops.put(route.getId(), linkedList);


				ConcurrentHashMap<Id<Departure>,Double> hashMap3 = 
						new ConcurrentHashMap<Id<Departure>,Double>();
				ConcurrentHashMap<Double, Id<Departure>> hashMap4 = 
						new ConcurrentHashMap<Double, Id<Departure>>();

				for(Departure departure : route.getDepartures().values()){
					dep2Route.put(departure.getId(), route.getId());
					dep2Line.put(departure.getId(), line.getId());
					hashMap3.put(departure.getId(),departure.getDepartureTime());
					hashMap4.put(departure.getDepartureTime(), departure.getId());
				}
				route2DepartureTime.put(route.getId(), hashMap3);
				route2Departure.put(route.getId(), hashMap4);
			}
		}
	}




	private static Config createConfig(){
		Config config = ConfigUtils.createConfig();
		config.network().setInputFile(RunMatsim.INPUT_FOLDER + "/OtherInput/network.xml.gz");
		//config.plans().setInputFile(INPUT_FOLDER + "/OtherInput/population.xml.gz");
		config.transit().setTransitScheduleFile(
				RunMatsim.INPUT_FOLDER + "/BaseSchedules/BaseSchedule_InfrastructureOnly.xml.gz");
		//config.transit().setTransitScheduleFile("/zhome/81/e/64390/git/matsim-example-project/input/full/schedule_CPH.xml.gz");


		//Implement the events based PT router instead - it uses less transfer links.

		config.transitRouter().setMaxBeelineWalkConnectionDistance(500.);
		config.transitRouter().setSearchRadius(3000.);
		config.transitRouter().setAdditionalTransferTime(0.);
		config.transitRouter().setExtensionRadius(5000.);
		config.transitRouter().setDirectWalkFactor(1.);
		config.plansCalcRoute().getOrCreateModeRoutingParams(TransportMode.walk).setBeelineDistanceFactor(
				1.);
		config.plansCalcRoute().getOrCreateModeRoutingParams(TransportMode.walk).setTeleportedModeSpeed(
				1.);

		return config;
	}



}



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
