package org.matsim.project;

import java.io.IOException;
import java.util.Arrays;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

public class RunMatsimIndirectly {

	public static void main(String[] args) throws IOException {

		/*
		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		CreateBaseTransitSchedule.init();
		CreateBaseTransitSchedule.createTransitInfrastructure(scenario);
		TransitScheduleWriter writer = new TransitScheduleWriter(scenario.getTransitSchedule());
		writer.writeFile(RunMatsim.INPUT_FOLDER + "/BaseSchedules/BaseSchedule_InfrastructureOnly.xml.gz");


		CreateBaseTransitSchedule.createAndWriteLocalTrainSchedule(scenario);
		CreateBaseTransitSchedule.clearTransitSchedule(scenario);
		CreateBaseTransitSchedule.createAndWriteMetroSchedule(scenario);
		System.out.println("Completed");
		System.exit(-1);
		CreateBaseTransitSchedule.mergeLocal();
		System.exit(-1);

		int busCount = 0;
		int trainCount = 0;
		Scenario scenario = ScenarioUtils.loadScenario(RunMatsim.createConfig());
		for(TransitStopFacility stop : scenario.getTransitSchedule().getFacilities().values()) {
			try {
				Double.parseDouble(stop.getId().toString());
				busCount++;
			} catch(Exception e) {
				trainCount++;
			}

		}
		System.out.println(trainCount);
		System.out.println(busCount);
		System.exit(-1);
		 */


		String[] a = new String[]{"/work1/s103232/PassengerDelay", "2014_10_31", "1", "27", "PERFECT"};
		RunMatsim.main(a);
	}
}
