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

import core.Coord;
import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimClock;

import org.jblas.*;
import org.shogun.*;

/**
 * Implementation of Game router as described in 
 * <I>Probabilistic routing in intermittently connected networks</I> by
 * Anders Lindgren et al.
 */
public class kMeansRouter extends ActiveRouter{


   private static int start=0; // Transferring messages when start=1
   private static int nodeCount=-1; //to store the count of no of nodes 
   private double zerothreshold; //to check how much of the encounter matrix has been filled

   public static final double DEFAULT_ZEROTHRESHOLD=0.25;
   
   public static final String edMulti_NS = "kMeansRouter";
    
   private double maxPossibleZeroes=nodeCount*nodeCount*zerothreshold;

   public static final String ZEROTHRESHOLD_S = "zerothreshold";
   
    /** number of encounters of every node with every other node*/
	private static int[][] encounters;

	/**
	 * Constructor. Creates a new message router based on the settings in
	 * the given Settings object.
	 * @param s The settings object
	 */
	public kMeansRouter(Settings s) {
		super(s);
		Settings edMultiSettings = new Settings(edMulti_NS);
		
		if (edMultiSettings.contains(ZEROTHRESHOLD_S)) {
			zerothreshold = edMultiSettings.getDouble(ZEROTHRESHOLD_S);
		}
		else {
			zerothreshold = DEFAULT_ZEROTHRESHOLD;
		}
	}

	/**
	 * Copyconstructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected kMeansRouter(kMeansRouter r) {
		super(r);
		this.zerothreshold=r.zerothreshold;
	}
	
	 void checkStart()  {   
	
		int countZeroes=0;
	
		int i,j;
	
		for(i=0;i<nodeCount;i++) {
	    	for(j=0;j<nodeCount;j++) {
	       		if(encounters[i][j]==0){
	       	   		countZeroes++;
	        	}
	    	}
		}
	    if(countZeroes < maxPossibleZeroes)
	    {
	        start=1; //set start to 1 if the encounter matrix satisfies threshold for no of zero values
	    }
	    
	    return;
	    
	}   //end of checkStart 
	
	
	@Override
	public void changedConnection(Connection con) {
		super.changedConnection(con);
		
		if (con.isUp()) {
			DTNHost otherHost = con.getOtherNode(getHost());
			
			if(nodeCount==-1)
			{
				nodeCount=otherHost.getHosts().size();
				maxPossibleZeroes=nodeCount*nodeCount*zerothreshold;
			}
			
			updateEncounters(getHost(),otherHost);
			//to set start
		 	if(start==0) { 
				checkStart();
			}  
		
		}
	}
	

	/**
	 * Updates the value of encounters when two nodes come in contact with each other
	 * @param host1 first node
	 * @param host2 second node
	 */
	public void updateEncounters(DTNHost host1, DTNHost host2) {
		//each message has different destination and we'll need encounters of every node with the destination(which is changing with each message) in same time instance, hence we've decided to use a 2D array
		if (this.encounters == null) {
			this.encounters=new int[host1.getHosts().size()][host1.getHosts().size()]; //TODO:replace 126 by hosts.size
		}
		kMeansRouter othRouter = (kMeansRouter)host2.getRouter();
		kMeansRouter myRouter = (kMeansRouter)host1.getRouter();

		
		if(myRouter!=othRouter)
		{
			this.encounters[host1.getAddress()][host2.getAddress()]++;
		}
		else
		{
			this.encounters[host1.getAddress()][host2.getAddress()]++;
			this.encounters[host2.getAddress()][host1.getAddress()]++;
		}
		//System.out.println(host1.getAddress()+ " " + sumEncounters.get(host1));
		//System.out.println(host2.getAddress()+ " " + sumEncounters.get(host2));
	}

	/**
	 * Returns the current encounter (E) value for a host
	 * @param host1 The host to look the E for
	 * @param host2 The node with respect to which we have to return the encounters
	 * @return the current E value
	 */
	public int getEncounter(DTNHost host1,DTNHost host2){
		return this.encounters[host1.getAddress()][host2.getAddress()];
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


        //tryOtherMessages();
		
		//To begin simulation when start=1
        //i.e. when the encounter matrix has no zero value
		 if(start==1)
		{
			tryOtherMessages();	
		}	 
			
	}
	
