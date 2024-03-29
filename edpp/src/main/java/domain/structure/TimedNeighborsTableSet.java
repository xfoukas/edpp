package domain.structure;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import domain.Id;
import domain.TimedNeighbor;

/**
 * Table for storing and managing in-neighbors using a HashSet. This table is
 * thread safe, but iterating over the TimedNeighbor objects should be performed
 * using an Iterator
 * 
 * @author Xenofon Foukas
 * 
 */
public class TimedNeighborsTableSet implements TimedNeighborsTable {

	private Set<TimedNeighbor> neighborsList;
	private AtomicLong defaultTimerValue;

	/**
	 * Constructor class. Creates an empty table of size 0. The default timer
	 * value is set to DEFAULT_TIMER
	 */
	public TimedNeighborsTableSet() {
		neighborsList = Collections
				.synchronizedSet(new HashSet<TimedNeighbor>());
		this.defaultTimerValue = new AtomicLong(TimedNeighbor.DEFAULT_TIMER);
	}

	/**
	 * Constructor class. Creates an empty table of size 0
	 * 
	 * @param timerValue
	 *            the value which will be used as the default for timer renewals
	 */
	public TimedNeighborsTableSet(long timerValue) {
		neighborsList = Collections
				.synchronizedSet(new HashSet<TimedNeighbor>());
		this.defaultTimerValue = new AtomicLong(timerValue);
	}

	/**
	 * Constructor class. Creates an empty table with an initial capacity. The
	 * default timer value is set to DEFAULT_TIMER
	 * 
	 * @param initCapacity
	 *            the initial capacity of the table
	 */
	public TimedNeighborsTableSet(int initCapacity) {
		neighborsList = Collections
				.synchronizedSet(new HashSet<TimedNeighbor>());
		this.defaultTimerValue = new AtomicLong(TimedNeighbor.DEFAULT_TIMER);
	}

	/**
	 * Constructor class. Creates an empty table of size 0.
	 * 
	 * @param initCapacity
	 *            the initial capacity of the table
	 * @param timerValue
	 *            the value which will be used as the default for timer renewals
	 */
	public TimedNeighborsTableSet(int initCapacity, long timerValue) {
		neighborsList = Collections
				.synchronizedSet(new HashSet<TimedNeighbor>());
		this.defaultTimerValue = new AtomicLong(timerValue);
	}

	@Override
	public TimedNeighbor getNeighbor(Id nodeId) {
		synchronized (neighborsList) {
			for (TimedNeighbor n : neighborsList) {
				if (n.getId().equals(nodeId)) {
					return n;
				}
			}
		}
		return null;
	}

	@Override
	public TimedNeighbor getNeighbor(String stringId) {
		synchronized (neighborsList) {
			for (Iterator<TimedNeighbor> i = neighborsList.iterator(); i
					.hasNext();) {
				TimedNeighbor n = i.next();
				if (n.getId().toString().equals(stringId)) {
					return n;
				}
			}
		}
		return null;
	}

	@Override
	public boolean addNeighbor(TimedNeighbor node) {
		return neighborsList.add(node);
	}

	@Override
	public boolean removeNeighbor(Id nodeId) {
		synchronized (neighborsList) {
			for (Iterator<TimedNeighbor> i = neighborsList.iterator(); i
					.hasNext();) {
				TimedNeighbor n = i.next();
				if (n.getId().equals(nodeId)) {
					i.remove();
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public boolean removeNeighbor(TimedNeighbor node) {
		return neighborsList.remove(node);
	}

	@Override
	public TimedNeighbor[] toArray() {
		return neighborsList.toArray(new TimedNeighbor[0]);
	}

	@Override
	public Iterator<TimedNeighbor> iterator() {
		return neighborsList.iterator();
	}

	@Override
	public TimedNeighbor[] getExpiredNeighbors() {
		HashSet<TimedNeighbor> s = new HashSet<TimedNeighbor>();
		synchronized (neighborsList) {
			for (TimedNeighbor n : neighborsList) {
				if (n.getTimeToProbe() == 0)
					s.add(n);
			}
		}
		return s.toArray(new TimedNeighbor[0]);
	}

	@Override
	public TimedNeighbor[] getValidNeighbors() {
		HashSet<TimedNeighbor> s = new HashSet<TimedNeighbor>();
		synchronized (neighborsList) {
			for (TimedNeighbor n : neighborsList) {
				if (n.getTimeToProbe() > 0)
					s.add(n);
			}
		}
		return s.toArray(new TimedNeighbor[0]);
	}

	@Override
	public boolean setNeighborTimer(Id nodeId, long time) {
		synchronized (neighborsList) {
			for (TimedNeighbor n : neighborsList) {
				if (n.getId().equals(nodeId)) {
					n.setRemainingTime(time);
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public boolean renewTimer(TimedNeighbor node) {
		synchronized (neighborsList) {
			for (TimedNeighbor n : neighborsList) {
				if (n.equals(node)) {
					if (n.getTimeToProbe() != TimedNeighbor.INF) {
						n.setRemainingTime(defaultTimerValue.get());
						return true;
					}
				}
			}
		}
		return false;
	}

	@Override
	public boolean renewTimer(Id nodeId) {
		synchronized (neighborsList) {
			for (TimedNeighbor n : neighborsList) {
				if (n.getId().equals(nodeId)) {
					if (n.getTimeToProbe() != TimedNeighbor.INF) {
						n.setRemainingTime(defaultTimerValue.get());
						return true;
					}
				}
			}
		}
		return false;
	}

	@Override
	public boolean renewTimer(String nodeId) {
		synchronized (neighborsList) {
			for (TimedNeighbor n : neighborsList) {
				if (n.getId().toString().equals(nodeId)) {
					n.setRemainingTime(defaultTimerValue.get());
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public void setDefaultTimeValue(long time) {
		this.defaultTimerValue.set(time);
	}

	@Override
	public int getSize() {
		return neighborsList.size();
	}

	@Override
	public void renewTimers() {
		synchronized (neighborsList) {
			for (TimedNeighbor tn : neighborsList) {
				tn.setRemainingTime(defaultTimerValue.get());
			}
		}
	}

	@Override
	public boolean setTimerToInf(String nodeId) {
		synchronized (neighborsList) {
			for (TimedNeighbor n : neighborsList) {
				if (n.getId().toString().equals(nodeId)) {
					n.setRemainingTime(TimedNeighbor.INF);
					return true;
				}
			}
		}
		return false;
	}

}
