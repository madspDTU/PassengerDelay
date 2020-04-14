package org.matsim.project;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.misc.OptionalTime;

public class RemoveFailedTripsFromPopulation {

	public static void main(String[] args) throws IOException {

		HashMap<Id<Person>, HashSet<Integer>> lateTrips = new HashMap<Id<Person>, HashSet<Integer>>();
		HashMap<Id<Person>, HashSet<Integer>> failTrips = new HashMap<Id<Person>, HashSet<Integer>>();
		HashSet<Id<Person>> cutOffTrips = new HashSet<Id<Person>>();
		HashSet<Id<Person>> repetitiveTrips = new HashSet<Id<Person>>();
		HashSet<Id<Person>> unfinishedTrips = new HashSet<Id<Person>>();
		HashSet<Id<Person>> cutOffTrips2 = new HashSet<Id<Person>>();
		HashSet<Id<Person>> repetitiveTrips2 = new HashSet<Id<Person>>();
		HashSet<Id<Person>> unfinishedTrips2 = new HashSet<Id<Person>>();
		HashSet<Id<Person>> cutOffTrips3 = new HashSet<Id<Person>>();
		HashSet<Id<Person>> repetitiveTrips3 = new HashSet<Id<Person>>();
		HashSet<Id<Person>> unfinishedTrips3 = new HashSet<Id<Person>>();
		HashSet<Id<Person>> cutOffTrips4 = new HashSet<Id<Person>>();
		HashSet<Id<Person>> repetitiveTrips4 = new HashSet<Id<Person>>();
		HashSet<Id<Person>> unfinishedTrips4 = new HashSet<Id<Person>>();
		HashSet<Id<Person>> cutOffTrips5 = new HashSet<Id<Person>>();
		HashSet<Id<Person>> repetitiveTrips5 = new HashSet<Id<Person>>();
		HashSet<Id<Person>> unfinishedTrips5 = new HashSet<Id<Person>>();
		HashSet<Id<Person>> cutOffTrips6 = new HashSet<Id<Person>>();
		HashSet<Id<Person>> repetitiveTrips6 = new HashSet<Id<Person>>();
		HashSet<Id<Person>> unfinishedTrips6 = new HashSet<Id<Person>>();

		
		LinkedList<Id<Person>> forgetItTrips = new LinkedList<Id<Person>>();
		LinkedList<Id<Person>> certifiedTrips = new LinkedList<Id<Person>>();
		LinkedList<Id<Person>> certifiedTrips2 = new LinkedList<Id<Person>>();
		LinkedList<Id<Person>> certifiedTrips3 = new LinkedList<Id<Person>>();
		LinkedList<Id<Person>> certifiedTrips4 = new LinkedList<Id<Person>>();
		LinkedList<Id<Person>> certifiedTrips5 = new LinkedList<Id<Person>>();
		LinkedList<Id<Person>> certifiedTrips6 = new LinkedList<Id<Person>>();



		BufferedReader br = new BufferedReader(new FileReader("/work1/s103232/PassengerDelay/OtherInput/cutOffAgentIds.csv"));
		String readLine = br.readLine();
		while((readLine = br.readLine()) != null) {
			String[] splitLine = readLine.split(";");	
			Id<Person> agent = Id.create(splitLine[0], Person.class);
			cutOffTrips.add(agent);
		}

		br = new BufferedReader(new FileReader("/work1/s103232/PassengerDelay/OtherInput/repetitiveAgentIds.csv"));
		readLine = br.readLine();
		while((readLine = br.readLine()) != null) {
			String[] splitLine = readLine.split(";");	
			Id<Person> agent = Id.create(splitLine[0], Person.class);
			repetitiveTrips.add(agent);
		}

		br = new BufferedReader(new FileReader("/work1/s103232/PassengerDelay/OtherInput/unfinishedAgentIds.csv"));
		readLine = br.readLine();
		while((readLine = br.readLine()) != null) {
			String[] splitLine = readLine.split(";");	
			Id<Person> agent = Id.create(splitLine[0], Person.class);
			unfinishedTrips.add(agent);
		}
	
		


		br = new BufferedReader(new FileReader("/work1/s103232/PassengerDelay/OtherInput/cutOffAgentIds_2.csv"));
		readLine = br.readLine();
		while((readLine = br.readLine()) != null) {
			String[] splitLine = readLine.split(";");	
			Id<Person> agent = Id.create(splitLine[0], Person.class);
			cutOffTrips2.add(agent);
		}
	
		br = new BufferedReader(new FileReader("/work1/s103232/PassengerDelay/OtherInput/repetitiveAgentIds_2.csv"));
		readLine = br.readLine();
		while((readLine = br.readLine()) != null) {
			String[] splitLine = readLine.split(";");	
			Id<Person> agent = Id.create(splitLine[0], Person.class);
			repetitiveTrips2.add(agent);
		}
	
		br = new BufferedReader(new FileReader("/work1/s103232/PassengerDelay/OtherInput/unfinishedAgentIds_2.csv"));
		readLine = br.readLine();
		while((readLine = br.readLine()) != null) {
			String[] splitLine = readLine.split(";");	
			Id<Person> agent = Id.create(splitLine[0], Person.class);
			unfinishedTrips2.add(agent);
		}

				
				br = new BufferedReader(new FileReader("/work1/s103232/PassengerDelay/OtherInput/cutOffAgentIds_3.csv"));
				readLine = br.readLine();
				while((readLine = br.readLine()) != null) {
					String[] splitLine = readLine.split(";");	
					Id<Person> agent = Id.create(splitLine[0], Person.class);
					cutOffTrips3.add(agent);
				}
		
				br = new BufferedReader(new FileReader("/work1/s103232/PassengerDelay/OtherInput/repetitiveAgentIds_3.csv"));
				readLine = br.readLine();
				while((readLine = br.readLine()) != null) {
					String[] splitLine = readLine.split(";");	
					Id<Person> agent = Id.create(splitLine[0], Person.class);
					repetitiveTrips3.add(agent);
				}
		
				br = new BufferedReader(new FileReader("/work1/s103232/PassengerDelay/OtherInput/unfinishedAgentIds_3.csv"));
				readLine = br.readLine();
				while((readLine = br.readLine()) != null) {
					String[] splitLine = readLine.split(";");	
					Id<Person> agent = Id.create(splitLine[0], Person.class);
					unfinishedTrips3.add(agent);
				}
				
				
				
		
				br = new BufferedReader(new FileReader("/work1/s103232/PassengerDelay/OtherInput/cutOffAgentIds_4.csv"));
				readLine = br.readLine();
				while((readLine = br.readLine()) != null) {
					String[] splitLine = readLine.split(";");	
					Id<Person> agent = Id.create(splitLine[0], Person.class);
					cutOffTrips4.add(agent);
				}
		
				br = new BufferedReader(new FileReader("/work1/s103232/PassengerDelay/OtherInput/repetitiveAgentIds_4.csv"));
				readLine = br.readLine();
				while((readLine = br.readLine()) != null) {
					String[] splitLine = readLine.split(";");	
					Id<Person> agent = Id.create(splitLine[0], Person.class);
					repetitiveTrips4.add(agent);
				}
		
				br = new BufferedReader(new FileReader("/work1/s103232/PassengerDelay/OtherInput/unfinishedAgentIds_4.csv"));
				readLine = br.readLine();
				while((readLine = br.readLine()) != null) {
					String[] splitLine = readLine.split(";");	
					Id<Person> agent = Id.create(splitLine[0], Person.class);
					unfinishedTrips4.add(agent);
				}
				
				
				
				br = new BufferedReader(new FileReader("/work1/s103232/PassengerDelay/OtherInput/cutOffAgentIds_5.csv"));
				readLine = br.readLine();
				while((readLine = br.readLine()) != null) {
					String[] splitLine = readLine.split(";");	
					Id<Person> agent = Id.create(splitLine[0], Person.class);
					cutOffTrips5.add(agent);
				}
		
				br = new BufferedReader(new FileReader("/work1/s103232/PassengerDelay/OtherInput/repetitiveAgentIds_5.csv"));
				readLine = br.readLine();
				while((readLine = br.readLine()) != null) {
					String[] splitLine = readLine.split(";");	
					Id<Person> agent = Id.create(splitLine[0], Person.class);
					repetitiveTrips5.add(agent);
				}
		
				br = new BufferedReader(new FileReader("/work1/s103232/PassengerDelay/OtherInput/unfinishedAgentIds_5.csv"));
				readLine = br.readLine();
				while((readLine = br.readLine()) != null) {
					String[] splitLine = readLine.split(";");	
					Id<Person> agent = Id.create(splitLine[0], Person.class);
					unfinishedTrips5.add(agent);
				}
				
				
				
				br = new BufferedReader(new FileReader("/work1/s103232/PassengerDelay/OtherInput/cutOffAgentIds_6.csv"));
				readLine = br.readLine();
				while((readLine = br.readLine()) != null) {
					String[] splitLine = readLine.split(";");	
					Id<Person> agent = Id.create(splitLine[0], Person.class);
					cutOffTrips6.add(agent);
				}
		
				br = new BufferedReader(new FileReader("/work1/s103232/PassengerDelay/OtherInput/repetitiveAgentIds_6.csv"));
				readLine = br.readLine();
				while((readLine = br.readLine()) != null) {
					String[] splitLine = readLine.split(";");	
					Id<Person> agent = Id.create(splitLine[0], Person.class);
					repetitiveTrips6.add(agent);
				}
		
				br = new BufferedReader(new FileReader("/work1/s103232/PassengerDelay/OtherInput/unfinishedAgentIds_6.csv"));
				readLine = br.readLine();
				while((readLine = br.readLine()) != null) {
					String[] splitLine = readLine.split(";");	
					Id<Person> agent = Id.create(splitLine[0], Person.class);
					unfinishedTrips6.add(agent);
				}
		
		
				br = new BufferedReader(new FileReader("/work1/s103232/PassengerDelay/OtherInput/forgetItAgentIds_7.csv"));
				readLine = br.readLine();
				while((readLine = br.readLine()) != null) {
					String[] splitLine = readLine.split(";");	
					Id<Person> agent = Id.create(splitLine[0], Person.class);
					forgetItTrips.add(agent);
				}

		System.out.println(cutOffTrips.size() + " 1st CutOff Trips size");
		System.out.println(repetitiveTrips.size() + " 1st Repetitive Trips size");
		System.out.println(unfinishedTrips.size() + " 1st Unfinished Trips size");
		System.out.println(cutOffTrips2.size() + " 2nd CutOff Trips size");
		System.out.println(repetitiveTrips2.size() + " 2nd Repetitive Trips size");
		System.out.println(unfinishedTrips2.size() + " 2nd Unfinished Trips size");
		System.out.println(cutOffTrips3.size() + " 3rd CutOff Trips size");
		System.out.println(repetitiveTrips3.size() + " 3rd Repetitive Trips size");
		System.out.println(unfinishedTrips3.size() + " 3rd Unfinished Trips size");
		System.out.println(cutOffTrips4.size() + " 4th CutOff Trips size");
		System.out.println(repetitiveTrips4.size() + " 4th Repetitive Trips size");
		System.out.println(unfinishedTrips4.size() + " 4th Unfinished Trips size");	
		System.out.println(cutOffTrips5.size() + " 5th cutOff Trips size");
		System.out.println(repetitiveTrips5.size() + " 5th Repetitive Trips size");
		System.out.println(unfinishedTrips5.size() + " 5th Unfinished Trips size");	
		System.out.println(cutOffTrips6.size() + " 6th cutOff Trips size");
		System.out.println(repetitiveTrips6.size() + " 6th Repetitive Trips size");
		System.out.println(unfinishedTrips6.size() + " 6th Unfinished Trips size");	
		


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



		PopulationWriter writer = new PopulationWriter(scenario.getPopulation());
		writer.write("/work1/s103232/PassengerDelay/OtherInput/PTPlans_CPH.xml.gz");

		Scenario tripScenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());

