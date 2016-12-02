package uk.ac.imperial.lsds.seep.manet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeSet;

import uk.ac.imperial.lsds.seep.GLOBALS;
import uk.ac.imperial.lsds.seep.comm.serialization.ControlTuple;
import uk.ac.imperial.lsds.seep.comm.serialization.controlhelpers.DownUpRCtrl;
import uk.ac.imperial.lsds.seep.comm.serialization.controlhelpers.UpDownRCtrl;
import uk.ac.imperial.lsds.seep.comm.serialization.controlhelpers.FailureCtrl;
import uk.ac.imperial.lsds.seep.runtimeengine.CoreRE;
import uk.ac.imperial.lsds.seep.runtimeengine.CoreRE.ControlTupleType;
import uk.ac.imperial.lsds.seep.runtimeengine.OutOfOrderBufferedBarrier;
import uk.ac.imperial.lsds.seep.manet.stats.Stats;


public class RoutingController implements Runnable{

	private final static Logger logger = LoggerFactory.getLogger(RoutingController.class);
	private final boolean separateControlNet = Boolean.parseBoolean(GLOBALS.valueFor("separateControlNet"));
	private final boolean piggybackControlTraffic = Boolean.parseBoolean(GLOBALS.valueFor("piggybackControlTraffic"));
	private final boolean mergeFailureAndRoutingCtrl = Boolean.parseBoolean(GLOBALS.valueFor("mergeFailureAndRoutingCtrl"));
	private final boolean enableDummies = Boolean.parseBoolean(GLOBALS.valueFor("sendDummyDownUpControlTraffic"));
	private final boolean sendQueueLengthsOnly;

	private final static double INITIAL_WEIGHT = -1;
	//private final static double COST_THRESHOLD = 3.9;
	//private final static double COST_THRESHOLD = 4.5;
	//private final static double COST_THRESHOLD = 5.5;
	private final static double COST_THRESHOLD = Double.parseDouble(GLOBALS.valueFor("costThreshold"));
	private final long MAX_WEIGHT_DELAY;// = 1 * 1000;
	private final CoreRE owner;
	private final Integer nodeId;
	private final int numLogicalInputs;
	private final Map<Integer, TreeMap<Integer, Integer>> upstreamQlens;
	private final Map<Integer, TreeMap<Integer, Double>> upstreamNetRates;
	private final double processingRate = 1; //TODO: Measure/update this dynamically?
	private final Map<Integer, Double> weights = new HashMap<>(); 
	private final Query query;
	private final boolean useCostThreshold;
	private final Map<Integer, Stats.IntervalTput> tputStats = new ConcurrentHashMap<>();
	
	private final Object lock = new Object(){};
	
	public RoutingController(CoreRE owner) {
		logger.info("Creating routing controller.");
		this.owner = owner;
		this.nodeId = owner.getProcessingUnit().getOperator().getOperatorId();
		this.query = owner.getProcessingUnit().getOperator().getOpContext().getMeanderQuery();
		//this.inputQueues = inputQueues;
		this.numLogicalInputs = query.getLogicalInputs(query.getLogicalNodeId(nodeId)).length;
		
		this.sendQueueLengthsOnly = Boolean.parseBoolean(GLOBALS.valueFor("enableUpstreamRoutingControl")) && numLogicalInputs <= 1;
		this.useCostThreshold = query.getPhysicalNodeIds(query.getLogicalNodeId(nodeId)).size() > 1;
		this.upstreamNetRates = new HashMap<>();
		this.upstreamQlens = new HashMap<>();

		if (numLogicalInputs > 1)
		{
			//this.MAX_WEIGHT_DELAY = 10 * 1000;
			//this.MAX_WEIGHT_DELAY = 2 * 1000;
			this.MAX_WEIGHT_DELAY = Long.parseLong(GLOBALS.valueFor("routingCtrlDelay"));
			weights.put(nodeId, INITIAL_WEIGHT);
		}
		else
		{
			//this.MAX_WEIGHT_DELAY = 1 * 50;
			//this.MAX_WEIGHT_DELAY = 1 * 25;
			this.MAX_WEIGHT_DELAY = Long.parseLong(GLOBALS.valueFor("routingCtrlDelay"));
		}
		
		for (int i = 0; i < numLogicalInputs; i++)
		{
			Set upstreamIds = query.getPhysicalInputs(query.getLogicalNodeId(nodeId))[i];
			//ArrayList<Integer> upstreamIds = owner.getProcessingUnit().getOperator().getOpContext().getUpstreamOpIdList();
			upstreamNetRates.put(i, new TreeMap<Integer, Double>());
			upstreamQlens.put(i, new TreeMap<Integer, Integer>());
			Iterator iter = upstreamIds.iterator();
			while (iter.hasNext())
			{
				Integer nextId = (Integer)iter.next();
				upstreamNetRates.get(i).put(nextId, 1.0);
				upstreamQlens.get(i).put(nextId, 0);
				if (numLogicalInputs == 1)
				{
					weights.put(nextId, INITIAL_WEIGHT);
				}
			}
		}
		logger.info("Created routing controller.");
	}
	

