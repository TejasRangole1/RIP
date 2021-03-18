package edu.wisc.cs.sdn.vnet.rt;



import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
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
	private RIPv2Sender ripSender;

	/** Thread to delete rip entries after time has expired*/
	private RIPv2Updater ripUpdater;

	/** Thread to process RIP responses */

	/**
	 * Creates a router for a specific host.
	 * @param host hostname for the router
	 */
	public Router(String host, DumpFile logfile)
	{
		super(host,logfile);
		this.routeTable = new RouteTable();
		this.arpCache = new ArpCache();
		this.ripTable = new ConcurrentHashMap<>();
		// initializing route table with entries of directly connected interfaces
		for(Iface i : this.getInterfaces().values()) {
			int subnet = i.getIpAddress() & i.getSubnetMask();
			ripTable.put(subnet, new RIPv2Entry(subnet, i.getSubnetMask(), 1, i.getIpAddress()));
		}
		RIPv2Sender sender = new RIPv2Sender(this, ripTable);
		
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
	/**
	 * Checks if ethernet frame is a RIPv2 packet, if it is then return the packet otherwise return null
	 * @param etherPacket
	 * @return
	 */
	public RIPv2 isRIPv2Packet(Ethernet etherPacket){
		IPv4 ipPacket = (IPv4) etherPacket.getPayload();
		if(ipPacket.getProtocol() == IPv4.PROTOCOL_UDP){
			if(ipPacket.getDestinationAddress() == IPv4.toIPv4Address("224.0.0.9")){
				UDP udpPacket = (UDP) ipPacket.getPayload();
				if(udpPacket.getDestinationPort() == (short) 520){
					RIPv2 ripPacket = (RIPv2) udpPacket.getPayload();
					return ripPacket;
				}
			}
		}
		return null;
	}
	/**
	 * Updates RIPv2 table by examining the response packet
	 * @param response
	 */
	public void handleResponse(RIPv2 response, int sourceSubnet, int sourceIP){
		for(RIPv2Entry entry : response.getEntries()){
			int dest = entry.getAddress();
			int cost = entry.getMetric();
			if(ripTable.containsKey(dest)){
				cost +=	ripTable.get(sourceSubnet).getMetric();
				// if the new cost to destination is less than the current cost, update the ripTable with the new route
				if(cost < ripTable.get(dest).getMetric()){
					entry.setMetric(cost);
					ripTable.put(dest, entry);
				}
			}
			// route does not exist in rip table, add it
			else{
				ripTable.put(dest, new RIPv2Entry(dest, entry.getSubnetMask(), cost + ripTable.get(sourceSubnet).getMetric(), sourceIP));
			}
		}
	}

	
	
	/**
	 * Handle an Ethernet packet received on a specific interface.
	 * @param etherPacket the Ethernet packet that was received
	 * @param inIface the interface on which the packet was received
	 */
	public void handlePacket(Ethernet etherPacket, Iface inIface)
	{
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
					RIPv2 response = new RIPv2();
					List<RIPv2Entry> entries = (List<RIPv2Entry>) ripTable.values();
					response.setEntries(entries);
					response.setCommand(RIPv2.COMMAND_RESPONSE);
					Ethernet ripFrame = encapsulateRIPv2Packet(response, inIface);
					sendPacket(ripFrame, inIface);
				}
				// update tables based on RIP response packet
				else{
					int sourceSubnet = ipPacket.getSourceAddress() & inIface.getSubnetMask();
					handleResponse(ripPacket, sourceSubnet, ipPacket.getSourceAddress());
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
		System.out.println("Handle IP packet");

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
		if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4)
		{ return; }
		System.out.println("Forward IP packet");

		// Get IP header
		IPv4 ipPacket = (IPv4)etherPacket.getPayload();
		int dstAddr = ipPacket.getDestinationAddress();

		// Find matching route table entry 
		// RouteEntry bestMatch = this.routeTable.lookup(dstAddr);
		// Find matching route in rip table entry
		RIPv2Entry bestMatch = ripTable.get(dstAddr);
		// If no entry matched, do nothing
		if (null == bestMatch)
		{ return; }

		// Make sure we don't sent a packet back out the interface it came in
		/*
		Iface outIface = bestMatch.getInterface();
		if (outIface == inIface)
		{ return; }
        */
		int nextHop = bestMatch.getNextHopAddress();
		Iface outIface = null;
		for(Map.Entry<String, Iface> entry : this.getInterfaces().entrySet()){
			outIface = (entry.getValue().getIpAddress() == nextHop) ? entry.getValue() : outIface;
			// Make sure we don't send a packet back out the interface it came in
			if(outIface == inIface)
			{return;}
		}
		// Set source MAC address in Ethernet header
		etherPacket.setSourceMACAddress(outIface.getMacAddress().toBytes());

		// If no gateway, then nextHop is IP destination
		/*
		int nextHop = bestMatch.getGatewayAddress();
		if (0 == nextHop)
		{ nextHop = dstAddr; }
		*/
		// Set destination MAC address in Ethernet header
		ArpEntry arpEntry = this.arpCache.lookup(nextHop);
		if (null == arpEntry)
		{ return; }
		etherPacket.setDestinationMACAddress(arpEntry.getMac().toBytes());

		this.sendPacket(etherPacket, outIface);

		//
	}
}
