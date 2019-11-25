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

public class AdvanceJob  implements Runnable {

	final private LinkedList<PassengerDelayPerson> persons;
	final int stopwatch;
	private Scenario scenario;
	private long buildDuration;
	private long fullDuration;

	AdvanceJob(int stopwatch, LinkedList<PassengerDelayPerson> persons, Scenario scenario){
		this.persons = persons;
		this.stopwatch = stopwatch;
		this.scenario = scenario;
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
			scenario = CreateBaseTransitSchedule.addTrainSchedule(scenario, 
					RunMatsim.INPUT_FOLDER + "/Disaggregate/Train/" + RunMatsim.date + "/DisaggregateSchedule_" + 
							RunMatsim.date + "_" + stopwatch + ".csv");
			scenario = CreateBaseTransitSchedule.addBusSchedule(scenario, 
					RunMatsim.INPUT_FOLDER + "/Disaggregate/Bus/" + RunMatsim.date + "/DisaggregateBusSchedule_" + 
							RunMatsim.date + "_" + stopwatch + ".csv");
			scenario = CreateBaseTransitSchedule.addStaticSchedule(scenario, 
					RunMatsim.INPUT_FOLDER + "/BaseSchedules/MetroSchedule.xml.gz", stopwatch);
			scenario = CreateBaseTransitSchedule.addStaticSchedule(scenario, 
					RunMatsim.INPUT_FOLDER + "/BaseSchedules/LocalTrainSchedule.xml.gz", stopwatch);


			SwissRailRaptorData data = SwissRailRaptorData.create(scenario.getTransitSchedule(), staticConfig ,
					scenario.getNetwork());

			long backMiddle = System.currentTimeMillis();
			buildDuration = (backMiddle - backThen)/1000;
			System.out.print( buildDuration + "s ");
			
			MySwissRailRaptor raptor = new MySwissRailRaptor(data, arg3, arg4, stopFinder);
			
			
			for(PassengerDelayPerson person : persons){
				person.setStopwatch(stopwatch);
				person.setRaptor(raptor);
				person.advance();
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