		int tripId = 0;
		for(Person person : scenario.getPopulation().getPersons().values()) {
			List<PlanElement> plan = person.getPlans().get(0).getPlanElements();
			for(int i = 0; i < plan.size(); i++) {
				PlanElement pe = plan.get(i);
				if(pe instanceof Leg && ((Leg) pe).getMode().contentEquals(TransportMode.pt)) {
					tripId++;
					Person tripPerson = tripScenario.getPopulation().getFactory().createPerson(Id.createPersonId(tripId));
					Plan tripPlan = PopulationUtils.createPlan(tripPerson);
					tripPlan.addActivity(cloneActivity((Activity) plan.get(i-1)));
					tripPlan.addLeg(PopulationUtils.createLeg(TransportMode.pt));
					tripPlan.addActivity(cloneActivity((Activity) plan.get(i+1)));
					tripPlan.setPerson(tripPerson);
					tripPerson.addPlan(tripPlan);
					tripScenario.getPopulation().addPerson(tripPerson);
				}
			}
		}


		System.out.println("Trips after: " + tripScenario.getPopulation().getPersons().size());


		writer = new PopulationWriter(tripScenario.getPopulation());
		writer.write("/work1/s103232/PassengerDelay/OtherInput/PTPlans_CPH_TripBased_All.xml.gz");


