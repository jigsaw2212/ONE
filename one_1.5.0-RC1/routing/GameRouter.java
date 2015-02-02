/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package routing;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import routing.util.RoutingInfo;

import util.Tuple;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimClock;
import core.World;
import core.SimScenario;
import core.Coord;

/**
 * Implementation of Game router as described in 
 * <I>Probabilistic routing in intermittently connected networks</I> by
 * Anders Lindgren et al.
 */
public class GameRouter extends ActiveRouter {
	/** delivery predictability initialization constant*/
	public static final double P_INIT = 0.75;
	/** delivery predictability transitivity scaling constant default value */
	public static final double DEFAULT_BETA = 0.25;
	/** delivery predictability aging constant */
	public static final double GAMMA = 0.98;
	
	/** Game router's setting namespace ({@value})*/ 
	public static final String Game_NS = "GameRouter";
	/**
	 * Number of seconds in time unit -setting id ({@value}).
	 * How many seconds one time unit is when calculating aging of 
	 * delivery predictions. Should be tweaked for the scenario.*/
	public static final String SECONDS_IN_UNIT_S ="secondsInTimeUnit";
	
	/**
	 * Transitivity scaling constant (beta) -setting id ({@value}).
	 * Default value for setting is {@link #DEFAULT_BETA}.
	 */
	public static final String BETA_S = "beta";

	/** the value of nrof seconds in time unit -setting */
	private int secondsInTimeUnit;
	/** value of beta setting */
	private double beta;

	/** delivery predictabilities */
	private Map<DTNHost, Double> preds;
	/** last delivery predictability update (sim)time */
	private double lastAgeUpdate;

	/** The World where all actors of the simulator are */
	protected World world;

	/** Scenario of the current simulation */
	protected SimScenario scen;
	
	private static int[][] encounters;

	private static int[] sum;

	private List<DTNHost> nodes;

	/**
	 * Constructor. Creates a new message router based on the settings in
	 * the given Settings object.
	 * @param s The settings object
	 */
	public GameRouter(Settings s) {
		super(s);
		Settings GameSettings = new Settings(Game_NS);
		secondsInTimeUnit = GameSettings.getInt(SECONDS_IN_UNIT_S);
		if (GameSettings.contains(BETA_S)) {
			beta = GameSettings.getDouble(BETA_S);
		}
		else {
			beta = DEFAULT_BETA;
		}
		initModel();
		//initNodes();
		initPreds();
	}

	/**
	 * Copyconstructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected GameRouter(GameRouter r) {
		super(r);
		this.secondsInTimeUnit = r.secondsInTimeUnit;
		this.beta = r.beta;
		initModel();
		initPreds();
		//initNodes();
	}
	
	/**
	 * Initializes predictability hash
	 */
	private void initPreds() {
		this.preds = new HashMap<DTNHost, Double>();
	}

	private void initModel() {
		this.scen = SimScenario.getInstance();
		this.world = this.scen.getWorld();
	}

	private void initNodes()
	{
		this.nodes=getNodes();
	}

	@Override
	public void changedConnection(Connection con) {
		super.changedConnection(con);
		
		if (con.isUp()) {
			DTNHost otherHost = con.getOtherNode(getHost());
			updateEncounters(getHost(),otherHost);
			updateDeliveryPredFor(otherHost);
			updateTransitivePreds(otherHost);
		}
	}
	
	public void updateEncounters(DTNHost host1, DTNHost host2) {
		//each message has different destination and we'll need encounters of every node with the destination(which is changing with each message) in same time instance, hence we've decided to use a 2D array
		if (this.encounters == null) {
			this.encounters=new int[126][126];	//replace 126 with hosts.size()
		}
		if(this.sum == null){
			this.sum=new int[126];
		}
		GameRouter othRouter = (GameRouter)host2.getRouter();
		GameRouter myRouter = (GameRouter)host1.getRouter();
		if(myRouter!=othRouter)
		{
			this.encounters[host1.getAddress()][host2.getAddress()]++;
			this.sum[host1.getAddress()]++;
		}
		else
		{
			this.encounters[host1.getAddress()][host2.getAddress()]++;
			this.encounters[host2.getAddress()][host1.getAddress()]++;
			this.sum[host1.getAddress()]++;
			this.sum[host2.getAddress()]++;
		}
		//System.out.println(Arrays.toString(sum));
		//System.out.println("fu");
			
	}

