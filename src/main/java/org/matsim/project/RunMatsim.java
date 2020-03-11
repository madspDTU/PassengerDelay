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
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.api.core.v01.population.Route;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.ConfigWriter;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ModeParams;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup.ModeRoutingParams;
import org.matsim.core.controler.Controler;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.population.routes.GenericRouteImpl;
import org.matsim.core.population.routes.LinkNetworkRouteFactory;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.router.RoutingModule;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.facilities.ActivityFacilitiesFactoryImpl;
import org.matsim.pt.routes.ExperimentalTransitRoute;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleFactory;
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
import ch.sbb.matsim.routing.pt.raptor.MySwissRailRaptor;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptor;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorData;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorModule;

public class RunMatsim {

	// static String INPUT_FOLDER = "c:/workAtHome/PassengerDelay";
	static String INPUT_FOLDER = "/work1/s103232/PassengerDelay";
	static String date = "2014_09_01";
	//static String date = "base";

	static ActivityFacilitiesFactoryImpl facFac = new ActivityFacilitiesFactoryImpl();
	static NetworkRoute networkRoute;
	public static ConcurrentHashMap<Id<TransitStopFacility>, TransitStopFacility> facilities = new ConcurrentHashMap<Id<TransitStopFacility>, TransitStopFacility>();
	private static int cores = 1;
	static ConcurrentHashMap<Integer, ConcurrentHashMap<Id<Departure>, Id<TransitRoute>>> dep2Route;
	static ConcurrentHashMap<Integer, ConcurrentHashMap<Id<TransitRoute>, ConcurrentHashMap<Id<TransitStopFacility>, Double>>> route2StopArrival;
	static ConcurrentHashMap<Integer, ConcurrentHashMap<Id<TransitRoute>, ConcurrentHashMap<Double, Id<Departure>>>> route2Departure;
	static ConcurrentHashMap<Id<Departure>, ConcurrentHashMap<Id<TransitStopFacility>, Double>> arrivalTimeOfDepartureAtStop;
	static ConcurrentHashMap<Id<Departure>, ConcurrentHashMap<Id<TransitStopFacility>, Double>> departureTimeOfDepartureAtStop;
	static ConcurrentHashMap<Id<Departure>, ConcurrentSkipListMap<Double, Id<TransitStopFacility>>> nextStationOfDepartureAtTime;

	static int startTime = 3 * 3600;
	static int endTime = 27 * 3600;

	private static final double transferBufferTime = 0.;
	public static final boolean elaborateLogging = false;
	public static final boolean aggregateLogging = true;

	public static MySwissRailRaptor raptor;

	// Needs to be implemented.
	static final String MODE_TRAIN = "train";
	static final String MODE_BUS = "bus";
	static final String MODE_METRO = "metro";
	static final String MODE_S_TRAIN = "S-train";
	static final String MODE_LOCAL_TRAIN = "local-train";

	private static HashSet<String> ptSubModes = new HashSet<String>(Arrays.asList(MODE_TRAIN, MODE_BUS, MODE_METRO,
			MODE_S_TRAIN, MODE_LOCAL_TRAIN));

	static double waitTimeUtility = -1.6;
	static double trainTimeUtility = -1.1;
	static double trainDistanceUtility = 0.;
	static double sTrainTimeUtility = -0.9;
	static double sTrainDistanceUtility = 0.;
	static double localTrainTimeUtility = sTrainTimeUtility;
	static double localTrainDistanceUtility = sTrainDistanceUtility;
	static double busTimeUtility = -1.;
	static double busDistanceUtility = 0.;
	static double metroTimeUtility = -0.85;
	static double metroDistanceUtility = 0.;
	public static double walkTimeUtility = -1.3;
	static double walkDistanceUtility = 0.;
	static final double maxBeelineTransferWalk = 600.; // Furthest walk
	// between to
	// _transfer_
	// stations [m]
	static double transferPenalty = -4. / 60;

	// Never used, except for transfering onto the current map in next timestep.

	final static int TIMESTEP = 150;
	private final static int DEPMAP_MEMORY = 3600 * 2;

	static boolean runSanityTests = false;
	static AdaptivenessType adaptivenessType;
	public static ConcurrentHashMap<Id<TransitStopFacility>, ConcurrentHashMap<Id<TransitStopFacility>, 
	ConcurrentSkipListMap<Double, Id<Departure>>>> stop2StopTreeMap;
	public static ConcurrentHashMap<Integer, ConcurrentHashMap<Id<Departure>, Id<TransitLine>>> dep2Line;

	public static ConcurrentHashMap<String, Id<TransitStopFacility>> stopToStopGroup;
	public static double walkBeelineDistanceFactor = 1.;
	public static Double walkSpeed = 1.;
	private static double searchRadius = 3600.; //according to / Andersson 2013 (evt 2016) (60 min walk)
	private static double extensionRadius = 7200.;

	public static enum AdaptivenessType {
		RIGID, NONADAPTIVE, SEMIADAPTIVE, FULLADAPTIVE, PERFECT;
	}

