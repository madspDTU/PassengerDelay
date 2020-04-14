package org.matsim.project;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.routes.LinkNetworkRouteFactory;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.project.pt.MyDepartureImpl;
import org.matsim.project.pt.MyTransitLineImpl;
import org.matsim.project.pt.MyTransitRouteImpl;
import org.matsim.project.pt.MyTransitRouteStopImpl;
import org.matsim.project.pt.MyTransitScheduleImpl;
import org.matsim.project.pt.MyTransitStopFacilityImpl;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;


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

	}

	public static MyTransitScheduleImpl clearTransitSchedule(MyTransitScheduleImpl schedule) {
		schedule.getTransitLines().clear();
		return schedule;
	}


	public static MyTransitScheduleImpl addBaseSchedule(MyTransitScheduleImpl schedule, String date){
		if(date.equals("base")){
			schedule = addTrainSchedule(schedule, INPUT_FOLDER + "/BaseSchedules/TrainSchedule.csv");
			schedule = addBusSchedule(schedule, INPUT_FOLDER + "/BaseSchedules/BusSchedule.csv");
			schedule = addStaticSchedule(schedule, INPUT_FOLDER + "/BaseSchedules/MetroSchedule.xml");
			schedule = addStaticSchedule(schedule, RunMatsim.INPUT_FOLDER + "/BaseSchedules/LocalTrainSchedule.xml");
		} else {
			schedule = addTrainSchedule(schedule,INPUT_FOLDER + "/Disaggregate/Train/" + date + "/AVLSchedule_" + date + ".csv");
			schedule = addBusSchedule(schedule, INPUT_FOLDER + "/Disaggregate/Bus/" + date + "/AVLSchedule_" + date + ".csv");	
			schedule = addStaticSchedule(schedule, INPUT_FOLDER + "/BaseSchedules/MetroSchedule.xml");
			schedule = addStaticSchedule(schedule, RunMatsim.INPUT_FOLDER + "/BaseSchedules/LocalTrainSchedule.xml");
		}
		return schedule;
	}

	public static MyTransitScheduleImpl addSchedule(MyTransitScheduleImpl schedule, String date, int stopwatch) {
		schedule = addTrainSchedule(schedule, RunMatsim.INPUT_FOLDER + "/Disaggregate/Train/" + date + "/DisaggregateSchedule_" + 
				date + "_" + stopwatch + ".csv");
		schedule = addBusSchedule(schedule, RunMatsim.INPUT_FOLDER + "/Disaggregate/Bus/" + date + "/DisaggregateBusSchedule_" + 
				date + "_" + stopwatch + ".csv");
		schedule = addStaticSchedule(schedule, INPUT_FOLDER + "/BaseSchedules/MetroSchedule.xml");
		schedule = addStaticSchedule(schedule, RunMatsim.INPUT_FOLDER + "/BaseSchedules/LocalTrainSchedule.xml");
		return schedule;
	}

	public static void createAndWriteLocalTrainSchedule(MyTransitScheduleImpl schedule) {
		schedule = addTrainSchedule(schedule, INPUT_FOLDER + "/OtherInput/lokalbaner_CorrectFormat.csv");
		TransitSchedule matsimSchedule = createScheduleFromMyTransitScheduleImpl(schedule);
		TransitScheduleWriter writer = new TransitScheduleWriter(matsimSchedule);
		writer.writeFile(INPUT_FOLDER + "/BaseSchedules/LocalTrainSchedule.xml");
	}

	public static MyTransitScheduleImpl addStaticSchedule(MyTransitScheduleImpl schedule, String inputFile, double stopwatch){
		return addStaticSchedule(schedule,  inputFile, stopwatch - timeBuffer, stopwatch + maxTripTime);
	}

	public static MyTransitScheduleImpl addStaticSchedule(MyTransitScheduleImpl schedule, String inputFile){
		return addStaticSchedule(schedule,  inputFile, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
	}

	private static MyTransitScheduleImpl addStaticSchedule(MyTransitScheduleImpl schedule, String inputFile, double minTime, double maxTime){
		Scenario importScenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		TransitScheduleReader reader = new TransitScheduleReader(importScenario);
		reader.readFile(inputFile);
		MyTransitScheduleImpl mySchedule = addToMyTransitScheduleImplFromSchedule(schedule, importScenario.getTransitSchedule().getTransitLines().values());

		for(Iterator<MyTransitLineImpl> it = mySchedule.getTransitLines().values().iterator(); it.hasNext(); ){
			MyTransitLineImpl line = it.next(); 
			LinkedList<MyTransitRouteImpl> routesToRemove = new LinkedList<MyTransitRouteImpl>();
			for(MyTransitRouteImpl route : line.getRoutes().values()){
				double routeDuration = route.getLastStop().getDepartureOffset();
				int n = route.getDepartures().size() - 1;
				while(n >= 0) {
					MyDepartureImpl departure = route.getDepartures().get(n);
					if(departure.getDepartureTime() + routeDuration < minTime ||
							departure.getDepartureTime() > maxTime ){
						route.removeDeparture(n);
					}
					n--;
				}
				if(route.getDepartures().isEmpty()){
					routesToRemove.add(route);
				}
			}
			for(MyTransitRouteImpl route : routesToRemove){
				line.removeRoute(route);
			}
			if(line.getRoutes().isEmpty()){
				it.remove();
			}
		}
		return schedule;
	}

	private static MyTransitScheduleImpl addToMyTransitScheduleImplFromSchedule(MyTransitScheduleImpl schedule, Collection<TransitLine> lines) {
		for(TransitLine oldLine : lines) {
			MyTransitLineImpl line = new MyTransitLineImpl(Id.create(oldLine.getId().toString(), MyTransitLineImpl.class));
			for(TransitRoute oldRoute : oldLine.getRoutes().values()) {
				LinkedList<MyTransitRouteStopImpl> stops = new LinkedList<MyTransitRouteStopImpl>();
				for(TransitRouteStop oldStop : oldRoute.getStops()) {
					MyTransitStopFacilityImpl stopFac = schedule.getFacilities().get(
							Id.create(oldStop.getStopFacility().getId().toString(), MyTransitStopFacilityImpl.class)); 
					MyTransitRouteStopImpl stop = new MyTransitRouteStopImpl(stopFac, 
							(int) Math.round(oldStop.getArrivalOffset()), (int) Math.round(oldStop.getDepartureOffset()), stops.size());
					stops.add(stop);
				}
				MyTransitRouteImpl route = new MyTransitRouteImpl(Id.create(oldRoute.getId().toString(), MyTransitRouteImpl.class), 
						stops, oldRoute.getTransportMode());
				for(Departure oldDeparture : oldRoute.getDepartures().values()) {
					MyDepartureImpl departure = new MyDepartureImpl(Id.create(oldDeparture.getId().toString(), MyDepartureImpl.class), 
							(int) Math.round(oldDeparture.getDepartureTime()));
					route.addDeparture(departure);
				}
				line.addRoute(route);
			}
			schedule.addTransitLine(line);
		}
		return schedule;
	}

	public static TransitSchedule createScheduleFromMyTransitScheduleImpl(MyTransitScheduleImpl oldSchedule) {
		TransitSchedule schedule = ScenarioUtils.createScenario(ConfigUtils.createConfig()).getTransitSchedule();
		for(MyTransitStopFacilityImpl stop : oldSchedule.getFacilities().values()) {
			TransitStopFacility newStop = schedule.getFactory().createTransitStopFacility(Id.create(stop.getId().toString(),TransitStopFacility.class),
					new Coord(stop.getCoord().getX(), stop.getCoord().getY()), false);
			schedule.addStopFacility(newStop);
		}

		for(MyTransitLineImpl oldLine : oldSchedule.getTransitLines().values()) {
			TransitLine line = schedule.getFactory().createTransitLine(Id.create(oldLine.getId().toString(), TransitLine.class));
			for(MyTransitRouteImpl oldRoute : oldLine.getRoutes().values()) {
				List<TransitRouteStop> stops = new LinkedList<TransitRouteStop>();
				for(MyTransitRouteStopImpl oldStop : oldRoute.getStops()) {
					TransitStopFacility stopFac = schedule.getFacilities().get(
							Id.create(oldStop.getStopFacility().getId().toString(), TransitStopFacility.class)); 
					TransitRouteStop stop = schedule.getFactory().createTransitRouteStop(stopFac, 
							oldStop.getArrivalOffset(), oldStop.getDepartureOffset());
					stops.add(stop);
				}
				TransitRoute route = schedule.getFactory().createTransitRoute(Id.create(oldRoute.getId().toString(), TransitRoute.class), null,
						stops, oldRoute.getTransportMode());
				for(MyDepartureImpl oldDeparture : oldRoute.getDepartures()) {
					Departure departure = schedule.getFactory().createDeparture(Id.create(oldDeparture.getId().toString(), Departure.class), 
							oldDeparture.getDepartureTime());
					route.addDeparture(departure);
				}
				line.addRoute(route);
			}
			schedule.addTransitLine(line);
		}
		return schedule;
	}

	public static void createAndWriteMetroSchedule(MyTransitScheduleImpl oldSchedule) {

		MyTransitScheduleImpl newSchedule = new MyTransitScheduleImpl();

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
		MyTransitLineImpl line = new MyTransitLineImpl(Id.create("M1",MyTransitLineImpl.class));
		LinkedList<MyTransitRouteStopImpl> stops = new LinkedList<MyTransitRouteStopImpl>();
		for(int i = 0; i< M1Stops.length; i++){
			MyTransitStopFacilityImpl stop = oldSchedule.getFacilities().get(Id.create(M1Stops[i],MyTransitStopFacilityImpl.class));
//			if(!newSchedule.getFacilities().containsKey(stop.getId())){
//				MyTransitStopFacilityImpl newStop = new MyTransitStopFacilityImpl(stop.getId(), stop.getCoord());
//				newSchedule.addStopFacility(newStop);
//			}
			MyTransitRouteStopImpl transitRouteStop = new MyTransitRouteStopImpl(stop, M1TravelTimes[i], M1TravelTimes[i], stops.size());
			stops.add(transitRouteStop);
		}
		MyTransitRouteImpl route = new MyTransitRouteImpl(Id.create("M1_VAN_VEA",MyTransitRouteImpl.class), stops, RunMatsim.MODE_METRO);
		for (int j = 0; j < frequencies.length; j++) {
			for (int k = breaks[j] * 3600; k < breaks[j + 1] * 3600; k += frequencies[j] * 60) {
				int time = k;
				MyDepartureImpl departure = new MyDepartureImpl(Id.create(110000000 + time, MyDepartureImpl.class), time);
				route.addDeparture(departure);
			}
		}
		line.addRoute(route);


		//M1_VEA_VAN
		stops.clear();
		for(int i = M1Stops.length-1; i >= 0; i--){
			MyTransitStopFacilityImpl stop = oldSchedule.getFacilities().get(Id.create(M1Stops[i],MyTransitStopFacilityImpl.class));
			int time = M1TravelTimes[M1TravelTimes.length-1] - M1TravelTimes[i];
			MyTransitRouteStopImpl transitRouteStop =  new MyTransitRouteStopImpl(stop, time, time, stops.size());
			stops.add(transitRouteStop);
		}
		route = new MyTransitRouteImpl(Id.create("M1_VEA_VAN",MyTransitRouteImpl.class), stops, RunMatsim.MODE_METRO);
		for (int j = 0; j < frequencies.length; j++) {
			for (int k = breaks[j] * 3600; k < breaks[j + 1] * 3600; k += frequencies[j] * 60) {
				int time = k + frequencies[j]/2*60 + 60;
				MyDepartureImpl departure = new MyDepartureImpl(Id.create(120000000 + time,MyDepartureImpl.class),time);
				route.addDeparture(departure);
			}
		}
		line.addRoute(route);
		newSchedule.addTransitLine(line);


		//M2_VAN_LUFT
		line = new MyTransitLineImpl(Id.create("M2",MyTransitLineImpl.class));
		stops.clear();
		for(int i = 0; i< M2Stops.length; i++){
			MyTransitStopFacilityImpl stop = oldSchedule.getFacilities().get(Id.create(M2Stops[i],MyTransitStopFacilityImpl.class));
//			if(!newSchedule.getFacilities().containsKey(stop.getId())){
//				MyTransitStopFacilityImpl newStop = new MyTransitStopFacilityImpl(stop.getId(), stop.getCoord());
//				newSchedule.addStopFacility(newStop);
//			}
			MyTransitRouteStopImpl transitRouteStop = new MyTransitRouteStopImpl(stop, M2TravelTimes[i], M2TravelTimes[i], stops.size());
			stops.add(transitRouteStop);
		}
		route = new MyTransitRouteImpl(Id.create("M2_VAN_LUFT",MyTransitRouteImpl.class),stops, RunMatsim.MODE_METRO);
		for (int j = 0; j < frequencies.length; j++) {
			for (int k = breaks[j] * 3600; k < breaks[j + 1] * 3600; k += frequencies[j] * 60) {
				int time = k + frequencies[j]/2*60;
				MyDepartureImpl departure = new MyDepartureImpl(Id.create(210000000 + time, MyDepartureImpl.class), time);
				route.addDeparture(departure);
			}
		}
		line.addRoute(route);


		//M2_LUFT_VAN
		stops.clear();
		for(int i = M2Stops.length-1; i >= 0; i--){
			MyTransitStopFacilityImpl stop = oldSchedule.getFacilities().get(Id.create(M2Stops[i], MyTransitStopFacilityImpl.class));
			int time = M2TravelTimes[M2TravelTimes.length-1] - M2TravelTimes[i];
			MyTransitRouteStopImpl transitRouteStop = new MyTransitRouteStopImpl(stop, time, time, stops.size());
			stops.add(transitRouteStop);
		}
		route = new MyTransitRouteImpl(Id.create("M2_LUFT_VAN",MyTransitRouteImpl.class), stops, RunMatsim.MODE_METRO);

		stops.clear();
		for (int j = 0; j < frequencies.length; j++) {
			for (int k = breaks[j] * 3600; k < breaks[j + 1] * 3600; k += frequencies[j] * 60){
				int time = k;
				MyDepartureImpl departure = new MyDepartureImpl(Id.create(220000000 + time, MyDepartureImpl.class), time);
				route.addDeparture(departure);
			}
		}
		line.addRoute(route);
		newSchedule.addTransitLine(line);


		TransitSchedule matsimSchedule = createScheduleFromMyTransitScheduleImpl(newSchedule);
		TransitScheduleWriter writer = new TransitScheduleWriter(matsimSchedule);
		writer.writeFile(INPUT_FOLDER + "/BaseSchedules/MetroSchedule.xml");
	}

	static MyTransitScheduleImpl addBusSchedule(MyTransitScheduleImpl schedule, String fileName) {
		BufferedReader br;
		try {
			br = new BufferedReader(new FileReader(fileName));
			String readLine = br.readLine();
			MyTransitLineImpl line = null;
			LinkedList<MyTransitRouteStopImpl> stops = new LinkedList<MyTransitRouteStopImpl>();
			Id<MyTransitRouteImpl> routeId = null;
			Id<MyTransitRouteImpl> prevRouteId = Id.create(-1, MyTransitRouteImpl.class);
			int offset = -1;

			HashMap<String,MyTransitRouteImpl> routeMap = new HashMap<String,MyTransitRouteImpl>();
			String suffix = "";
			while((readLine = br.readLine()) != null){
				String[] splitLine = readLine.split(";");
				Id<MyTransitLineImpl> lineId = Id.create(splitLine[4], MyTransitLineImpl.class);

				//TODO Make a prettier fix for this, i.e. create base and fullday schedules with the 1310000 already subtracted.
				long num = Long.parseLong(splitLine[0]);
				if(splitLine[0].substring(0, 7).equals("1310000")){
					num -=  1310000 * Math.pow(10,9);
				}

				routeId = Id.create(num, MyTransitRouteImpl.class);

				int arr = (int)Math.round(Double.valueOf(splitLine[2]));
				int dep = (int) Math.round(Double.valueOf(splitLine[3]));

				MyTransitStopFacilityImpl stopFacility = null;
				if(RunMatsim.stopToStopGroup.containsKey(splitLine[1])){
					stopFacility = RunMatsim.facilities.get(RunMatsim.stopToStopGroup.get(splitLine[1]));
				}
				//	TransitStopFacility stopFacility = schedule.getFacilities().get(Id.create(splitLine[1], TransitStopFacility.class));
				if(routeId != prevRouteId){
					if(!stops.isEmpty()){
						MyTransitRouteImpl route;
						String routeString = createRouteString(line.getId(), stops);
						if(!routeMap.containsKey(routeString)){
							route = new MyTransitRouteImpl(prevRouteId,stops, RunMatsim.MODE_BUS);	
							routeMap.put(routeString, route);
							line.addRoute(route);
						} else {
							route = routeMap.get(routeString);
						}
						MyDepartureImpl departureToAdd = new MyDepartureImpl(Id.create(prevRouteId.toString(), MyDepartureImpl.class), offset);
						route.addDeparture(departureToAdd);

						stops.clear();
					}
					if(!schedule.getTransitLines().containsKey(lineId)){
						line = new MyTransitLineImpl(lineId);
						schedule.addTransitLine(line);
					} else {
						line = schedule.getTransitLines().get(lineId);
					}
					
					//Ensuring that both arr and dep starts at offset 0.
					if(dep > arr) {
						arr = dep;
					}
					offset = arr;
				}

				if(stopFacility != null){
					arr = potentiallyAdjustByAFullDay(arr,offset);
					dep = potentiallyAdjustByAFullDay(dep,offset);
					if(dep < arr ) {
						dep = arr;
					}

					if(stops.size() > 0 && stops.get(stops.size()-1).getStopFacility().getId() == stopFacility.getId()){
						MyTransitRouteStopImpl prevStop = stops.get(stops.size()-1);
						stops.remove(stops.size()-1);
						// Fix weird instances with negative departures for last stop...
						if(prevStop.getArrivalOffset() > dep - offset) {
							if(prevStop.getArrivalOffset() > arr - offset) {
								dep = prevStop.getArrivalOffset() + offset;
							} else {
								dep = arr;
							}
						}
						MyTransitRouteStopImpl stop = new MyTransitRouteStopImpl(prevStop.getStopFacility(),
								prevStop.getArrivalOffset(), dep-offset, stops.size());
						stops.add(stop);
					} else {
						MyTransitRouteStopImpl stop = new MyTransitRouteStopImpl(stopFacility,arr-offset, dep-offset, stops.size());
						stops.add(stop);
					}
				}
				prevRouteId = routeId;
			}
			if(!stops.isEmpty()){
				MyTransitRouteImpl route = new MyTransitRouteImpl(routeId,stops,RunMatsim.MODE_BUS);
				// THIS MUST BE CHANGED!
				MyDepartureImpl departureToAdd = new MyDepartureImpl(Id.create(prevRouteId.toString(), MyDepartureImpl.class), offset);
				route.addDeparture(departureToAdd);
				line.addRoute(route);
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return schedule;
	}


	public static int potentiallyAdjustByAFullDay(int t, int ref) {
		if(t < ref){
			t += 24*3600;
		} else if( t >= 24*3600 + ref){
			t -= 24*3600;
		}
		return t;
	}

	private static String createRouteString(Id<MyTransitLineImpl> lineId, List<MyTransitRouteStopImpl> stops) {
		StringBuilder s = new StringBuilder(lineId.toString());
		for(MyTransitRouteStopImpl stop : stops){
			s.append("_" + stop.getStopFacility().getId().toString());
			s.append("_" + stop.getArrivalOffset());
			s.append("_" + stop.getDepartureOffset());
		}
		return s.toString();
	}

	static MyTransitScheduleImpl addTrainSchedule(MyTransitScheduleImpl schedule, String fileName){
		try {
			BufferedReader br = new BufferedReader(new FileReader(fileName));
			String readLine = br.readLine();
			MyTransitLineImpl line = null;
			LinkedList<MyTransitRouteStopImpl> stops = new LinkedList<MyTransitRouteStopImpl>();
			Id<MyTransitRouteImpl> prevRouteId = Id.create("-1", MyTransitRouteImpl.class);
			Id<MyTransitRouteImpl> routeId = null;
			int arrival = -1;
			int departure = -1;
			int offset = -1;
			Id<MyTransitLineImpl> prevLineId = null;
			String subMode = "";
			while((readLine = br.readLine()) != null){
				String[] splitLine = readLine.split(";");
				Id<MyTransitLineImpl> lineId = Id.create(splitLine[0], MyTransitLineImpl.class);
				int time = (int) Math.round(Double.valueOf(splitLine[1]));
				int trainNum = Integer.parseInt(splitLine[2]);

				routeId = Id.create(trainNum, MyTransitRouteImpl.class);		
				MyTransitStopFacilityImpl stopFacility = schedule.getFacilities().get(Id.create(splitLine[3], MyTransitStopFacilityImpl.class));
				String moveType = splitLine[4];
				if(!schedule.getTransitLines().containsKey(lineId)){
					line = new MyTransitLineImpl(lineId);
					schedule.addTransitLine(line);
				}
				if(prevLineId != null){
					line = schedule.getTransitLines().get(prevLineId);
				}

				if(!prevRouteId.equals(routeId)){
					if(!stops.isEmpty()){
						MyTransitRouteImpl route = new MyTransitRouteImpl(prevRouteId,stops, subMode);
						MyDepartureImpl departureToAdd = new MyDepartureImpl(Id.create(prevRouteId.toString(), MyDepartureImpl.class), offset);
						route.addDeparture(departureToAdd);
						line.addRoute(route);
						stops.clear();
					}
				}

				switch(moveType){
				case "I":
					arrival = time;
					departure = arrival;
					break;
				case "U":
					departure = time;
					if(arrival == -1){
						arrival = departure;
					}
					if(stops.size() == 0) {	
						if(arrival > departure) {
							arrival = departure;
						}
						offset = arrival;
					}
					if(stopFacility != null){
						arrival = potentiallyAdjustByAFullDay(arrival, offset);
						departure = potentiallyAdjustByAFullDay(departure, offset);
						MyTransitRouteStopImpl stop = new MyTransitRouteStopImpl(stopFacility,arrival-offset, departure-offset, stops.size());
						stops.add(stop);
					}
					arrival = -1;
					break;
				default:  //if G
					break;
				}
				prevLineId = lineId;
				prevRouteId = routeId;

				if(trainNum >= 10000 && trainNum < 80000 && !(trainNum >= 24000 && trainNum < 25000)){
					subMode = RunMatsim.MODE_S_TRAIN;
				} else if(trainNum >= 100000 && trainNum < 200000){
					subMode = RunMatsim.MODE_LOCAL_TRAIN;
				} else {
					subMode = RunMatsim.MODE_TRAIN;
				}
			}

			//adding the last departure of the train schedule...
			if(!stops.isEmpty()){
				MyTransitRouteImpl route = new MyTransitRouteImpl(prevRouteId,stops, subMode);
				MyDepartureImpl departureToAdd = new MyDepartureImpl(Id.create(prevRouteId.toString(), MyDepartureImpl.class), offset);
				route.addDeparture(departureToAdd);
				line.addRoute(route);
				stops.clear();
			}


		} catch (IOException e) {
			e.printStackTrace();
		}
		return schedule;
	}

//	public static MyTransitScheduleImpl createTransitInfrastructure(MyTransitScheduleImpl schedule){
//		for(String inputFile : new String[]{INPUT_FOLDER + "/OtherInput/stations.csv",
//				INPUT_FOLDER + "/OtherInput/NewStopGroups.csv"}){
//			BufferedReader br;
//			try {
//				br = new BufferedReader(new FileReader(inputFile));
//
//				String readLine = br.readLine();
//				while((readLine = br.readLine()) != null){
//					String[] splitLine = readLine.split(";");
//					Id<MyTransitStopFacilityImpl> id = Id.create(splitLine[0], MyTransitStopFacilityImpl.class);
//					double x = Double.valueOf(splitLine[1]);
//					double y = Double.valueOf(splitLine[2]);
//					Coord coord = new Coord(x,y);
//					MyTransitStopFacilityImpl stop = new MyTransitStopFacilityImpl(id, coord);
//					schedule.addStopFacility(stop);
//				}
//			} catch (IOException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
//		}
//		return schedule;
//	}

	
	public static void mergeLocal(){
		Scenario scenario = ScenarioUtils.loadScenario(RunMatsim.createConfig());
		MyTransitScheduleImpl schedule = new MyTransitScheduleImpl();
		schedule = RunMatsim.addTransitStopFacilitiesFromSchedule(schedule, scenario.getTransitSchedule().getFacilities().values());
		schedule = addStaticSchedule(schedule, RunMatsim.INPUT_FOLDER + "/BaseSchedules/LocalTrainSchedule_old.xml");
		for(MyTransitLineImpl line : schedule.getTransitLines().values()) {
			HashMap<String, MyTransitRouteImpl> routeMap = new HashMap<String, MyTransitRouteImpl>();
			LinkedList<MyTransitRouteImpl> routesToRemove = new LinkedList<MyTransitRouteImpl>();
			
			for(MyTransitRouteImpl route : line.getRoutes().values()) {
				String routeString = createRouteString(line.getId(), Arrays.asList(route.getStops()));
				if(!routeMap.containsKey(routeString)){
					routeMap.put(routeString, route);
				} else {
					MyTransitRouteImpl containingRoute = routeMap.get(routeString);
					routesToRemove.add(route);
					for(MyDepartureImpl departure : route.getDepartures()) {
						containingRoute.addDeparture(departure);
					}
				}
				
			}
			for(MyTransitRouteImpl route : routesToRemove) {
				line.removeRoute(route);
			}
		}
		
		TransitSchedule matsimSchedule = createScheduleFromMyTransitScheduleImpl(schedule);
		TransitScheduleWriter writer = new TransitScheduleWriter(matsimSchedule);
		writer.writeFile(RunMatsim.INPUT_FOLDER + "/BaseSchedules/LocalTrainSchedule.xml");
		
	}
}
