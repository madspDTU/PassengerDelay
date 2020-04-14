package org.matsim.project;

import java.util.LinkedList;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.project.pt.MyTransitScheduleImpl;
import ch.sbb.matsim.routing.pt.raptor.DefaultRaptorParametersForPerson;
import ch.sbb.matsim.routing.pt.raptor.RaptorParametersForPerson;
import ch.sbb.matsim.routing.pt.raptor.RaptorStaticConfig;
import ch.sbb.matsim.routing.pt.raptor.MySwissRailRaptor;
import ch.sbb.matsim.routing.pt.raptor.MySwissRailRaptorData;

public class BaseJob  implements Runnable {

	final private LinkedList<PassengerDelayPerson> persons;
	final int stopwatch;
	private Scenario scenario;
	private long buildDuration;
	private long fullDuration;
	private String date;
	private MyTransitScheduleImpl schedule;

	BaseJob(int stopwatch, LinkedList<PassengerDelayPerson> persons, Scenario scenario, String date, MyTransitScheduleImpl schedule){
		this.persons = persons;
		this.stopwatch = stopwatch;
		this.scenario = scenario;
		this.date = date;
		this.schedule = schedule;
	}


	@Override
	public void run() {
		try{
			long backThen = System.currentTimeMillis();

			//	System.out.println("Free memory before: " +
			//			Runtime.getRuntime().freeMemory() /1000000000.);
			Config config = this.scenario.getConfig();

			RaptorParametersForPerson parameters = new DefaultRaptorParametersForPerson(config);
	
			RaptorStaticConfig staticConfig = RunMatsim.createRaptorStaticConfig(config);

			this.schedule = CreateBaseTransitSchedule.clearTransitSchedule(this.schedule);
			this.schedule = CreateBaseTransitSchedule.addBaseSchedule(this.schedule, date);
			//TransitScheduleWriter writer = new TransitScheduleWriter(scenario.getTransitSchedule());
			//writer.writeFile("/work1/s103232/PassengerDelay/Diagnostics/perfectSchedule.xml");
			//this.scenario = CreateBaseTransitSchedule.clearTransitSchedule(scenario);
			//TransitScheduleReader reader = new TransitScheduleReader(scenario);
			//reader.readFile("/work1/s103232/PassengerDelay/Diagnostics/perfectSchedule_reduced.xml");





			MySwissRailRaptorData data = MySwissRailRaptorData.create(schedule, staticConfig);

			long backMiddle = System.currentTimeMillis();
			buildDuration = (backMiddle - backThen)/1000;
			System.out.print( buildDuration + "s ");

			MySwissRailRaptor raptor = new MySwissRailRaptor(data, parameters);
			int counter = 0;
			System.gc();
			for(PassengerDelayPerson person : persons){
				person.setStopwatch(stopwatch);
				person.setRaptor(raptor);
				if(RunMatsim.adaptivenessType == RunMatsim.AdaptivenessType.RIGID){
					person.createAllRoutesOfDay();
				} else { //Perfect or perfect_EXTENDED
					person.createEntireDayEvents();
				}
				counter++;
				int printInterval = 10000;
				if(counter % printInterval == 0){
					long backThousands = System.currentTimeMillis();
					long deltaTime = (backThousands - backMiddle)/1000;
					System.out.println(counter + " persons processed by this thread. Previous " + printInterval + " persons: " +
							printInterval / (double) deltaTime + " persons per second.");
					backMiddle = backThousands;
				}
			}
			long backNow = System.currentTimeMillis();
			fullDuration = (backNow - backThen)/1000;

			//		System.out.println("Free memory after " + (int) (backNow-backThen)/1000 + " seconds: " +
			//				Runtime.getRuntime().freeMemory() / 1000000000.);
		} catch(Exception e){
			e.printStackTrace();
			//	System.err.println("An advance job for person " + person.id + " did not terminate");
			System.err.println("A BaseJob at time " + stopwatch + " did not terminate");
		}

	}

	double getFullDuration(){
		return fullDuration;
	}

	double getBuildDuration(){
		return buildDuration;
	}


}
