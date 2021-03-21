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
  
    @Override
    public void run() {
        // TODO Auto-generated method stub
        while(true) {
            lock.lock();
            try {
                System.out.println("RIPv2Updater.java: run() Successfully acquired lock");
                Set<Integer> expiredNextHops = new HashSet<>();
                //find all entries which are expired and delete them
                for (Map.Entry<Integer, RIPv2Entry> entry : ripTable.entrySet()) {
                    if (System.currentTimeMillis() - entry.getValue().getLastUpdated() >= TIMEOUT && entry.getValue().isHost() == false){
                        System.out.println("RIPv2Updater.java: run(): removed " + "dest: " + IPv4.fromIPv4Address(entry.getKey()));
                        expiredNextHops.add(entry.getValue().getNextHopAddress());
                        ripTable.remove(entry.getKey());
                    }
                }
                // deleting all entries whose route to next hop was deleted in the previous step
                for(Map.Entry<Integer, RIPv2Entry> entry : ripTable.entrySet()){
                    if(expiredNextHops.contains(entry.getValue().getNextHopAddress())) ripTable.remove(entry.getKey());
                }
            } catch (Exception e){
                e.printStackTrace();
            } finally {
                lock.unlock();
            }
        }
    }
}