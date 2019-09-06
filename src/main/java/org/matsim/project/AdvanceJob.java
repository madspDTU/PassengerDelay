package org.matsim.project;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.router.RoutingModule;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitSchedule;

import ch.sbb.matsim.routing.pt.raptor.DefaultRaptorIntermodalAccessEgress;
import ch.sbb.matsim.routing.pt.raptor.DefaultRaptorParametersForPerson;
import ch.sbb.matsim.routing.pt.raptor.DefaultRaptorStopFinder;
import ch.sbb.matsim.routing.pt.raptor.LeastCostRaptorRouteSelector;
import ch.sbb.matsim.routing.pt.raptor.RaptorIntermodalAccessEgress;
import ch.sbb.matsim.routing.pt.raptor.RaptorParametersForPerson;
import ch.sbb.matsim.routing.pt.raptor.RaptorRouteSelector;
import ch.sbb.matsim.routing.pt.raptor.RaptorStaticConfig;
import ch.sbb.matsim.routing.pt.raptor.RaptorStopFinder;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptor;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorData;
import ch.sbb.matsim.routing.pt.raptor.RaptorStaticConfig.RaptorOptimization;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorData;

public class AdvanceJob  implements Runnable {

	final private LinkedList<PassengerDelayPerson> persons;
	final int stopwatch;
	private Scenario scenario;

	AdvanceJob(int stopwatch, LinkedList<PassengerDelayPerson> persons, Scenario scenario){
		this.persons = persons;
		this.stopwatch = stopwatch;
		this.scenario = scenario;
	}


	@Override
	public void run() {
		try{
			long backThen = System.currentTimeMillis();
			
			System.out.println("Free memory before: " +
					Runtime.getRuntime().freeMemory() /1000000000.);
			Config config = this.scenario.getConfig();
		

			RaptorParametersForPerson arg3 = new DefaultRaptorParametersForPerson(config);
			RaptorRouteSelector arg4 = new LeastCostRaptorRouteSelector();

			RaptorIntermodalAccessEgress iae = new DefaultRaptorIntermodalAccessEgress();
			Map<String, RoutingModule> routingModuleMap = new HashMap<String, RoutingModule>();

			RaptorStopFinder stopFinder = new DefaultRaptorStopFinder(scenario.getPopulation(), iae, routingModuleMap);

			RaptorStaticConfig staticConfig = new RaptorStaticConfig();
			staticConfig.setBeelineWalkConnectionDistance(
					config.transitRouter().getMaxBeelineWalkConnectionDistance());
			staticConfig.setBeelineWalkDistanceFactor(
					config.transitRouter().getDirectWalkFactor());
			staticConfig.setBeelineWalkSpeed(
					config.plansCalcRoute().getOrCreateModeRoutingParams(
							TransportMode.walk).getTeleportedModeSpeed());
			staticConfig.setMinimalTransferTime(
					config.transitRouter().getAdditionalTransferTime());
			staticConfig.setOptimization(RaptorOptimization.OneToOneRouting);
			staticConfig.setUseModeMappingForPassengers(false);

			this.scenario = CreateBaseTransitSchedule.clearTransitSchedule(scenario);
			scenario = CreateBaseTransitSchedule.addTrainSchedule(scenario, 
					RunMatsim.INPUT_FOLDER + "/Disaggregate/Train/" + RunMatsim.date + "/DisaggregateSchedule_" + 
							RunMatsim.date + "_" + stopwatch + ".csv");

			scenario = CreateBaseTransitSchedule.addBusSchedule(scenario, 
					RunMatsim.INPUT_FOLDER + "/Disaggregate/Bus/" + RunMatsim.date + "/DisaggregateBusSchedule_" + 
							RunMatsim.date + "_" + stopwatch + ".csv");


		
			SwissRailRaptorData data = SwissRailRaptorData.create(scenario.getTransitSchedule(), staticConfig ,
					scenario.getNetwork());


			
			SwissRailRaptor raptor = new SwissRailRaptor(data, arg3, arg4, stopFinder);
		
			for(PassengerDelayPerson person : persons){
				person.setStopwatch(stopwatch);
				person.setRaptor(raptor);
				person.advance();
			}
			long backNow = System.currentTimeMillis();
			
			System.out.println("Free memory after " + (int) (backNow-backThen)/1000 + " seconds: " +
					Runtime.getRuntime().freeMemory() / 1000000000.);
		} catch(Exception e){
			e.printStackTrace();
			//	System.err.println("An advance job for person " + person.id + " did not terminate");
			System.err.println("An advance job did not terminate");

			System.exit(-1);
		}

	}

}
