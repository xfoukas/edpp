package domain;

import java.io.Serializable;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import org.jblas.DoubleMatrix;

import algorithms.Algorithms;

import com.google.common.util.concurrent.AtomicDoubleArray;

import core.ProtocolController;
import domain.network.Node;
import domain.structure.PlainNeighborsTable;
import domain.structure.PlainNeighborsTableSet;
import domain.structure.TimedNeighborsTable;
import domain.structure.TimedNeighborsTableSet;

/**
 * Class performing the tasks described in the decentralized impulse algorithm
 * by Carzaniga et al.
 * 
 * @author Xenofon Foukas
 * 
 */
public class Execution implements Serializable {

	private static final long serialVersionUID = 6505271319331510315L;

	private final int executionNumber;
	private final int numOfRounds;
	private transient AtomicInteger round;
	private transient final Node localNode;
	private transient PlainNeighborsTable outNeighbors;
	private transient TimedNeighborsTable inNeighbors;
	private transient Phase phase;
	private AtomicDoubleArray impulseResponse;
	private transient Map<Integer, Double> roundVals;
	private transient AtomicLong initTimeout;
	private transient AtomicLong snapshot;
	private DoubleMatrix matrixA;
	private boolean hasComputedMatrix, computedMedian;
	private double[] eigenvalues;
	private double[] medianEigenvalues;
	private transient GossipData gossip;
	private Map<String, double[]> pendingGossip;
	private Map<Integer, Queue<String>> pendingData;

	private transient Logger logger;

	/**
	 * Class constructor
	 * 
	 * @param executionNumber
	 *            the id of the current execution
	 * @param numOfRounds
	 *            the total number of rounds for the execution
	 * @param localNode
	 *            the local underlying network node
	 */
	public Execution(final int executionNumber, final int numOfRounds,
			final Node localNode) {
		logger = Logger.getLogger(Execution.class.getName());

		this.executionNumber = executionNumber;
		this.numOfRounds = numOfRounds;
		this.localNode = localNode;
		createOutTable();
		inNeighbors = new TimedNeighborsTableSet();
		setPhase(Phase.INIT);
		impulseResponse = new AtomicDoubleArray(numOfRounds);
		roundVals = new ConcurrentHashMap<Integer, Double>();
		double initialValue = chooseInitialValue();
		logger.info("The initial value chosen by execution number "
				+ executionNumber + " was " + initialValue);
		roundVals.put(1, initialValue);
		impulseResponse.set(0, roundVals.get(1));
		round = new AtomicInteger(1);
		snapshot = new AtomicLong(System.nanoTime());
		initTimeout = new AtomicLong(10 * ProtocolController.TIMEOUT);
		this.hasComputedMatrix = false;
		gossip = new GossipData();
		computedMedian = false;
		pendingGossip = new ConcurrentHashMap<String, double[]>();
		pendingData = new ConcurrentHashMap<Integer, Queue<String>>();
	}

	/**
	 * 
	 * @return the number of the current execution
	 */
	public int getExecutionNumber() {
		return executionNumber;
	}

	/**
	 * 
	 * @return the current round of the execution
	 */
	public int getCurrentRound() {
		return round.get();
	}

	/**
	 * Sets the current round of the execution. This also changes the current
	 * computing value to the value of the proper round
	 * 
	 * @param round
	 *            the new round number
	 */
	public void setRound(int round) {
		setImpulseResponse(this.round.get(), roundVals.get(this.round.get()));
		this.round.set(round);
		Double d = roundVals.get(round);
		if (d == null) {
			this.setCurrentValue(0);
		} else {
			this.setCurrentValue(d);
		}
		Queue<String> nodes = pendingData.get(round);
		if (nodes != null) {
			String nodeId;
			while ((nodeId = nodes.poll()) != null) {
				inNeighbors.setTimerToInf(nodeId);
			}
		}
	}

	/**
	 * Will set the timers of in-neighbors which have already sent messages for
	 * the current round to INF
	 */
	public void setProperTimersToInf() {
		int currRound = this.getCurrentRound();
		Queue<String> nodes = pendingData.get(currRound);
		if (nodes != null) {
			String nodeId;
			while ((nodeId = nodes.poll()) != null) {
				inNeighbors.setTimerToInf(nodeId);
			}
		}
	}

