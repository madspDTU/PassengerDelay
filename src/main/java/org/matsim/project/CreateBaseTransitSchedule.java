package org.matsim.project;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.NetworkWriter;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Route;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.routes.LinkNetworkRouteFactory;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.population.routes.RouteFactories;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleFactory;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.visum.VisumNetwork.Stop;


public class CreateBaseTransitSchedule {

	static Link dummyLink;
	static NetworkRoute networkRoute;
	//static String INPUT_FOLDER = "c:/workAtHome/PassengerDelay";
	static String INPUT_FOLDER = "/work1/s103232/PassengerDelay";
	private static double timeBuffer = 2 * RunMatsim.TIMESTEP;
	private static double maxTripTime = 5*3600;

	//Coord fromCoord = new Coord(719991.463908,6174840.523082);	
	//Coord toCoord = new Coord(723728.644952,6180425.027057);


	//NOT NEEDED

	public static void init(){
		Config config = ConfigUtils.createConfig();
		Scenario scenario = ScenarioUtils.createScenario(config);
		Node dummyNode1 = scenario.getNetwork().getFactory().createNode(Id.create("DummyNode1",Node.class), new Coord(719991.463908,6174840.523082));
		Node dummyNode2 = scenario.getNetwork().getFactory().createNode(Id.create("DummyNode2",Node.class), new Coord(723728.644952,6180425.027057));
		dummyLink = scenario.getNetwork().getFactory().createLink(Id.create("DummyLink",Link.class),dummyNode1,dummyNode2);
		scenario.getNetwork().addNode(dummyNode1);
		scenario.getNetwork().addNode(dummyNode2);
		scenario.getNetwork().addLink(dummyLink);

		networkRoute = (NetworkRoute) new LinkNetworkRouteFactory().createRoute(dummyLink.getId(), dummyLink.getId());

		scenario = createTransitInfrastructure(scenario);
		scenario = addBaseSchedule(scenario);

		//		TransitScheduleWriter writer = new TransitScheduleWriter(scenario.getTransitSchedule());
		//		writer.writeFile(INPUT_FOLDER + "/BaseSchedules/BaseSchedule.xml.gz");
		//		scenario = clearTransitSchedule(scenario);
		//		writer.writeFile(INPUT_FOLDER + "/BaseSchedules/BaseSchedule_InfrastructureOnly.xml.gz");
		//		NetworkWriter networkWriter = new NetworkWriter(scenario.getNetwork());
		//		networkWriter.write(INPUT_FOLDER + "/OtherInput/network.xml.gz");
	}

	public static Scenario clearTransitSchedule(Scenario scenario) {
		LinkedList<TransitLine> linesToRemove = new LinkedList<TransitLine>();
		for(TransitLine transitLine : scenario.getTransitSchedule().getTransitLines().values()){
			linesToRemove.add(transitLine);
		}
		for (TransitLine transitLine : linesToRemove){
			scenario.getTransitSchedule().removeTransitLine(transitLine);
		}
		return scenario;
	}

	private static Scenario addBaseSchedule(Scenario scenario) {
		scenario = addTrainSchedule(scenario, INPUT_FOLDER + "/BaseSchedules/TrainSchedule.csv");
		scenario = addBusSchedule(scenario, INPUT_FOLDER + "/BaseSchedules/BusSchedule.csv");
		createMetroSchedule(scenario);
		createLocalTrainSchedule(scenario);
		return scenario;
	}

	private static void createLocalTrainSchedule(Scenario scenario) {
		scenario = addTrainSchedule(scenario, INPUT_FOLDER + "/OtherInput/lokalbaner.csv");
		Scenario newScenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		HashSet<TransitStopFacility> stops = new HashSet<TransitStopFacility>();
		for(TransitLine line : scenario.getTransitSchedule().getTransitLines().values()){
			long departureNumber = -1;
			boolean isLocalTrain = false;
			for(TransitRoute route : line.getRoutes().values()){
				for(Departure departure : route.getDepartures().values()){
					departureNumber = Long.parseLong(String.valueOf(departure.getId()).split("X")[0]);
					if(departureNumber > 100000 && departureNumber < 1000000){
						isLocalTrain = true;
					} else {
						isLocalTrain = false;
					}
					break;
				}
				for(TransitRouteStop stop :route.getStops()){
					stops.add(stop.getStopFacility());
				}
			}
			if(isLocalTrain){
				newScenario.getTransitSchedule().addTransitLine(line);
			}
		}

		for(TransitStopFacility stop : stops){
			newScenario.getTransitSchedule().addStopFacility(stop);
		}

		TransitScheduleWriter writer = new TransitScheduleWriter(newScenario.getTransitSchedule());
		writer.writeFile(INPUT_FOLDER + "/BaseSchedules/LocalTrainSchedule.xml.gz");
	}

