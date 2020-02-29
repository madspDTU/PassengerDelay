package org.matsim.project;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

public class createTablesOfVehicleDelays {

	public static ConcurrentHashMap<String, Id<TransitStopFacility>> stopToStopGroup;
	static String[] dates = new String[]{
		"2014_09_01","2014_09_02","2014_09_03","2014_09_04","2014_09_05","2014_09_08","2014_09_09","2014_09_10",
		"2014_09_11","2014_09_12","2014_09_15","2014_09_16","2014_09_17","2014_09_18","2014_09_19","2014_09_22",
		"2014_09_23","2014_09_24","2014_09_25","2014_09_26","2014_09_29","2014_09_30","2014_10_01","2014_10_02",
		"2014_10_03","2014_10_06","2014_10_07","2014_10_08","2014_10_09","2014_10_10","2014_10_13","2014_10_14",
		"2014_10_15","2014_10_16","2014_10_17","2014_10_20","2014_10_21","2014_10_22","2014_10_23","2014_10_24",
		"2014_10_27","2014_10_28","2014_10_29","2014_10_30","2014_10_31","2014_11_03","2014_11_04","2014_11_05",
		"2014_11_06","2014_11_07","2014_11_10","2014_11_11","2014_11_12","2014_11_13","2014_11_14","2014_11_17",
		"2014_11_18","2014_11_19","2014_11_20","2014_11_21","2014_11_24","2014_11_25","2014_11_26","2014_11_27","2014_11_28"};
	private static HashMap<Id<TransitRoute>, List<TransitRouteStop>> baseStopsMap = new HashMap<Id<TransitRoute>,List<TransitRouteStop>>();
	private static HashMap<Id<TransitRoute>, Double> baseOffsets = new HashMap<Id<TransitRoute>,Double>();
	private static ConcurrentHashMap<Id<TransitStopFacility>, TransitStopFacility> facilities =
			new ConcurrentHashMap<Id<TransitStopFacility>, TransitStopFacility>();
	private static boolean useDeparturesInstead = false;