	/**
	 * Adds a received value to a specific round
	 * 
	 * @param val
	 *            the value to be added
	 * @param round
	 *            the round to which the value corresponds
	 */
	public void addValToRound(double val, int round) {
		if (roundVals.get(round) == null) {
			roundVals.put(round, val);
		} else {
			double newVal = roundVals.get(round);
			newVal += val;
			roundVals.put(round, newVal);
		}
	}

	/**
	 * Adds a received value to a specific round
	 * 
	 * @param val
	 *            the value to be added
	 * @param round
	 *            the round to which the value corresponds
	 */
	public synchronized void addValToNextRound(String nodeId, double val,
			int round) {
		boolean gotValue = true;

		Queue<String> nodes = pendingData.get(round);
		if (nodes == null) {
			nodes = new ConcurrentLinkedQueue<String>();
			nodes.offer(nodeId);
			pendingData.put(round, nodes);
			gotValue = false;
		} else {
			if (!nodes.contains(nodeId)) {
				nodes.offer(nodeId);
				gotValue = false;
			}
		}
		if (!gotValue) {
			if (roundVals.get(round) == null) {
				roundVals.put(round, val);
			} else {
				double newVal = roundVals.get(round);
				newVal += val;
				roundVals.put(round, newVal);
			}
		}
	}

	/**
	 * 
	 * @param round
	 *            the round to be checked
	 * @return the sum of the currently received values for some round
	 */
	public double getValsOfRound(int round) {
		if (roundVals.get(round) == null) {
			return 0;
		} else {
			return roundVals.get(round);
		}
	}

	/**
	 * This method adds a received message of type GOSSIP to a list of received
	 * gossip messages for use when the Execution enters the Gossip Round
	 * 
	 * @param nodeId
	 *            the string representation of the id of the in-neighbor that
	 *            sent the eigenvalue estimations
	 * @param eigenvalues
	 *            a double array containing the eigenvalues proposed by the
	 *            in-neighbor
	 */
	public void addPendingGossipMessage(String nodeId, double[] eigenvalues) {
		pendingGossip.put(nodeId, eigenvalues);
	}

	/**
	 * This method adds to the GossipData data structure all the eigenvalue
	 * estimations temporarily stored in a list because they arrived before the
	 * Execution entered the GOSSIP phase. It also sets the timers of all the
	 * in-neighbors who sent these estimations to INF
	 */
	public void transferPendingGossipMessages() {
		for (Iterator<Map.Entry<String, double[]>> it = pendingGossip
				.entrySet().iterator(); it.hasNext();) {
			Map.Entry<String, double[]> entry = it.next();
			this.addGossipEigenvalues(entry.getKey(), entry.getValue());
			this.setTimerToInf(entry.getKey());
		}
	}

	/**
	 * Computes the remaining time in the current execution's timer, by
	 * subtractiong the time since the last sampling
	 * 
	 * @return the remaining time of the execution's timer
	 */
	public synchronized long remainingInitTime() {
		long interval = System.nanoTime() - snapshot.get();
		// set the time for the next sampling
		snapshot = new AtomicLong(System.nanoTime());
		long remTime = TimeUnit.MILLISECONDS.convert(interval,
				TimeUnit.NANOSECONDS);
		return initTimeout.addAndGet(-remTime);
	}

	/**
	 * Runs Kung's realization algorithm to compute the approximate matrix A and
	 * its eigenvalues
	 * 
	 * @return the approximation of matrix A, if we had gathered all the
	 *         required impulse responses, otherwise null
	 */
	public synchronized DoubleMatrix computeRealizationMatrix() {
		if (this.hasAnotherRound() || hasComputedMatrix) {
			return null;
		}
		double[] responses = new double[impulseResponse.length()];
		for (int i = 0; i < impulseResponse.length(); i++) {
			responses[i] = impulseResponse.get(i);
		}

		this.matrixA = Algorithms.computeSystemMatrixA(responses);
		eigenvalues = Algorithms.computeEigenvaluesModulus(matrixA);
		gossip.setNewProposal(localNode.getLocalId().toString(), eigenvalues);
		hasComputedMatrix = true;
		return matrixA;
	}

