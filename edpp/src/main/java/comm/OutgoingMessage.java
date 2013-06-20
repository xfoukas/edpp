package comm;

import java.net.InetAddress;

import comm.ProtocolMessage.Message;

public class OutgoingMessage {
	
	private Message message;
	private InetAddress address;

	public OutgoingMessage(Message message, InetAddress address) {
		this.setMessage(message);
		this.address = address;
	}

	public Message getMessage() {
		return message;
	}

	public void setMessage(Message message) {
		this.message = message;
	}
	
	public InetAddress getAddress() {
		return address;
	}

	public void setAddress(InetAddress address) {
		this.address = address;
	}
	
}
