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
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Route;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ModeParams;
import org.matsim.core.controler.Controler;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.population.routes.GenericRouteImpl;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.project.pt.MyDepartureImpl;
import org.matsim.project.pt.MyTransitLineImpl;
import org.matsim.project.pt.MyTransitRouteImpl;
import org.matsim.project.pt.MyTransitRouteStopImpl;
import org.matsim.project.pt.MyTransitScheduleImpl;
import org.matsim.project.pt.MyTransitStopFacilityImpl;
import org.matsim.pt.routes.ExperimentalTransitRoute;
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import ch.sbb.matsim.routing.pt.raptor.RaptorStaticConfig;
import ch.sbb.matsim.routing.pt.raptor.RaptorStaticConfig.RaptorOptimization;
import ch.sbb.matsim.routing.pt.raptor.MySwissRailRaptor;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorModule;

public class RunMatsim {

	// static String INPUT_FOLDER = "c:/workAtHome/PassengerDelay";
	static String INPUT_FOLDER = "/work1/s103232/PassengerDelay";
	static String date = "2014_09_01";
	//static String date = "base";

	public static ConcurrentHashMap<Id<MyTransitStopFacilityImpl>, MyTransitStopFacilityImpl> facilities = 
			new ConcurrentHashMap<Id<MyTransitStopFacilityImpl>, MyTransitStopFacilityImpl>();
	private static int cores = 1;
	static ConcurrentHashMap<Id<MyDepartureImpl>, ArrDep[]> arrivalAndDepartureTimesOfDepartureAtStop;
	static ConcurrentHashMap<Id<MyDepartureImpl>, ConcurrentSkipListMap<Integer, Id<MyTransitStopFacilityImpl>>> nextStationOfDepartureAtTime;
	public static ConcurrentHashMap<Id<MyDepartureImpl>, Id<MyTransitLineImpl>> dep2Line;

	static int startTime = 3 * 3600; // 24600;//
	static int endTime = 27 * 3600;

	private static final double transferBufferTime = 0.;
	public static final boolean elaborateLogging = false;
	public static final boolean aggregateLogging = true;

	public static MySwissRailRaptor raptor;

	public static final String MODE_TRAIN = "train";
	public static final String MODE_BUS = "bus";
	public static final String MODE_METRO = "metro";
	public static final String MODE_S_TRAIN = "S-train";
	public static final String MODE_LOCAL_TRAIN = "local-train";

	public static HashSet<String> ptSubModes = new HashSet<String>(Arrays.asList(MODE_TRAIN, MODE_BUS, MODE_METRO,
			MODE_S_TRAIN, MODE_LOCAL_TRAIN));

	public static double waitTimeUtility = -1.6 / 60.;
	public static double trainTimeUtility = -1.1 / 60.;
	public static double trainDistanceUtility = 0. / 60.;
	public static double sTrainTimeUtility = -0.9 / 60.;
	public static double sTrainDistanceUtility = 0. / 60.;
	public static double localTrainTimeUtility = sTrainTimeUtility;
	public static double localTrainDistanceUtility = sTrainDistanceUtility;
	public static double busTimeUtility = -1. / 60.;
	public static double busDistanceUtility = 0. / 60.;
	public static double metroTimeUtility = -0.85 / 60.;
	public static double metroDistanceUtility = 0. / 60.;
	public static double walkTimeUtility = -1.3 / 60.;
	public static double walkDistanceUtility = 0. / 60.;
	public static double maxBeelineTransferWalk = 600.; // Furthest transfer walk //600.
	// between to
	// _transfer_
	// stations [m]
	public static double transferUtility = -4.;




	// Never used, except for transfering onto the current map in next timestep.

	final static int TIMESTEP = 150;

	static boolean runSanityTests = false;
	static AdaptivenessType adaptivenessType;
	public static ConcurrentHashMap<Id<MyTransitStopFacilityImpl>, ConcurrentHashMap<Id<MyTransitStopFacilityImpl>,
	ConcurrentSkipListMap<Integer, NewDepartureBundle>>> stop2StopTreeMap;