	public static Scenario addStaticSchedule(Scenario scenario, String inputFile, double stopwatch){
		return addStaticSchedule(scenario,  inputFile, stopwatch - timeBuffer, stopwatch + maxTripTime);
	}
	
	public static Scenario addStaticSchedule(Scenario scenario, String inputFile){
		return addStaticSchedule(scenario,  inputFile, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
	}
	
	private static Scenario addStaticSchedule(Scenario newScenario, String inputFile, double minTime, double maxTime){
		TransitSchedule schedule = newScenario.getTransitSchedule();
		Scenario importScenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		TransitScheduleReader reader = new TransitScheduleReader(importScenario);
		reader.readFile(inputFile);
		for(TransitLine line : importScenario.getTransitSchedule().getTransitLines().values()){
			LinkedList<TransitRoute> routesToRemove = new LinkedList<TransitRoute>();
			for(TransitRoute route : line.getRoutes().values()){
				double routeDuration = route.getStops().get(route.getStops().size()-1).getDepartureOffset();
				LinkedList<Departure> departuresToRemove = new LinkedList<Departure>();
				for(Departure departure : route.getDepartures().values()){
					if(departure.getDepartureTime() + routeDuration < minTime ||
							departure.getDepartureTime() > maxTime ){
						departuresToRemove.add(departure);
					}

				}
				for(Departure departure : departuresToRemove){
					route.removeDeparture(departure);
				}
				if(route.getDepartures().isEmpty()){
					routesToRemove.add(route);
				}
			}
			for(TransitRoute route : routesToRemove){
				line.removeRoute(route);
			}
			if(!line.getRoutes().isEmpty()){
				schedule.addTransitLine(line);
			}
		}
		return newScenario;
	}

	public static void createMetroSchedule(Scenario scenario) {

		TransitSchedule oldSchedule = scenario.getTransitSchedule();
		TransitSchedule newSchedule = ScenarioUtils.createScenario(ConfigUtils.createConfig()).getTransitSchedule();
		TransitScheduleFactory newFac = newSchedule.getFactory();

		int[] breaks = {0, 5, 7, 9, 14, 18, 24, 27}; // Creating
		int[] frequencies = {20, 6, 4, 6, 4, 6, 20};


		String[] M1Stops = {"VAN", "FL", "LIT", "SOT", "FB", "FOR",
				"KN", "KGN", "KHC", "ISB", "UNI","KHS","BC","oRE","VEA"};
		int[] M1TravelTimes = {0 * 60, 1 * 60, 2 * 60,
				4 * 60, 6 * 60, 7 * 60, 9 * 60, 11 * 60, 12 * 60, 14 * 60, 16 * 60, 18 * 60, 20 * 60, 21 * 60, 23 * 60};
		String[] M2Stops = {"VAN", "FL", "LIT", "SOT", "FB", "FOR",
				"KN", "KGN", "KHC", "AMB","LGP","oSV","AMS","FEo","KSA","LUFT"};
		int[] M2TravelTimes = {0 * 60, 1 * 60,
				2 * 60, 4 * 60, 6 * 60, 7 * 60, 9 * 60, 11 * 60, 12 * 60, 14 * 60, 16 * 60, 18 * 60, 19 * 60, 21 * 60,
				23 * 60, 24 * 60};

		//M1_VAN_VEA
		TransitLine line = newFac.createTransitLine(Id.create("M1",TransitLine.class));
		List<TransitRouteStop> stops = new LinkedList<TransitRouteStop>();
		for(int i = 0; i< M1Stops.length; i++){
			TransitStopFacility stop = oldSchedule.getFacilities().get(Id.create(M1Stops[i],TransitStopFacility.class));
			if(!newSchedule.getFacilities().containsKey(stop.getId())){
				TransitStopFacility newStop = newFac.createTransitStopFacility(stop.getId(), stop.getCoord(), true);
				newStop.setLinkId(dummyLink.getId());
				newSchedule.addStopFacility(newStop);
			}
			TransitRouteStop transitRouteStop = 
					newFac.createTransitRouteStop(stop, M1TravelTimes[i], M1TravelTimes[i]);
			stops.add(transitRouteStop);
		}
		TransitRoute route = newFac.createTransitRoute(Id.create("M1_VAN_VEA",TransitRoute.class),
				networkRoute, stops, "metro");
		for (int j = 0; j < frequencies.length; j++) {
			for (int k = breaks[j] * 3600; k < breaks[j + 1] * 3600; k += frequencies[j] * 60) {
				int time = k;
				Departure departure = newFac.createDeparture(
						Id.create(110000000 + time, Departure.class), time);
				route.addDeparture(departure);
			}
		}
		line.addRoute(route);


		//M1_VEA_VAN
		stops.clear();
		for(int i = M1Stops.length-1; i >= 0; i--){
			TransitStopFacility stop = oldSchedule.getFacilities().get(Id.create(M1Stops[i],TransitStopFacility.class));
			int time = M1TravelTimes[M1TravelTimes.length-1] - M1TravelTimes[i];
			TransitRouteStop transitRouteStop = 
					newFac.createTransitRouteStop(stop, time, time);
			stops.add(transitRouteStop);
		}
		route = newFac.createTransitRoute(
				Id.create("M1_VEA_VAN",TransitRoute.class),networkRoute, stops, "metro");
		for (int j = 0; j < frequencies.length; j++) {
			for (int k = breaks[j] * 3600; k < breaks[j + 1] * 3600; k += frequencies[j] * 60) {
				int time = k + frequencies[j]/2*60 + 60;
				Departure departure = newFac.createDeparture(
						Id.create(120000000 + time,Departure.class),time);
				route.addDeparture(departure);
			}
		}
		line.addRoute(route);
		newSchedule.addTransitLine(line);


		//M2_VAN_LUFT
		line = newFac.createTransitLine(Id.create("M2",TransitLine.class));
		stops.clear();
		for(int i = 0; i< M2Stops.length; i++){
			TransitStopFacility stop = oldSchedule.getFacilities().get(Id.create(M2Stops[i],TransitStopFacility.class));
			if(!newSchedule.getFacilities().containsKey(stop.getId())){
				TransitStopFacility newStop = newFac.createTransitStopFacility(stop.getId(), stop.getCoord(), true);
				newStop.setLinkId(dummyLink.getId());
				newSchedule.addStopFacility(newStop);
			}
			TransitRouteStop transitRouteStop = 
					newFac.createTransitRouteStop(stop, M2TravelTimes[i], M2TravelTimes[i]);
			stops.add(transitRouteStop);
		}
		route = newFac.createTransitRoute(Id.create("M2_VAN_LUFT",TransitRoute.class),
				networkRoute, stops, "metro");
		for (int j = 0; j < frequencies.length; j++) {
			for (int k = breaks[j] * 3600; k < breaks[j + 1] * 3600; k += frequencies[j] * 60) {
				int time = k + frequencies[j]/2*60;
				Departure departure = newFac.createDeparture(
						Id.create(210000000 + time, Departure.class), time);
				route.addDeparture(departure);
			}
		}
		line.addRoute(route);


		//M2_LUFT_VAN
		stops.clear();
		for(int i = M2Stops.length-1; i >= 0; i--){
			TransitStopFacility stop = oldSchedule.getFacilities().get(Id.create(M2Stops[i], TransitStopFacility.class));
			int time = M2TravelTimes[M2TravelTimes.length-1] - M2TravelTimes[i];
			TransitRouteStop transitRouteStop = 
					newFac.createTransitRouteStop(stop, time, time);
			stops.add(transitRouteStop);
		}
		route = newFac.createTransitRoute(
				Id.create("M2_LUFT_VAN",TransitRoute.class),networkRoute, stops, "metro");

		stops.clear();
		for (int j = 0; j < frequencies.length; j++) {
			for (int k = breaks[j] * 3600; k < breaks[j + 1] * 3600; k += frequencies[j] * 60){
				int time = k;
				Departure departure = newFac.createDeparture(
						Id.create(220000000 + time, Departure.class), time);
				route.addDeparture(departure);
			}
		}
		line.addRoute(route);
		newSchedule.addTransitLine(line);

		TransitScheduleWriter writer = new TransitScheduleWriter(newSchedule);
		writer.writeFile(INPUT_FOLDER + "/BaseSchedules/MetroSchedule.xml.gz");
	}

	static Scenario addBusSchedule(Scenario scenario, String fileName) {
		TransitSchedule schedule = scenario.getTransitSchedule();

		BufferedReader br;
		try {
			br = new BufferedReader(new FileReader(fileName));
			String readLine = br.readLine();
			TransitLine line = null;
			List<TransitRouteStop> stops = new LinkedList<TransitRouteStop>();
			Id<TransitRoute> routeId = null;
			Id<TransitRoute> prevRouteId = Id.create(-1, TransitRoute.class);
			double offset = -1;

			HashMap<String,TransitRoute> routeMap = new HashMap<String,TransitRoute>();
			HashSet<Id<TransitStopFacility>> transitStopFacilityIds = new HashSet<Id<TransitStopFacility>>();
			String suffix = "";
			while((readLine = br.readLine()) != null){
				String[] splitLine = readLine.split(";");
				Id<TransitLine> lineId = Id.create(splitLine[4], TransitLine.class);
				routeId = Id.create(splitLine[0], TransitRoute.class);
				double arr = Math.round(Double.valueOf(splitLine[2]));
				double dep = Math.round(Double.valueOf(splitLine[3]));
				TransitStopFacility stopFacility = schedule.getFacilities().get(Id.create(splitLine[1], TransitStopFacility.class));
				boolean isStopDuplicate = transitStopFacilityIds.contains(stopFacility.getId());
				if(routeId != prevRouteId ||  isStopDuplicate){
					// Split lines 
					if(routeId == prevRouteId && isStopDuplicate){ // Happens in the middle somewhere
						suffix+="X"; //All parts except the last will get k X's added.
						prevRouteId = Id.create(prevRouteId.toString() + suffix, TransitRoute.class);
					} else {
						suffix = "";
					}
					transitStopFacilityIds.clear();
					if(!stops.isEmpty()){
						TransitRoute route;
						String routeString = createRouteString(line.getId(), stops);
						if(!routeMap.containsKey(routeString)){
							route = scenario.getTransitSchedule().getFactory().createTransitRoute(prevRouteId,networkRoute,stops, "bus");	
							routeMap.put(routeString, route);
							line.addRoute(route);
						} else {
							route = routeMap.get(routeString);
						}
						route.addDeparture(scenario.getTransitSchedule().getFactory().createDeparture(
								Id.create(prevRouteId.toString(), Departure.class), offset));
						stops.clear();
					}
					offset = arr;
					if(!schedule.getTransitLines().containsKey(lineId)){
						line = schedule.getFactory().createTransitLine(lineId);
						schedule.addTransitLine(line);
					} else {
						line = schedule.getTransitLines().get(lineId);
					}
				}
				if(stopFacility != null){
					if(arr < offset){
						arr += 24*3600;
					}
					if(dep < offset){
						dep += 24*3600;
					}
					TransitRouteStop stop = scenario.getTransitSchedule().getFactory().createTransitRouteStop(
							stopFacility,arr-offset, dep-offset);
					stops.add(stop);
					transitStopFacilityIds.add(stopFacility.getId());
				}
				prevRouteId = routeId;
			}
			if(!stops.isEmpty()){
				TransitRoute route = scenario.getTransitSchedule().getFactory().createTransitRoute(routeId,networkRoute,stops, "bus");
				// THIS MUST BE CHANGED!
				route.addDeparture(schedule.getFactory().createDeparture(Id.create(prevRouteId.toString(), Departure.class), offset));
				line.addRoute(route);
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return scenario;
	}


	private static String createRouteString(Id<TransitLine> lineId, List<TransitRouteStop> stops) {
		String s = lineId.toString();
		for(TransitRouteStop stop : stops){
			s += "_" + stop.getStopFacility().getId().toString();
			s += "_" + stop.getArrivalOffset();
			s += "_" + stop.getDepartureOffset();
		}
		return s;
	}

	static Scenario addTrainSchedule(Scenario scenario, String fileName){
		TransitSchedule schedule = scenario.getTransitSchedule();
		try {
			BufferedReader br = new BufferedReader(new FileReader(fileName));
			String readLine = br.readLine();
			TransitLine line = null;
			List<TransitRouteStop> stops = new LinkedList<TransitRouteStop>();
			Id<TransitRoute> prevRouteId = Id.create("-1", TransitRoute.class);
			Id<TransitRoute> routeId = null;
			double arrival = -1;
			double departure = -1;
			double offset = -1;
			Id<TransitLine> prevLineId = null;
			while((readLine = br.readLine()) != null){
				String[] splitLine = readLine.split(";");
				Id<TransitLine> lineId = Id.create(splitLine[0], TransitLine.class);
				double time = Math.round(Double.valueOf(splitLine[1]));
				routeId = Id.create(splitLine[2], TransitRoute.class);
				TransitStopFacility stopFacility = schedule.getFacilities().get(Id.create(splitLine[3], TransitStopFacility.class));
				String moveType = splitLine[4];
				if(!schedule.getTransitLines().containsKey(lineId)){
					line = schedule.getFactory().createTransitLine(lineId);
					schedule.addTransitLine(line);
				}
				if(prevLineId != null){
					line = schedule.getTransitLines().get(prevLineId);
				}

				if(!prevRouteId.equals(routeId)){
					if(!stops.isEmpty()){
						TransitRoute route = scenario.getTransitSchedule().getFactory().createTransitRoute(prevRouteId,networkRoute,stops, "train");
						route.addDeparture(schedule.getFactory().createDeparture( 
								Id.create(prevRouteId.toString(), Departure.class), offset));
						line.addRoute(route);
						stops.clear();
					}
					offset = time;
				}
				if(moveType.equals("I")){
					arrival = time;
					departure = arrival;
				} else {
					departure = time;
					if(arrival == -1){
						arrival = departure;
					}
					if(stopFacility != null){
						if(arrival < offset){
							arrival += 2400*3600;
						}
						if(departure < offset){
							departure += 2400*3600;
						}
						TransitRouteStop stop = scenario.getTransitSchedule().getFactory().createTransitRouteStop(
								stopFacility,arrival-offset, departure-offset);
						stops.add(stop);
					}
					arrival = -1;
				}
				prevLineId = lineId;
				prevRouteId = routeId;
			}

			//adding the last departure of the train schedule...
			if(!stops.isEmpty()){
				TransitRoute route = scenario.getTransitSchedule().getFactory().createTransitRoute(prevRouteId,networkRoute,stops, "train");
				route.addDeparture(schedule.getFactory().createDeparture( 
						Id.create(prevRouteId.toString(), Departure.class), offset));
				line.addRoute(route);
				stops.clear();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return scenario;
	}

	public static Scenario createTransitInfrastructure(Scenario scenario){

		TransitSchedule schedule = scenario.getTransitSchedule();

		for(String inputFile : new String[]{INPUT_FOLDER + "/OtherInput/stations.csv",
				INPUT_FOLDER + "/OtherInput/busStops.csv"}){
			BufferedReader br;
			try {
				br = new BufferedReader(new FileReader(inputFile));

				String readLine = br.readLine();
				while((readLine = br.readLine()) != null){
					String[] splitLine = readLine.split(";");
					Id<TransitStopFacility> id = Id.create(splitLine[0], TransitStopFacility.class);
					double x = Double.valueOf(splitLine[1]);
					double y = Double.valueOf(splitLine[2]);
					Coord coord = new Coord(x,y);
					TransitStopFacility stop = schedule.getFactory().createTransitStopFacility(id, coord, false);
					stop.setLinkId(dummyLink.getId());
					schedule.addStopFacility(stop);
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return scenario;
	}

}
