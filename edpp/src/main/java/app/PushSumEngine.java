package app;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.util.concurrent.AtomicDoubleArray;

import domain.Neighbor;
import domain.network.Node;
import app.ApplicationMessage.AppMessage;
import app.ApplicationMessage.AppMessage.MessageType;

/**
 * Class for executing the push-sum algorithm by Kempe et. al. It makes an
 * estimation of the average number of files per node in a Pastry overlay. The
 * number of rounds required during the execution are defined by the mixing time
 * of the network and by the size of the routing table
 * 
 * @author Xenofon Foukas
 * 
 */
public class PushSumEngine {

	public static final int PUSH_SUM_PORT = 9856;

	private Node localNode;
	private AtomicInteger mixTime;

	private int currentRound;
	private int numOfRounds;

	private AtomicBoolean protocolRunning;

	private Map<String, Double> query;

	AtomicDoubleArray values;
	AtomicDoubleArray weights;
	AtomicDoubleArray receivedValues;
	AtomicDoubleArray receivedWeights;

	private String currentQuery;
	private int numberOfInLinks;
	private int[] linksInRound;
	private double expectedVal;
	private int numOfNodes;
	private InetAddress bootNode;

	private ExecutorService executor;

	public PushSumEngine(Node localNode, InetAddress bootNode) {
		this.localNode = localNode;
		this.mixTime = new AtomicInteger(0);
		this.numOfRounds = 0;
		this.protocolRunning = new AtomicBoolean(false);
		this.currentRound = 0;
		executor = Executors.newFixedThreadPool(2);
		executor.execute(new MessageReceiver());
		executor.execute(new LightMessageReceiver());
		query = new ConcurrentHashMap<String, Double>();
		currentQuery = "";
		numberOfInLinks = 0;
		expectedVal = 0;
		numOfNodes = 0;
		this.bootNode = bootNode;
	}

