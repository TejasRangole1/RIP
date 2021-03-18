package edu.wisc.cs.sdn.vnet.rt;

import java.util.Map;

import net.floodlightcontroller.packet.RIPv2Entry;

/**
 * Deletes RIPv2 entries that have not received an update in 30 seconds
 */
public class RIPv2Updater implements Runnable {
    
    private Map<Integer, RIPv2Entry> ripTable;

    private Thread timeoutThread;

    private static final long TIMEOUT = 30;

    public RIPv2Updater(Map<Integer, RIPv2Entry> ripTable){
        this.ripTable = ripTable;
        timeoutThread = new Thread();
        timeoutThread.start();
    }

    @Override
    public void run() {
        // TODO Auto-generated method stub
        while(true) {
            for(Map.Entry<Integer, RIPv2Entry> entry : ripTable.entrySet()){
                if(System.currentTimeMillis() - entry.getValue().getLastUpdated() >= TIMEOUT) ripTable.remove(entry.getKey());
            }
        }
    }
}