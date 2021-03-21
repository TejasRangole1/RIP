package edu.wisc.cs.sdn.vnet.rt;



import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.MACAddress;
import net.floodlightcontroller.packet.RIPv2;
import net.floodlightcontroller.packet.RIPv2Entry;
import net.floodlightcontroller.packet.UDP;

/**
 * @author Aaron Gember-Jacobson and Anubhavnidhi Abhashkumar
 */
public class Router extends Device
{	
	/** Routing table for the router */
	private RouteTable routeTable;

	/** ARP cache for the router */
	private ArpCache arpCache;
    /** Data Structure for RIP-based route table */
	private Map<Integer, RIPv2Entry> ripTable;
    /** Thread to send RIP responses */
	private RIPv2Sender sender;

	/** Thread to delete rip entries after time has expired*/
	private RIPv2Updater updater;

	/** Thread to process RIP responses */

	/** Lock responsible for protecting the ripTable */

	ReentrantLock lock;

	/**
	 * Creates a router for a specific host.
	 * @param host hostname for the router
	 */
	public Router(String host, DumpFile logfile)
	{
		super(host,logfile);
		this.routeTable = new RouteTable();
		this.arpCache = new ArpCache();
		
	}

	public void start(){
		this.ripTable = new ConcurrentHashMap<>();
		this.lock = new ReentrantLock();
		// initializing route table with entries of directly connected interfaces
		for(Iface i : this.getInterfaces().values()) {
			int subnet = i.getIpAddress() & i.getSubnetMask();
			//System.out.println("Router.java : Router(): adding subnet " + subnet + "to RIP table");
			ripTable.put(subnet, new RIPv2Entry(subnet, i.getSubnetMask(), 1, 0, i, true));
		}
		sender = new RIPv2Sender(this, ripTable);
		Thread ripUpdater = new Thread(new RIPv2Updater(ripTable, lock));
		ripUpdater.start();
	}

	/**
	 * @return routing table for the router
	 */
	public RouteTable getRouteTable()
	{ return this.routeTable; }

	/**
	 * Load a new routing table from a file.
	 * @param routeTableFile the name of the file containing the routing table
	 */
	public void loadRouteTable(String routeTableFile)
	{
		if (!routeTable.load(routeTableFile, this))
		{
			System.err.println("Error setting up routing table from file "
					+ routeTableFile);
			System.exit(1);
		}

		System.out.println("Loaded static route table");
		System.out.println("-------------------------------------------------");
		System.out.print(this.routeTable.toString());
		System.out.println("-------------------------------------------------");
	}

	/**
	 * Load a new ARP cache from a file.
	 * @param arpCacheFile the name of the file containing the ARP cache
	 */
	public void loadArpCache(String arpCacheFile)
	{
		if (!arpCache.load(arpCacheFile))
		{
			System.err.println("Error setting up ARP cache from file "
					+ arpCacheFile);
			System.exit(1);
		}

		System.out.println("Loaded static ARP cache");
		System.out.println("----------------------------------");
		System.out.print(this.arpCache.toString());
		System.out.println("----------------------------------");
	}
	/**
	 * Encapsulates rip packet into ethernet frame
	 * @param ripPacket
	 * @param i
	 * @return
	 */
	public Ethernet encapsulateRIPv2Packet(RIPv2 ripPacket, MACAddress srcMac, MACAddress dstMac, int srcIP, int dstIP){
		UDP udpPacket = new UDP();
		udpPacket.setSourcePort((short) 520);
		udpPacket.setDestinationPort((short) 520);
		udpPacket.setPayload(ripPacket);
		IPv4 ipPacket = new IPv4();
		ipPacket.setPayload(udpPacket);
		ipPacket.setSourceAddress(srcIP);
		ipPacket.setDestinationAddress(dstIP);
		ipPacket.setProtocol(IPv4.PROTOCOL_UDP);
		ipPacket.setTtl((byte) 2); 
		Ethernet etherPacket = new Ethernet();
		etherPacket.setPayload(ipPacket);
		etherPacket.setSourceMACAddress(srcMac.toBytes());
		etherPacket.setDestinationMACAddress(dstMac.toBytes());
		etherPacket.setEtherType(Ethernet.TYPE_IPv4);
		return etherPacket;
	}
	/**
	 * Checks if ethernet frame is a RIPv2 packet, if it is then return the packet otherwise return null
	 * @param etherPacket
	 * @return
	 */
	public RIPv2 isRIPv2Packet(Ethernet etherPacket){
		IPv4 ipPacket = (IPv4) etherPacket.getPayload();
		if(ipPacket.getProtocol() == IPv4.PROTOCOL_UDP && ipPacket.getDestinationAddress() == IPv4.toIPv4Address("224.0.0.9")){
			UDP udpPacket = (UDP) ipPacket.getPayload();
			if(udpPacket.getDestinationPort() == (short) 520){
				RIPv2 ripPacket = (RIPv2) udpPacket.getPayload();
				return ripPacket;
			}
		}
		return null;
	}
	public int lookupSubnet(int ip){
		for(Map.Entry<Integer, RIPv2Entry> entry : ripTable.entrySet()){
			if((entry.getValue().getSubnetMask() & ip) == entry.getKey()){
				//System.out.println("Router.java: lookupSubnet() found subnet " + 
				//IPv4.fromIPv4Address(entry.getValue().getSubnetMask() & ip) + " for ip " + IPv4.fromIPv4Address(ip));
				return ip & entry.getValue().getSubnetMask();
			}
		}
		//System.out.println("Router.java: lookupSubnet(): entry failed");
		return -1;
	}
	/**
	 * Updates RIPv2 table by examining the response packet
	 * @param response
	 */
	public void handleResponse(RIPv2 response, int sourceSubnet, int sourceIP, Iface sourceIface){
		for(RIPv2Entry entry : response.getEntries()){
			int dest = entry.getAddress(); //destination subnet
			int cost = entry.getMetric();
			lock.lock();
			try {
				if (ripTable.containsKey(dest)) {
					cost += ripTable.get(sourceSubnet).getMetric();
					// if the new cost to destination is less than the current cost, update the ripTable with the new route
					if (cost < ripTable.get(dest).getMetric()) {
						ripTable.put(dest, new RIPv2Entry(dest, entry.getSubnetMask(), cost, sourceIP, sourceIface, false));
					}
				}
				// route does not exist in rip table, add it
				else {
					ripTable.put(dest, new RIPv2Entry(dest, entry.getSubnetMask(), cost + ripTable.get(sourceSubnet).getMetric(), sourceIP, sourceIface, false));
				}
			} catch (Exception e){
				e.printStackTrace();
			} finally {
				lock.unlock();
			}
		}
	}

