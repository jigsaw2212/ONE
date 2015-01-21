/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package routing;

import java.util.List;

import core.ConnectionListener;
import core.DTNHost;
import core.UpdateListener;

/**
 * A report of the distribution of how many encounters (contacts) a node has had 
 * 
 * @author Frans Ekman
 */
public class TotalEncounters implements ConnectionListener,
	UpdateListener {

	private int[] encounters;
	
	public TotalEncounters() {
		//System.out.println("constructor");
		
	}
	
	public void hostsConnected(DTNHost host1, DTNHost host2) {
		System.out.println("hostsConnected");
		if (this.encounters == null) {
			return;
		}
		this.encounters[host1.getAddress()]++;
		this.encounters[host2.getAddress()]++;
	}

	public void hostsDisconnected(DTNHost host1, DTNHost host2) {}

	public void updated(List<DTNHost> hosts) {
		System.out.println("updated");
		if (this.encounters == null) {
			this.encounters = new int[hosts.size()];
		}
	}

	

	public int[] getEncounters() {
		return this.encounters;
	}

	public void setEncounters(int[] encounters) {
		this.encounters = encounters;
	}
	
}
