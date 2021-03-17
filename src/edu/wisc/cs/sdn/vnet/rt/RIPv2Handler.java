package edu.wisc.cs.sdn.vnet.rt;

import java.util.concurrent.locks.ReentrantLock;

import edu.wisc.cs.sdn.vnet.Iface;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.RIPv2;
import net.floodlightcontroller.packet.UDP;

public class RIPv2Handler implements Runnable {

	private Router router;
   
	private RouteTable routeTable;
	
	private Thread thread;
	
	public RIPv2Handler(Router router, RouteTable rTable) {
		this.router = router;
		this.routeTable = rTable;
		RIPv2 request = new RIPv2();
		thread = new Thread(this);
		thread.start();
	}

	public void sendRIPv2Packet(RIPv2 ripPacket){
		ripPacket.setCommand(RIPv2.COMMAND_REQUEST);
		UDP udpPacket = new UDP();
		udpPacket.setSourcePort((short) 520);
		udpPacket.setDestinationPort((short) 520);
		udpPacket.setPayload(ripPacket);
		for(Iface i : router.getInterfaces().values()) {
			IPv4 ipPacket = new IPv4();
			ipPacket.setPayload(udpPacket);
			ipPacket.setSourceAddress(i.getIpAddress());
			ipPacket.setDestinationAddress("224.0.0.9");
			ipPacket.setProtocol(IPv4.PROTOCOL_UDP);
			ipPacket.setTtl((byte) 2); 
			Ethernet etherPacket = new Ethernet();
			etherPacket.setPayload(ipPacket);
			etherPacket.setSourceMACAddress(i.getMacAddress().toBytes());
			etherPacket.setDestinationMACAddress("FF:FF:FF:FF:FF:FF");
			etherPacket.setEtherType(Ethernet.TYPE_IPv4);
			router.sendPacket(etherPacket, i);
		}
	}
	
	/*
	 * Sends an unsolicted RIPv2 response every 10 seconds
	 */
	@Override
	public void run() {
		// TODO Auto-generated method stub
		while(true) {
			try {
				lock.lock();
			} finally {
				
			}
		}
	}
	
}