	public void printRIPTable(){
		for(Map.Entry<Integer, RIPv2Entry> entry : ripTable.entrySet()){
			System.out.println("dest subnet: " + IPv4.fromIPv4Address(entry.getKey()) + " cost= " + entry.getValue().getMetric() + " next hop IP: " + IPv4.fromIPv4Address(entry.getValue().getNextHopAddress()) + 
			" is host= " + entry.getValue().isHost());
		}
	}

	
	
	/**
	 * Handle an Ethernet packet received on a specific interface.
	 * @param etherPacket the Ethernet packet that was received
	 * @param inIface the interface on which the packet was received
	 */
	public void handlePacket(Ethernet etherPacket, Iface inIface)
	{    
		/*
		System.out.println("*** -> Received packet: " +
				etherPacket.toString().replace("\n", "\n\t"));

		/********************************************************************/
		/* TODO: Handle packets                                             */

		switch(etherPacket.getEtherType())
		{
		case Ethernet.TYPE_IPv4:
		    // checks if incoming packet is a RIP packet
			IPv4 ipPacket = (IPv4) etherPacket.getPayload();
			RIPv2 ripPacket = isRIPv2Packet(etherPacket);
			if(ripPacket != null){
				// send response if the packet received is a RIP request
				if(ripPacket.getCommand() == RIPv2.COMMAND_REQUEST){
					// handle rip request
					//System.out.println("Router.java: handlePacket() recevied rip request from " + ipPacket.getSourceAddress());
					RIPv2 response = new RIPv2();
					List<RIPv2Entry> entries = new ArrayList<>(ripTable.values());
					response.setEntries(entries);
					response.setCommand(RIPv2.COMMAND_RESPONSE);
					
					MACAddress srcMac = inIface.getMacAddress();
					MACAddress dstMac = etherPacket.getSourceMAC();
					
					/*
					MACAddress srcMac = MACAddress.valueOf("FF:FF:FF:FF:FF:FF");
					MACAddress dstMac = inIface.getMacAddress();
					*/
					int srcIP = inIface.getIpAddress();
					int dstIP = ipPacket.getSourceAddress();
					Ethernet ripFrame = encapsulateRIPv2Packet(response, srcMac, dstMac, srcIP, dstIP);
					sendPacket(ripFrame, inIface);
				}
				// update tables based on RIP response packet
				else{
					//System.out.println("Router.java: handlePacket(): Outputting RIP table: ");
					//System.out.println("----------------------------------------------------------");
                    //printRIPTable();
					//System.out.println("-------------------------------------------------------------");
					int sourceSubnet = ipPacket.getSourceAddress() & inIface.getSubnetMask();
					//System.out.println("Router.java: handlePacket(): source subnet of incoming rip response: " + 
					//IPv4.fromIPv4Address(sourceSubnet));
					handleResponse(ripPacket, sourceSubnet, ipPacket.getSourceAddress(), inIface);
				}
			}
			else{
				this.handleIpPacket(etherPacket, inIface);
			}
			break;
		// Ignore all other packet types, for now
		}

		/********************************************************************/
	}
	

