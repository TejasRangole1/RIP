package edu.wisc.cs.sdn.vnet.rt;

import java.util.concurrent.locks.ReentrantLock;

import net.floodlightcontroller.packet.RIPv2;

public class RIPv2Handler implements Runnable {
   
	private RouteTable routeTable;
	
	private Thread thread;
	
	private ReentrantLock lock = new ReentrantLock();
	
	public RIPv2Handler(RouteTable rTable) {
		this.routeTable = rTable;
		thread = new Thread(this);
		thread.start();
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