	@Override
	public void run() {
		logger.info("Starting routing controller.");
		if (sendQueueLengthsOnly)
		{
			sendQueueLengths();
		}
		else
		{
			sendWeights();
		}
	}
	
	private void sendWeights()
	{
		try 
		{
			long tLast = System.currentTimeMillis();	
			while(true)
			{
				Map<Integer, Double> weightsCopy = null;
				boolean downstreamsRoutable = areDownstreamsRoutable();
				synchronized(lock)
				{
					//TODO: Should possibly change this to call getLocalOutputQLen without holdinng the lock.
					updateWeight(downstreamsRoutable);
					weightsCopy = new HashMap<>(weights);
				}
				
				logger.info("Routing controller sending weights upstream: "+weightsCopy);
				
				long tSendBegin = System.currentTimeMillis();
				if (numLogicalInputs > 1)
				{
					ArrayList<RangeSet<Long>> routingConstraints = ((OutOfOrderBufferedBarrier)owner.getDSA().getUniqueDso()).getRoutingConstraints();
					for (Integer upstreamId : owner.getProcessingUnit().getOperator().getOpContext().getUpstreamOpIdList())
					{
						int logicalInputIndex = query.getLogicalInputIndex(query.getLogicalNodeId(nodeId), query.getLogicalNodeId(upstreamId));
						//N.B. Sending the *aggregate* weight across all upstreams.
						logger.info("Routing controller sending constraints upstream op "+upstreamId+": "+routingConstraints.get(logicalInputIndex));
						//ControlTuple ct = new ControlTuple(ControlTupleType.DOWN_UP_RCTRL, nodeId, weightsCopy.get(nodeId), routingConstraints.get(logicalInputIndex));
						int upOpIndex = owner.getProcessingUnit().getOperator().getOpContext().getUpOpIndexFromOpId(upstreamId);

						sendWeight(upstreamId, upOpIndex, weightsCopy.get(nodeId), routingConstraints.get(logicalInputIndex));
					}
				}
				else
				{
			
					for (Integer upstreamId : weightsCopy.keySet())
					{
	
						RangeSet<Long> empty = TreeRangeSet.create();
						//ControlTuple ct = new ControlTuple(ControlTupleType.DOWN_UP_RCTRL, nodeId, weightsCopy.get(upstreamId), empty);
						double weight = weightsCopy.get(upstreamId);
						//if (nodeId == 1 && upstreamId == 10 || nodeId == 110 && upstreamId == 0 || nodeId == -2 && upstreamId == 110 || nodeId == -190 && upstreamId == 1) { weight = 0.0 ; } 
						//if (nodeId == 1 && upstreamId == 0 || nodeId == 110 && upstreamId == 10 || nodeId == -2 && upstreamId == 110 || nodeId == -190 && upstreamId == 1) { weight = 0.0 ; } 
						//ControlTuple ct = new ControlTuple(ControlTupleType.DOWN_UP_RCTRL, nodeId, weight, empty);
						int upOpIndex = owner.getProcessingUnit().getOperator().getOpContext().getUpOpIndexFromOpId(upstreamId);

						/*
						if (nodeId.intValue() == -2 && upstreamId.intValue() != 0 ||
							nodeId.intValue() == -190 && upstreamId.intValue() != 10 ||
							nodeId.intValue() == -189 && upstreamId.intValue() != 11)
						{ continue; }
						*/
						sendWeight(upstreamId, upOpIndex, weight, empty);
					}
				}
				logger.info("Routing controller send weights upstream in "+(System.currentTimeMillis() - tSendBegin)+ " ms, since last="+(System.currentTimeMillis()-tLast)+" ms");
				long tStart = System.currentTimeMillis();
				tLast = tStart;
				long tNow = tStart;
				while (tNow - tStart < MAX_WEIGHT_DELAY)
				{
					synchronized(lock)
					{
						try {
							lock.wait(MAX_WEIGHT_DELAY - (tNow - tStart));
						} catch (InterruptedException e) {
							//Woken up early, that's fine.
						}
					}
					tNow = System.currentTimeMillis();
				}
			}
		}
		catch(Exception e) { logger.error("Routing controller exception: "+ e); System.exit(1); }

	}
	

