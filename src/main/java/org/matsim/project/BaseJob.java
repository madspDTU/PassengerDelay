package org.matsim.project;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.core.config.Config;
import org.matsim.core.router.RoutingModule;

import ch.sbb.matsim.routing.pt.raptor.DefaultRaptorIntermodalAccessEgress;
import ch.sbb.matsim.routing.pt.raptor.DefaultRaptorParametersForPerson;
import ch.sbb.matsim.routing.pt.raptor.DefaultRaptorStopFinder;
import ch.sbb.matsim.routing.pt.raptor.LeastCostRaptorRouteSelector;
import ch.sbb.matsim.routing.pt.raptor.RaptorIntermodalAccessEgress;
import ch.sbb.matsim.routing.pt.raptor.RaptorParametersForPerson;
import ch.sbb.matsim.routing.pt.raptor.RaptorRouteSelector;
import ch.sbb.matsim.routing.pt.raptor.RaptorStaticConfig;
import ch.sbb.matsim.routing.pt.raptor.RaptorStopFinder;
import ch.sbb.matsim.routing.pt.raptor.MySwissRailRaptor;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorData;
import ch.sbb.matsim.routing.pt.raptor.RaptorStaticConfig.RaptorOptimization;

public class BaseJob  implements Runnable {

	final private LinkedList<PassengerDelayPerson> persons;
	final int stopwatch;
	private Scenario scenario;
	private long buildDuration;
	private long fullDuration;
	private String date;

	BaseJob(int stopwatch, LinkedList<PassengerDelayPerson> persons, Scenario scenario, String date){
		this.persons = persons;
		this.stopwatch = stopwatch;
		this.scenario = scenario;
		this.date = date;
	}


	@Override
	public void run() {
		try{
			long backThen = System.currentTimeMillis();
			
		//	System.out.println("Free memory before: " +
		//			Runtime.getRuntime().freeMemory() /1000000000.);
			Config config = this.scenario.getConfig();
		

			RaptorParametersForPerson arg3 = new DefaultRaptorParametersForPerson(config);
			
			RaptorRouteSelector arg4 = new LeastCostRaptorRouteSelector();

			RaptorIntermodalAccessEgress iae = new DefaultRaptorIntermodalAccessEgress();
			Map<String, RoutingModule> routingModuleMap = new HashMap<String, RoutingModule>();

			RaptorStopFinder stopFinder = new DefaultRaptorStopFinder(scenario.getPopulation(), iae, routingModuleMap);

			RaptorStaticConfig staticConfig = RunMatsim.createRaptorStaticConfig(config);

			this.scenario = CreateBaseTransitSchedule.clearTransitSchedule(scenario);
			this.scenario = CreateBaseTransitSchedule.addBaseSchedule(scenario, date);
			
			SwissRailRaptorData data = SwissRailRaptorData.create(scenario.getTransitSchedule(), staticConfig ,
					scenario.getNetwork());

			long backMiddle = System.currentTimeMillis();
			buildDuration = (backMiddle - backThen)/1000;
			System.out.print( buildDuration + "s ");
			
			MySwissRailRaptor raptor = new MySwissRailRaptor(data, arg3, arg4, stopFinder);
			int counter = 0;
			for(PassengerDelayPerson person : persons){
				person.setStopwatch(stopwatch);
				person.setRaptor(raptor);
				if(RunMatsim.adaptivenessType == RunMatsim.AdaptivenessType.RIGID){
					person.createAllRoutesOfDay();
				} else {
					person.createEntireDayEvents();

				}
				counter++;
				if(counter % 5000 == 0){
					System.out.println(counter + " persons processed by this thread");
				}
			}
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