	public static ConcurrentHashMap<String, Id<MyTransitStopFacilityImpl>> stopToStopGroup;
	public static double walkBeelineDistanceFactor = 1.;
	public static double walkSpeed = 1.;
	public static double searchRadius = 3600.; //3600//according to / Andersson 2013 (evt 2016) (60 min walk)
	public static double extensionRadius = 7200.;

	public static double[][] distances;
	public static int stopwatch = startTime;
	public static final int maxWait = 5400; //5400 Only used to prune transfers
	public static final double reachedDistance = 3600.; //3600
	public static int maxTotalTransfers = 10;
	public static int maxTransfersAfterFirstArrival = 4;
	public static int maxAllowedTimeDifferenceAtStop = 2*3600;
	public static int numberOfTransitStopFacilities = 0;
	private static boolean pruneByShortestDistances = true;


	public static enum AdaptivenessType {
		RIGID, NONADAPTIVE, SEMIADAPTIVE, FULLADAPTIVE, PERFECT, PERFECT_EXTENDED;
	}


	public static void main(String[] args) {

		CreateBaseTransitSchedule.init();

		// Config config =
		// ConfigUtils.loadConfig("/zhome/81/e/64390/git/matsim-example-project/input/1percent/config_eventPTRouter.xml");
		Config config = createConfig();		
		//	ConfigWriter configWriter = new ConfigWriter(config);
		//	configWriter.write("/zhome/81/e/64390/git/ErroneousRAPTORSearch/input/config.xml");
		//	System.exit(-1);
		Config nextConfig = createConfig();

		// Things only read once

		if (args != null && args.length > 0) {
			INPUT_FOLDER = args[0];
			date = args[1];
			if (args.length > 2) {
				cores = Integer.valueOf(args[2]);
				if (args.length > 3) {
					//	endTime = Integer.valueOf(args[3]) * 3600;
				}
				if (args.length > 4) {
					adaptivenessType = AdaptivenessType.valueOf(args[4]);
				}
			}
		}
		
		if(!date.equals("base") && adaptivenessType.equals(AdaptivenessType.PERFECT_EXTENDED)) {
			RunMatsim.searchRadius += RunMatsim.maxBeelineTransferWalk;
			RunMatsim.maxBeelineTransferWalk += RunMatsim.maxBeelineTransferWalk;
		}

		System.out.println("Using " + cores + " cores");

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

		Controler controler = new Controler(scenario);

		// To use the fast pt router (Part 1 of 1)
		controler.addOverridingModule(new SwissRailRaptorModule());


		/*	if (runSanityTests) {
			sanityTests(scenario, staticConfig);
			System.exit(-1);
		}
		 */

		PopulationReader populationReader = new PopulationReader(scenario);
		populationReader.readFile("/work1/s103232/PassengerDelay/OtherInput/PTPlans_CPH_TripBased.xml.gz");
		//populationReader.readFile("/work1/s103232/PassengerDelay/OtherInput/PTPlans_CPH_TripBased_1Trip.xml");
	

		if (cores > scenario.getPopulation().getPersons().size()) {
			cores = scenario.getPopulation().getPersons().size();
			System.out.println("Number of cores reduced to " + cores + " due to lack of agents");
		}

		PassengerDelayPerson[] passengerDelayPersons = new PassengerDelayPerson[scenario.getPopulation().getPersons().size()];
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
			persons[r].addLast(passengerDelayPersons[i]);
		}


		Scenario[] scenarios = new Scenario[cores];
		MyTransitScheduleImpl[] schedules = new MyTransitScheduleImpl[cores];
		for (i = 0; i < cores; i++) {
			scenarios[i] = ScenarioUtils.loadScenario(createConfig());
			schedules[i] = new MyTransitScheduleImpl();
			schedules[i] = addTransitStopFacilitiesFromSchedule(schedules[i], scenarios[i].getTransitSchedule().getFacilities().values());
		}
		MyTransitScheduleImpl schedule = new MyTransitScheduleImpl();
		schedule = addTransitStopFacilitiesFromSchedule(schedule, scenario.getTransitSchedule().getFacilities().values());

