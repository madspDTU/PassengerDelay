package org.matsim.project;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.scenario.ScenarioUtils;

public class RemoveFailedTripsFromPopulation {

	public static void main(String[] args) throws IOException {

		HashMap<Id<Person>, HashSet<Integer>> lateTrips = new HashMap<Id<Person>, HashSet<Integer>>();
		HashMap<Id<Person>, HashSet<Integer>> failTrips = new HashMap<Id<Person>, HashSet<Integer>>();

		for(int i = 1; i <= 4; i++) {
			BufferedReader br = new BufferedReader(new FileReader("/work1/s103232/PassengerDelay/OtherInput/FailingTrips_part" + i + ".csv"));
			String readLine = br.readLine();
			while((readLine = br.readLine()) != null) {
				String[] splitLine = readLine.split(";");	
				Id<Person> agent = Id.create(splitLine[0], Person.class);
				int trip = Integer.valueOf(splitLine[1]);
				boolean late = "1".contentEquals(splitLine[2]) ? true : false;

				if(late) {
					if(!lateTrips.containsKey(agent)) {
						lateTrips.put(agent, new HashSet<Integer>() );
					}
					lateTrips.get(agent).add(trip);
				} else if ("0".contentEquals(splitLine[2])) {
					if(!failTrips.containsKey(agent)) {
						failTrips.put(agent, new HashSet<Integer>() );
					}
					failTrips.get(agent).add(trip);
				}
			}
		}



		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		PopulationReader populationReader = new PopulationReader(scenario);
		populationReader.readFile("/work1/s103232/PassengerDelay/OtherInput/PTPlans_CPH_BASE_Original.xml.gz");

		System.out.println("Persons before: " + scenario.getPopulation().getPersons().size());
		int ptTrips = 0;
		for(Person person : scenario.getPopulation().getPersons().values()) {
			for(Leg leg : PopulationUtils.getLegs(person.getPlans().get(0))) {
				if(leg.getMode().equals(TransportMode.pt)) {
					ptTrips++;
				}
			}
		}
		System.out.println("Trips before: " + ptTrips);

		if(true) {

			LinkedList<Id<Person>> personsToRemove = new LinkedList<Id<Person>>();
			for(Person person : scenario.getPopulation().getPersons().values()) {
				int tripI = 0;
				int n = person.getPlans().get(0).getPlanElements().size();
				for(int i = 0; i < n; i++) {
					PlanElement pe = person.getPlans().get(0).getPlanElements().get(i);
					if(pe instanceof Activity) {

					} else if(pe instanceof Leg) {
						tripI++;
						if(lateTrips.containsKey(person.getId()) && lateTrips.get(person.getId()).contains(tripI)) {
							while( n > i ) {
								person.getPlans().get(0).getPlanElements().remove(n -1);
								n--;
							}
						} else if(failTrips.containsKey(person.getId()) && failTrips.get(person.getId()).contains(tripI)) {
							((Leg) pe).setMode("Teleportation");
						}
					}
				}
				boolean removePerson = true;
				for(Leg leg : PopulationUtils.getLegs(person.getPlans().get(0))) {
					if(leg.getMode().contentEquals(TransportMode.pt)) {
						removePerson = false;
						break;
					}
				}
				if(removePerson) {
					personsToRemove.add(person.getId());
				} 
			}

			for(Id<Person> id : personsToRemove) {
				scenario.getPopulation().removePerson(id);
			}

			System.out.println("Persons after: " + scenario.getPopulation().getPersons().size());
			ptTrips = 0;
			for(Person person : scenario.getPopulation().getPersons().values()) {
				for(Leg leg : PopulationUtils.getLegs(person.getPlans().get(0))) {
					if(leg.getMode().equals(TransportMode.pt)) {
						ptTrips++;
					}
				}
			}
			System.out.println("Trips after: " + ptTrips);

		}

		PopulationWriter writer = new PopulationWriter(scenario.getPopulation());
		writer.write("/work1/s103232/PassengerDelay/OtherInput/PTPlans_CPH.xml.gz");

	}
}
