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
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ModeParams;
import org.matsim.core.controler.Controler;
import org.matsim.core.gbl.Gbl;
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
	//static String date = "2014_09_04";
	static String date = "base";
	
	static ActivityFacilitiesFactoryImpl facFac = new ActivityFacilitiesFactoryImpl();
	static NetworkRoute networkRoute;
	static ConcurrentHashMap<Id<TransitStopFacility>, TransitStopFacility> facilities = 
			new ConcurrentHashMap<Id<TransitStopFacility>, TransitStopFacility>();
	private static int cores = 1;
	static ConcurrentHashMap<Integer, ConcurrentHashMap<Id<Departure>, Id<TransitRoute>>> dep2Route;


	static ConcurrentHashMap<Id<TransitRoute>, ConcurrentSkipListMap<Double, TransitStopFacility>> latestRoute2OrderedStopOffset;
	// Only used as an interim map for the below one;
	static ConcurrentHashMap<Id<TransitRoute>, ConcurrentSkipListMap<Double, TransitStopFacility>> currentRoute2OrderedStopOffset;

	static ConcurrentHashMap<Integer, ConcurrentHashMap<Id<TransitRoute>, ConcurrentHashMap<Id<TransitStopFacility>, Double>>> route2StopDeparture;

	static ConcurrentHashMap<Integer, ConcurrentHashMap<Id<TransitRoute>, ConcurrentHashMap<Id<Departure>, Double>>> route2DepartureTime;
	static ConcurrentHashMap<Integer,ConcurrentHashMap<Id<TransitRoute>, 
	ConcurrentHashMap<Id<TransitStopFacility>,Double>>> route2StopArrival;
	static ConcurrentHashMap<Integer,ConcurrentHashMap<Id<TransitRoute>, ConcurrentHashMap<Double,Id<Departure>>>> route2Departure;

	static  int startTime = 3*3600;
	static  int endTime = 27*3600;


	private static final double transferBufferTime = 0.;
	public static final boolean elaborateLogging = false;
	public static final boolean aggregateLogging = true;
	public static final boolean runParallelThreads = true;

	public static SwissRailRaptor raptor;

	//Needs to be implemented.
	private static HashSet<String> ptSubModes = new HashSet<String>(Arrays.asList("train","bus","metro","train"));

	private static double waitTimeUtility = -1.3;
	private static double trainTimeUtility = -1.;
	private static double trainDistanceUtility = 0.;
	// Local trains even?
	// Local trains even?
	private static double busTimeUtility = -1.;
	private static double busDistanceUtility = 0.;
	private static double metroTimeUtility = -1.;
	private static double metroDistanceUtility = 0.;
	private static double walkTimeUtility = -1.6;
	private static double walkDistanceUtility = 0.;
	private static final double maxBeelineTransferWalk = 600.; // Furthest walk between to _transfer_ stations [m]




	//Never used, except for transfering onto the current map in next timestep.

	final static int TIMESTEP = 150;
	private final static int DEPMAP_MEMORY = 3600*2;







	public static void main(String[] args){

		CreateBaseTransitSchedule.init();
		//Config config = ConfigUtils.loadConfig("/zhome/81/e/64390/git/matsim-example-project/input/1percent/config_eventPTRouter.xml");
		Config config = ConfigUtils.createConfig();
		Config nextConfig = ConfigUtils.createConfig();



		// Things only read once

		if(args != null && args.length > 0){
			INPUT_FOLDER = args[0];
			date = args[1];
			if(args.length > 2){
				cores = Integer.valueOf(args[2]);
				if(args.length > 3){
					endTime = Integer.valueOf(args[3])*3600;
				}
			}
		}


		config.network().setInputFile(INPUT_FOLDER + "/OtherInput/network.xml.gz");
		config.transit().setTransitScheduleFile(INPUT_FOLDER + "/BaseSchedules/BaseSchedule_InfrastructureOnly.xml.gz");
		if(!runParallelThreads){
			config.transit().setTransitScheduleFile("/zhome/81/e/64390/git/matsim-example-project/input/full/schedule_CPH.xml.gz");
		}
		nextConfig.network().setInputFile(INPUT_FOLDER + "/OtherInput/network.xml.gz");
		nextConfig.transit().setTransitScheduleFile(INPUT_FOLDER + "/BaseSchedules/BaseSchedule_InfrastructureOnly.xml.gz");
		if(!runParallelThreads){
			nextConfig.transit().setTransitScheduleFile("/zhome/81/e/64390/git/matsim-example-project/input/full/schedule_CPH.xml.gz");
		}


		// Google Maps of empirisk viden
		config.transitRouter().setMaxBeelineWalkConnectionDistance(maxBeelineTransferWalk );
		config.transitRouter().setSearchRadius(3000.);  // Andersson 2013 (evt 2016)
		config.transitRouter().setAdditionalTransferTime(transferBufferTime);
		config.transitRouter().setExtensionRadius(5000.);
		config.transitRouter().setDirectWalkFactor(1.);
		config.plansCalcRoute().getOrCreateModeRoutingParams(TransportMode.walk).setBeelineDistanceFactor(
				1.);
		config.plansCalcRoute().getOrCreateModeRoutingParams(TransportMode.walk).setTeleportedModeSpeed(
				1.);
		config.transit().setTransitModes(ptSubModes);
		for(String mode : ptSubModes){
			ModeParams params = new ModeParams(mode);
			switch(mode){
			case "train": 
				params.setMarginalUtilityOfTraveling(trainTimeUtility);
				params.setMarginalUtilityOfDistance(trainDistanceUtility);
			case "bus":
				params.setMarginalUtilityOfTraveling(busTimeUtility);
				params.setMarginalUtilityOfDistance(busDistanceUtility);
			case "metro":
				params.setMarginalUtilityOfTraveling(metroTimeUtility);
				params.setMarginalUtilityOfDistance(metroDistanceUtility);
			case TransportMode.transit_walk:
				params.setMarginalUtilityOfTraveling(walkTimeUtility);
				params.setMarginalUtilityOfDistance(walkDistanceUtility);
			case TransportMode.walk:
				params.setMarginalUtilityOfDistance(walkTimeUtility);
				params.setMarginalUtilityOfDistance(walkDistanceUtility);
			default:
				// Blank
			}
			config.planCalcScore().addModeParams(params);
		}
		config.planCalcScore().setMarginalUtlOfWaitingPt_utils_hr(waitTimeUtility);




		config.travelTimeCalculator().setTraveltimeBinSize(TIMESTEP);
		config.qsim().setStartTime(0);
		config.qsim().setEndTime(endTime);



		Scenario scenario = ScenarioUtils.loadScenario(config) ;
		Scenario nextScenario = ScenarioUtils.loadScenario(nextConfig);




		Controler controler = new Controler(scenario);

		// To use the fast pt router (Part 1 of 1)
		controler.addOverridingModule(new SwissRailRaptorModule());




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
		staticConfig.setUseModeMappingForPassengers(true);




		//	SwissRailRaptorFactory fac = new SwissRailRaptorFactory(scenario.getTransitSchedule(), config, scenario.getNetwork(), arg3, arg4, 
		//			null, config.plans(), fakePopulation, arg7);
		//	SwissRailRaptor raptor = fac.get();


		PopulationReader populationReader = new PopulationReader(scenario);
		populationReader.readFile("/work1/s103232/PassengerDelay/OtherInput/PTPlans_CPH.xml.gz");

		if(cores > scenario.getPopulation().getPersons().size()){
			cores = scenario.getPopulation().getPersons().size(); 
		}
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

		initialiseDepMaps();



		System.out.println("Using " + cores + " cores");	
		if(date.equals("base")){
			
			
			CreateBaseTransitSchedule.clearTransitSchedule(scenario);
			nextScenario = CreateBaseTransitSchedule.addStaticSchedule(scenario, 
					RunMatsim.INPUT_FOLDER + "/BaseSchedules/BaseSchedule.xml.gz");
			nextScenario = CreateBaseTransitSchedule.addStaticSchedule(scenario, 
					RunMatsim.INPUT_FOLDER + "/BaseSchedules/MetroSchedule.xml.gz");
			nextScenario = CreateBaseTransitSchedule.addStaticSchedule(scenario, 
					RunMatsim.INPUT_FOLDER + "/BaseSchedules/LocalTrainSchedule.xml.gz");
			createDepMaps(RunMatsim.startTime, nextScenario.getTransitSchedule().getTransitLines().values());
			
			long backThen = System.currentTimeMillis();
			
			System.out.print("- Graph building times: ");

			BaseJob[] baseJobs = new BaseJob[cores];
			for(int j = 0; j < cores; j++){
				baseJobs[j] = new BaseJob(startTime, persons[j], scenarios[j]);		
			}
			Thread[] threads = new Thread[cores];
			for(int j = 0; j < cores; j++){
				threads[j] = new Thread(baseJobs[j]);
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
			

			long now = System.currentTimeMillis(); 
			long duration = now - backThen;
			System.out.println("\n - Finished simulating after: " + duration/1000 + "s. (s/r = " + duration/1000./TIMESTEP +")"  );


			
			FileWriter writer;
			try {
				System.out.println("Beginning to write base results...");
				writer = new FileWriter(new File("/work1/s103232/PassengerDelay/Output/Events_base.csv"));
				writer.append("AgentId;TripId;Type;Time;FromX;FromY;FromString;ToX;ToY;ToString;How;DepartureId\n");
				for(PassengerDelayPerson person :passengerDelayPersons){
					writer.append(person.eventsToString());
				}
				writer.flush();
				writer.close();
				System.out.println("Finished writing base results...");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		} else {

			for(int stopwatch = startTime; stopwatch <= endTime; stopwatch += TIMESTEP ){
				long backThen = System.currentTimeMillis();

				if(stopwatch == startTime){ // stopwatch == startTime
					CreateBaseTransitSchedule.clearTransitSchedule(nextScenario);
					nextScenario= CreateBaseTransitSchedule.addTrainSchedule(nextScenario, 
							INPUT_FOLDER + "/Disaggregate/Train/" + date + "/DisaggregateSchedule_" + date + "_" + stopwatch + ".csv");
					nextScenario = CreateBaseTransitSchedule.addBusSchedule(nextScenario,
							INPUT_FOLDER + "/Disaggregate/Bus/" + date + "/DisaggregateBusSchedule_" + date + "_" + stopwatch + ".csv");
					nextScenario = CreateBaseTransitSchedule.addStaticSchedule(nextScenario, INPUT_FOLDER + "/BaseSchedules/MetroSchedule.xml.gz", stopwatch); //Add string to input. TODO
					nextScenario = CreateBaseTransitSchedule.addStaticSchedule(nextScenario, INPUT_FOLDER + "/BaseSchedules/LocalTrainSchedule.xml.gz", stopwatch); //Add string to input. TODO
					createDepMaps(stopwatch, nextScenario.getTransitSchedule().getTransitLines().values());
				}	
				currentRoute2OrderedStopOffset = latestRoute2OrderedStopOffset;
				Gbl.assertIf(areDeparturesNonReversing(nextScenario.getTransitSchedule().getTransitLines(), stopwatch));
				if(stopwatch < endTime){
					CreateBaseTransitSchedule.clearTransitSchedule(nextScenario);
					nextScenario= CreateBaseTransitSchedule.addTrainSchedule(nextScenario, 
							INPUT_FOLDER + "/Disaggregate/Train/" + date + "/DisaggregateSchedule_" + date + "_" + (stopwatch + TIMESTEP) + ".csv");
					nextScenario = CreateBaseTransitSchedule.addBusSchedule(nextScenario,
							INPUT_FOLDER + "/Disaggregate/Bus/" + date + "/DisaggregateBusSchedule_" + date + "_" + (stopwatch + TIMESTEP) + ".csv");
					nextScenario = CreateBaseTransitSchedule.addStaticSchedule(nextScenario, INPUT_FOLDER + "/BaseSchedules/MetroSchedule.xml.gz", stopwatch + TIMESTEP); 
					nextScenario = CreateBaseTransitSchedule.addStaticSchedule(nextScenario, INPUT_FOLDER + "/BaseSchedules/LocalTrainSchedule.xml.gz", stopwatch + TIMESTEP); 
				} // when stopwatch == endTime we just add them to the last maps without updating the schedule. 
				createDepMaps(stopwatch + TIMESTEP, nextScenario.getTransitSchedule().getTransitLines().values());

				if(stopwatch > startTime + DEPMAP_MEMORY){
					removeOldEntriesOfDepMaps(stopwatch - DEPMAP_MEMORY);
				}

				Date dateObject = new Date();
				SimpleDateFormat formatter = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
				String date = "___" + formatter.format(dateObject) + "___";
				System.out.println(date +"\nStopwatch is now " + intToTimeString(stopwatch));
				System.out.print("- Graph building times: ");

				if(runParallelThreads){
					AdvanceJob[] advanceJobs = new AdvanceJob[cores];
					for(int j = 0; j < cores; j++){
						advanceJobs[j] = new AdvanceJob(stopwatch, persons[j], scenarios[j]);		
					}
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

				} else {
					for(PassengerDelayPerson person : passengerDelayPersons){
						person.setStopwatch(stopwatch);
						person.advance();
					}
				}

				long now = System.currentTimeMillis(); 
				long duration = now - backThen;
				System.out.println("\n - Finished simulating after: " + duration/1000 + "s. (s/r = " + duration/1000./TIMESTEP +")"  );



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
					System.out.print("\n\n");
				}

				if(stopwatch  == (6*3600) || stopwatch == (8*3600) || stopwatch == endTime){
					System.out.println("\nWriting data at time " + stopwatch + "...");
					String hourString = "";
					if(stopwatch != endTime){
						int hour = (int) stopwatch / 3600;
						hourString = "_" + hour;
					}
					try {
						File folder =new File("/work1/s103232/PassengerDelay/Output");
						File files[]=  folder.listFiles();
						for(File f:files){
							if(f.getName().contains(date)){
								f.delete();
							}
						}
						FileWriter writer = new FileWriter(new File("/work1/s103232/PassengerDelay/Output/Events_" +
								date + hourString + ".csv"));
						writer.append("AgentId;TripId;Type;Time;FromX;FromY;FromString;ToX;ToY;ToString;How;DepartureId\n");
						for(PassengerDelayPerson person :passengerDelayPersons){
							writer.append(person.eventsToString());
						}
						writer.flush();
						writer.close();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}

		}



	}







	private static boolean areDeparturesNonReversing(Map<Id<TransitLine>, TransitLine> transitLines, double stopwatch) {
		for(TransitLine line : transitLines.values()){
			for(TransitRoute route : line.getRoutes().values()){
				double prevTime = Double.NEGATIVE_INFINITY;
				int counter = 1;
				for(TransitRouteStop stop : route.getStops()){
					double time = stop.getArrivalOffset();
					if(prevTime > time && prevTime - time <= 20*3600){
						System.err.println("@" + stopwatch + ": " + route.getId() + " of line " + line.getId() + 
								" Stop#" + counter + ": " + stop.getStopFacility().getId() + "Arrival error: " + prevTime + " > " + time);
						return false;
					}
					prevTime = time;
					time = stop.getDepartureOffset();
					if(prevTime > time && prevTime - time <= 20*3600){
						System.err.println("@" + stopwatch + ": " + route.getId() + " of line " + line.getId() + 
								" Stop #" + counter + ": " + stop.getStopFacility().getId() + ": Departure error: " + prevTime + " > " + time);
						return false;
					}
					counter++;
				}
			}
		}
		return true;
	}







	private static void removeOldEntriesOfDepMaps(int time) {
		dep2Route.remove(time);
		route2StopArrival.remove(time);
		route2Departure.remove(time);
		route2DepartureTime.remove(time);
		route2StopDeparture.remove(time);
	}







	private static void initialiseDepMaps() {

		route2StopArrival= new ConcurrentHashMap<Integer,ConcurrentHashMap<Id<TransitRoute>,
				ConcurrentHashMap<Id<TransitStopFacility>,Double>>>();
		route2Departure= new ConcurrentHashMap<Integer,ConcurrentHashMap<Id<TransitRoute>,ConcurrentHashMap<Double,Id<Departure>>>>();

		dep2Route = new ConcurrentHashMap<Integer, ConcurrentHashMap<Id<Departure>,Id<TransitRoute>>>();
		route2DepartureTime =  new ConcurrentHashMap<Integer,ConcurrentHashMap<Id<TransitRoute>,
				ConcurrentHashMap<Id<Departure>, Double>>>();
		route2StopDeparture = new ConcurrentHashMap<Integer,ConcurrentHashMap<Id<TransitRoute>,
				ConcurrentHashMap<Id<TransitStopFacility>,Double>>>();


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




	private static void createDepMaps(int stopwatch, Collection<TransitLine> transitLines) {
		dep2Route.put(stopwatch, new ConcurrentHashMap<Id<Departure>,Id<TransitRoute>>());

		latestRoute2OrderedStopOffset = 
				new ConcurrentHashMap<Id<TransitRoute>, ConcurrentSkipListMap<Double,TransitStopFacility>>();
		route2Departure.put(stopwatch, new ConcurrentHashMap<Id<TransitRoute>, ConcurrentHashMap<Double, Id<Departure>>>()); 
		route2DepartureTime.put(stopwatch, new ConcurrentHashMap<Id<TransitRoute>, ConcurrentHashMap<Id<Departure>,Double>>());
		route2StopDeparture.put(stopwatch, new ConcurrentHashMap<Id<TransitRoute>, ConcurrentHashMap<Id<TransitStopFacility>,Double>>());
		route2StopArrival.put(stopwatch, 
				new ConcurrentHashMap<Id<TransitRoute>, ConcurrentHashMap<Id<TransitStopFacility>, Double>>());

		for(TransitLine line : transitLines){
			for(TransitRoute route : line.getRoutes().values()){

				ConcurrentSkipListMap<Double,TransitStopFacility> skipListMap =
						new ConcurrentSkipListMap<Double,TransitStopFacility>();
				ConcurrentHashMap<Id<TransitStopFacility>,Double> hashMap = 
						new ConcurrentHashMap<Id<TransitStopFacility>,Double>();
				ConcurrentHashMap<Id<TransitStopFacility>,Double> hashMap2 = 
						new ConcurrentHashMap<Id<TransitStopFacility>,Double>();
				for(TransitRouteStop stop : route.getStops()){
					skipListMap.putIfAbsent(stop.getDepartureOffset(), stop.getStopFacility());
					hashMap.put(stop.getStopFacility().getId(), stop.getDepartureOffset());
					hashMap2.put(stop.getStopFacility().getId(), stop.getArrivalOffset());
				}
				latestRoute2OrderedStopOffset.put(route.getId(), skipListMap);
				route2StopDeparture.get(stopwatch).put(route.getId(), hashMap);
				route2StopArrival.get(stopwatch).put(route.getId(), hashMap2);

				ConcurrentHashMap<Id<Departure>,Double> hashMap3 = 
						new ConcurrentHashMap<Id<Departure>,Double>();
				ConcurrentHashMap<Double, Id<Departure>> hashMap4 = 
						new ConcurrentHashMap<Double, Id<Departure>>();
				for(Departure departure : route.getDepartures().values()){
					dep2Route.get(stopwatch).put(departure.getId(), route.getId());
					hashMap3.put(departure.getId(),departure.getDepartureTime());
					hashMap4.put(departure.getDepartureTime(), departure.getId());
				}
				route2DepartureTime.get(stopwatch).put(route.getId(), hashMap3);
				route2Departure.get(stopwatch).put(route.getId(), hashMap4);
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

		//750. takes 56, whereas 600 takes 39.. This parameter is probably the one that influences computation time the most
		// 600 is 10 minutes walk. Seems legit.
		config.transitRouter().setMaxBeelineWalkConnectionDistance(maxBeelineTransferWalk); 
		config.transitRouter().setSearchRadius(3000.);
		config.transitRouter().setAdditionalTransferTime(transferBufferTime);
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
