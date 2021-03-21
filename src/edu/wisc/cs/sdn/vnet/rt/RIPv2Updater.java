package edu.wisc.cs.sdn.vnet.rt;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.RIPv2Entry;

/**
 * Deletes RIPv2 entries that have not received an update in 30 seconds
 */
public class RIPv2Updater implements Runnable {
    
    private Map<Integer, RIPv2Entry> ripTable;

    // private Thread timeoutThread;

    private static final long TIMEOUT = 30000;

    private ReentrantLock lock;

    public RIPv2Updater(Map<Integer, RIPv2Entry> ripTable, ReentrantLock lock){
        this.ripTable = ripTable;
        System.out.println("RIPv2Updater Created updater");

        //this.timeoutThread = new Thread();
        this.lock = lock;
        //this.timeoutThread.start();
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

    public void printRIPTable(){
		for(Map.Entry<Integer, RIPv2Entry> entry : ripTable.entrySet()){
			System.out.println("dest subnet: " + IPv4.fromIPv4Address(entry.getKey()) + " cost= " + entry.getValue().getMetric() + " next hop IP: " + IPv4.fromIPv4Address(entry.getValue().getNextHopAddress()) + 
			" is host= " + entry.getValue().isHost());
		}
	}

  
    @Override
    public void run() {
        // TODO Auto-generated method stub
        while(true) {
            lock.lock();
            try {
                //System.out.println("RIPv2Updater.java: run() Successfully acquired lock");
                Set<Integer> expiredSubnets = new HashSet<>();
                //find all entries which are expired and delete them
                for (Map.Entry<Integer, RIPv2Entry> entry : ripTable.entrySet()) {
                    if (System.currentTimeMillis() - entry.getValue().getLastUpdated() >= TIMEOUT && entry.getValue().isHost() == false){
                        int deletedSubnet = entry.getKey();
                        expiredSubnets.add(deletedSubnet);
                        ripTable.remove(deletedSubnet);
                        System.out.println("Router.java: handleResponse(): DELETING ENTRY: " + IPv4.fromIPv4Address(deletedSubnet) + " Outputting RIPv2 Table");
						System.out.println("------------------------------------------------------");
						printRIPTable();
						System.out.println("-------------------------------------------------------");
                    }
                }
                /*
                // deleting all entries whose route to next hop was deleted in the previous step
                for(Map.Entry<Integer, RIPv2Entry> entry : ripTable.entrySet()){
                    int nextHop = entry.getValue().getNextHopAddress();
                    int subnetNumber = lookupSubnet(nextHop);
                    if(expiredSubnets.contains(subnetNumber)){
                        ripTable.remove(subnetNumber);
                        System.out.println("RIPv2Updater.java: run() removed dest: " + IPv4.fromIPv4Address(subnetNumber) + " as a result of the previous removal");
                    }
                }
                */
            } catch (Exception e){
                e.printStackTrace();
            } finally {
                lock.unlock();
            }
        }
    }
}