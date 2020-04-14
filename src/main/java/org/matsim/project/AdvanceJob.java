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

public class AdvanceJob  implements Runnable {

	final private LinkedList<PassengerDelayPerson> persons;
	final int stopwatch;
	private Scenario scenario;
	private long buildDuration;
	private long fullDuration;
	private MyTransitScheduleImpl schedule;

	AdvanceJob(int stopwatch, LinkedList<PassengerDelayPerson> persons, Scenario scenario, MyTransitScheduleImpl schedule){
		this.persons = persons;
		this.stopwatch = stopwatch;
		this.scenario = scenario;
		this.schedule = schedule;
	}


	@Override
	public void run() {
		try{
			long backThen = System.currentTimeMillis();

			//	System.out.println("Free memory before: " +
			//			Runtime.getRuntime().freeMemory() /1000000000.);

			MySwissRailRaptorData data = null;
			MySwissRailRaptor raptor = null;
			if(RunMatsim.adaptivenessType != RunMatsim.AdaptivenessType.RIGID){
				Config config = this.scenario.getConfig();


				RaptorParametersForPerson parameters = new DefaultRaptorParametersForPerson(config);
				
				RaptorStaticConfig staticConfig = RunMatsim.createRaptorStaticConfig(config);
				
				this.schedule = CreateBaseTransitSchedule.clearTransitSchedule(this.schedule);
				this.schedule= CreateBaseTransitSchedule.addSchedule(this.schedule, RunMatsim.date, stopwatch);
		//		TransitScheduleWriter schedWriter = new TransitScheduleWriter(CreateBaseTransitSchedule.createScheduleFromMyTransitScheduleImpl(schedule));			
		//		schedWriter.writeFile("/work1/s103232/PassengerDelay/Diagnostics/alongSchedule_" + this.stopwatch + ".xml");
	
				data = MySwissRailRaptorData.create(this.schedule, staticConfig);

				long backMiddle = System.currentTimeMillis();
				buildDuration = (backMiddle - backThen)/1000;
				System.out.print( buildDuration + "s ");
				
				raptor = new MySwissRailRaptor(data, parameters);
			}


			for(PassengerDelayPerson person : persons){
				person.setStopwatch(stopwatch);
				if(RunMatsim.adaptivenessType != RunMatsim.AdaptivenessType.RIGID){
					person.setRaptor(raptor);
				}
				person.advance();
			}
			raptor = null;
			data = null;
			long backNow = System.currentTimeMillis();
			fullDuration = (backNow - backThen)/1000;

			//		System.out.println("Free memory after " + (int) (backNow-backThen)/1000 + " seconds: " +
			//				Runtime.getRuntime().freeMemory() / 1000000000.);
		} catch(Exception e){
			e.printStackTrace();
			//	System.err.println("An advance job for person " + person.id + " did not terminate");
			System.err.println("An advance job did not terminate");

			System.exit(-1);
		}

	}

	double getFullDuration(){
		return fullDuration;
	}

	double getBuildDuration(){
		return buildDuration;
	}


}
