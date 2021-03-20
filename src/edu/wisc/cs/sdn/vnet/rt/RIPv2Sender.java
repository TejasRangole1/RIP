package edu.wisc.cs.sdn.vnet.rt;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import edu.wisc.cs.sdn.vnet.Iface;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.RIPv2;
import net.floodlightcontroller.packet.RIPv2Entry;
import net.floodlightcontroller.packet.UDP;

/**
 * Sends RIP requsts on start and sends rip responses every 10 seconds
 */
public class RIPv2Sender implements Runnable {


	private Router router;
   /** Table to store rip entries */
	private Map<Integer, RIPv2Entry> ripTable;
	
	private Thread responseThread;

	
	public RIPv2Sender(Router router, Map<Integer, RIPv2Entry> someTable){
		this.router = router;
		this.ripTable = someTable;
		RIPv2 request = new RIPv2();
		request.setCommand(RIPv2.COMMAND_REQUEST);
		
		floodRIPv2Packet(request);
		responseThread = new Thread(this);
		responseThread.start();
	}
    /**
	 * Sends a RIP packet out all interfaces
	 * @param ripPacket
	 */
	public void floodRIPv2Packet(RIPv2 ripPacket){
		for(Iface i : router.getInterfaces().values()) {
			Ethernet etherPacket = encapsulateRIPv2Packet(ripPacket, i);
			//System.out.println("RIPv2Sender.java(): floodRIPv2Packet() sending rip packet out interface: " + i.getName());
			router.sendPacket(etherPacket, i);
		}
	}
	public Ethernet encapsulateRIPv2Packet(RIPv2 ripPacket, Iface i){
		UDP udpPacket = new UDP();
		udpPacket.setSourcePort((short) 520);
		udpPacket.setDestinationPort((short) 520);
		udpPacket.setPayload(ripPacket);
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
		return etherPacket;
	}
	/*
	 * Sends an unsolicted RIPv2 response every 10 seconds
	 */
	@Override
	public void run(){
		// TODO Auto-generated method stub
		while(true){
			try{
				// sleep for 10 seconds
				responseThread.sleep(10000);
			} catch (InterruptedException e){
				break;
			}
			RIPv2 response = new RIPv2();
			List<RIPv2Entry> entries = new ArrayList<>(ripTable.values());
			response.setEntries(entries);
			response.setCommand(RIPv2.COMMAND_RESPONSE);
			floodRIPv2Packet(response);
		}
	}
	
}