	public int getEncounter(DTNHost host1,DTNHost host2){
		return this.encounters[host1.getAddress()][host2.getAddress()];
	}

	public int getSum(DTNHost host){
		return this.sum[host.getAddress()];
	}

	public List<DTNHost> getNodes(){
		
		return this.world.getHosts();
	}

	
	/**
	 * Updates delivery predictions for a host.
	 * <CODE>P(a,b) = P(a,b)_old + (1 - P(a,b)_old) * P_INIT</CODE>
	 * @param host The host we just met
	 */
	private void updateDeliveryPredFor(DTNHost host) {
		double oldValue = getPredFor(host);
		double newValue = oldValue + (1 - oldValue) * P_INIT;
		preds.put(host, newValue);
	}
	
	/**
	 * Returns the current prediction (P) value for a host or 0 if entry for
	 * the host doesn't exist.
	 * @param host The host to look the P for
	 * @return the current P value
	 */
	public double getPredFor(DTNHost host) {
		ageDeliveryPreds(); // make sure preds are updated before getting
		if (preds.containsKey(host)) {
			return preds.get(host);
		}
		else {
			return 0;
		}
	}
	
	/**
	 * Updates transitive (A->B->C) delivery predictions.
	 * <CODE>P(a,c) = P(a,c)_old + (1 - P(a,c)_old) * P(a,b) * P(b,c) * BETA
	 * </CODE>
	 * @param host The B host who we just met
	 */
	private void updateTransitivePreds(DTNHost host) {
		MessageRouter otherRouter = host.getRouter();
		assert otherRouter instanceof GameRouter : "Game only works " + 
			" with other routers of same type";
		
		double pForHost = getPredFor(host); // P(a,b)
		Map<DTNHost, Double> othersPreds = 
			((GameRouter)otherRouter).getDeliveryPreds();
		
		for (Map.Entry<DTNHost, Double> e : othersPreds.entrySet()) {
			if (e.getKey() == getHost()) {
				continue; // don't add yourself
			}
			
			double pOld = getPredFor(e.getKey()); // P(a,c)_old
			double pNew = pOld + ( 1 - pOld) * pForHost * e.getValue() * beta;
			preds.put(e.getKey(), pNew);
		}
	}

	/**
	 * Ages all entries in the delivery predictions.
	 * <CODE>P(a,b) = P(a,b)_old * (GAMMA ^ k)</CODE>, where k is number of
	 * time units that have elapsed since the last time the metric was aged.
	 * @see #SECONDS_IN_UNIT_S
	 */
	private void ageDeliveryPreds() {
		double timeDiff = (SimClock.getTime() - this.lastAgeUpdate) / 
			secondsInTimeUnit;
		
		if (timeDiff == 0) {
			return;
		}
		
		double mult = Math.pow(GAMMA, timeDiff);
		for (Map.Entry<DTNHost, Double> e : preds.entrySet()) {
			e.setValue(e.getValue()*mult);
		}
		
		this.lastAgeUpdate = SimClock.getTime();
	}
	
	/**
	 * Returns a map of this router's delivery predictions
	 * @return a map of this router's delivery predictions
	 */
	private Map<DTNHost, Double> getDeliveryPreds() {
		ageDeliveryPreds(); // make sure the aging is done
		return this.preds;
	}
	
	@Override
	public void update() {
		super.update();
		if (!canStartTransfer() ||isTransferring()) {
			return; // nothing to transfer or is currently transferring 
		}
		
		// try messages that could be delivered to final recipient
		if (exchangeDeliverableMessages() != null) {
			return;
		}
		
		tryOtherMessages();		
	}
	