	/**
	 * 
	 * @return the approximation matrix, if the computation has already been
	 *         performed, otherwise {@literal null}
	 */
	public DoubleMatrix getRealizationMatrix() {
		if (hasComputedMatrix) {
			return matrixA;
		} else {
			return null;
		}
	}

	/**
	 * 
	 * @return a double array with the eigenvalues of the approximation matrix
	 *         or null if the approximation matrix has not been computed yet
	 */
	public double[] getMatrixAEigenvalues() {
		if (hasComputedMatrix) {
			return eigenvalues;
		} else {
			return null;
		}
	}

	/**
	 * Changes the values of the eigenvalue array. This method should be used if
	 * the approximation matrix has already been computed and we are in the
	 * GOSSIP round, otherwise no change will be made to the eigenvalues array
	 * 
	 * @param newValues
	 *            an array with the new values the eigenvalues array should get
	 * @return {@literal true} if the values were updated successfully,
	 *         {@literal false} otherwise
	 */
	public boolean setMatrixAEigenvalues(double[] newValues) {
		if (hasComputedMatrix && this.getPhase() == Phase.GOSSIP) {
			eigenvalues = newValues;
			gossip.setNewProposal(localNode.getLocalId().toString(), newValues);
			return true;
		}
		return false;
	}

	/**
	 * adds an array of eigenvalues for a node. If the node has already proposed
	 * some values, they will be overwritten
	 * 
	 * @param nodeId
	 *            the {@link Id} of the node that proposed the eigenvalues
	 * @param array
	 *            an array containing the proposed eigenvalues
	 */
	public void addGossipEigenvalues(String nodeId, double[] array) {
		gossip.setNewProposal(nodeId, array);
	}

	/**
	 * Computes the median of the eigenvalues based on the local node and the
	 * proposals if its in-neighbors
	 * 
	 * @return an array containing the median of the eigenvalues, or null if the
	 *         execution has not reached the gossip phase and has not computed
	 *         the required matrix
	 */
	public double[] computeMedianEigenvalues() {
		if (hasComputedMatrix && this.getPhase() == Phase.GOSSIP) {
			medianEigenvalues = gossip.computeMedianOfProposedValues();
			computedMedian = true;
			return medianEigenvalues;
		}
		return null;
	}

	/**
	 * 
	 * @return an array with the final eigenvalues if they have been computed,
	 *         null otherwise
	 */
	public double[] getMedianEigenvalues() {
		if (computedMedian) {
			return medianEigenvalues;
		}
		return null;
	}

	/**
	 * 
	 * @return a {@link PlainNeighborsTable} with all the out-neighbors along
	 *         with their weights
	 */
	public PlainNeighborsTable getOutNeighbors() {
		return outNeighbors;
	}

	/**
	 * 
	 * @return a {@link TimedNeighborsTable} with all the in-neighbors along
	 *         with their remaining alive time
	 */
	public TimedNeighborsTable getInNeighbors() {
		return inNeighbors;
	}

	/**
	 * 
	 * @return the total number of rounds of this execution
	 */
	public int getNumOfRounds() {
		return numOfRounds;
	}

	/**
	 * 
	 * @return the {@link Phase} the execution is currently at
	 */
	public synchronized Phase getPhase() {
		return phase;
	}

	/**
	 * change the current phase of the execution
	 * 
	 * @param phase
	 *            the new {@link Phase} the execution will be at
	 */
	public synchronized void setPhase(Phase phase) {
		this.phase = phase;
	}

	/**
	 * 
	 * @param round
	 *            the round wanted
	 * @return the impulse response of a round
	 */
	public double getImpulseResponse(int round) {
		return impulseResponse.get(round - 1);
	}

	/**
	 * 
	 * @return the current value that will be used to compute the value sent to
	 *         all out-neighbors
	 */
	public double getCurrentValue() {
		return roundVals.get(this.round.get());
	}

	/**
	 * 
	 * @param nodeVal
	 *            the current value that will be used to compute the value sent
	 *            to all out-neighbors
	 */
	public void setCurrentValue(double nodeVal) {
		this.roundVals.put(this.round.get(), nodeVal);
	}

	/**
	 * 
	 * @return an array of all the impulse responses in the current execution
	 */
	public double[] getImpulseResponses() {
		double[] responses = new double[impulseResponse.length()];
		for (int i = 0; i < impulseResponse.length(); i++) {
			responses[i] = impulseResponse.get(i);
		}
		return responses;
	}