	public static void main(String[] args) throws IOException{
		System.out.println("Preparation.... ");

		CreateBaseTransitSchedule.init();
		Scenario scenario = ScenarioUtils.loadScenario(RunMatsim.createConfig());
		stopToStopGroup = RunMatsim.createStop2StopGroupMap();
		for (TransitStopFacility stopFacility : scenario.getTransitSchedule().getFacilities().values()) {
			facilities.put(stopFacility.getId(), stopFacility);
		}
		gatherPlannedTimesBus("/work1/s103232/PassengerDelay/BaseSchedules/BusSchedule.csv", scenario);
		gatherPlannedTimesTrain("/work1/s103232/PassengerDelay/BaseSchedules/TrainSchedule.csv", scenario);

		Scenario metroScenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		Scenario localTrainScenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		TransitScheduleReader metroReader = new TransitScheduleReader(metroScenario);
		TransitScheduleReader localTrainReader = new TransitScheduleReader(localTrainScenario);
		metroReader.readFile("/work1/s103232/PassengerDelay/BaseSchedules/MetroSchedule.xml");
		localTrainReader.readFile("/work1/s103232/PassengerDelay/BaseSchedules/LocalTrainSchedule.xml");

		int metroDepartures = 0;
		long metroArrivals = 0l;
		for(TransitLine line : metroScenario.getTransitSchedule().getTransitLines().values()){
			for(TransitRoute route : line.getRoutes().values()){
				metroArrivals += route.getStops().size() * route.getDepartures().size();
				metroDepartures += route.getDepartures().size();
			}
		}
		System.out.println("A total of " + metroArrivals + " metro stop arrivals per day...");
		System.out.println("A total of " + metroDepartures+ " metro departures per day...");
		int localDepartures = 0;
		long localTrainArrivals = 0l;
		int localLines = 0;
		for(TransitLine line : localTrainScenario.getTransitSchedule().getTransitLines().values()){
			for(TransitRoute route : line.getRoutes().values()){
				localTrainArrivals += route.getStops().size() * route.getDepartures().size();
				localDepartures += route.getDepartures().size();
			}
		}
		System.out.println("A total of " + localTrainArrivals + " local train stop arrivals per day...");
		System.out.println("A total of " + localDepartures + " local train departures per day...");

		Scenario baseScenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		TransitScheduleReader baseReader = new TransitScheduleReader(baseScenario);
		baseReader.readFile("/work1/s103232/PassengerDelay/BaseSchedules/BaseSchedule.xml");
		System.out.println("#Transit Lines: " + 
								baseScenario.getTransitSchedule().getTransitLines().size());
		int numberOfDepartures = 0;
		for(TransitLine line : baseScenario.getTransitSchedule().getTransitLines().values()){
			for(TransitRoute route : line.getRoutes().values()){
				numberOfDepartures += route.getDepartures().size();
			}
		}
		System.out.println("#Departures: " +  numberOfDepartures);


		List<String> scenarios = Arrays.asList("BASE","RIGID","NONADAPTIVE","SEMIADAPTIVE","FULLADAPTIVE");
		for(String type : scenarios){
			File dir = new File("/zhome/81/e/64390/MATSim/PassengerDelay/" + type);
			String[] files = dir.list();
			double times = 0;
			int instances = 0;
			for(String file : files){
				BufferedReader br = new BufferedReader(new FileReader(dir + "/" +  file));
				String nextLine;
				while((nextLine =br.readLine()) != null){
					if(nextLine.contains("Total time: ")){
						int indexOfColon = nextLine.lastIndexOf(":");
						int time = Integer.parseInt(nextLine.substring(indexOfColon-2,indexOfColon))*3600 +
								Integer.parseInt(nextLine.substring(indexOfColon+1,indexOfColon+3))*60;
						times += time/60.;
						instances++;
	//					System.out.println(type + ": " + time/60.);
					}
				}
			}
			System.out.println(type + ": " + times / instances);
		}


		
		FileWriter trainWriter = useDeparturesInstead ?  
				new FileWriter("/work1/s103232/PassengerDelay/VehicleDelays/TrainDelays_Departures.csv") :
				new FileWriter("/work1/s103232/PassengerDelay/VehicleDelays/TrainDelays.csv");
		trainWriter.append("DepartureId;StopId;Date;ScheduledTime;Delay\n");

		FileWriter busWriter = useDeparturesInstead ?
				new FileWriter("/work1/s103232/PassengerDelay/VehicleDelays/BusDelays_Departures.csv") :
				new FileWriter("/work1/s103232/PassengerDelay/VehicleDelays/BusDelays.csv");
		busWriter.append(  "DepartureId;StopId;Date;ScheduledTime;Delay\n");
		for(String date : dates){
			System.out.println(date);
			String fileName = "/work1/s103232/PassengerDelay/Disaggregate/Train/" + date + "/AVLSchedule_" + date + ".csv";
			LinkedList<String> delays = gatherTrainDelays(fileName, scenario, date);
			for(String s : delays){
				trainWriter.append(s);
			}
			fileName = "/work1/s103232/PassengerDelay/Disaggregate/Bus/" + date + "/AVLSchedule_" + date + ".csv";
			delays = gatherBusDelays(fileName, scenario, date);
			for(String s : delays){
				busWriter.append(s);
			}
		}

		System.out.print("Writing....");
		trainWriter.flush();
		trainWriter.close();
		busWriter.flush();
		busWriter.close();
		System.out.println(" Done!");
	}

