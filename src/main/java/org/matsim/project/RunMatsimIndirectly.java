package org.matsim.project;

import java.io.IOException;

public class RunMatsimIndirectly {

	public static void run() throws IOException {
		String inputFolder = "c:/workAtHome/PassengerDelay";
		String date = "2014_09_01";
		String[] args = new String[]{inputFolder,date};
		RunMatsim.main(args);
	}
}