	/**
	 * Tries to send all other messages to all connected hosts
	 * @return The return value of {@link #tryMessagesForConnected(List)}
	 */
	private Tuple<Message, Connection> tryOtherMessages() {
		List<Tuple<Message, Connection>> messages = 
			new ArrayList<Tuple<Message, Connection>>(); 
	
		Collection<Message> msgCollection = getMessageCollection();

		
		for(Message m : msgCollection){

            DTNHost dest = m.getTo();
            int noOfFeatures=2;
            double[][] featureMatrix=new double[getConnections().size()][noOfFeatures];
            int i=0;


            //to get the feature matrix of the neighbours.
			for (Connection con : getConnections()){


				//DTNHost me = getHost();
				DTNHost other = con.getOtherNode(getHost());
				//kMeansRouter othRouter = (kMeansRouter)other.getRouter();
				
				featureMatrix[i][0]=getEncounter(other,dest);
				featureMatrix[i][1]=getDistFor(other,dest);
				i++;
			}

			//convert the java Double Matrix to jblas DubleMatrix
			DoubleMatrix features = new DoubleMatrix(featureMatrix);

			//load the shogun library
			System.load("/usr/local/lib/jni/libmodshogun.so");

			//initialize shogun with default values
			modshogun.init_shogun_with_defaults();

			//number of clusters=2
			int k=2;

			//convert jblas features to RealFeatures that are compatible with shogun
			RealFeatures feats_train = new RealFeatures(features);

																//???
			EuclideanDistance distance=new EuclideanDistance(feats_train,feats_train);

			KMeans kmeans=new KMeans(k,distance);
			kmeans.train();

			DoubleMatrix cluster_centers=kmeans.get_cluster_centers();

			DoubleMatrix cluster_radiuses=kmeans.get_radiuses();

			int positive_cluster;
			//find out the positive cluster
		    if(cluster_centers.get(0,1)<cluster_centers.get(1,1))
		    {
		    	//distance of cluster1<distance of cluster 2
		    	positive_cluster=0;
		    }
		    else if(cluster_centers.get(0,1)>cluster_centers.get(1,1))
		    {
		    	//distance of cluster1>distance of cluster 2
		    	positive_cluster=1;
		    }
		    else
		    {
		    	//distance of cluster1==distance of cluster 2
		    	//if distances are equal then compare by encounters
		    	if(cluster_centers.get(0,0)>cluster_centers.get(0,0))
		    	{
		    		positive_cluster=0;
		    	}
		    	else
		    	{
		    		positive_cluster=1;
		    	}
		    }

		    System.out.println("positive_cluster="+ positive_cluster);
			modshogun.exit_shogun();
			/*
			//loop over neighbors for transferring messages
			for(Connection con : getConnections())
			{
				DTNHost other=con.getOtherNode(getHost());
				kMeansRouter othRouter = (kMeansRouter)other.getRouter();
				if(othRouter.isTransferring()){
					continue;
				}
				if(othRouter.hasMessage(m.getId())){
					continue;
				}
				if(bestGammaLocal.containsKey(other))
				{
				   
       				messages.add(new Tuple<Message, Connection>(m,con));	
				}
			}  //end of for loop				
			*/

			//flooding
			for(Connection con : getConnections())
				{
					DTNHost other=con.getOtherNode(getHost());
					edMultiRouter othRouter = (edMultiRouter)other.getRouter();
					if(othRouter.isTransferring()){
						continue;
					}
					if(othRouter.hasMessage(m.getId())){
						continue;
					}
					messages.add(new Tuple<Message, Connection>(m,con));	
				} 		
		}
		if (messages.size() == 0) {
			return null;
		}
		
		// sort the message-connection tuples (i dont's see the need to sort:-Kunal)
		//Collections.sort(messages, new TupleComparator());
		return tryMessagesForConnected(messages);	// try to send messages
	}

//calculates euclidean distance between two nodes
private double euclideanDist(double[][] featureMatrix,int rows,int columns,int index1,int index2)
{
	double sum=0;
	for(int i=0;i<columns;i++)
	{
		sum=sum+java.lang.Math.pow((featureMatrix[index1][i]-featureMatrix[index2][i]),2);
	}
	java.lang.Math.sqrt(sum);
	return sum;
}

//calculates euclidean distance of a node
private double euclideanDist(double[][] featureMatrix,int rows,int columns,int index1)
{
	double sum=0;
	for(int i=0;i<columns;i++)
	{
		sum=sum+java.lang.Math.pow((featureMatrix[index1][i]),2);
	}
	java.lang.Math.sqrt(sum);
	return sum;
}

private void normalize(double[][] featureMatrix,int rows,int columns,int k)
{
	double sum,min,max,diff;
	for(int i=0;i<columns;i++)
	{
		sum=0;
		max=-1;
		min=Double.POSITIVE_INFINITY;
		for(int j=0;j<rows;j++)
		{
			if(featureMatrix[j][i]>max)
			{
				max=featureMatrix[j][i];
			}
			if(featureMatrix[j][i]<min)
			{
				min=featureMatrix[j][i];
			}
		}
		diff=max-min;
		for(int j=0;j<rows;j++)
		{
			featureMatrix[j][i]=(featureMatrix[j][i]-min)/diff;
		}
	}
}
/**	
* Returns the current distance between dest node and the nextHost node
* @param dest The destination node 
* @param nextHost The node from which we want to calculate the diatance from
* @return the current distance
*/
private double getDistFor(DTNHost dest,DTNHost nextHost)
{
	Coord destLoc = dest.getLocation();
	Coord nextHostLoc = nextHost.getLocation();
	double x1 = nextHostLoc.getX();
	double y1 = nextHostLoc.getY();
	double x2 = destLoc.getX();
	double y2 = destLoc.getY();
	double a = y1 - y2;
	double b = x2 - x1;
	double c = y2*x1 - y1*x2;
	double dist = java.lang.Math.pow((a*a+b*b),0.5); 
	if(dist<0) dist = -dist;
	return dist;
}

//display matrix (for debugging purposes)
private void dispMatrix(double[][] featureMatrix,int rows,int columns)
{
	for(int i=0;i<rows;i++)
	{
		for(int j=0;j<columns;j++)
		{
			System.out.print(featureMatrix[i][j]);
			System.out.print("\t");
		}
		System.out.println();
	}
}
	@Override
	public MessageRouter replicate() {
		kMeansRouter r = new kMeansRouter(this);
		return r;
	}

}