	private static void gatherPlannedTimesTrain(String fileName, Scenario scenario) {

		long trainStopArrivals = 0;
		int trainDepartures = 0;
		TransitSchedule schedule = scenario.getTransitSchedule();
		try {
			BufferedReader br = new BufferedReader(new FileReader(fileName));
			String readLine = br.readLine();
			List<TransitRouteStop> stops = new LinkedList<TransitRouteStop>();
			Id<TransitRoute> prevRouteId = Id.create("-1", TransitRoute.class);
			Id<TransitRoute> routeId = null;
			double arrival = -1;
			double departure = -1;
			double offset = -1;

			while((readLine = br.readLine()) != null){
				String[] splitLine = readLine.split(";");
				double time = Math.round(Double.valueOf(splitLine[1]));
				int trainNum = Integer.parseInt(splitLine[2]);
				routeId = Id.create(trainNum, TransitRoute.class);		
				TransitStopFacility stopFacility = schedule.getFacilities().get(Id.create(splitLine[3], TransitStopFacility.class));
				String moveType = splitLine[4];
				if(!prevRouteId.equals(routeId)){
					if(!stops.isEmpty()){
						putInBaseStopsMap(prevRouteId, stops, scenario);
						baseOffsets.put(prevRouteId, offset);
						trainStopArrivals += stops.size();
						trainDepartures++;
						stops.clear();
					}
					offset = time;
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
					if(stopFacility != null){
						arrival = CreateBaseTransitSchedule.potentiallyAdjustByAFullDay(arrival, offset);
						departure = CreateBaseTransitSchedule.potentiallyAdjustByAFullDay(departure, offset);
						TransitRouteStop stop = scenario.getTransitSchedule().getFactory().createTransitRouteStop(
								stopFacility,arrival-offset, departure-offset);
						stops.add(stop);
					}
					arrival = -1;
					break;
				default:  //if G
					break;
				}
				prevRouteId = routeId;
			}

			//adding the last departure of the train schedule...
			if(!stops.isEmpty()){
				if(!stops.isEmpty()){
					putInBaseStopsMap(prevRouteId, stops, scenario);
					baseOffsets.put(prevRouteId, offset);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		System.out.println("A total of " + trainStopArrivals + " train arrivals per day");

		System.out.println("A total of " + trainDepartures + " train departures per day");
	}

	private static void gatherPlannedTimesBus(String fileName, Scenario scenario) {

		long busStopArrivals = 0;
		int busDepartures = 0;


		BufferedReader br;
		try {
			br = new BufferedReader(new FileReader(fileName));
			String readLine = br.readLine();
			List<TransitRouteStop> stops = new LinkedList<TransitRouteStop>();
			Id<TransitRoute> routeId = null;
			Id<TransitRoute> prevRouteId = Id.create(-1, TransitRoute.class);
			double offset = -1;

			HashSet<Id<TransitStopFacility>> transitStopFacilityIds = new HashSet<Id<TransitStopFacility>>();
			String suffix = "";
			while((readLine = br.readLine()) != null){
				String[] splitLine = readLine.split(";");

				//TODO Make a prettier fix for this, i.e. create base and fullday schedules with the 1310000 already subtracted.
				long num = Long.parseLong(splitLine[0]);
				if(splitLine[0].substring(0, 7).equals("1310000")){
					num -=  1310000 * Math.pow(10,9);
				}

				routeId = Id.create(num, TransitRoute.class);

				double arr = Math.round(Double.valueOf(splitLine[2]));
				double dep = Math.round(Double.valueOf(splitLine[3]));

				TransitStopFacility stopFacility = null;
				if(stopToStopGroup.containsKey(splitLine[1])){
					stopFacility = facilities.get(stopToStopGroup.get(splitLine[1]));
				}
				//	TransitStopFacility stopFacility = schedule.getFacilities().get(Id.create(splitLine[1], TransitStopFacility.class));
				boolean isStopDuplicate = transitStopFacilityIds.contains(stopFacility.getId());
				if(routeId != prevRouteId ||  isStopDuplicate){
					// Split lines 
					if(routeId == prevRouteId && isStopDuplicate){ // Happens in the middle somewhere
						// Current stop group equal to previous stop group 
						if(stops.get(stops.size()-1).getStopFacility().getId() == stopFacility.getId()){
							TransitRouteStop prevStop = stops.get(stops.size()-1);
							stops.remove(stops.size()-1);
							TransitRouteStop stop = scenario.getTransitSchedule().getFactory().createTransitRouteStop(
									prevStop.getStopFacility(), prevStop.getArrivalOffset(), dep-offset);
							stops.add(stop);
							continue;
						} else {
							suffix += "X"; //All parts will get k X's added.
							prevRouteId = Id.create(prevRouteId.toString() + suffix, TransitRoute.class);							
						}
					} else {
						suffix = "";
					}

					transitStopFacilityIds.clear();
					if(!stops.isEmpty()){
						putInBaseStopsMap(prevRouteId, stops, scenario);
						baseOffsets.put(prevRouteId,offset);
						busStopArrivals += stops.size();
						if(suffix.equals("")){
							busDepartures++;
						}
						stops.clear();
					}
					offset = arr;
				}
				if(stopFacility != null){
					arr = CreateBaseTransitSchedule.potentiallyAdjustByAFullDay(arr,offset);
					dep = CreateBaseTransitSchedule.potentiallyAdjustByAFullDay(dep,offset);

					TransitRouteStop stop = scenario.getTransitSchedule().getFactory().createTransitRouteStop(
							stopFacility,arr-offset, dep-offset);
					stops.add(stop);
					transitStopFacilityIds.add(stopFacility.getId());
				}
				prevRouteId = routeId;
			}

			/// The same as some lines above....
			if(!stops.isEmpty()){
				putInBaseStopsMap(prevRouteId, stops, scenario);
				baseOffsets.put(prevRouteId,offset);
				stops.clear();
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		System.out.println("A total of " + busStopArrivals + " bus stop arrivals per day...");
		System.out.println("A total of " + busDepartures + " bus departures per day...");
	}

	private static void putInBaseStopsMap(Id<TransitRoute> key, List<TransitRouteStop> stops, Scenario scenario) {
		List<TransitRouteStop> copy = new ArrayList<TransitRouteStop>();
		for(TransitRouteStop stop : stops){
			TransitRouteStop newStop = scenario.getTransitSchedule().getFactory().createTransitRouteStop(
					stop.getStopFacility(), stop.getArrivalOffset(), stop.getDepartureOffset());
			copy.add(newStop);
		}
		baseStopsMap.put(key,copy);
	}


	private static LinkedList<String> gatherTrainDelays(String fileName, Scenario scenario, String date) {
		TransitSchedule schedule = scenario.getTransitSchedule();
		LinkedList<String> writer = new LinkedList<String>();
		try {
			BufferedReader br = new BufferedReader(new FileReader(fileName));
			String readLine = br.readLine();
			List<TransitRouteStop> stops = new LinkedList<TransitRouteStop>();
			Id<TransitRoute> prevRouteId = Id.create("-1", TransitRoute.class);
			Id<TransitRoute> routeId = null;
			double arrival = -1;
			double departure = -1;
			double offset = -1;
			while((readLine = br.readLine()) != null){
				String[] splitLine = readLine.split(";");
				double time = Math.round(Double.valueOf(splitLine[1]));
				int trainNum = Integer.parseInt(splitLine[2]);

				routeId = Id.create(trainNum, TransitRoute.class);		
				TransitStopFacility stopFacility = schedule.getFacilities().get(Id.create(splitLine[3], TransitStopFacility.class));
				String moveType = splitLine[4];
				if(!prevRouteId.equals(routeId)){
					if(!stops.isEmpty()){
						addDelaysToWriterIfNecessary(writer, stops, prevRouteId, offset, date);
						stops.clear();
					}
					offset = time;
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
					if(stopFacility != null){
						arrival = CreateBaseTransitSchedule.potentiallyAdjustByAFullDay(arrival, offset);
						departure = CreateBaseTransitSchedule.potentiallyAdjustByAFullDay(departure, offset);
						TransitRouteStop stop = scenario.getTransitSchedule().getFactory().createTransitRouteStop(
								stopFacility,arrival-offset, departure-offset);
						stops.add(stop);
					}
					arrival = -1;
					break;
				default:  //if G
					break;
				}
				prevRouteId = routeId;
			}

			//adding the last departure of the train schedule...
			if(!stops.isEmpty()){
				addDelaysToWriterIfNecessary(writer, stops, prevRouteId, offset, date);
				stops.clear();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return writer;

	}


	private static LinkedList<String> gatherBusDelays(String fileName, Scenario scenario, String date) {
		BufferedReader br;
		LinkedList<String> writer = new LinkedList<String>();
		try {
			br = new BufferedReader(new FileReader(fileName));
			String readLine = br.readLine();
			List<TransitRouteStop> stops = new LinkedList<TransitRouteStop>();
			Id<TransitRoute> routeId = null;
			Id<TransitRoute> prevRouteId = Id.create(-1, TransitRoute.class);
			double offset = -1;

			HashSet<Id<TransitStopFacility>> transitStopFacilityIds = new HashSet<Id<TransitStopFacility>>();
			String suffix = "";
			while((readLine = br.readLine()) != null){
				String[] splitLine = readLine.split(";");

				//TODO Make a prettier fix for this, i.e. create base and fullday schedules with the 1310000 already subtracted.
				long num = Long.parseLong(splitLine[0]);
				if(splitLine[0].substring(0, 7).equals("1310000")){
					num -=  1310000 * Math.pow(10,9);
				}

				routeId = Id.create(num, TransitRoute.class);

				double arr = Math.round(Double.valueOf(splitLine[2]));
				double dep = Math.round(Double.valueOf(splitLine[3]));

				TransitStopFacility stopFacility = null;
				if(stopToStopGroup.containsKey(splitLine[1])){
					stopFacility = facilities.get(stopToStopGroup.get(splitLine[1]));
				}
				//	TransitStopFacility stopFacility = schedule.getFacilities().get(Id.create(splitLine[1], TransitStopFacility.class));
				boolean isStopDuplicate = transitStopFacilityIds.contains(stopFacility.getId());
				if(routeId != prevRouteId ||  isStopDuplicate){
					// Split lines 
					if(routeId == prevRouteId && isStopDuplicate){ // Happens in the middle somewhere
						// Current stop group equal to previous stop group 
						if(stops.get(stops.size()-1).getStopFacility().getId() == stopFacility.getId()){
							TransitRouteStop prevStop = stops.get(stops.size()-1);
							stops.remove(stops.size()-1);
							TransitRouteStop stop = scenario.getTransitSchedule().getFactory().createTransitRouteStop(
									prevStop.getStopFacility(), prevStop.getArrivalOffset(), dep-offset);
							stops.add(stop);
							continue;
						} else {
							suffix += "X"; //All parts will get k X's added.
							prevRouteId = Id.create(prevRouteId.toString() + suffix, TransitRoute.class);							
						}
					} else {
						suffix = "";
					}

					transitStopFacilityIds.clear();
					if(!stops.isEmpty()){
						addDelaysToWriterIfNecessary(writer, stops, prevRouteId, offset, date);
						stops.clear();
					}
					offset = arr;
				}
				if(stopFacility != null){
					arr = CreateBaseTransitSchedule.potentiallyAdjustByAFullDay(arr,offset);
					dep = CreateBaseTransitSchedule.potentiallyAdjustByAFullDay(dep,offset);
					TransitRouteStop stop = scenario.getTransitSchedule().getFactory().createTransitRouteStop(
							stopFacility,arr-offset, dep-offset);
					stops.add(stop);
					transitStopFacilityIds.add(stopFacility.getId());
				}
				prevRouteId = routeId;
			}

			/// The same as some lines above....
			if(!stops.isEmpty()){
				addDelaysToWriterIfNecessary(writer, stops, prevRouteId, offset, date);
				stops.clear();
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return writer;
	}

	private static void addDelaysToWriterIfNecessary(LinkedList<String> writer, List<TransitRouteStop> stops,
			Id<TransitRoute> prevRouteId, double offset, String date) {
		List<TransitRouteStop> baseStops = baseStopsMap.get(prevRouteId);
		Gbl.assertIf(baseStops.size() == stops.size());
		double baseOffset = baseOffsets.get(prevRouteId);
		boolean haveAnyDelaysBeenRecorded = false;
		for(int i = 0; i < baseStops.size(); i++){
			TransitRouteStop baseStop = baseStops.get(i);
			TransitRouteStop currentStop = stops.get(i);
			Gbl.assertIf(baseStop.getStopFacility().getId() == currentStop.getStopFacility().getId());
			if(Math.abs(baseStop.getArrivalOffset() + baseOffset - (currentStop.getArrivalOffset() + offset)) > 1.5){
				haveAnyDelaysBeenRecorded = true;
			}
		}
		if(haveAnyDelaysBeenRecorded){
			for(int i = 0; i < baseStops.size(); i++){
				TransitRouteStop baseStop = baseStops.get(i);
				TransitRouteStop currentStop = stops.get(i);
				double baseTime =  useDeparturesInstead ? 
						baseStop.getDepartureOffset() + baseOffset :
						baseStop.getArrivalOffset() + baseOffset;
				double delay = useDeparturesInstead ?
						currentStop.getDepartureOffset() + offset - baseTime :
						currentStop.getArrivalOffset() + offset - baseTime;
				writer.addLast(prevRouteId.toString() + ";" + baseStop.getStopFacility().getId().toString() + ";" +
						date + ";" + baseTime + "; "+ delay + "\n");
			}
		}
	}
}
