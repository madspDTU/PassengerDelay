package org.matsim.project;

import java.io.IOException;
import java.util.Arrays;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;

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
		*/
		
		
		String[] a = new String[]{"/work1/s103232/PassengerDelay", "base", "1", "27"};
		RunMatsim.main(a);
	}
}
