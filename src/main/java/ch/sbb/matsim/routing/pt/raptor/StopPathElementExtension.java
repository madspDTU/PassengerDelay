package ch.sbb.matsim.routing.pt.raptor;

import java.util.ArrayList;
import java.util.Collection;

import org.matsim.project.RunMatsim;

public class StopPathElementExtension {

	ArrayList<PathElement> container;
	int lowestArrivalTime;

	StopPathElementExtension(PathElement pe){
		this.container = new ArrayList<PathElement>();
		this.container.add(pe);
		this.lowestArrivalTime = pe.arrivalTime;
	}

	public PathElement get(int i) {
		return this.container.get(i);
	}

	public int size() {
		return container.size();
	}

	public Collection<PathElement> getPEs() {
		return this.container;
	}

	public boolean insertIfNotDominated(PathElement pe){
		if(this.container.size() == 0) {
			this.container.add(pe);
			this.lowestArrivalTime = pe.arrivalTime;
			return true;
		} 
		int buffer = RunMatsim.maxAllowedTimeDifferenceAtStop; //TODO, may be changed..
		if(pe.arrivalTime > lowestArrivalTime + buffer) {
			return false;
		} else if(pe.arrivalTime< lowestArrivalTime) {
			lowestArrivalTime = pe.arrivalTime;
			int n = this.container.size() - 1;
			while(n >= 0) {
				PathElement existingPE = this.container.get(n);
				if(existingPE.arrivalTime> lowestArrivalTime + buffer) {
					this.container.remove(n);
				} else {
					break;
				}
				n--;
			}
			this.container.add(0,pe);
			return true;
		} else {
			int placeToInsert;
			if (this.container.size() > 5) {
				placeToInsert = binarySearch(this.container, 1, this.container.size() - 1, pe.arrivalTime); //Cannot be 0, so ok.
				int n = placeToInsert; //If equal, the candidate has to be checked
				if(placeToInsert < 0) {
					// When binary search does not find a match, it returns -(insertion point) -1.
					placeToInsert = -placeToInsert -1;
					n = placeToInsert -1;  // Not equal, we can skip one.
				}
				while(n >= 0) {
					PathElement existingPE = this.container.get(n);
					if(pe.arrivalTime== existingPE.arrivalTime) { //Will happen as the first if key is present....
						if(pe.potentialAtArrival < existingPE.potentialAtArrival) {
							//this.container.remove(n); //Will automatically be removed later
							placeToInsert = n;
							break;
						} else {
							return false;
						}
					} else if(pe.potentialAtArrival >= existingPE.potentialAtArrival){
						return false;
					} 
					n--;
				}
			} else {
				placeToInsert = this.container.size();
				int n = placeToInsert -1;
				while(n >= 0) {
					PathElement existingPE = this.container.get(n);
					if(existingPE.arrivalTime== pe.arrivalTime) { //Will happen as the first if key is present....
						if(pe.potentialAtArrival < existingPE.potentialAtArrival ) {
							//this.container.remove(n); // Will automatically be removed later
							placeToInsert = n;
							break;
						} else {
							return false;
						}
					} else if(pe.arrivalTime> existingPE.arrivalTime) {
						if(pe.potentialAtArrival >= existingPE.potentialAtArrival) { //we have higher time and higher cost than existing
							return false;
						}
					} else { // We have lower time... we cannot be dominated by PEs with higher time.
						placeToInsert = n;
					}
					n--;
				}
			}
			int n = this.container.size();
			int i = placeToInsert;
			while(i < n) {
				PathElement existingPE = this.container.get(i);
				if(pe.potentialAtArrival <= existingPE.potentialAtArrival) {
					this.container.remove(i);
					n--;
				} else {
					break;
				}
			}
			this.container.add(placeToInsert, pe);
			return true;
		}
	}

	private int binarySearch(ArrayList<PathElement> arr, int l, int r, int x) { 
		if (r >= l) { 
			int mid = l + (r - l) / 2; 
			int value = arr.get(mid).arrivalTime;
			if (value == x) {
				return mid; 
			}
			if (value > x) {
				return binarySearch(arr,l, mid - 1, x); 
			}
			return binarySearch(arr,mid + 1, r, x); 
		} 
		return -l -1; // -insertionIndex - 1
	} 
}
