package io;

import java.io.Console;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;

import network.PastryOverlayNode;

import core.ProtocolEngine;
import core.Session;
import evaluation.EvaluationResults;
import evaluation.Evaluator;

import rice.environment.Environment;
import rice.pastry.NodeIdFactory;
import rice.pastry.PastryNode;
import rice.pastry.PastryNodeFactory;
import rice.pastry.socket.SocketPastryNodeFactory;
import rice.pastry.standard.RandomNodeIdFactory;
import util.SamplingParameters;

/**
 * 
 * @author Xenofon Foukas
 *
 */
public class EvaluationPastryNode {
	
	private ProtocolEngine pe;
	private PastryNode node;

	public EvaluationPastryNode(int bindport, InetSocketAddress bootaddress, Environment env) throws Exception {

			    // Generate the NodeIds Randomly
			    NodeIdFactory nidFactory = new RandomNodeIdFactory(env);

			    // construct the PastryNodeFactory, this is how we use rice.pastry.socket
			    PastryNodeFactory factory = new SocketPastryNodeFactory(nidFactory, bindport, env);

			    // construct a node
			    node = factory.newNode();

			    // construct a new MyApp
			    PastryOverlayNode pon = new PastryOverlayNode(node, 2);
			    pe = new ProtocolEngine(pon);
			    node.boot(bootaddress);

			    // the node may require sending several messages to fully boot into the ring
			    synchronized(node) {
			      while(!node.isReady() && !node.joinFailed()) {
			        // delay so we don't busy-wait
			        node.wait(500);

			        // abort if can't join
			        if (node.joinFailed()) {
			          throw new IOException("Could not join the FreePastry ring.  Reason:"+node.joinFailedReason()); 
			        }
			      }       
			    }
			    Runtime.getRuntime().addShutdownHook(new Thread() {
			        public void run() { 
			        	terminate();
			        }
			     });
			   
			    System.out.println("Finished creating new node "+node);
			    System.out.println("Wait to properly join pastry overlay");
			    // wait 10 seconds
			    env.getTimeSource().sleep(5000);
			    System.out.println("Application ready");
	}
	
	public void terminate() {
		pe.terminate();
		node.destroy();
	}
	
	public ProtocolEngine getProtocolEngine() {
		return this.pe;
	}
	
	public Session requestSamplingData(int numberOfExecutions, int numberOfRounds) {
		SamplingParameters sp = new SamplingParameters(numberOfExecutions, numberOfRounds);
		return pe.requestSessionData(sp);
	}
	
		
	/**
	 * Usage:
	 * java [-cp FreePastry-<version>.jar] rice.tutorial.lesson3.DistTutorial localbindport bootIP bootPort
	 * example java rice.tutorial.DistTutorial 9001 pokey.cs.almamater.edu 9001
	 */
	public static void main(String[] args) throws Exception {
	    // Loads pastry settings
		Environment env = new Environment();
		Evaluator eval;
			
		// disable the UPnP setting (in case you are testing this on a NATted LAN)
		env.getParameters().setString("nat_search_policy","never");
			   
		try {
			// the port to use locally
			int bindport = Integer.parseInt(args[0]);
	     
			// build the bootaddress from the command line args
			InetAddress bootaddr = InetAddress.getByName(args[1]);
			int bootport = Integer.parseInt(args[2]);
			InetSocketAddress bootaddress = new InetSocketAddress(bootaddr,bootport);

			// launch our node!
			EvaluationPastryNode dt = new EvaluationPastryNode(bindport, bootaddress, env);
			if (bootaddr.isAnyLocalAddress() || bootaddr.isLoopbackAddress()) {
				eval = new Evaluator(dt.getProtocolEngine(), bootaddr, true);
			} else {
				eval = new Evaluator(dt.getProtocolEngine(), bootaddr, false);
			}
			
			Console c = System.console();
			if (c == null) {
				System.err.println("No console.");
				System.err.println("Running in support mode");
			} else {
				while (true) {
					String command = c.readLine("Command:");
					List<String> items = Arrays.asList(command.split("\\s+"));
					if (items.get(0).equals("evaluate")) {
						if (items.size() != 3) {
							System.out.println("Must give two arguments - (evaluate numOfExecutions numOfRounds)");
							continue;
						}
						int numberOfExecutions = Integer.parseInt(items.get(1));
						int numberOfRounds = Integer.parseInt(items.get(2));
						EvaluationResults results = eval.evaluateEngine(numberOfExecutions, numberOfRounds);
						
						System.out.println("Evaluation complete...");
						System.out.println("The 50th percentile of the spectral gap percent error is "
								+ results.getSpectralGapPercentError(50));
						System.out.println("The 50th percentile of the mixing time percent error is "
								+ results.getMixingTimePercentError(50));
					} else if (items.get(0).equals("exit")) {
//						dt.terminate();
						System.out.println("Exiting...");
						System.exit(0);
					} else {
						System.out.println("Accepted commands are evaluate and exit...");
					}
				}
			}
		} catch (Exception e) {
			// remind user how to use
			System.out.println("Usage:"); 
			System.out.println("java [-cp FreePastry-<version>.jar] rice.tutorial.lesson3.DistTutorial localbindport bootIP bootPort");
			System.out.println("example java rice.tutorial.DistTutorial 9001 pokey.cs.almamater.edu 9001");
			throw e; 
		}
	}

	
}