	private void sendWeight(int upstreamId, int upOpIndex, double weight, RangeSet<Long> constraints)
	{
						ControlTuple ct = new ControlTuple(ControlTupleType.DOWN_UP_RCTRL, nodeId, weight, constraints);
						if (!piggybackControlTraffic || !mergeFailureAndRoutingCtrl)
						{
							owner.getControlDispatcher().sendUpstream(ct, upOpIndex, false);
						}
						else
						{
							ControlTuple fct = owner.removeLastFCtrl(upOpIndex);
							if (fct != null)
							{
								FailureCtrl fctrl = fct.getOpFailureCtrl().getFailureCtrl();
								ControlTuple mct = new ControlTuple(ControlTupleType.MERGED_CTRL, nodeId, weight, constraints, fctrl);
								owner.getControlDispatcher().sendUpstream(mct, upOpIndex, false);
								logger.debug("Sending merged failure ctrl from "+nodeId+"->"+upstreamId);
							}
							else { owner.getControlDispatcher().sendUpstream(ct, upOpIndex, false); }
						}
						if (separateControlNet && enableDummies) { owner.getControlDispatcher().sendDummyUpstream(ct, upOpIndex); }
	}

	private void sendQueueLengths()
	{

		try 
		{
			long tLast = System.currentTimeMillis();	
			while(true)
			{
				boolean downstreamsRoutable = areDownstreamsRoutable();
				int localInputQueueLength = owner.getDSA().getUniqueDso().size();
				int localOutputQueueLength = getLocalOutputQLen();
				int localQueueLength = localInputQueueLength + localOutputQueueLength;
				if (!downstreamsRoutable) { localQueueLength = -1; }
				
				logger.info("Routing controller sending queue length upstream: "+localQueueLength);
				
				long tSendBegin = System.currentTimeMillis();
				for (Object upstreamIdObj : query.getPhysicalInputs(query.getLogicalNodeId(nodeId))[0])
				{
					Integer upstreamId = (Integer)upstreamIdObj;

					RangeSet<Long> empty = TreeRangeSet.create();
					ControlTuple ct = new ControlTuple(ControlTupleType.DOWN_UP_RCTRL, nodeId, localQueueLength, empty);
					int upOpIndex = owner.getProcessingUnit().getOperator().getOpContext().getUpOpIndexFromOpId(upstreamId);

					if (!piggybackControlTraffic || !mergeFailureAndRoutingCtrl)
					{
						owner.getControlDispatcher().sendUpstream(ct, upOpIndex, false);
					}
					else
					{
						ControlTuple fct = owner.removeLastFCtrl(upOpIndex);
						if (fct != null)
						{
							FailureCtrl fctrl = fct.getOpFailureCtrl().getFailureCtrl();
							ControlTuple mct = new ControlTuple(ControlTupleType.MERGED_CTRL, nodeId, localQueueLength, empty, fctrl);
							owner.getControlDispatcher().sendUpstream(mct, upOpIndex, false);
							logger.debug("Sending merged failure ctrl from "+nodeId+"->"+upstreamId);
						}
						else { owner.getControlDispatcher().sendUpstream(ct, upOpIndex, false); }
					}

					if (separateControlNet && enableDummies) { owner.getControlDispatcher().sendDummyUpstream(ct, upOpIndex); }
				}

				logger.info("Routing controller sent queue length upstream in "+(System.currentTimeMillis() - tSendBegin)+ " ms, since last="+(System.currentTimeMillis()-tLast)+" ms");
				long tStart = System.currentTimeMillis();
				tLast = tStart;
				long tNow = tStart;
				while (tNow - tStart < MAX_WEIGHT_DELAY)
				{
					synchronized(lock)
					{
						try {
							lock.wait(MAX_WEIGHT_DELAY - (tNow - tStart));
						} catch (InterruptedException e) {
							//Woken up early, that's fine.
						}
					}
					tNow = System.currentTimeMillis();
				}
			}
		}
		catch(Exception e) { logger.error("Routing controller exception: "+ e); System.exit(1); }

	}

