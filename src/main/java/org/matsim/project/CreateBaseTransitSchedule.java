package org.matsim.project;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
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
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

public class CreateBaseTransitSchedule {

	static Link dummyLink;
	static NetworkRoute networkRoute;
	//static String INPUT_FOLDER = "c:/workAtHome/PassengerDelay";
	static String INPUT_FOLDER = "/work1/s103232/PassengerDelay";

	Coord fromCoord = new Coord(719991.463908,6174840.523082);	
	Coord toCoord = new Coord(723728.644952,6180425.027057);

	public static void main(String[] args) throws IOException{
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

		TransitScheduleWriter writer = new TransitScheduleWriter(scenario.getTransitSchedule());
		writer.writeFile(INPUT_FOLDER + "/BaseSchedules/BaseSchedule.xml.gz");
		NetworkWriter networkWriter = new NetworkWriter(scenario.getNetwork());
		networkWriter.write(INPUT_FOLDER + "/OtherInput/network.xml.gz");

	}

	private static Scenario addBaseSchedule(Scenario scenario) throws IOException {
		scenario = addTrainSchedule(scenario);
			scenario = addBusSchedule(scenario);
		return scenario;
	}

	private static Scenario addBusSchedule(Scenario scenario) throws IOException {
		TransitSchedule schedule = scenario.getTransitSchedule();
		BufferedReader br = new BufferedReader(new FileReader(INPUT_FOLDER + "/BaseSchedules/BusSchedule.csv"));
		String readLine = br.readLine();
		TransitLine line = null;
		List<TransitRouteStop> stops = new LinkedList<TransitRouteStop>();
		Id<TransitRoute> routeId = null;
		Id<TransitRoute> prevRouteId = Id.create(-1, TransitRoute.class);
		double offset = -1;
		while((readLine = br.readLine()) != null){
			String[] splitLine = readLine.split(";");
			Id<TransitLine> lineId = Id.create(splitLine[0], TransitLine.class);
			routeId = Id.create(splitLine[0], TransitRoute.class);
			double arr = Double.valueOf(splitLine[2]);
			double dep = Double.valueOf(splitLine[3]);
			TransitStopFacility stopFacility = schedule.getFacilities().get(Id.create(splitLine[1], TransitStopFacility.class));
			if(!schedule.getTransitLines().containsKey(lineId)){
				if(!stops.isEmpty()){
					TransitRoute route = scenario.getTransitSchedule().getFactory().createTransitRoute(prevRouteId,networkRoute,stops, "bus");	
					route.addDeparture(schedule.getFactory().createDeparture(Id.create(String.valueOf(offset), Departure.class), offset));
					line.addRoute(route);
					stops.clear();
				}
				offset = arr;
				line = schedule.getFactory().createTransitLine(lineId);
				schedule.addTransitLine(line);
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
			}
			prevRouteId = routeId;
		}
		if(!stops.isEmpty()){
			TransitRoute route = scenario.getTransitSchedule().getFactory().createTransitRoute(routeId,networkRoute,stops, "bus");
			route.addDeparture(schedule.getFactory().createDeparture(Id.create(String.valueOf(offset), Departure.class), offset));
			line.addRoute(route);
		}
		return scenario;
	}


	private static Scenario addTrainSchedule(Scenario scenario) throws IOException {
		TransitSchedule schedule = scenario.getTransitSchedule();
		BufferedReader br = new BufferedReader(new FileReader(INPUT_FOLDER + "/BaseSchedules/TrainSchedule.csv"));
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
			double time = Double.valueOf(splitLine[1]);
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
					route.addDeparture(schedule.getFactory().createDeparture(Id.create(String.valueOf(offset), Departure.class), offset));
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
		return scenario;
	}

	public static Scenario createTransitInfrastructure(Scenario scenario) throws IOException{

		TransitSchedule schedule = scenario.getTransitSchedule();

		for(String inputFile : new String[]{INPUT_FOLDER + "/OtherInput/stations.csv",
				INPUT_FOLDER + "/OtherInput/busStops.csv"}){
			BufferedReader br = new BufferedReader(new FileReader(inputFile));
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
		}
		return scenario;
	}

}