	private void handleIpPacket(Ethernet etherPacket, Iface inIface)
	{
		// Make sure it's an IP packet
		if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4)
		{ return; }

		// Get IP header
		IPv4 ipPacket = (IPv4)etherPacket.getPayload();

		// Verify checksum
		short origCksum = ipPacket.getChecksum();
		ipPacket.resetChecksum();
		byte[] serialized = ipPacket.serialize();
		ipPacket.deserialize(serialized, 0, serialized.length);
		short calcCksum = ipPacket.getChecksum();
		if (origCksum != calcCksum)
		{ return; }

		// Check TTL
		ipPacket.setTtl((byte)(ipPacket.getTtl()-1));
		if (0 == ipPacket.getTtl())
		{ return; }

		// Reset checksum now that TTL is decremented
		ipPacket.resetChecksum();

		// Check if packet is destined for one of router's interfaces
		for (Iface iface : this.interfaces.values())
		{
			if (ipPacket.getDestinationAddress() == iface.getIpAddress())
			{ return; }
		}

		// Do route lookup and forward
		this.forwardIpPacket(etherPacket, inIface);
	}

	private void forwardIpPacket(Ethernet etherPacket, Iface inIface)
	{
		// Make sure it's an IP packet
		//System.out.println("Router.java: forwardIpPacket(): Outputting RIP Table");
		//System.out.println("-------------------------------------------------------");
		//printRIPTable();
		//System.out.println("-------------------------------------------------------");
		if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4)
		{ return; }
        
		// Get IP header
		IPv4 ipPacket = (IPv4)etherPacket.getPayload();
		int destAddr = ipPacket.getDestinationAddress();
		int subnet = lookupSubnet(destAddr); // to retrieve entry from ripTable for which subnet is a key.
		//System.out.println("Router.java: forwardIpPacket(): dest addr: " + IPv4.fromIPv4Address(destAddr) + " dest subnet: " + IPv4.fromIPv4Address(subnet));
		// Find matching route table entry 
		// RouteEntry bestMatch = this.routeTable.lookup(dstAddr);
		// Find matching route in rip table entry
		RIPv2Entry bestMatch = ripTable.get(subnet);
		//System.out.println("Router.java: forwardIpPacket(): bestMatch subnet: " + IPv4.fromIPv4Address(bestMatch.getAddress()));
		// If no entry matched, do nothing
		if (null == bestMatch)
		{ return; }

		// Make sure we don't sent a packet back out the interface it came in
		/*
		Iface outIface = bestMatch.getInterface();
		if (outIface == inIface)
		{ return; }
        */
		System.out.println("Router.java: forwardIpPacket(): sending to dest subnet: " + IPv4.fromIPv4Address(bestMatch.getAddress()));
		System.out.println("------------------------Route Table------------------------------------------------");
		printRIPTable();
		System.out.println("------------------------------------------------------------------------------------");	
		// this router is the destination, do not forward
		Iface outIface = bestMatch.getOutIface();
		//System.out.println("Router.java: forwardIpPacket() outIface is " + outIface);
		if(outIface == null) return;
		etherPacket.setSourceMACAddress(outIface.getMacAddress().toBytes());
		int nextHopAddr = bestMatch.getNextHopAddress();
		nextHopAddr = (nextHopAddr == 0) ? destAddr : nextHopAddr;
		//System.out.println("Router.java: forwardIpPacket(): nextHop address: " + IPv4.fromIPv4Address(nextHopAddr));
		// Set source MAC address in Ethernet header
		
		// If no gateway, then nextHop is IP destination
		/*
		int nextHop = bestMatch.getGatewayAddress();
		if (0 == nextHop)
		{ nextHop = dstAddr; }
		*/
		// Set destination MAC address in Ethernet header
		ArpEntry arpEntry = this.arpCache.lookup(nextHopAddr);
		if (null == arpEntry)
		{ return; }
		etherPacket.setDestinationMACAddress(arpEntry.getMac().toBytes());
		//System.out.println("Router.java(): forwardIPPacket(): Sending IP packet to " + IPv4.fromIPv4Address(destAddr));
		this.sendPacket(etherPacket, outIface);

		//
	}
}