	public void handleRCtrl(UpDownRCtrl rctrl)
	{
		synchronized(lock)
		{
			if (query == null) { throw new RuntimeException("Logic error?"); }
			logger.info("Phys node "+ nodeId + " with logical id " + query.getLogicalNodeId(nodeId) +" received updown rctrl:"+rctrl.toString());
			int inputIndex = query.getLogicalInputIndex(query.getLogicalNodeId(nodeId), query.getLogicalNodeId(rctrl.getOpId()));
			if (!upstreamQlens.get(inputIndex).containsKey(rctrl.getOpId())) { throw new RuntimeException("Logic error."); }
			this.upstreamQlens.get(inputIndex).put(rctrl.getOpId(),  new Integer(rctrl.getQlen()));
			//updateWeight();
			lock.notifyAll();
		}
	}
	
	public void handleNetCostsUpdate(Map<Integer, Double> upstreamCosts)
	{
		synchronized (lock) {
			for (int i = 0; i < upstreamNetRates.size(); i++) {
				Iterator<Integer> iter = upstreamNetRates.get(i).keySet()
						.iterator();
				while (iter.hasNext()) {
					Integer upstreamId = iter.next();
					Double cost = upstreamCosts.get(upstreamId);
					if (tputStats.get(upstreamId) != null) { logger.info(tputStats.get(upstreamId).toString()+", cost="+cost); }

					if (cost==null || (useCostThreshold && cost > COST_THRESHOLD)) {
						cost = new Double(GraphUtil.SUB_INFINITE_DISTANCE);
						if (useCostThreshold && cost > COST_THRESHOLD)
						{ logger.warn("Cost exceeded threshold: node="+nodeId+",up="+upstreamId+",thresh="+COST_THRESHOLD+",cost="+cost); }
					}
					
					if (cost >= GraphUtil.SUB_INFINITE_DISTANCE
							.intValue()) {
						upstreamNetRates.get(i).put(upstreamId, new Double(0));
					}
					else
					{
						upstreamNetRates.get(i).put(upstreamId,
								new Double(1.0 / Math.pow(cost.doubleValue(), 2)));
					}
				}
			}
			logger.info("Updated upstream net rates: "+upstreamNetRates);
			//updateWeight();
			lock.notifyAll();
		}
	}

	public void handleIntervalTputUpdate(Stats.IntervalTput update)
	{
		tputStats.put(update.upstreamId, update);
	}

	private int getLocalOutputQLen()
	{
		if (owner.getProcessingUnit().getDispatcher() != null)
		{
			/*
			if (owner.getProcessingUnit().getOperator().getOpContext().isSink())
			{ throw new RuntimeException("Logic error."); }
			*/
			int localOutputQlen = owner.getProcessingUnit().getDispatcher().getTotalQlen();
			//logger.info("Op "+nodeId+" local output qlen=" + localOutputQlen);
			return localOutputQlen;
		}
		else
		{
			throw new RuntimeException("Logic error.");
		}
	}

	private boolean areDownstreamsRoutable()
	{
		if (owner.getProcessingUnit().getDispatcher() != null)
		{
			return owner.getProcessingUnit().getDispatcher().areDownstreamsRoutable();
		}
		else
		{
			throw new RuntimeException("Logic error.");
		}
		
	}
	