		RunMatsim.stopToStopGroup = createStop2StopGroupMap();
		for (MyTransitStopFacilityImpl stopFacility : schedule.getFacilities().values()) {
			facilities.put(stopFacility.getId(), stopFacility);
		}

		

		if (date.equals("base") || adaptivenessType == AdaptivenessType.PERFECT || adaptivenessType == AdaptivenessType.PERFECT_EXTENDED) {
			schedule = CreateBaseTransitSchedule.clearTransitSchedule(schedule);
			schedule = CreateBaseTransitSchedule.addBaseSchedule(schedule, date);
			if(adaptivenessType == AdaptivenessType.PERFECT || adaptivenessType == AdaptivenessType.PERFECT_EXTENDED) {
//				TransitScheduleWriter schedWriter = new TransitScheduleWriter(CreateBaseTransitSchedule.createScheduleFromMyTransitScheduleImpl(schedule));			
//				schedWriter.writeFile("/work1/s103232/PassengerDelay/Diagnostics/RealisedSchedule_" + date + ".xml");
			} else {
				TransitScheduleWriter schedWriter = new TransitScheduleWriter(CreateBaseTransitSchedule.createScheduleFromMyTransitScheduleImpl(schedule));			
				schedWriter.writeFile("/work1/s103232/PassengerDelay/Diagnostics/BaseSchedule.xml");
			}
			RunMatsim.distances = schedule.createShortestPathDistances(pruneByShortestDistances);
			createConstantMaps(schedule.getTransitLines().values());
			performingBaseJob(passengerDelayPersons, persons, scenarios, date, schedules);
		} else {
			schedule = CreateBaseTransitSchedule.clearTransitSchedule(schedule);
			if(adaptivenessType == AdaptivenessType.RIGID){
				// using base maps.
				schedule = CreateBaseTransitSchedule.addBaseSchedule(schedule, "base");
				TransitScheduleWriter schedWriter = new TransitScheduleWriter(CreateBaseTransitSchedule.createScheduleFromMyTransitScheduleImpl(schedule));			
				schedWriter.writeFile("/work1/s103232/PassengerDelay/Diagnostics/rigidSchedule.xml");
				RunMatsim.distances = schedule.createShortestPathDistances(pruneByShortestDistances);
				createConstantMaps(schedule.getTransitLines().values());
				performingBaseJob(passengerDelayPersons, persons, scenarios, "base", schedules);
				clearConstantMaps();
				schedule = CreateBaseTransitSchedule.clearTransitSchedule(schedule);
			}

			schedule = CreateBaseTransitSchedule.addBaseSchedule(schedule, date);
			Gbl.assertIf(areDeparturesNonReversing(schedule.getTransitLines()));
			createConstantMaps(schedule.getTransitLines().values());
			if(!adaptivenessType.equals(AdaptivenessType.RIGID)) {
				RunMatsim.distances = schedule.createShortestPathDistances(pruneByShortestDistances);
			}
		
			System.gc();
			for (stopwatch  = startTime; stopwatch <= endTime; stopwatch += TIMESTEP) {
				long backThen = System.currentTimeMillis();

				Date dateObject = new Date();
				SimpleDateFormat formatter = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
				String dateString = "___" + formatter.format(dateObject) + "___";
				System.out.println("___Stopwatch is now " + intToTimeString(stopwatch) + "___  (reported at "
						+ dateString + ")");
				System.out.print("- Graph building times: ");

				AdvanceJob[] advanceJobs = new AdvanceJob[cores];
				for (int j = 0; j < cores; j++) {
					advanceJobs[j] = new AdvanceJob(stopwatch, persons[j], scenarios[j], schedules[j]);
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
				//	distancesMap.clear();
				//	distancesLocked.set(false);
				//	distancesLock = new CountDownLatch(1);
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

				if ( stopwatch == endTime) { // stopwatch == (6 * 3600) || stopwatch == (8 * 3600) ||
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
						writer.append("AgentId;Type;Time;FromX;FromY;FromString;ToX;ToY;ToString;How;DepartureId\n");
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


	static MyTransitScheduleImpl addTransitStopFacilitiesFromSchedule(MyTransitScheduleImpl schedule, Collection<TransitStopFacility> stops) {
		int counter = 0;
		for (TransitStopFacility stopFacility : stops) {
			MyTransitStopFacilityImpl stop = new MyTransitStopFacilityImpl(Id.create(stopFacility.getId().toString(), MyTransitStopFacilityImpl.class),
					new Coord(stopFacility.getCoord().getX(), stopFacility.getCoord().getY()), counter);
			schedule.addStopFacility(stop);
			counter++;
		}
		if(RunMatsim.numberOfTransitStopFacilities == 0) {
			RunMatsim.numberOfTransitStopFacilities = counter;
		}
		return schedule;
	}


	

	public static ConcurrentHashMap<String, Id<MyTransitStopFacilityImpl>> createStop2StopGroupMap() {
		String inputFile = INPUT_FOLDER + "/OtherInput/NewStops.csv";
		ConcurrentHashMap<String, Id<MyTransitStopFacilityImpl>> output = new ConcurrentHashMap<String,Id<MyTransitStopFacilityImpl>>();
		BufferedReader br;
		try {
			br = new BufferedReader(new FileReader(inputFile));

			String readLine = br.readLine();
			while((readLine = br.readLine()) != null){
				String[] splitLine = readLine.split(";");
				String stopNumber = splitLine[0].split("_")[0];	
				Id<MyTransitStopFacilityImpl> stopGroupId = Id.create(splitLine[3], MyTransitStopFacilityImpl.class);
				output.put(stopNumber,stopGroupId);
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return output;
	}


	private static void performingBaseJob(PassengerDelayPerson[] passengerDelayPersons,
			LinkedList<PassengerDelayPerson>[] persons, Scenario[] scenarios, String date, MyTransitScheduleImpl[] schedules) {
		long backThen = System.currentTimeMillis();

		System.out.print("- Graph building times: ");

		BaseJob[] baseJobs = new BaseJob[cores];
		for (int j = 0; j < cores; j++) {	
			baseJobs[j] = new BaseJob(startTime, persons[j], scenarios[j], date, schedules[j]);
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
					writer = new FileWriter(new File("/work1/s103232/PassengerDelay/Output/" + adaptivenessType.toString() + "/Events_" + date + ".csv"));
				}
				writer.append("AgentId;Type;Time;FromX;FromY;FromString;ToX;ToY;ToString;How;DepartureId\n");
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

	/*
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
		RaptorParametersForPerson parameters = new DefaultRaptorParametersForPerson(config);
		MySwissRailRaptorData data = MySwissRailRaptorData.create(scenario.getTransitSchedule(), staticConfig,
				scenario.getNetwork());
		MySwissRailRaptor raptor = new MySwissRailRaptor(data, parameters); // TODO madsp

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
	 */


	private static boolean areDeparturesNonReversing(Map<Id<MyTransitLineImpl>, MyTransitLineImpl> transitLines) {
		for (MyTransitLineImpl line : transitLines.values()) {
			for (MyTransitRouteImpl route : line.getRoutes().values()) {
				double prevTime = Double.NEGATIVE_INFINITY;
				int counter = 1;
				for (MyTransitRouteStopImpl stop : route.getStops()) {
					double time = stop.getArrivalOffset();
					if (prevTime > time && prevTime - time <= 20 * 3600) {
						System.err.println("@: " + route.getId() + " of line " + line.getId()
						+ " Stop#" + counter + ": " + stop.getStopFacility().getId() + "Arrival error: "
						+ prevTime + " > " + time);
						return false;
					}
					prevTime = time;
					time = stop.getDepartureOffset();
					if (prevTime > time && prevTime - time <= 20 * 3600) {
						System.err.println("@: " + route.getId() + " of line " + line.getId()
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

	private static String intToTimeString(int n) {
		int h = n / 3600;
		n -= h * 3600;
		int m = n / 60;
		n -= m * 60;
		return ((h >= 10) ? h : ("0" + h)) + ":" + ((m >= 10) ? m : ("0" + m)) + ":" + ((n >= 10) ? n : ("0" + n));
	}

	private static void clearConstantMaps(){
		dep2Line.clear();
		arrivalAndDepartureTimesOfDepartureAtStop.clear(); 
		nextStationOfDepartureAtTime.clear();
		if(adaptivenessType == AdaptivenessType.NONADAPTIVE || adaptivenessType == AdaptivenessType.RIGID){
			stop2StopTreeMap.clear();
		}
	}

	private static void createConstantMaps(Collection<MyTransitLineImpl> transitLines) {
		arrivalAndDepartureTimesOfDepartureAtStop = new ConcurrentHashMap<Id<MyDepartureImpl>, ArrDep[]>();
		nextStationOfDepartureAtTime = new ConcurrentHashMap<Id<MyDepartureImpl>, ConcurrentSkipListMap<Integer, Id<MyTransitStopFacilityImpl>>>();
		dep2Line = new ConcurrentHashMap<Id<MyDepartureImpl>,  Id<MyTransitLineImpl>>();


		if(adaptivenessType == AdaptivenessType.NONADAPTIVE || adaptivenessType == AdaptivenessType.RIGID){
			stop2StopTreeMap = new ConcurrentHashMap<Id<MyTransitStopFacilityImpl>, ConcurrentHashMap<Id<MyTransitStopFacilityImpl>, 
					ConcurrentSkipListMap<Integer,NewDepartureBundle>>>();
		}
		for (MyTransitLineImpl line : transitLines) {
			for (MyTransitRouteImpl route : line.getRoutes().values()) {
				for (MyDepartureImpl departure : route.getDepartures()) {
					int depTime = departure.getDepartureTime();
					Id<MyDepartureImpl> depId = departure.getId();
					dep2Line.put(depId, line.getId());
					nextStationOfDepartureAtTime.put(depId,
							new ConcurrentSkipListMap<Integer, Id<MyTransitStopFacilityImpl>>());
					ArrDep[] arrDepArray = new ArrDep[route.getStops().length];

					for (int i = 0; i < route.getStops().length; i++) {
						MyTransitRouteStopImpl stop = route.getStop(i);
						Id<MyTransitStopFacilityImpl> stopId = stop.getStopFacility().getId();
						ArrDep arrDep = new ArrDep (depTime + stop.getArrivalOffset(), depTime + stop.getDepartureOffset());
						arrDepArray[stop.getIndexAlongRoute()] = arrDep;
						nextStationOfDepartureAtTime.get(depId).putIfAbsent(depTime + stop.getArrivalOffset(), stopId);
						if(adaptivenessType == AdaptivenessType.NONADAPTIVE || adaptivenessType == AdaptivenessType.RIGID){
							int j = i +1 ;
							while(j < route.getStops().length){
								MyTransitRouteStopImpl toStop = route.getStop(j);
								Id<MyTransitStopFacilityImpl> toStopId = toStop.getStopFacility().getId();
								if(toStopId == stopId){ //Happens if some stop occurs twice...
									break;
								} else {
									if(!stop2StopTreeMap.containsKey(stopId)){
										stop2StopTreeMap.put(stopId, new ConcurrentHashMap<Id<MyTransitStopFacilityImpl>, 
												ConcurrentSkipListMap<Integer,NewDepartureBundle>>());
									}
									if(!stop2StopTreeMap.get(stopId).containsKey(toStopId)){
										stop2StopTreeMap.get(stopId).put(toStopId, new ConcurrentSkipListMap<Integer,NewDepartureBundle>() );
									}
									NewDepartureBundle newDepartureBundle = new NewDepartureBundle(depId, i, j);
									stop2StopTreeMap.get(stopId).get(toStopId).put(depTime + stop.getDepartureOffset(), newDepartureBundle);
								} 
								j++;
							}
						}
					}
					arrivalAndDepartureTimesOfDepartureAtStop.put(depId, arrDepArray);
				}
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


		// 750. takes 56, whereas 600 takes 39.. This parameter is probably the
		// one that influences computation time the most
		// 600 is 10 minutes walk. Seems legit.

		config.transitRouter().setMaxBeelineWalkConnectionDistance(maxBeelineTransferWalk);
		config.transitRouter().setSearchRadius(searchRadius); 
		config.transitRouter().setAdditionalTransferTime(transferBufferTime);
		config.transitRouter().setExtensionRadius(extensionRadius);
		config.transitRouter().setDirectWalkFactor(walkBeelineDistanceFactor);
		config.plansCalcRoute().clearModeRoutingParams();

		for(String mode : Arrays.asList(TransportMode.walk, TransportMode.transit_walk, TransportMode.non_network_walk)) {
			config.plansCalcRoute().getOrCreateModeRoutingParams(mode).setBeelineDistanceFactor(walkBeelineDistanceFactor);
			config.plansCalcRoute().getOrCreateModeRoutingParams(mode).setTeleportedModeSpeed(walkSpeed);
			ModeParams walkParams = new ModeParams(mode);
			walkParams.setMarginalUtilityOfDistance(walkDistanceUtility);
			walkParams.setMarginalUtilityOfTraveling(walkTimeUtility * 3600.);
			config.planCalcScore().addModeParams(walkParams);
		}

		config.planCalcScore().setPerforming_utils_hr(0);
		config.transit().setTransitModes(ptSubModes);
		for (String mode : ptSubModes) {
			ModeParams params = new ModeParams(mode);
			switch (mode) {
			case MODE_TRAIN:
				params.setMarginalUtilityOfTraveling(trainTimeUtility * 3600.);
				params.setMarginalUtilityOfDistance(trainDistanceUtility * 3600.);
				break;
			case MODE_BUS:
				params.setMarginalUtilityOfTraveling(busTimeUtility * 3600.);
				params.setMarginalUtilityOfDistance(busDistanceUtility * 3600.);
				break;
			case MODE_METRO:
				params.setMarginalUtilityOfTraveling(metroTimeUtility * 3600.);
				params.setMarginalUtilityOfDistance(metroDistanceUtility * 3600.);
				break;
			case MODE_S_TRAIN:
				params.setMarginalUtilityOfTraveling(sTrainTimeUtility * 3600.);
				params.setMarginalUtilityOfDistance(sTrainDistanceUtility * 3600.);
				break;
			case MODE_LOCAL_TRAIN:
				params.setMarginalUtilityOfTraveling(localTrainTimeUtility * 3600.);
				params.setMarginalUtilityOfDistance(localTrainDistanceUtility * 3600.);
				break;
			default:
				break;
			}
			config.planCalcScore().addModeParams(params);
		}
		config.planCalcScore().setMarginalUtlOfWaitingPt_utils_hr(waitTimeUtility * 3600.);
		config.planCalcScore().setUtilityOfLineSwitch(transferUtility);

		config.travelTimeCalculator().setTraveltimeBinSize(TIMESTEP);
		config.qsim().setStartTime(RunMatsim.startTime);
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

	/*	private static double determineMinCostPerMeter(Collection<MyTransitLineImpl> transitLines, PlanCalcScoreConfigGroup planCalcScore) {
		double minCostPerMeter = Double.POSITIVE_INFINITY;
		for(MyTransitLineImpl line : transitLines) {
			for(TransitRoute route : line.getRoutes().values()) {
				double modeDisutility = -planCalcScore.getOrCreateModeParams(route.getTransportMode()).getMarginalUtilityOfTraveling();
				List<TransitRouteStop> stops = route.getStops();
				for(int i = 1; i < stops.size(); i++) {
					double distance = CoordUtils.calcProjectedEuclideanDistance(stops.get(i-1).getStopFacility().getCoord(),
							stops.get(i).getStopFacility().getCoord());
					double time = stops.get(i).getArrivalOffset() - stops.get(i-1).getDepartureOffset();
					double costPerMeter = ( time / 3600. * modeDisutility) / distance;
					if(costPerMeter < minCostPerMeter) {
						minCostPerMeter = costPerMeter;
					}
				}
			}
		}
		if( minCostPerMeter < 0.2/1000.) {
			minCostPerMeter = 0.2/1000.;
		}
		return minCostPerMeter;
	}*/

}