	public static void main(String[] args) {
		CreateBaseTransitSchedule.init();

		// Config config =
		// ConfigUtils.loadConfig("/zhome/81/e/64390/git/matsim-example-project/input/1percent/config_eventPTRouter.xml");
		Config config = createConfig();
		Config nextConfig = createConfig();

		// Things only read once

		if (args != null && args.length > 0) {
			INPUT_FOLDER = args[0];
			date = args[1];
			if (args.length > 2) {
				cores = Integer.valueOf(args[2]);
				if (args.length > 3) {
					endTime = Integer.valueOf(args[3]) * 3600;
				}
				if (args.length > 4) {
					adaptivenessType = AdaptivenessType.valueOf(args[4]);
				}
			}
		}

		if (!runSanityTests) {
			config.network().setInputFile(INPUT_FOLDER + "/OtherInput/network.xml.gz");
			config.transit().setTransitScheduleFile(
					INPUT_FOLDER + "/BaseSchedules/BaseSchedule_InfrastructureOnly.xml.gz");
			nextConfig.network().setInputFile(INPUT_FOLDER + "/OtherInput/network.xml.gz");
			nextConfig.transit().setTransitScheduleFile(
					INPUT_FOLDER + "/BaseSchedules/BaseSchedule_InfrastructureOnly.xml.gz");
		}

		// Google Maps of empirisk viden

		Scenario scenario = ScenarioUtils.loadScenario(config);
		Scenario nextScenario = ScenarioUtils.loadScenario(nextConfig);

		Controler controler = new Controler(scenario);

		// To use the fast pt router (Part 1 of 1)
		controler.addOverridingModule(new SwissRailRaptorModule());

		RaptorStaticConfig staticConfig = createRaptorStaticConfig(config);

		if (runSanityTests) {
			sanityTests(scenario, staticConfig);
			System.exit(-1);
		}

		PopulationReader populationReader = new PopulationReader(scenario);
		populationReader.readFile("/work1/s103232/PassengerDelay/OtherInput/PTPlans_CPH.xml.gz");

		if (cores > scenario.getPopulation().getPersons().size()) {
			cores = scenario.getPopulation().getPersons().size();
		}
		PassengerDelayPerson[] passengerDelayPersons = new PassengerDelayPerson[scenario.getPopulation().getPersons()
		                                                                        .size()];
		int i = 0;
		for (Person person : scenario.getPopulation().getPersons().values()) {
			passengerDelayPersons[i] = new PassengerDelayPerson(person.getId(), person.getPlans().get(0));
			i++;
		}

		LinkedList<PassengerDelayPerson>[] persons = new LinkedList[cores];

		for (i = 0; i < passengerDelayPersons.length; i++) {
			int r = i % cores;
			if (persons[r] == null) {
				persons[r] = new LinkedList<PassengerDelayPerson>();
			}
			//			if(passengerDelayPersons[i].getId().toString().equals("848859_1_Person") || persons[r].size() < 5){
			//				persons[r].addLast(passengerDelayPersons[i]);
			//			}

			persons[r].addLast(passengerDelayPersons[i]);

			//			//To test with a small sample
			//			if(adaptivenessType == AdaptivenessType.RIGID && i > 200){
			//				break;
			//			}
		}


		//TODO SMALL SCALE DIAGNOSTICS
		boolean onlyRunSmallScaleDiagnostics = false;
		if(onlyRunSmallScaleDiagnostics){


			// Total travel time: 3689.0 when allowing all options.

			double depTime = 70015.;
			for(int k = 0; k <= 1; k++) {
				if(k == 1) {
					waitTimeUtility = -1;
					trainTimeUtility = -1;
					trainDistanceUtility = 0.;
					trainTimeUtility = -1;
					sTrainDistanceUtility = 0.;
					localTrainTimeUtility = sTrainTimeUtility;
					localTrainDistanceUtility = sTrainDistanceUtility;
					busTimeUtility = -1.;
					busDistanceUtility = 0.;
					metroTimeUtility = -1;
					metroDistanceUtility = 0.;
					walkTimeUtility = -1;
					walkDistanceUtility = 0.;
					transferPenalty = 0;
					System.out.println("Same disutilities: ");
				} else {
					System.out.println("Varying disutilities");
				}
				Config smallScaleConfig = createConfig();
				smallScaleConfig.transit().setTransitScheduleFile(
						"/work1/s103232/PassengerDelay/Diagnostics/perfectschedule2014_09_01_Reduced2.xml");
				smallScaleConfig.plans().setInputFile(null);


				Scenario smallScaleScenario = ScenarioUtils.loadScenario(smallScaleConfig);

				RaptorParametersForPerson arg3 = new DefaultRaptorParametersForPerson(smallScaleConfig);

				RaptorRouteSelector arg4 = new LeastCostRaptorRouteSelector();

				RaptorIntermodalAccessEgress iae = new DefaultRaptorIntermodalAccessEgress();
				Map<String, RoutingModule> routingModuleMap = new HashMap<String, RoutingModule>();

				RaptorStopFinder stopFinder = new DefaultRaptorStopFinder(smallScaleScenario.getPopulation(), iae, routingModuleMap);

				RaptorStaticConfig smallScaleStaticConfig = RunMatsim.createRaptorStaticConfig(smallScaleConfig);


				SwissRailRaptorData data = SwissRailRaptorData.create(smallScaleScenario.getTransitSchedule(), smallScaleStaticConfig ,
						smallScaleScenario.getNetwork());

				MySwissRailRaptor raptor = new MySwissRailRaptor(data, arg3, arg4, stopFinder);

				System.out.println("Departure time: " +  depTime);
				MyFakeFacility fromFac = new MyFakeFacility(new Coord(719025.7, 6177189));
				MyFakeFacility toFac = new MyFakeFacility(new Coord(  691950, 6192075));
				MyFakeFacility vanloseFac = new MyFakeFacility(new Coord(  719525, 6176783.1 ));
				MyFakeFacility  oFac = new MyFakeFacility(new Coord(  723564.4773, 6176587.9534));
				MyFakeFacility  dFac = new MyFakeFacility(new Coord(  723435.9862, 6176825.2263));
				
				
				System.out.println("\ntest: ");
				List<Leg> path = raptor.calcRoute(oFac, dFac, 55549.0, null, false, null, null);
				printAboutPath(path);
				path = raptor.calcRoute(oFac, dFac, 55549.0, null);
				printAboutPath(path);
				
				


				System.out.println("\nEntire Trip: ");
				path = raptor.calcRoute(fromFac, toFac, depTime, null, false, null, null);
				printAboutPath(path);

				System.out.println("\nTo Vanlose: ");
				path = raptor.calcRoute(fromFac, vanloseFac, depTime, null, false, null, null);
				printAboutPath(path);
			}

			System.exit(-1);

		}




		Scenario[] scenarios = new Scenario[cores];
		for (i = 0; i < cores; i++) {
			scenarios[i] = ScenarioUtils.loadScenario(createConfig());
		}

		RunMatsim.stopToStopGroup = createStop2StopGroupMap();
		for (TransitStopFacility stopFacility : scenario.getTransitSchedule().getFacilities().values()) {
			facilities.put(stopFacility.getId(), stopFacility);
		}

		initialiseDepMaps();

		System.out.println("Using " + cores + " cores");
		if (date.equals("base") || adaptivenessType == AdaptivenessType.PERFECT) {
			CreateBaseTransitSchedule.clearTransitSchedule(scenario);
			scenario = CreateBaseTransitSchedule.addBaseSchedule(scenario, date);
			createConstantMaps(scenario.getTransitSchedule().getTransitLines().values());
			createDepMaps(RunMatsim.startTime, scenario.getTransitSchedule().getTransitLines().values());
			performingBaseJob(passengerDelayPersons, persons, scenarios,date);
		} else {
			CreateBaseTransitSchedule.clearTransitSchedule(scenario);
			if(adaptivenessType == AdaptivenessType.RIGID){
				// using base maps.
				scenario = CreateBaseTransitSchedule.addBaseSchedule(scenario, "base");
				createConstantMaps(scenario.getTransitSchedule().getTransitLines().values());
				createDepMaps(RunMatsim.startTime, scenario.getTransitSchedule().getTransitLines().values());
				performingBaseJob(passengerDelayPersons, persons, scenarios, "base");
				clearConstantMaps();
				CreateBaseTransitSchedule.clearTransitSchedule(scenario);
			}

			scenario = CreateBaseTransitSchedule.addBaseSchedule(scenario, date);
			createConstantMaps(scenario.getTransitSchedule().getTransitLines().values());
			initialiseDepMaps();
			CreateBaseTransitSchedule.clearTransitSchedule(nextScenario);
			nextScenario = CreateBaseTransitSchedule.addSchedule(nextScenario, date, RunMatsim.startTime);
			createDepMaps(RunMatsim.startTime, nextScenario.getTransitSchedule().getTransitLines().values());

			for (int stopwatch = startTime; stopwatch <= endTime; stopwatch += TIMESTEP) {
				long backThen = System.currentTimeMillis();

				Gbl.assertIf(areDeparturesNonReversing(nextScenario.getTransitSchedule().getTransitLines(), stopwatch));
				if (stopwatch < endTime) {
					CreateBaseTransitSchedule.clearTransitSchedule(nextScenario);
					nextScenario = CreateBaseTransitSchedule.addSchedule(nextScenario, date, stopwatch + TIMESTEP);

				} // when stopwatch == endTime we just add them to the last maps
				// without updating the schedule.
				createDepMaps(stopwatch + TIMESTEP, nextScenario.getTransitSchedule().getTransitLines().values());

				if (stopwatch > startTime + DEPMAP_MEMORY) {
					removeOldEntriesOfDepMaps(stopwatch - DEPMAP_MEMORY);
				}

				Date dateObject = new Date();
				SimpleDateFormat formatter = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
				String dateString = "___" + formatter.format(dateObject) + "___";
				System.out.println("___Stopwatch is now " + intToTimeString(stopwatch) + "___  (reported at "
						+ dateString + ")");
				System.out.print("- Graph building times: ");

				AdvanceJob[] advanceJobs = new AdvanceJob[cores];
				for (int j = 0; j < cores; j++) {
					advanceJobs[j] = new AdvanceJob(stopwatch, persons[j], scenarios[j]);
				}
				Thread[] threads = new Thread[cores];
				for (int j = 0; j < cores; j++) {
					threads[j] = new Thread(advanceJobs[j]);
				}
				for (int j = 0; j < cores; j++) {
					threads[j].start();
				}

				for (int j = 0; j < cores; j++) {
					try {
						threads[j].join();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
				advanceJobs = null;
				threads = null;

				long now = System.currentTimeMillis();
				long duration = now - backThen;
				System.out.println("\n - Finished simulating after: " + duration / 1000 + "s. (s/r = " + duration
						/ 1000. / TIMESTEP + ")");

				if (aggregateLogging) {
					// System.out.println("real/sim (Graph): " +
					// 300./(backNow-backThen)*1000. );
					// System.out.println("real/sim (Simulation): " +
					// 300./(now-then)*1000. );
					System.out.println("Status:");
					int activity = 0, station = 0, vehicle = 0, walk = 0, dead = 0;
					for (PassengerDelayPerson person : passengerDelayPersons) {
						switch (person.getStatus()) {
						case ACTIVITY:
							activity++;
							break;
						case STATION:
							station++;
							break;
						case VEHICLE:
							vehicle++;
							break;
						case WALK:
							walk++;
							break;
						case DEAD:
							dead++;
							break;
						default:
							System.err.println("Something's terribly wrong with the statuses");
							break;
						}
					}
					System.out.println("  - Activity: " + activity);
					System.out.println("  - Station: " + station);
					System.out.println("  - Vehicle: " + vehicle);
					System.out.println("  - Walk: " + walk);
					System.out.println("  - Dead: " + dead);
					System.out.print("\n");
				}

				if (stopwatch == (6 * 3600) || stopwatch == (8 * 3600) || stopwatch == endTime) {
					System.out.println("\nWriting data at time " + stopwatch + "...");
					String hourString = "";
					if (stopwatch != endTime) {
						int hour = (int) stopwatch / 3600;
						hourString = "_" + hour;
					}
					try {
						File folder = new File("/work1/s103232/PassengerDelay/Output/" + adaptivenessType);
						File files[] = folder.listFiles();
						for (File f : files) {
							if (f.getName().contains(date)) {
								f.delete();
							}
						}
						FileWriter writer = new FileWriter(new File("/work1/s103232/PassengerDelay/Output/" +
								adaptivenessType + "/Events_" + date + hourString + ".csv"));
						writer.append("AgentId;TripId;Type;Time;FromX;FromY;FromString;ToX;ToY;ToString;How;DepartureId\n");
						for (PassengerDelayPerson person : passengerDelayPersons) {
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

	private static void printAboutPath(List<Leg> path) {
		if(path == null) {
			System.out.println("Path is null....");
		} else {
			double totalTravelTime = 0.;
			for(Leg leg : path) {
				Route rawRoute = leg.getRoute();
				if(rawRoute instanceof GenericRouteImpl) {
					GenericRouteImpl walkRoute = (GenericRouteImpl) rawRoute;
					System.out.println("Walk: " + " Distance: " + walkRoute.getDistance() + " TravelTime: " +
							walkRoute.getTravelTime());
				} else  {
					ExperimentalTransitRoute ptRoute = (ExperimentalTransitRoute) rawRoute;
					System.out.println("PT: " + ptRoute.getAccessStopId() + "->" + ptRoute.getEgressStopId() + ", a distance of " +
							ptRoute.getDistance() + " lasting " + ptRoute.getTravelTime() + " seconds.");
				}
				totalTravelTime += leg.getTravelTime();
			}
			System.out.println("Total travel time: " + totalTravelTime);
		}

	}

	public static ConcurrentHashMap<String, Id<TransitStopFacility>> createStop2StopGroupMap() {
		String inputFile = INPUT_FOLDER + "/OtherInput/NewStops.csv";
		ConcurrentHashMap<String, Id<TransitStopFacility>> output = new ConcurrentHashMap<String,Id<TransitStopFacility>>();
		BufferedReader br;
		try {
			br = new BufferedReader(new FileReader(inputFile));

			String readLine = br.readLine();
			while((readLine = br.readLine()) != null){
				String[] splitLine = readLine.split(";");
				String stopNumber = splitLine[0].split("_")[0];	
				Id<TransitStopFacility> stopGroupId = Id.create(splitLine[3], TransitStopFacility.class);
				output.put(stopNumber,stopGroupId);
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return output;
	}


	private static void performingBaseJob(PassengerDelayPerson[] passengerDelayPersons,
			LinkedList<PassengerDelayPerson>[] persons, Scenario[] scenarios, String date) {
		long backThen = System.currentTimeMillis();

		System.out.print("- Graph building times: ");

		BaseJob[] baseJobs = new BaseJob[cores];
		for (int j = 0; j < cores; j++) {	
			baseJobs[j] = new BaseJob(startTime, persons[j], scenarios[j], date);
		}
		Thread[] threads = new Thread[cores];
		for (int j = 0; j < cores; j++) {
			threads[j] = new Thread(baseJobs[j]);
		}
		for (int j = 0; j < cores; j++) {
			threads[j].start();
		}
		for (int j = 0; j < cores; j++) {
			try {
				threads[j].join();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		if(adaptivenessType != AdaptivenessType.RIGID){
			long now = System.currentTimeMillis();
			long duration = now - backThen;
			System.out.println("\n - Finished simulating after: " + duration / 1000 + "s. (s/r = " + duration / 1000.
					/ TIMESTEP + ")");

			try {
				FileWriter writer;
				if(date.equals("base")){
					System.out.println("Beginning to write results...");
					writer = new FileWriter(new File("/work1/s103232/PassengerDelay/Output/BASE/Events_base.csv"));
				} else {
					writer = new FileWriter(new File("/work1/s103232/PassengerDelay/Output/PERFECT/Events_" + date + ".csv"));
				}
				writer.append("AgentId;TripId;Type;Time;FromX;FromY;FromString;ToX;ToY;ToString;How;DepartureId\n");
				for (PassengerDelayPerson person : passengerDelayPersons) {
					writer.append(person.eventsToString());
				}
				writer.flush();
				writer.close();
				System.out.println("Finished writing results...");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	private static void sanityTests(Scenario scenario, RaptorStaticConfig staticConfig) {
		Config config = scenario.getConfig();
		TransitSchedule schedule = scenario.getTransitSchedule();
		TransitScheduleFactory fac = scenario.getTransitSchedule().getFactory();

		// network
		Node nodeO = scenario.getNetwork().getFactory().createNode(Id.create("NodeO", Node.class), new Coord(0, 0));
		Node nodeI = scenario.getNetwork().getFactory().createNode(Id.create("NodeI", Node.class), new Coord(1000, 0));
		Node nodeD = scenario.getNetwork().getFactory().createNode(Id.create("NodeD", Node.class), new Coord(2000, 0));
		Node stopNodeO = scenario.getNetwork().getFactory()
				.createNode(Id.create("StopNodeO", Node.class), new Coord(0, 5));
		Node stopNodeI = scenario.getNetwork().getFactory()
				.createNode(Id.create("StopNodeI", Node.class), new Coord(1000, 5));
		Node stopNodeD = scenario.getNetwork().getFactory()
				.createNode(Id.create("StopNodeD", Node.class), new Coord(2000, 5));

		Link linkOtoStopO = scenario.getNetwork().getFactory()
				.createLink(Id.create("LinkOtoStopO", Link.class), nodeO, stopNodeO);
		Link linkStopOtoO = scenario.getNetwork().getFactory()
				.createLink(Id.create("LinkStopOtoO", Link.class), stopNodeO, nodeO);
		Link linkOtoI = scenario.getNetwork().getFactory().createLink(Id.create("LinkOtoI", Link.class), nodeO, nodeI);
		Link linkItoStopI = scenario.getNetwork().getFactory()
				.createLink(Id.create("LinkItoStopI", Link.class), nodeI, stopNodeI);
		Link linkStopItoI = scenario.getNetwork().getFactory()
				.createLink(Id.create("LinkStopItoI", Link.class), stopNodeI, nodeI);
		Link linkItoD = scenario.getNetwork().getFactory().createLink(Id.create("LinkItoD", Link.class), nodeI, nodeD);
		Link linkDtoStopD = scenario.getNetwork().getFactory()
				.createLink(Id.create("LinkDtoStopD", Link.class), nodeD, stopNodeD);
		Link linkStopDtoD = scenario.getNetwork().getFactory()
				.createLink(Id.create("LinkStopDtoD", Link.class), stopNodeD, nodeD);

		scenario.getNetwork().addNode(nodeO);
		scenario.getNetwork().addNode(nodeI);
		scenario.getNetwork().addNode(nodeD);
		scenario.getNetwork().addNode(stopNodeO);
		scenario.getNetwork().addNode(stopNodeI);
		scenario.getNetwork().addNode(stopNodeD);
		scenario.getNetwork().addLink(linkOtoStopO);
		scenario.getNetwork().addLink(linkStopOtoO);
		scenario.getNetwork().addLink(linkOtoI);
		scenario.getNetwork().addLink(linkItoStopI);
		scenario.getNetwork().addLink(linkStopItoI);
		scenario.getNetwork().addLink(linkItoD);

		networkRoute = (NetworkRoute) new LinkNetworkRouteFactory().createRoute(linkOtoStopO.getId(),
				linkDtoStopD.getId());
		LinkedList<Id<Link>> iLinks = new LinkedList<Id<Link>>();
		iLinks.add(linkStopOtoO.getId());
		iLinks.add(linkOtoI.getId());
		iLinks.add(linkItoStopI.getId());
		iLinks.add(linkStopItoI.getId());
		iLinks.add(linkItoD.getId());
		networkRoute.setLinkIds(linkOtoStopO.getId(), iLinks, linkDtoStopD.getId());

		TransitLine line = fac.createTransitLine(Id.create("TheOnlyLine", TransitLine.class));
		TransitStopFacility stopO = fac.createTransitStopFacility(Id.create("Stop_O", TransitStopFacility.class),
				stopNodeO.getCoord(), false);
		stopO.setLinkId(linkOtoStopO.getId());
		stopO.setCoord(stopNodeO.getCoord());
		TransitStopFacility stopI = fac.createTransitStopFacility(Id.create("Stop_I", TransitStopFacility.class),
				stopNodeI.getCoord(), false);
		stopI.setLinkId(linkItoStopI.getId());
		stopI.setCoord(stopNodeI.getCoord());
		TransitStopFacility stopD = fac.createTransitStopFacility(Id.create("Stop_D", TransitStopFacility.class),
				stopNodeD.getCoord(), false);
		stopD.setLinkId(linkItoStopI.getId());
		stopD.setCoord(stopNodeD.getCoord());
		schedule.addStopFacility(stopO);
		schedule.addStopFacility(stopI);
		schedule.addStopFacility(stopD);

		// bus
		{
			TransitRouteStop trStopO = fac.createTransitRouteStop(stopO, -2 * 60, -2 * 60 + 3);
			TransitRouteStop trStopI = fac.createTransitRouteStop(stopI, 2 * 60, 2 * 60 + 3);
			TransitRouteStop trStopD = fac.createTransitRouteStop(stopD, 3 * 60, 3 * 60 + 3);
			LinkedList<TransitRouteStop> stops = new LinkedList<TransitRouteStop>();
			stops.add(trStopO);
			stops.add(trStopI);
			stops.add(trStopD);
			TransitRoute route = fac.createTransitRoute(Id.create("BusRoute", TransitRoute.class), networkRoute, stops,
					MODE_BUS);
			Departure dep = fac.createDeparture(Id.create("BusDeparture", Departure.class), 3 * 3600 + 60);
			route.addDeparture(dep);
			line.addRoute(route);
		}

		// s-train
		{
			TransitRouteStop trStopO = fac.createTransitRouteStop(stopO, 0, +3);
			TransitRouteStop trStopI = fac.createTransitRouteStop(stopI, 2 * 60, 2 * 60 + 3);
			TransitRouteStop trStopD = fac.createTransitRouteStop(stopD, 4 * 60, 4 * 60 + 3);
			LinkedList<TransitRouteStop> stops = new LinkedList<TransitRouteStop>();
			stops.add(trStopO);
			stops.add(trStopI);
			stops.add(trStopD);
			TransitRoute route = fac.createTransitRoute(Id.create("S-TrainRoute", TransitRoute.class), networkRoute,
					stops, MODE_S_TRAIN);
			Departure dep = fac.createDeparture(Id.create("S-TrainDeparture", Departure.class), 3 * 3600 + 60);
			route.addDeparture(dep);
			line.addRoute(route);
		}

		// train
		{
			TransitRouteStop trStopO = fac.createTransitRouteStop(stopO, 0, +3);
			TransitRouteStop trStopI = fac.createTransitRouteStop(stopI, 2 * 60, 2 * 60 + 3);
			TransitRouteStop trStopD = fac.createTransitRouteStop(stopD, 4 * 60, 4 * 60 + 3);
			LinkedList<TransitRouteStop> stops = new LinkedList<TransitRouteStop>();
			stops.add(trStopO);
			stops.add(trStopI);
			stops.add(trStopD);
			TransitRoute route = fac.createTransitRoute(Id.create("TrainRoute", TransitRoute.class), networkRoute,
					stops, MODE_TRAIN);
			Departure dep = fac.createDeparture(Id.create("TrainDeparture", Departure.class), 3 * 3600 + 60);
			route.addDeparture(dep);
			line.addRoute(route);
		}

		// metro
		{
			TransitRouteStop trStopO = fac.createTransitRouteStop(stopO, 0, +3);
			TransitRouteStop trStopI = fac.createTransitRouteStop(stopI, 2 * 60, 2 * 60 + 3);
			TransitRouteStop trStopD = fac.createTransitRouteStop(stopD, 4 * 60, 4 * 60 + 3);
			LinkedList<TransitRouteStop> stops = new LinkedList<TransitRouteStop>();
			stops.add(trStopO);
			stops.add(trStopI);
			stops.add(trStopD);
			TransitRoute route = fac.createTransitRoute(Id.create("MetroRoute", TransitRoute.class), networkRoute,
					stops, MODE_METRO);
			Departure dep = fac.createDeparture(Id.create("MetroDeparture", Departure.class), 3 * 3600 + 60);
			route.addDeparture(dep);
			line.addRoute(route);
		}
		schedule.addTransitLine(line);

		// create person
		Plan plan = scenario.getPopulation().getFactory().createPlan();
		Activity act1 = PopulationUtils.createActivityFromCoord("home", nodeO.getCoord());
		act1.setEndTime(3 * 3600);
		plan.addActivity(act1);
		plan.addLeg(PopulationUtils.createLeg("pt"));
		Activity act2 = PopulationUtils.createActivityFromCoord("work", nodeD.getCoord());
		act2.setEndTime(Double.POSITIVE_INFINITY);
		plan.addActivity(act2);
		PassengerDelayPerson person = new PassengerDelayPerson(Id.create("Neo", Person.class), plan);

		// create raptor
		RaptorParametersForPerson arg3 = new DefaultRaptorParametersForPerson(config);
		RaptorRouteSelector arg4 = new LeastCostRaptorRouteSelector();
		RaptorIntermodalAccessEgress iae = new DefaultRaptorIntermodalAccessEgress();
		Map<String, RoutingModule> routingModuleMap = new HashMap<String, RoutingModule>();
		RaptorStopFinder stopFinder = new DefaultRaptorStopFinder(scenario.getPopulation(), iae, routingModuleMap);
		SwissRailRaptorData data = SwissRailRaptorData.create(scenario.getTransitSchedule(), staticConfig,
				scenario.getNetwork());
		MySwissRailRaptor raptor = new MySwissRailRaptor(data, arg3, arg4, stopFinder);

		for (TransitStopFacility stopFacility : scenario.getTransitSchedule().getFacilities().values()) {
			facilities.put(stopFacility.getId(), stopFacility);
		}
		initialiseDepMaps();
		for (int i = 0; i <= 600; i += 150) {
			createDepMaps(3 * 3600 + i, schedule.getTransitLines().values());
		}

		// Simulation
		person.setStopwatch(3 * 3600);
		person.setRaptor(raptor);
		person.advance();

		person.setStopwatch(3 * 3600 + 150);
		person.setRaptor(raptor);
		person.advance();

		person.setStopwatch(3 * 3600 + 300);
		person.setRaptor(raptor);
		person.advance();

		person.setStopwatch(3 * 3600 + 450);
		person.setRaptor(raptor);
		person.advance();

		person.setStopwatch(3 * 3600 + 600);
		person.setRaptor(raptor);
		person.advance();

		person.createEntireDayEvents();
		System.out.println(person.eventsToString());

	}

	private static boolean areDeparturesNonReversing(Map<Id<TransitLine>, TransitLine> transitLines, double stopwatch) {
		for (TransitLine line : transitLines.values()) {
			for (TransitRoute route : line.getRoutes().values()) {
				double prevTime = Double.NEGATIVE_INFINITY;
				int counter = 1;
				for (TransitRouteStop stop : route.getStops()) {
					double time = stop.getArrivalOffset();
					if (prevTime > time && prevTime - time <= 20 * 3600) {
						System.err.println("@" + stopwatch + ": " + route.getId() + " of line " + line.getId()
						+ " Stop#" + counter + ": " + stop.getStopFacility().getId() + "Arrival error: "
						+ prevTime + " > " + time);
						return false;
					}
					prevTime = time;
					time = stop.getDepartureOffset();
					if (prevTime > time && prevTime - time <= 20 * 3600) {
						System.err.println("@" + stopwatch + ": " + route.getId() + " of line " + line.getId()
						+ " Stop #" + counter + ": " + stop.getStopFacility().getId() + ": Departure error: "
						+ prevTime + " > " + time);
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
		if(adaptivenessType == AdaptivenessType.NONADAPTIVE || adaptivenessType == AdaptivenessType.RIGID){
			dep2Line.remove(time);	
		}
		route2StopArrival.remove(time);
		route2Departure.remove(time);
	}

	private static void initialiseDepMaps() {

		route2StopArrival = new ConcurrentHashMap<Integer, ConcurrentHashMap<Id<TransitRoute>, ConcurrentHashMap<Id<TransitStopFacility>, Double>>>();
		route2Departure = new ConcurrentHashMap<Integer, ConcurrentHashMap<Id<TransitRoute>, ConcurrentHashMap<Double, Id<Departure>>>>();

		dep2Route = new ConcurrentHashMap<Integer, ConcurrentHashMap<Id<Departure>, Id<TransitRoute>>>();
		if(adaptivenessType == AdaptivenessType.NONADAPTIVE || adaptivenessType == AdaptivenessType.RIGID){
			dep2Line = new ConcurrentHashMap<Integer, ConcurrentHashMap<Id<Departure>, Id<TransitLine>>>();
		}


	}

	private static String intToTimeString(int n) {
		int h = n / 3600;
		n -= h * 3600;
		int m = n / 60;
		n -= m * 60;
		return ((h >= 10) ? h : ("0" + h)) + ":" + ((m >= 10) ? m : ("0" + m)) + ":" + ((n >= 10) ? n : ("0" + n));
	}

	private static void clearConstantMaps(){
		arrivalTimeOfDepartureAtStop.clear(); 
		departureTimeOfDepartureAtStop.clear();
		nextStationOfDepartureAtTime.clear();
		if(adaptivenessType == AdaptivenessType.NONADAPTIVE || adaptivenessType == AdaptivenessType.RIGID){
			stop2StopTreeMap.clear();
		}
	}

	private static void createConstantMaps(Collection<TransitLine> transitLines) {
		arrivalTimeOfDepartureAtStop = new ConcurrentHashMap<Id<Departure>, ConcurrentHashMap<Id<TransitStopFacility>, Double>>();
		departureTimeOfDepartureAtStop = new ConcurrentHashMap<Id<Departure>, ConcurrentHashMap<Id<TransitStopFacility>, Double>>();
		nextStationOfDepartureAtTime = new ConcurrentHashMap<Id<Departure>, ConcurrentSkipListMap<Double, Id<TransitStopFacility>>>();
		if(adaptivenessType == AdaptivenessType.NONADAPTIVE || adaptivenessType == AdaptivenessType.RIGID){
			stop2StopTreeMap = new ConcurrentHashMap<Id<TransitStopFacility>, ConcurrentHashMap<Id<TransitStopFacility>, 
					ConcurrentSkipListMap<Double,Id<Departure>>>>();
		}
		for (TransitLine line : transitLines) {
			for (TransitRoute route : line.getRoutes().values()) {
				for (Departure departure : route.getDepartures().values()) {
					double depTime = departure.getDepartureTime();
					Id<Departure> depId = departure.getId();
					arrivalTimeOfDepartureAtStop.put(depId, new ConcurrentHashMap<Id<TransitStopFacility>, Double>());
					departureTimeOfDepartureAtStop.put(depId, new ConcurrentHashMap<Id<TransitStopFacility>, Double>());
					nextStationOfDepartureAtTime.put(depId,
							new ConcurrentSkipListMap<Double, Id<TransitStopFacility>>());
					for (TransitRouteStop stop : route.getStops()) {
						Id<TransitStopFacility> stopId = stop.getStopFacility().getId();
						arrivalTimeOfDepartureAtStop.get(depId).put(stopId, depTime + stop.getArrivalOffset());
						departureTimeOfDepartureAtStop.get(depId).put(stopId, depTime + stop.getDepartureOffset());
						nextStationOfDepartureAtTime.get(depId).putIfAbsent(depTime + stop.getArrivalOffset(), stopId);
						if(adaptivenessType == AdaptivenessType.NONADAPTIVE || adaptivenessType == AdaptivenessType.RIGID){
							boolean afterFromStop = false;
							for(TransitRouteStop toStop : route.getStops()){
								Id<TransitStopFacility> toStopId = toStop.getStopFacility().getId();
								if(afterFromStop){
									if(!stop2StopTreeMap.containsKey(stopId)){
										stop2StopTreeMap.put(stopId, new ConcurrentHashMap<Id<TransitStopFacility>, ConcurrentSkipListMap<Double,Id<Departure>>>());
									}
									if(!stop2StopTreeMap.get(stopId).containsKey(toStopId)){
										stop2StopTreeMap.get(stopId).put(toStopId, new ConcurrentSkipListMap<Double,Id<Departure>>() );
									}
									stop2StopTreeMap.get(stopId).get(toStopId).put(depTime + stop.getDepartureOffset(), depId);
								} else if(toStopId == stopId){
									afterFromStop = true;
								}
							}
						}
					}
				}
			}
		}
	}

	private static void createDepMaps(int stopwatch, Collection<TransitLine> transitLines) {
		dep2Route.put(stopwatch, new ConcurrentHashMap<Id<Departure>, Id<TransitRoute>>());
		if(adaptivenessType == AdaptivenessType.NONADAPTIVE || adaptivenessType == AdaptivenessType.RIGID ){
			dep2Line.put(stopwatch, new ConcurrentHashMap<Id<Departure>, Id<TransitLine>>());
		}


		route2Departure.put(stopwatch,
				new ConcurrentHashMap<Id<TransitRoute>, ConcurrentHashMap<Double, Id<Departure>>>());
		route2StopArrival.put(stopwatch,
				new ConcurrentHashMap<Id<TransitRoute>, ConcurrentHashMap<Id<TransitStopFacility>, Double>>());

		for (TransitLine line : transitLines) {
			for (TransitRoute route : line.getRoutes().values()) {

				ConcurrentSkipListMap<Double, TransitStopFacility> skipListMap = new ConcurrentSkipListMap<Double, TransitStopFacility>();
				ConcurrentHashMap<Id<TransitStopFacility>, Double> hashMap = new ConcurrentHashMap<Id<TransitStopFacility>, Double>();
				ConcurrentHashMap<Id<TransitStopFacility>, Double> hashMap2 = new ConcurrentHashMap<Id<TransitStopFacility>, Double>();
				for (TransitRouteStop stop : route.getStops()) {
					skipListMap.putIfAbsent(stop.getDepartureOffset(), stop.getStopFacility());
					Id<TransitStopFacility> stopId = stop.getStopFacility().getId();
					hashMap.put(stopId, stop.getDepartureOffset());
					hashMap2.put(stopId, stop.getArrivalOffset());
				}
				route2StopArrival.get(stopwatch).put(route.getId(), hashMap2);

				ConcurrentHashMap<Id<Departure>, Double> hashMap3 = new ConcurrentHashMap<Id<Departure>, Double>();
				ConcurrentHashMap<Double, Id<Departure>> hashMap4 = new ConcurrentHashMap<Double, Id<Departure>>();
				for (Departure departure : route.getDepartures().values()) {
					dep2Route.get(stopwatch).put(departure.getId(), route.getId());
					if(adaptivenessType == AdaptivenessType.NONADAPTIVE || adaptivenessType == AdaptivenessType.RIGID){
						dep2Line.get(stopwatch).put(departure.getId(), line.getId());
					}
					hashMap3.put(departure.getId(), departure.getDepartureTime());
					hashMap4.put(departure.getDepartureTime(), departure.getId());
				}
				route2Departure.get(stopwatch).put(route.getId(), hashMap4);
			}
		}
	}

	public static Config createConfig() {
		Config config = ConfigUtils.createConfig();
		config.network().setInputFile(RunMatsim.INPUT_FOLDER + "/OtherInput/network.xml.gz");
		// config.plans().setInputFile(INPUT_FOLDER +
		// "/OtherInput/population.xml.gz");
		config.transit().setTransitScheduleFile(
				RunMatsim.INPUT_FOLDER + "/BaseSchedules/BaseSchedule_InfrastructureOnly.xml.gz");
		// config.transit().setTransitScheduleFile("/zhome/81/e/64390/git/matsim-example-project/input/full/schedule_CPH.xml.gz");

		// Implement the events based PT router instead - it uses less transfer
		// links.

		// 750. takes 56, whereas 600 takes 39.. This parameter is probably the
		// one that influences computation time the most
		// 600 is 10 minutes walk. Seems legit.

		config.transitRouter().setMaxBeelineWalkConnectionDistance(maxBeelineTransferWalk);
		config.transitRouter().setSearchRadius(searchRadius); 
		config.transitRouter().setAdditionalTransferTime(transferBufferTime);
		config.transitRouter().setExtensionRadius(extensionRadius);
		config.transitRouter().setDirectWalkFactor(walkBeelineDistanceFactor);
		config.plansCalcRoute().clearModeRoutingParams();

		for(String mode : Arrays.asList(TransportMode.walk, TransportMode.transit_walk, TransportMode.non_network_walk,
				TransportMode.access_walk, TransportMode.egress_walk)) {
			config.plansCalcRoute().getOrCreateModeRoutingParams(mode).setBeelineDistanceFactor(walkBeelineDistanceFactor);
			config.plansCalcRoute().getOrCreateModeRoutingParams(mode).setTeleportedModeSpeed(walkSpeed);
			ModeParams walkParams = new ModeParams(mode);
			walkParams.setMarginalUtilityOfDistance(walkDistanceUtility);
			walkParams.setMarginalUtilityOfTraveling(walkTimeUtility);
			config.planCalcScore().addModeParams(walkParams);
		}

		config.planCalcScore().setPerforming_utils_hr(0);
		config.transit().setTransitModes(ptSubModes);
		for (String mode : ptSubModes) {
			ModeParams params = new ModeParams(mode);
			switch (mode) {
			case MODE_TRAIN:
				params.setMarginalUtilityOfTraveling(trainTimeUtility);
				params.setMarginalUtilityOfDistance(trainDistanceUtility);
				break;
			case MODE_BUS:
				params.setMarginalUtilityOfTraveling(busTimeUtility);
				params.setMarginalUtilityOfDistance(busDistanceUtility);
				break;
			case MODE_METRO:
				params.setMarginalUtilityOfTraveling(metroTimeUtility);
				params.setMarginalUtilityOfDistance(metroDistanceUtility);
				break;
			case MODE_S_TRAIN:
				params.setMarginalUtilityOfTraveling(sTrainTimeUtility);
				params.setMarginalUtilityOfDistance(sTrainDistanceUtility);
				break;
			case MODE_LOCAL_TRAIN:
				params.setMarginalUtilityOfTraveling(localTrainTimeUtility);
				params.setMarginalUtilityOfDistance(localTrainDistanceUtility);
				break;
			default:
				break;
			}
			config.planCalcScore().addModeParams(params);
		}
		config.planCalcScore().setMarginalUtlOfWaitingPt_utils_hr(waitTimeUtility);
		config.planCalcScore().setUtilityOfLineSwitch(transferPenalty);

		config.travelTimeCalculator().setTraveltimeBinSize(TIMESTEP);
		config.qsim().setStartTime(0);
		config.qsim().setEndTime(endTime);

		return config;
	}

	public static RaptorStaticConfig createRaptorStaticConfig(Config config) {
		RaptorStaticConfig staticConfig = new RaptorStaticConfig();
		staticConfig.setBeelineWalkConnectionDistance(config.transitRouter().getMaxBeelineWalkConnectionDistance());
		staticConfig.setBeelineWalkDistanceFactor(config.transitRouter().getDirectWalkFactor());
		staticConfig.setBeelineWalkSpeed(config.plansCalcRoute().getOrCreateModeRoutingParams(TransportMode.walk)
				.getTeleportedModeSpeed());
		staticConfig.setMinimalTransferTime(config.transitRouter().getAdditionalTransferTime());
		staticConfig.setOptimization(RaptorOptimization.OneToOneRouting);

		staticConfig.setUseModeMappingForPassengers(true);
		for (String mode : ptSubModes) {
			staticConfig.addModeMappingForPassengers(mode, mode);
		}
		return staticConfig;
	}

}
