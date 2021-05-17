package no.paneon.api.model;

import java.util.HashMap;
import java.util.Map;

public class Counter {

	public Map<String,Integer> counts;

	public Counter() {
		this.counts=new HashMap<>();
	}

	public void increment(String label) {
		if(!counts.containsKey(label)) counts.put(label,0);
		counts.put(label,1+counts.get(label));
	}

}
