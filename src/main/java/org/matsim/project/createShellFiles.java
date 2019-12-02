package org.matsim.project;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class createShellFiles {

	public static void main(String[] args) throws IOException{
		
		String dir = "/zhome/81/e/64390/MATSim/PassengerDelay/";
		
		String part1 = "#!/bin/sh\n" +
				"#BSUB -q hpc\n" +
				"#BSUB -J PassengerDelay_";
		String part2 = "\n#BSUB -n 20\n" +
		"#BSUB -R \"span[hosts=1]\"\n" +
		"#BSUB -R \"rusage[mem=5500MB]\"\n" +
		"#BSUB -M 6GB\n" +
		"#BSUB -W 72:00\n\n" +
		"#BSUB -u madsp@dtu.dk\n" +
		"#BSUB -B\n" +
		"#BSUB -N\n" +
		"#BSUB -oo PassengerDelay_";
		String part3 = "_O\n" +
		"#BSUB -eo PassengerDelay_";
		String part4 = "_E\n\n" +
		"export JAVA_HOME=/usr/java/jdk-10.0.1/\n" +
		"cd /zhome/81/e/64390/git/PassengerDelayGit/\n" +
		"/zhome/81/e/64390/maven/bin/mvn install -DskipTests=true -q\n" +
		"export MAVEN_OPTS=-Xmx102g\n" +
		"/zhome/81/e/64390/maven/bin/mvn exec:java -Dexec.mainClass=\"org.matsim.project.RunMatsim\" -Dexec.args=\"/work1/s103232/PassengerDelay 2014_";
		String part5 = " 20 27\"";
		
		List<String> dates = Arrays.asList("09_01","09_02","09_03","09_04","09_05","09_08","09_09","09_10","09_11","09_12","09_15","09_16","09_17","09_18","09_19",
				"09_22","09_23","09_24","09_25","09_26","09_29","09_30","10_01","10_02","10_03","10_06","10_07","10_08","10_09","10_10",
				"10_13","10_14","10_15","10_16","10_17","10_20","10_21","10_22","10_23","10_24","10_27","10_28","10_29","10_30","10_31",
				"11_03","11_04","11_05","11_06","11_07","11_10","11_11","11_12","11_13","11_14","11_17","11_18","11_19","11_20","11_21",
				"11_24","11_25","11_26","11_27","11_28");
		for(String date : dates){
			FileWriter writer = new FileWriter(dir + "runPassengerDelay_2014_" + date + ".sh");
			writer.append(part1); writer.append(date); writer.append(part2); writer.append(date);
			writer.append(part3); writer.append(date); writer.append(part4); writer.append(date); writer.append(part5);
			writer.flush();
			writer.close();
		}
	}
}
