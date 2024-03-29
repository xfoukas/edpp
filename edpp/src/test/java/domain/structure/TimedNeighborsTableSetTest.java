package domain.structure;

import static org.junit.Assert.*;

import java.net.InetAddress;

import org.junit.Before;
import org.junit.Test;

import domain.Id;
import domain.TimedNeighbor;
import domain.structure.TimedNeighborsTable;
import domain.structure.TimedNeighborsTableSet;

public class TimedNeighborsTableSetTest {

	TimedNeighborsTable table;
	InetAddress a1,a2,a3;
	
	@Before
	public void setUp() throws Exception {
		byte [] id1 = {1,2,3,4,5,6,7,8,9,0};
		byte [] id2 = {0,9,8,7,6,5,4,3,2,1};
		byte [] id3 = {11,22,33,44,55,66,77,88,99,10};
		
		a1 = InetAddress.getByName("192.168.0.1");
		a2 = InetAddress.getByName("192.168.0.2");
		a3 = InetAddress.getByName("192.168.0.3");

		TimedNeighbor n1, n2, n3;
		
		n1 = new TimedNeighbor(id1, a1);
		n2 = new TimedNeighbor(id2, a2);
		n3 = new TimedNeighbor(id3, a3);
		
		table = new TimedNeighborsTableSet();
		
		table.addNeighbor(n1);
		table.addNeighbor(n2);
		table.addNeighbor(n3);
	
	}

	@Test
	public void getNeighborById() throws Exception {
		byte [] id1 = {1,2,3,4,5,6,7,8,9,0};
		Id i = new Id(id1);
		InetAddress a1;
		a1 = InetAddress.getByName("192.168.0.1");
		TimedNeighbor n = table.getNeighbor(i);
		TimedNeighbor expected = new TimedNeighbor(i, a1);
		assertEquals(expected, n);
	}
	
	@Test
	public void getNeighborByStringId() {
		byte [] id1 = {1,2,3,4,5,6,7,8,9,0};
		Id i = new Id(id1);
		TimedNeighbor n = table.getNeighbor(i.toString());
		TimedNeighbor expected = new TimedNeighbor(i, a1);
		assertEquals(expected, n);
	}
	
	@Test
	public void neighborDoesNotExistById() {
		byte [] id1 = {11,2,3,4,5};
		Id i = new Id(id1);
		assertNull(table.getNeighbor(i));
	}
	
	@Test
	public void neighborDoesNotExistByStringId() {
		byte [] id1 = {11,2,3,4,5};
		Id i = new Id(id1);
		assertNull(table.getNeighbor(i.toString()));
	}
	
	@Test
	public void addAlreadyExistingNeighbor() {
		byte [] id1 = {1,2,3,4,5,6,7,8,9,0};
		Id i = new Id(id1);
		TimedNeighbor n = new TimedNeighbor(i, a1);
		assertFalse(table.addNeighbor(n));
	}
	
	@Test
	public void removeNode() {
		byte [] id1 = {1,2,3,4,5,6,7,8,9,0};
		Id i = new Id(id1);
		TimedNeighbor n = new TimedNeighbor(i, a1);
		assertTrue(table.removeNeighbor(n));
	}
	
	@Test
	public void removeNonExistentNode() {
		byte [] id1 = {1,2,3,4,5};
		Id i = new Id(id1);
		TimedNeighbor n = new TimedNeighbor(i, a1);
		assertFalse(table.removeNeighbor(n));
	}
	
	@Test
	public void removeNodeUsingId() {
		byte [] id1 = {1,2,3,4,5,6,7,8,9,0};
		Id i = new Id(id1);
		assertTrue(table.removeNeighbor(i));
	}
	
	@Test
	public void removeNonExistentNodeUsingId() {
		byte [] id1 = {1,2,3,4,5};
		Id i = new Id(id1);
		assertFalse(table.removeNeighbor(i));
	}
	
	@Test
	public void convertTableToArray() {
		TimedNeighbor [] n1 = table.toArray();
		assertEquals(3, n1.length);
	}
	
	@Test
	public void provideSize() {
		assertEquals(3,table.getSize());
	}
	
	@Test
	public void findNeighborsThatHaveExpired() {
		byte [] id1 = {1,2,3,4,5,6,7,8,9,0};
		Id i = new Id(id1);
		TimedNeighbor t = table.getNeighbor(i);
		t.setRemainingTime(0);
		TimedNeighbor [] expired = table.getExpiredNeighbors();
		assertEquals(i,expired[0].getId());
		assertEquals(1, expired.length);
	}
	
	@Test
	public void findNeighborsThatHaveNotExpired() {
		byte [] id1 = {1,2,3,4,5,6,7,8,9,0};
		Id i = new Id(id1);
		TimedNeighbor t = table.getNeighbor(i);
		t.setRemainingTime(0);
		TimedNeighbor [] valid = table.getValidNeighbors();
		assertEquals(2, valid.length);
	}
	
	@Test
	public void changeTimerOfNode() {
		byte [] id1 = {1,2,3,4,5,6,7,8,9,0};
		Id i = new Id(id1);
		table.setNeighborTimer(i, 2000);
		TimedNeighbor t = table.getNeighbor(i);
		assertEquals(2000, t.getTimeToProbe());
	}
	
	@Test
	public void renewTimerOfNode() {
		byte [] id1 = {1,2,3,4,5,6,7,8,9,0};
		Id i = new Id(id1);
		TimedNeighbor t = table.getNeighbor(i);
		t.decreaseTime(500);
		table.renewTimer(t);
		assertEquals(TimedNeighbor.DEFAULT_TIMER, t.getTimeToProbe());
	}
	
	@Test
	public void renewTimerOfNodeUsingStringId() {
		byte [] id1 = {1,2,3,4,5,6,7,8,9,0};
		Id i = new Id(id1);
		TimedNeighbor t = table.getNeighbor(i);
		t.decreaseTime(500);
		table.renewTimer(i.toString());
		assertEquals(TimedNeighbor.DEFAULT_TIMER, t.getTimeToProbe());
	}
	
	@Test
	public void renewTimerOfNodeThatDoesNotExist() {
		byte [] id1 = {1,2,3,4,5};
		Id i = new Id(id1);
		assertFalse(table.renewTimer(i.toString()));
	}
	
	@Test
	public void changeDefaultRenewalValueOfTimer() {
		byte [] id1 = {1,2,3,4,5,6,7,8,9,0};
		Id i = new Id(id1);
		table.setDefaultTimeValue(5000);
		table.renewTimer(i);
		assertEquals(5000, table.getNeighbor(i).getTimeToProbe());
	}
	
	@Test
	public void renewAllTimers() {
		table.setDefaultTimeValue(5000);
		table.renewTimers();
		synchronized (table) {
			for (TimedNeighbor tn : table) {
				assertEquals(5000, tn.getTimeToProbe());
			}
		}
	}
	
	@Test
	public void setExistingTimerToInf() {
		byte [] id1 = {1,2,3,4,5,6,7,8,9,0};
		Id i = new Id(id1);
		assertTrue(table.setTimerToInf(i.toString()));
		assertEquals(TimedNeighbor.INF, table.getNeighbor(i).getTimeToProbe());
	}
	
	@Test
	public void setNonExistingTimerToInf() {
		byte [] id1 = {1,1,1,1,1};
		Id i = new Id(id1);
		assertFalse(table.setTimerToInf(i.toString()));
	}

}
