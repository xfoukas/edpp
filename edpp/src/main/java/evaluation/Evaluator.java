package evaluation;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import algorithms.Algorithms;

import comm.ProtocolMessage.SessionEvent;
import comm.ProtocolMessage.SessionEvent.EventType;

import util.SamplingParameters;
import core.ProtocolEngine;
import core.Session;
import core.SessionListener;

public class Evaluator {
	
	public static final int EVALUATION_PORT = 34567;

	private ProtocolEngine pe;
	private boolean initiator;
	private SessionListener listener;
	private List<SessionEvent> initialEvents;
	private List<SessionEvent> terminationEvents;
	private ExecutorService executor;
	private IncomingEvaluationDataListener iedl; 
	
	class IncomingEvaluationDataListener implements Runnable {
		
		Socket s;
		ServerSocket ss;
		
		@Override
		public void run() {
			try {
				ss = new ServerSocket(EVALUATION_PORT);
				
				while ((s = ss.accept()) != null) {
					SessionEvent se = SessionEvent.parseFrom(s.getInputStream());
					if (se.getType() == EventType.INITIAL) {
						initialEvents.add(se);
					} else {
						terminationEvents.add(se);
					}
					s.close();
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		}
	}
	
	public Evaluator(ProtocolEngine pe, final InetAddress evaluatorAddress, boolean init) {
		
		
		
		this.pe = pe;
		this.initiator = init;
		
		initialEvents = new ArrayList<SessionEvent>();
		terminationEvents = new ArrayList<SessionEvent>();
		
		if (initiator) {
			executor = Executors.newSingleThreadExecutor();
			iedl = new IncomingEvaluationDataListener();
			executor.execute(iedl);
		}

		listener = new SessionListener() {
			
			@Override
			public void sessionInitiated(SessionEvent e) {
				if (initiator) {
					initialEvents.add(e);
				} else {
					try {
						Socket s = new Socket(evaluatorAddress, EVALUATION_PORT);
						e.writeTo(s.getOutputStream());
						s.close();
					} catch (IOException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
				}
			}
			
			@Override
			public void sessionCompleted(SessionEvent e) {
				if (initiator) {
					terminationEvents.add(e);
				} else {
					try {
						Socket s = new Socket(evaluatorAddress, EVALUATION_PORT);
						e.writeTo(s.getOutputStream());
						s.close();
					} catch (IOException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
				}
			}
		};
	}
	
	public EvaluationResults evaluateEngine(int numberOfExecutions, int numberOfRounds) {
		
		pe.addSessionListener(listener);
		
		
		if (initiator) {
			SamplingParameters sp = new SamplingParameters(numberOfExecutions, numberOfRounds);
			Session s = pe.requestSessionData(sp);
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			double [] computedEigenvals = s.getComputedEigenvalues();
			//TODO must construct graphs and analyze returned data and data from graphs
			NetworkGraph initialGraph = new NetworkGraph();
			NetworkGraph terminalGraph = new NetworkGraph();
			
			for (SessionEvent se : initialEvents) {
				//Construct initial graph
				initialGraph.addNode(se.getLocalNodeId());
				List<String> outNeighbors = se.getOutNeighborsList();
				double weight = (double) 1 / outNeighbors.size();
				for (String outNeighbor : outNeighbors) {
					initialGraph.addNode(outNeighbor);
					initialGraph.addLinkWithWeight(se.getLocalNodeId(), outNeighbor, weight);
				}
			}
			for (SessionEvent se : terminationEvents) {
				//Construct final graph
				terminalGraph.addNode(se.getLocalNodeId());
				List<String> outNeighbors = se.getOutNeighborsList();
				double weight = (double) 1 / outNeighbors.size();
				for (String outNeighbor : outNeighbors) {
					terminalGraph.addNode(outNeighbor);
					terminalGraph.addLinkWithWeight(se.getLocalNodeId(), outNeighbor, weight);
				}
			}
			
			double [] expectedEigenvals = Algorithms.computeEigenvalues(terminalGraph.getMatrixOfWeights());
			//TODO analyze both the expected and the comoputed data and create the EvaluationResults
		}
		return null;
	}
	
	public void detachEvaluator() {
		pe.removeSessionListener(listener);
		executor.shutdown();
		try {
			iedl.s.close();
			iedl.ss.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
}