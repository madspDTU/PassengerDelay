package org.matsim.project;

import java.util.Arrays;
import java.util.HashSet;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.facilities.ActivityFacilities;

public class ExtractPTPopulation {

	final static int beginningOfDay = 3*3600;
	static PopulationFactory pf;

	public static void main(String[] args){

		HashSet<String> validModes = new HashSet<String>(Arrays.asList(TransportMode.pt));

		Config config = ConfigUtils.createConfig();
		Scenario scenario = ScenarioUtils.createScenario(config);
		Population population = scenario.getPopulation();
		PopulationReader pr = new PopulationReader(scenario);

		Config newConfig = ConfigUtils.createConfig();
		Scenario newScenario = ScenarioUtils.createScenario(newConfig);
		Population newPopulation = newScenario.getPopulation();
		pf = newPopulation.getFactory();
		PopulationWriter pw = new PopulationWriter(newPopulation);


		pr.readFile("/zhome/81/e/64390/git/matsim-example-project/input/full/plans_CPH.xml.gz");


		for(Person person : population.getPersons().values()){
			if(person.getId().toString().equals("585990_3_Person")) {
				
			Plan plan = person.getSelectedPlan();
			int i = 0;
			boolean[] keptElement = new boolean[plan.getPlanElements().size()];

			boolean atLeastOneElement = false;
			for(PlanElement pe : plan.getPlanElements()){
				if(pe instanceof Leg){
					Leg leg = (Leg) pe;
					if(validModes.contains(leg.getMode())){
						keptElement[i-1] = true;
						keptElement[i] = true;
						keptElement[i+1] = true;
						atLeastOneElement = true;
					} 
				}
				i++;
			}
			if(atLeastOneElement){
				Plan newPlan = PopulationUtils.createPlan();
				newPlan = addElementsToPlan(plan, keptElement, newPlan);
				addPlanToPersonAndPopulation(newPopulation, pf, person, newPlan);
			}
		}
		}




		pw.write("/work1/s103232/PassengerDelay/OtherInput/PTPlans_CPH_ONEPERSON.xml.gz");


		System.out.println("Total number of agents before: " + population.getPersons().size());
		System.out.println("Total number of agents now: " + newPopulation.getPersons().size());


	}



	private static Person addPlanToPersonAndPopulation(Population newPopulation, PopulationFactory pf, Person person,
			Plan newPlan) {
		if(newPlan.getPlanElements().size() <3 ){
			//do nothing - the person has no valid trips, and thus not qualified for the new population.
			return null;
		} else {

			Person newPerson = pf.createPerson(person.getId());
			newPlan.setPerson(newPerson);
			newPerson.addPlan(newPlan);
			newPerson.setSelectedPlan(newPlan);
			newPopulation.addPerson(newPerson);
			return newPerson;
		}
	}



	private static Plan addElementsToPlan(Plan plan, boolean[] keptElement, Plan newPlan) {	
		int i = 0;
		int firstIndexAfterThree = plan.getPlanElements().size();
		boolean firstIndexFound = false;
		for(PlanElement pe : plan.getPlanElements()){
			if(keptElement[i] && pe instanceof Activity){
				double endTime = ((Activity) pe).getEndTime().seconds();
				if(!firstIndexFound && endTime >= beginningOfDay){ 
					firstIndexFound = true;
					firstIndexAfterThree = i;		
				} 
			}
			i++;
		}

		i = 0;
		Coord prevActivityCoord = null;
		boolean firstActivityHasPassed = false;
		for(PlanElement pe : plan.getPlanElements()){
			if(keptElement[i] && i >= firstIndexAfterThree){
				if(pe instanceof Leg){
					Leg leg = (Leg) pe;
					newPlan.addLeg(leg);
				} else {
					Activity activity = (Activity) pe;
					if(newPlan.getPlanElements().size() > 0 &&
							newPlan.getPlanElements().get(newPlan.getPlanElements().size()-1) instanceof Activity ){
						newPlan.addLeg(pf.createLeg("Teleportation"));
					}

					if(activity.getCoord().equals(prevActivityCoord)){
						newPlan.getPlanElements().remove(newPlan.getPlanElements().size()-1);
					} else {
						Activity newActivity =  pf.createActivityFromCoord(activity.getType(), activity.getCoord());
						newActivity.setEndTime(activity.getEndTime().seconds());
						if(i == plan.getPlanElements().size()-1 && firstIndexAfterThree > 0 &&
								newActivity.getEndTime().seconds() > 24*3600){
							newActivity.setEndTime(24*3600);
							//Will be followed by a Teleportation leg in the following.
						}
						newPlan.addActivity(newActivity);
					}
					prevActivityCoord = activity.getCoord();
				}
			}
			i++;
		}
		i = 0;
		for(PlanElement pe : plan.getPlanElements()){
			if(keptElement[i] && i<= firstIndexAfterThree && firstIndexAfterThree > 0){	
				if(pe instanceof Leg){
					Leg leg = (Leg) pe;
					newPlan.addLeg(leg);
				} else {
					Activity activity = (Activity) pe;
					if(newPlan.getPlanElements().size() > 0 &&
							newPlan.getPlanElements().get(newPlan.getPlanElements().size()-1) instanceof Activity ){
						newPlan.addLeg(pf.createLeg("Teleportation"));
					}
					if(activity.getCoord().equals(prevActivityCoord)){
						newPlan.getPlanElements().remove(newPlan.getPlanElements().size()-1);		
					} else {
						Activity newActivity =  pf.createActivityFromCoord(activity.getType(), activity.getCoord());
						newActivity.setEndTime(activity.getEndTime().seconds() + 24*3600);
						newPlan.addActivity(newActivity);
					}
					prevActivityCoord = activity.getCoord();
				}
			}
			i++;
		}

		//Setting all final activities to last until 3am the following day;
		if(newPlan.getPlanElements().size()>0){
			((Activity) newPlan.getPlanElements().get(newPlan.getPlanElements().size()-1)).setEndTime(27*3600);
		}
		return newPlan;
	}
}