		for(Person person : tripScenario.getPopulation().getPersons().values()) {
			Id<Person> id = person.getId();
			Activity firstActivity = (Activity) person.getPlans().get(0).getPlanElements().get(0);
			double endTime = firstActivity.getEndTime().seconds();
			int multiplier = endTime < 14 * 3600 ? -1 : 1;
			Coord coord = firstActivity.getCoord();
			if(repetitiveTrips.contains(id)){
				endTime -= 47 * 60. * multiplier;
				if(repetitiveTrips2.contains(id)) {
					endTime -= 41 * 60 * multiplier;
					if(repetitiveTrips3.contains(id)) {
						endTime -= 34 * 60 * multiplier;
						if(repetitiveTrips4.contains(id)) {
							endTime -= 79 * 60 * multiplier;
							if(repetitiveTrips5.contains(id)) {
								endTime -= 13 * 60 * multiplier;
								if(repetitiveTrips6.contains(id)) {
									endTime -= 19 * 60 * multiplier;
								} 
							} 
						} 
					} 
				} 
			}
			multiplier = endTime < 14 * 3600 ? -1 : 1;
			if (unfinishedTrips.contains(id)) {
				endTime -= 3600 * multiplier;
				if(unfinishedTrips2.contains(id)) {
					endTime -= 2*3600 * multiplier;
					if(unfinishedTrips3.contains(id)) {
						endTime -= 2*3600 * multiplier;
						if(unfinishedTrips4.contains(id)) {
							endTime -= 2*3600 * multiplier;
							if(unfinishedTrips5.contains(id)) {
								endTime -= 2*3600 * multiplier;
								if(unfinishedTrips6.contains(id)) {
									endTime -= 2*3600 * multiplier;
								}
							}
						} 
					} 
				} 
			}
			if (cutOffTrips.contains(id)) {
				coord = new Coord(coord.getX() - 500., coord.getY() - 500.);
				if(cutOffTrips2.contains(id)) {
					coord = new Coord(coord.getX() - 500., coord.getY() - 500.);
					if(cutOffTrips3.contains(id)) {
						coord = new Coord(coord.getX() - 500., coord.getY() - 500.);
						if(cutOffTrips4.contains(id)) {
							coord = new Coord(coord.getX() - 500., coord.getY() - 500.);
							if(cutOffTrips5.contains(id)) {
								coord = new Coord(coord.getX() - 500., coord.getY() - 500.);
								if(cutOffTrips6.contains(id)) {
									coord = new Coord(coord.getX() - 500., coord.getY() - 500.);
								}
							}
						}
					}
				}
			}
			firstActivity.setEndTime(endTime);
			firstActivity.setCoord(coord);
			((Activity) person.getPlans().get(0).getPlanElements().get(2)).setEndTime(RunMatsim.endTime);	
			if(!(cutOffTrips.contains(id) || repetitiveTrips.contains(id) || unfinishedTrips.contains(id))) {
				certifiedTrips.add(id);
			} else if (!(cutOffTrips2.contains(id) || repetitiveTrips2.contains(id) || unfinishedTrips2.contains(id))) {
				certifiedTrips2.add(id);
			} else if (!(cutOffTrips3.contains(id) || repetitiveTrips3.contains(id) || unfinishedTrips3.contains(id))) {
				certifiedTrips3.add(id);
			} else if (!(cutOffTrips4.contains(id) || repetitiveTrips4.contains(id) || unfinishedTrips4.contains(id))) {
				certifiedTrips4.add(id);
			} else if (!(cutOffTrips5.contains(id) || repetitiveTrips5.contains(id) || unfinishedTrips5.contains(id))) {
				certifiedTrips5.add(id);
			} else if (!(cutOffTrips6.contains(id) || repetitiveTrips6.contains(id) || unfinishedTrips6.contains(id))) {
				certifiedTrips6.add(id);
			} 
		}
		
		
		System.out.println(certifiedTrips.size() + " was okay right from the beginning.");
		System.out.println("Additional " + certifiedTrips2.size() + " was fixed after 2nd round.");
		System.out.println("Additional " + certifiedTrips3.size() + " was fixed after 3rd round.");
		System.out.println("Additional " + certifiedTrips4.size() + " was fixed after 4rd round.");
		System.out.println("Additional " + certifiedTrips5.size() + " was fixed after 5th round.");
		System.out.println("Additional " + certifiedTrips6.size() + " was fixed after 6th round.");

//		for(Id<Person> person : certifiedTrips) {
//			tripScenario.getPopulation().removePerson(person);
//		}
//		for(Id<Person> person : certifiedTrips2) {
//			tripScenario.getPopulation().removePerson(person);
//		}
//		for(Id<Person> person : certifiedTrips3) {
//			tripScenario.getPopulation().removePerson(person);
//		}
//		for(Id<Person> person : certifiedTrips4) {
//			tripScenario.getPopulation().removePerson(person);
//		}
//		for(Id<Person> person : certifiedTrips5) {
//			tripScenario.getPopulation().removePerson(person);
//		}
//		for(Id<Person> person : certifiedTrips6) {
//			tripScenario.getPopulation().removePerson(person);
//		}
		for(Id<Person> person : forgetItTrips) {
			tripScenario.getPopulation().removePerson(person);
		}

		System.out.println("Trips after: " + tripScenario.getPopulation().getPersons().size());
		writer = new PopulationWriter(tripScenario.getPopulation());
		writer.write("/work1/s103232/PassengerDelay/OtherInput/PTPlans_CPH_TripBased.xml.gz");
	}

	private static Activity cloneActivity(Activity act) {
		Activity newAct = PopulationUtils.createActivityFromCoord(act.getType(), new Coord(act.getCoord().getX(), act.getCoord().getY()));
		newAct.setEndTime(act.getEndTime().seconds());
		return newAct;
	}
}
