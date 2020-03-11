package org.matsim.project;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Route;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ModeParams;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.routes.GenericRouteImpl;
import org.matsim.core.router.RoutingModule;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.scoring.SumScoringFunction;
import org.matsim.deprecated.scoring.functions.CharyparNagelActivityScoring;
import org.matsim.pt.routes.ExperimentalTransitRoute;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;

import ch.sbb.matsim.routing.pt.raptor.DefaultRaptorIntermodalAccessEgress;
import ch.sbb.matsim.routing.pt.raptor.DefaultRaptorParametersForPerson;
import ch.sbb.matsim.routing.pt.raptor.DefaultRaptorStopFinder;
import ch.sbb.matsim.routing.pt.raptor.LeastCostRaptorRouteSelector;
import ch.sbb.matsim.routing.pt.raptor.MySwissRailRaptor;
import ch.sbb.matsim.routing.pt.raptor.RaptorIntermodalAccessEgress;
import ch.sbb.matsim.routing.pt.raptor.RaptorParametersForPerson;
import ch.sbb.matsim.routing.pt.raptor.RaptorRouteSelector;
import ch.sbb.matsim.routing.pt.raptor.RaptorStaticConfig;
import ch.sbb.matsim.routing.pt.raptor.RaptorStopFinder;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptor;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorData;

public class MinimalExample {

	public static void main(String[] args) {
		
		for(int k = 0; k <= 1; k++) {

			Config config = createConfig(k);
			
			Scenario scenario = ScenarioUtils.createScenario(config);
			TransitScheduleReader reader = new TransitScheduleReader(scenario);
			reader.readFile("./Input/schedule.xml");


			RaptorParametersForPerson personParams = new DefaultRaptorParametersForPerson(config);
			RaptorRouteSelector routeSelector = new LeastCostRaptorRouteSelector();
			RaptorIntermodalAccessEgress iae = new DefaultRaptorIntermodalAccessEgress();
			Map<String, RoutingModule> routingModuleMap = new HashMap<String, RoutingModule>();
			RaptorStopFinder stopFinder = new DefaultRaptorStopFinder(scenario.getPopulation(), iae, routingModuleMap);
			RaptorStaticConfig staticConfig = RunMatsim.createRaptorStaticConfig(config);
			SwissRailRaptorData data = SwissRailRaptorData.create(scenario.getTransitSchedule(), staticConfig, scenario.getNetwork());
			SwissRailRaptor raptor = new SwissRailRaptor(data, personParams, routeSelector, stopFinder);

			double depTime = 70015.00;
			MyFakeFacility fromFac = new MyFakeFacility(new Coord(719025.7, 6177189));
			MyFakeFacility toFac = new MyFakeFacility(new Coord(  691950, 6192075));
			MyFakeFacility t3Fac = new MyFakeFacility(new Coord(  719525, 6176783.1 ));

			System.out.println(k == 0 ? "\n\nUnit utility" : "\n\nVarying utility:");

			System.out.println("Entire Trip: ");
			List<Leg> path = raptor.calcRoute(fromFac, toFac, depTime, null);
			printAboutPath(path, k);

			
			System.out.println("\nTo T3: ");
			path = raptor.calcRoute(fromFac, t3Fac, depTime, null);
			printAboutPath(path, k);
		}
	}


	private static Config createConfig(int k) {

		Config config = ConfigUtils.createConfig();
		config.transitRouter().setMaxBeelineWalkConnectionDistance(600.);
		config.transitRouter().setSearchRadius(3000.);
		config.transitRouter().setAdditionalTransferTime(0.);
		config.transitRouter().setExtensionRadius(5000.);
		config.transitRouter().setDirectWalkFactor(1.);

		config.plansCalcRoute().clearModeRoutingParams();
		config.plansCalcRoute().getOrCreateModeRoutingParams(TransportMode.walk).setBeelineDistanceFactor(1.);
		config.plansCalcRoute().getOrCreateModeRoutingParams(TransportMode.walk).setTeleportedModeSpeed(1.);
		config.plansCalcRoute().getOrCreateModeRoutingParams(TransportMode.transit_walk).setBeelineDistanceFactor(1.);
		config.plansCalcRoute().getOrCreateModeRoutingParams(TransportMode.transit_walk).setTeleportedModeSpeed(1.);

		ModeParams walkParams = new ModeParams(TransportMode.walk);
		walkParams.setMarginalUtilityOfDistance(0.);
		walkParams.setMarginalUtilityOfTraveling(k == 0 ? -1 : -1.3);
		config.planCalcScore().addModeParams(walkParams);
		ModeParams transitWalkParams = new ModeParams(TransportMode.transit_walk);
		transitWalkParams.setMarginalUtilityOfDistance(0.);
		transitWalkParams.setMarginalUtilityOfTraveling(k == 0 ? -1 : -1.3);
		config.planCalcScore().addModeParams(transitWalkParams);

		config.planCalcScore().setPerforming_utils_hr(0.);
		ModeParams busParams = new ModeParams("bus");
		busParams.setMarginalUtilityOfTraveling(-1.);
		busParams.setMarginalUtilityOfDistance(0);
		config.planCalcScore().addModeParams(busParams);
		ModeParams sTrainParams = new ModeParams("S-train");
		sTrainParams.setMarginalUtilityOfTraveling(k == 0 ? -1. : -0.9);
		sTrainParams.setMarginalUtilityOfDistance(0);
		config.planCalcScore().addModeParams(sTrainParams);

		config.planCalcScore().addModeParams(sTrainParams);
		config.planCalcScore().setMarginalUtlOfWaitingPt_utils_hr(k == 0 ? -1 : -1.6);
		config.planCalcScore().setUtilityOfLineSwitch(k == 0 ? 0 : -4./60.);

		return config;
	}


	private static void printAboutPath(List<Leg> path, int k) {
		double travelTime = 0;
		for(Leg leg : path) {
			Route rawRoute = leg.getRoute();
			if(rawRoute instanceof ExperimentalTransitRoute) {
				ExperimentalTransitRoute ptRoute = (ExperimentalTransitRoute) rawRoute;
				System.out.print(ptRoute.getAccessStopId() + " -> " + ptRoute.getEgressStopId());
			} else if (rawRoute instanceof GenericRouteImpl) {
				System.out.print(" Walk -> ");
			}
			travelTime += leg.getTravelTime();
		}
		System.out.print( ". TravelTime: " + travelTime);
	}



}


