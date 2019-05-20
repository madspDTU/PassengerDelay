package org.matsim.project;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;

public class ExtractTimeSchedulesForZhu {


	public static void main(String[] args) throws IOException{

		for(String version : new String[]{"Base","LightRail"}){
			Config config = ConfigUtils.createConfig();
			Scenario scenario = ScenarioUtils.createScenario(config);

			TransitScheduleReader scheduleReader = new TransitScheduleReader(scenario);
			scheduleReader.readFile("/work1/s103232/Zhu_" + version + "_v24/Zhu_" + version + 
					"_v24.output_transitSchedule.xml.gz");

			FileWriter writer = new FileWriter(new File("/work1/s103232/Zhu_" + version + "_v24/TransitSchedule.csv"));
			writer.append("DepartureId;FromStopId;DepartureTime;LineId\n");
			for(TransitLine line : scenario.getTransitSchedule().getTransitLines().values()){
				for(TransitRoute route : line.getRoutes().values()){
					for(Departure departure : route.getDepartures().values()){
						double offset = departure.getDepartureTime();
						int i = 1;
						for(TransitRouteStop stop : route.getStops()){
							writer.append(departure.getId() + ";" + stop.getStopFacility().getId() + 
									";" + (offset + stop.getDepartureOffset()) + ";" + line.getId() + "\n");
							i++;
							if(i == route.getStops().size()){
								break;
							}
						}
					}
				}
			}
			writer.flush();
			writer.close();
		}
	}
}