	private void updateWeight(boolean downstreamsRoutable)
	{
			Map<Integer, Integer> localInputQlens = null;
			if (numLogicalInputs > 1)
			{
				 localInputQlens = ((OutOfOrderBufferedBarrier)owner.getDSA().getUniqueDso()).sizes();
				 logger.info("Op "+nodeId+" oob inputqlens="+localInputQlens); 
			}
			else
			{
				//TODO: Just change dsa inf to have a single sizes method.
				localInputQlens = new HashMap<Integer, Integer>();
				localInputQlens.put(-1,owner.getDSA().getUniqueDso().size());
			}
			
			//int localInputQlen = getLocalInputQLen();	//TODO: What if join!
			int localOutputQlen = getLocalOutputQLen();
			ArrayList<Set<Double>> joinWeights = new ArrayList<>();
			for (int i = 0; i < numLogicalInputs; i++)
			{
				//Iterator<Integer> iter = query.getPhysicalInputs(query.getLogicalNodeId(nodeId))[i].iterator();
				joinWeights.add(new HashSet<Double>());
				ArrayList<Integer> upstreamIds = new ArrayList<>(query.getPhysicalInputs(query.getLogicalNodeId(nodeId))[i]);
				Iterator<Integer> iter = upstreamIds.iterator();
				while (iter.hasNext())
				{
					Integer upstreamId = iter.next();
					int localTotalInputQlen = localInputQlens.get(-1);

					//TODO: Should we be multiplying the input q length by the processing rate?
					logger.info("Op "+ nodeId+ " computing weight for input="+i+", upOpId="+upstreamId+",upstreamQlens="+upstreamQlens+",upstreamNetRates="+upstreamNetRates);
					double weight = computeWeight(upstreamQlens.get(i).get(upstreamId), 
							localTotalInputQlen + localOutputQlen, upstreamNetRates.get(i).get(upstreamId), processingRate);
					long t = System.currentTimeMillis();	
					logger.info("t="+t+",op="+nodeId+",total qlen="+(localTotalInputQlen+localOutputQlen)+",inputq="+localTotalInputQlen+",outputq="+localOutputQlen);
					logger.info("Op "+nodeId+" upstream "+upstreamId+" weight="+weight+",qlen="+upstreamQlens.get(i).get(upstreamId)+",netRate="+upstreamNetRates.get(i).get(upstreamId));
					
					if (numLogicalInputs == 1)
					{
						weights.put(upstreamId, downstreamsRoutable ? weight : -1);
					}
					else
					{
						joinWeights.get(i).add(weight);
						//Also compute per upstream weight for routing constraints
						//TODO: Debatable what should actually be used here.
						int localPerInputQlen = localInputQlens.get(i);
						weight = computeWeight(upstreamQlens.get(i).get(upstreamId), 
								localPerInputQlen + localOutputQlen, upstreamNetRates.get(i).get(upstreamId), processingRate);
						weights.put(upstreamId, weight);
						weights.put(upstreamId, downstreamsRoutable? weight : -1);
						//throw new RuntimeException("TODO: Downstreams routable");
					}					
				}
			}
			
			if (numLogicalInputs > 1)
			{
				weights.put(nodeId, aggregate(joinWeights));
			}
			logger.info("Updated routing controller weights: "+ weights);
	}
	
	private double computeWeight(int qLenUpstream, int qLenLocal, double netRate, double pRate)
	{
		// N.B. TODO: Need to discuss this.
		//N.B. I think the +1 here will do what we want since
		//if there is no link we will still have a weight of 0,
		//But when sending initially it will act as a gradient.
		//Not sure if it will overload the queues though?
		return (qLenUpstream + 1 - qLenLocal) * netRate * pRate;
		//return netRate * pRate;
	}
	
	private double aggregate(ArrayList<Set<Double>> joinWeights)
	{
		ArrayList<Double> perInputAggregates = aggregateInputs(joinWeights);
		
		//Return mean for now.
		double sum = 0;
		for (Double aggregatedInputWeight : perInputAggregates)
		{
			sum += aggregatedInputWeight;
		}
		logger.debug("Aggregate weight = "+ (sum/perInputAggregates.size()));
		return sum / perInputAggregates.size();
	}
	
	private ArrayList<Double> aggregateInputs(ArrayList<Set<Double>> joinWeights)
	{
		//Get the max across all upstreams for this logical input.
		ArrayList<Double> result = new ArrayList<>();
		for (Set<Double> inputWeights : joinWeights)
		{
			double max = 0;
			for (Double upstreamWeight : inputWeights)
			{
				max = Math.max(upstreamWeight, max);
			}
			result.add(max);
		}
		return result;
	}
}