	/**
	 * Stores to the defined round a new impulse response
	 * 
	 * @param round
	 *            the round to set the impulse response
	 * @param response
	 *            the new impulse response
	 * @return true if the impulse response is properly set, false otherwise
	 */
	public boolean setImpulseResponse(int round, double response) {
		if (round <= numOfRounds) {
			impulseResponse.set(round - 1, response);
			return true;
		}
		return false;
	}

	/**
	 * Sets the impulse response of the current round
	 * 
	 * @param response
	 *            the new impulse response
	 * @return true if the impulse response was set properly, false otherwise
	 */
	public boolean setCurrentImpulseResponse(double response) {
		if (round.get() <= numOfRounds) {
			impulseResponse.set(round.get() - 1, response);
			return true;
		}
		return false;
	}

	/**
	 * 
	 * @return true if the execution has terminated, false otherwise
	 */
	public boolean hasTerminated() {
		return round.get() == this.numOfRounds + 1;
	}

	/**
	 * Checks whether the current execution has another round
	 * 
	 * @return true if this is not the last round of execution
	 */
	public boolean hasAnotherRound() {
		return round.get() < this.numOfRounds;
	}

	/**
	 * Adds a new in-neighbor to the in-neighbors list
	 * 
	 * @param tn
	 *            the {@link TimedNeighbor} to be added to the list
	 * @return true if the neighbor was successfully added, false if the
	 *         neighbor already existed
	 */
	public boolean addInNeighbor(TimedNeighbor tn) {
		if (inNeighbors.getNeighbor(tn.getId()) == null) {
			return inNeighbors.addNeighbor(tn);
		}

		return false;
	}

	/**
	 * Recomputes the weights of the out-neighbors depending on the current
	 * out-neighbors list
	 */
	public void recomputeWeight() {
		Set<Neighbor> on = localNode.getOutNeighbors();
		double weight = 1.0 / on.size();
		logger.info("Having " + on.size() + " neighbors. Their weight is "
				+ weight);
		synchronized (outNeighbors) {
			for (PlainNeighbor n : outNeighbors) {
				n.setWeight(weight);
			}
		}
	}

	/**
	 * Sets the timer of a node to INF, denoting, that the value expected from
	 * that node has been received
	 * 
	 * @param nodeId
	 *            the string representation of the remote node
	 * @return true if the remote node's timer is set to INF, false otherwise
	 */
	public boolean setTimerToInf(String nodeId) {
		return inNeighbors.setTimerToInf(nodeId);
	}

	/**
	 * Resets the timer of all the in-neighbors
	 */
	public void resetTimers() {
		inNeighbors.renewTimers();
	}

	/**
	 * Resets the timer of an in-neighbor
	 * 
	 * @param nodeId
	 *            the string representation of the remote node's Id
	 * @return <code>true</code> if the timer was renewed or <code>false</code>
	 *         if the remote node was not found
	 */
	public boolean resetTimer(String nodeId) {
		return inNeighbors.renewTimer(nodeId);
	}

	/**
	 * Checks whether the current round of the Execution is over
	 * 
	 * @return true if the round is over, false otherwise
	 */
	public boolean roundIsOver() {
		synchronized (inNeighbors) {
			for (TimedNeighbor tn : inNeighbors) {
				if (tn.getTimeToProbe() != TimedNeighbor.INF) {
					return false;
				}
			}
		}
		return true;
	}

	private void createOutTable() {
		PlainNeighbor pn;
		Set<Neighbor> on = localNode.getOutNeighbors();
		outNeighbors = new PlainNeighborsTableSet();
		double weight = 1.0 / on.size();
		for (Neighbor n : on) {
			pn = new PlainNeighbor(n.getId(), n.getAddress(), weight);
			outNeighbors.addNeighbor(pn);
		}
	}

	private static int chooseInitialValue() {
		int initVal;
		Random rand = new Random();
		initVal = rand.nextInt(2);
		return initVal;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this)
			return true;
		if (obj == null)
			return false;
		if (!(obj instanceof Execution))
			return false;
		Execution e = (Execution) obj;

		return this.executionNumber == e.getExecutionNumber();
	}

}