	/**
	 * Tries to send all other messages to all connected hosts ordered by
	 * their delivery probability
	 * @return The return value of {@link #tryMessagesForConnected(List)}
	 */
	private Tuple<Message, Connection> tryOtherMessages() {
		List<Tuple<Message, Connection>> messages = 
			new ArrayList<Tuple<Message, Connection>>(); 
	
		Collection<Message> msgCollection = getMessageCollection();
		
		/* for all connected hosts collect all messages that have a higher
		   probability of delivery by the other host */
		for (Connection con : getConnections()) {

			DTNHost me = getHost();
			DTNHost other = con.getOtherNode(getHost());
			GameRouter othRouter = (GameRouter)other.getRouter();
			
			if (othRouter.isTransferring()) {
				continue; // skip hosts that are transferring
			}
			
			for (Message m : msgCollection) {
				if (othRouter.hasMessage(m.getId())) {
					continue; // skip messages that the other one has
				}
				DTNHost dest = m.getTo();
				/*if (othRouter.getDistFor(m.getTo()) > getDistFor(m.getTo())) {
					// the other node has higher probability of delivery
					messages.add(new Tuple<Message, Connection>(m,con));
				}*/
				//System.out.println("poop1");
				double alphaOther;
				double alphaMe;
				if(getSum(dest)==0)
					alphaOther=0;
				else
					alphaOther=getEncounter(dest,other)/getSum(dest);
				if(getSum(dest)==0)
					alphaMe=0;
				else
					alphaMe=getEncounter(dest,me)/getSum(dest);
				//System.out.println(this.nodes[0]);
				if((alphaOther/getDistFor(dest,other))<(alphaMe/getDistFor(dest,me))){
				//	System.out.println("poop2");
					messages.add(new Tuple<Message, Connection>(m,con));	
				}
			}			
		}
		
		if (messages.size() == 0) {
			return null;
		}
		
		// sort the message-connection tuples
		Collections.sort(messages, new TupleComparator());
		return tryMessagesForConnected(messages);	// try to send messages
	}
	
	/**
	 * Comparator for Message-Connection-Tuples that orders the tuples by
	 * their delivery probability by the host on the other side of the 
	 * connection (GRTRMax)
	 */
	private class TupleComparator implements Comparator 
		<Tuple<Message, Connection>> {

		public int compare(Tuple<Message, Connection> tuple1,
				Tuple<Message, Connection> tuple2) {
			// delivery probability of tuple1's message with tuple1's connection
			double p1 = ((GameRouter)tuple1.getValue().
					getOtherNode(getHost()).getRouter()).getPredFor(
					tuple1.getKey().getTo());
			// -"- tuple2...
			double p2 = ((GameRouter)tuple2.getValue().
					getOtherNode(getHost()).getRouter()).getPredFor(
					tuple2.getKey().getTo());

			// bigger probability should come first
			if (p2-p1 == 0) {
				/* equal probabilities -> let queue mode decide */
				return compareByQueueMode(tuple1.getKey(), tuple2.getKey());
			}
			else if (p2-p1 < 0) {
				return -1;
			}
			else {
				return 1;
			}
		}
	}


private double getDistFor(DTNHost dest,DTNHost nextHost)
{
	Coord destLoc = dest.getLocation();
	Coord nextHostLoc = nextHost.getLocation();
	double x1 = nextHostLoc.getX();
	double y1 = nextHostLoc.getY();
	double x2 = destLoc.getX();
	double y2 = destLoc.getY();
	//find line constants
	//System.out.println(x1+" "+y1);


	double a = y1 - y2;
	double b = x2 - x1;
	double c = y2*x1 - y1*x2;
	
	double dist = Math.pow((a*a+b*b),0.5); 
	if(dist<0) dist = -dist;
	
	//System.out.println(dist);
	return dist;
}

	
	@Override
	public RoutingInfo getRoutingInfo() {
		ageDeliveryPreds();
		RoutingInfo top = super.getRoutingInfo();
		RoutingInfo ri = new RoutingInfo(preds.size() + 
				" delivery prediction(s)");
		
		for (Map.Entry<DTNHost, Double> e : preds.entrySet()) {
			DTNHost host = e.getKey();
			Double value = e.getValue();
			
			ri.addMoreInfo(new RoutingInfo(String.format("%s : %.6f", 
					host, value)));
		}
		
		top.addMoreInfo(ri);
		return top;
	}
	
	@Override
	public MessageRouter replicate() {
		GameRouter r = new GameRouter(this);
		return r;
	}

}
