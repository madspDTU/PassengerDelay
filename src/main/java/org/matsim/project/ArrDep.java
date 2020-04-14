package org.matsim.project;

public class ArrDep {
	public int arr;
	public int dep;
	
	ArrDep(double arr, double dep){
		this.arr = (int) Math.ceil(arr);
		this.dep = (int) Math.ceil(dep);
	}
}