	/**
	 * This method initiates the push-sum algorithm for estimating the average
	 * number of files per node in the network
	 * 
	 * @param initiator
	 *            true if this node is the initiator of the estimation
	 * @param mixTime
	 *            the mixing time of the network
	 * @return the estimation of the average number of files per node in the
	 *         network
	 */
	public double estimateSize(boolean initiator, double mixTime) {
		double estimation = 0;
		expectedVal = 0;
		numOfNodes = 0;
		this.protocolRunning.set(true);

		// write the estimations of each round to a file. required for the
		// experiments
		PrintWriter pw = null;
		try {

			FileWriter write = new FileWriter("estimated_vals.txt", false);

			pw = new PrintWriter(write);
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println("The mixing time of the network is: " + mixTime);
		System.out.println("The size of the routing table is: "
				+ localNode.getOutNeighbors().size());
		this.mixTime = new AtomicInteger((int) Math.ceil(mixTime));
		if (initiator) {
			this.numOfRounds = this.mixTime.get()
					+ localNode.getOutNeighbors().size();			
		} else {
			this.numOfRounds = this.mixTime.get();
		}
		System.out.println("The total number of rounds required are "
				+ numOfRounds);
		Random r = new Random();
		if (query.get(currentQuery) != null)
			return Double.POSITIVE_INFINITY;
		linksInRound = new int[numOfRounds];
		this.currentRound = 0;
		values = new AtomicDoubleArray(numOfRounds + 1);
		weights = new AtomicDoubleArray(numOfRounds + 1);
		receivedValues = new AtomicDoubleArray(numOfRounds + 1);
		receivedWeights = new AtomicDoubleArray(numOfRounds + 1);

		int initValue = new File("./").listFiles().length;

		AppMessage init = AppMessage.newBuilder().setType(MessageType.REAL_VAL)
				.setQueryId(currentQuery).setValue(initValue).build();

		MessageContainer cont = new MessageContainer(init, bootNode);
		// Put to sending queue
		sendMessage(cont, true);

		receivedValues.set(currentRound, initValue);
		receivedWeights.set(currentRound, 1);

		// Notify the rest of the nodes
		Set<Neighbor> neighbors = localNode.getOutNeighbors();

		if (initiator) {
			currentQuery = UUID.randomUUID().toString();
		}

		for (Neighbor n : neighbors) {
			AppMessage m = AppMessage.newBuilder().setType(MessageType.NEW)
					.setQueryId(currentQuery).setMixTime(mixTime).build();

			MessageContainer mc = new MessageContainer(m, n.getAddress());
			// Put to sending queue
			sendMessage(mc, true);
		}

		// Wait to receive messages from in-neighbors
		try {
			Thread.sleep(10000);
		} catch (InterruptedException e1) {
			// TODO Auto-generated catch block
		}

		System.out.println("The expected value is "
				+ (expectedVal / numOfNodes));

		for (int i = 0; i < numOfRounds; i++) {
			synchronized (linksInRound) {
				linksInRound[i] += numberOfInLinks;

			}
		}
		// For i=1 to mixTime
		// Wait to receive values
		// Get the values add them and proceed to the next round

		for (int round = 1; round < numOfRounds; round++) {
			weights.set(round, receivedWeights.get(round - 1));
			values.set(round, receivedValues.get(round - 1));
			estimation = values.get(round) / weights.get(round);

			float percentComplete = ((float) round / numOfRounds) * 100;
			System.out.println("Sampling at " + (int) percentComplete
					+ "% (round )" + round + "...Current estimation: "
					+ estimation);
			pw.println(estimation);
			double currentRecWeight = receivedWeights.get(round);
			double currentRecVals = receivedValues.get(round);
			receivedWeights.set(round, weights.get(round) * 0.5
					+ currentRecWeight);
			receivedValues.set(round, values.get(round) * 0.5 + currentRecVals);

			AppMessage m = AppMessage.newBuilder().setType(MessageType.VAL)
					.setQueryId(currentQuery).setRound(round)
					.setWeight(weights.get(round) * 0.5)
					.setValue(values.get(round) * 0.5).build();

			neighbors = localNode.getOutNeighbors();
			Neighbor[] n = neighbors.toArray(new Neighbor[0]);
			int size = n.length;
			// pick one out-neighbor at random
			int item = r.nextInt(size);
			Neighbor selected = null;

			// send to the out-neighbor 0.5*current_estimation
			// send a void message to the rest so that they can proceed to the
			// next round
			for (int i = 0; i < size; i++) {
				if (i == item) {
					selected = n[i];
				} else {
					AppMessage voidM = AppMessage.newBuilder()
							.setType(MessageType.VOID).setQueryId(currentQuery)
							.setRound(round).build();
					MessageContainer mc = new MessageContainer(voidM,
							n[i].getAddress());
					sendMessage(mc, false);
				}
			}

			// wait at most 3 seconds to receive the estimations of the other
			// nodes
			// then just proceed
			MessageContainer mc = new MessageContainer(m, selected.getAddress());
			sendMessage(mc, false);
			synchronized (linksInRound) {
				if (linksInRound[round] != 0) {
					try {
						linksInRound.wait(3000);
						linksInRound[round] = 0;
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
					}
				}
			}
		}

		// produce the final estimation from the values you got in the final
		// round
		estimation = values.get(numOfRounds - 1) / weights.get(numOfRounds - 1);
		pw.println(estimation);
		pw.close();

		query.put(currentQuery, estimation);
		currentQuery = "";
		numberOfInLinks = 0;
		protocolRunning.set(false);
		return estimation;
	}

	// method to send a message to a remote node. if the sendReliably flag is
	// set to true, the message will be sent using TCP, otherwise UDP
	private void sendMessage(MessageContainer mc, boolean sendReliably) {
		if (sendReliably) {
			boolean notSent = true;
			while (notSent) {
				try {
					Socket s = new Socket();
					s.connect(new InetSocketAddress(mc.getAddress(),
							PUSH_SUM_PORT));
					mc.getMessage().writeTo(s.getOutputStream());
					s.close();
					notSent = false;
				} catch (IOException e) {
				}
			}
		} else {
			try {
				ByteArrayOutputStream output = new ByteArrayOutputStream(1024);
				mc.getMessage().writeDelimitedTo(output);
				DatagramSocket socket = new DatagramSocket();
				byte[] buf = output.toByteArray();
				DatagramPacket packet = new DatagramPacket(buf, buf.length,
						mc.getAddress(), PUSH_SUM_PORT);
				socket.send(packet);
				socket.close();
			} catch (SocketException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	}

	class MessageReceiver implements Runnable {

		private ServerSocket ss;
		Socket incomingSocket;
		private ExecutorService protocolExec = Executors.newFixedThreadPool(1);

		@Override
		public void run() {
			try {
				ss = new ServerSocket(PUSH_SUM_PORT);
				while (true) {
					Socket incomingSocket = ss.accept();
					AppMessage pm = AppMessage.parseFrom(incomingSocket
							.getInputStream());
					if (pm.getType() == MessageType.NEW) {
						numberOfInLinks++;
						// if this is the first NEW message you received begin
						// the sampling, otherwise just ignore it
						if (!protocolRunning.get()) {
							protocolRunning.set(true);
							currentQuery = pm.getQueryId();
							final double mixTime = pm.getMixTime();
							Thread t = new Thread() {
								public void run() {
									estimateSize(false, mixTime);
								}
							};
							protocolExec.execute(t);
						}
					} else if (pm.getType() == MessageType.REAL_VAL) {
						// use the value received from producing a new
						// estimation
						expectedVal += pm.getValue();
						// increase the number of in-neighbors
						numOfNodes++;
					} else {
						int round = pm.getRound();
						// expect a message for this round from a neighbor less
						synchronized (linksInRound) {
							linksInRound[round] -= 1;
							if (linksInRound[round] == 0)
								linksInRound.notify();
						}
					}
					incomingSocket.close();
				}
			} catch (IOException e) {
				// TODO Must fix this to locate the exact case of the exception
			}

		}

	}

	class LightMessageReceiver implements Runnable {

		private DatagramSocket ss;

		@Override
		public void run() {
			try {
				ss = new DatagramSocket(PUSH_SUM_PORT);
				while (true) {
					byte[] buf = new byte[1024];
					DatagramPacket packet = new DatagramPacket(buf, buf.length);
					ss.receive(packet);
					ByteArrayInputStream input = new ByteArrayInputStream(buf);
					AppMessage pm = AppMessage.parseDelimitedFrom(input);
					if (pm.getType() == MessageType.VOID) {
						int round = pm.getRound();
						synchronized (linksInRound) {
							linksInRound[round] -= 1;
							if (linksInRound[round] == 0)
								linksInRound.notify();
						}
					} else if (pm.getType() == MessageType.VAL) {
						double weight = pm.getWeight();
						double value = pm.getValue();
						int round = pm.getRound();
						double newWeight = receivedWeights.get(round);
						double newValue = receivedValues.get(round);
						synchronized (linksInRound) {
							linksInRound[round] -= 1;
							if (linksInRound[round] == 0)
								linksInRound.notify();
						}
						receivedWeights.set(round, weight + newWeight);
						receivedValues.set(round, value + newValue);
					}
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				// TODO Must fix this to locate the exact case of the exception
			}

		}

	}

	class MessageContainer {
		private InetAddress address;
		private AppMessage message;

		public MessageContainer(AppMessage m, InetAddress a) {
			this.setMessage(m);
			this.setAddress(a);
		}

		public InetAddress getAddress() {
			return address;
		}

		public void setAddress(InetAddress address) {
			this.address = address;
		}

		public AppMessage getMessage() {
			return message;
		}

		public void setMessage(AppMessage message) {
			this.message = message;
		}
	}
}
