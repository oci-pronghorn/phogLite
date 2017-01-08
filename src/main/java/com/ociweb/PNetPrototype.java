package com.ociweb;

import static org.junit.Assert.fail;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ociweb.pronghorn.network.ModuleConfig;
import com.ociweb.pronghorn.network.NetGraphBuilder;
import com.ociweb.pronghorn.network.ServerCoordinator;
import com.ociweb.pronghorn.stage.monitor.MonitorConsoleStage;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;
import com.ociweb.pronghorn.stage.scheduling.StageScheduler;
import com.ociweb.pronghorn.stage.scheduling.ThreadPerStageScheduler;

public class PNetPrototype {

	private final static Logger logger = LoggerFactory.getLogger(PNetPrototype.class);
	
    public static String getOptArg(String longName, String shortName, String[] args, String defaultValue) {
        
        String prev = null;
        for (String token : args) {
            if (longName.equals(prev) || shortName.equals(prev)) {
                if (token == null || token.trim().length() == 0 || token.startsWith("-")) {
                    return defaultValue;
                }
                return reportChoice(longName, shortName, token.trim());
            }
            prev = token;
        }
        return reportChoice(longName, shortName, defaultValue);
    }
    
    public static String reportChoice(final String longName, final String shortName, final String value) {
        System.out.print(longName);
        System.out.print(" ");
        System.out.print(shortName);
        System.out.print(" ");
        System.out.println(value);
        return value;
    }

	public static String buildStaticFileFolderPath(String testFile) {
		URL dir = ClassLoader.getSystemResource(testFile);
		String root = "";	//file:/home/nate/Pronghorn/target/test-classes/OCILogo.png
						
		try {
		
			String uri = dir.toURI().toString();
			uri = uri.replace("jar:","");
			uri = uri.replace("file:","");
			
			root = uri.substring(0, uri.lastIndexOf('/'));
			
		} catch (URISyntaxException e) {						
			e.printStackTrace();
			fail();
		}
		return root;
	}

	public static ServerCoordinator serverSetup(boolean isTLS, String bindHost, int port, GraphManager gm, boolean large, ModuleConfig config) {
		
		int cores = Runtime.getRuntime().availableProcessors();
		if (cores>64) {
			cores = cores >> 2;
		}
		if (cores<4) {
			cores = 4;
		}
		
		PhogLite.logger.info("cores in use {}", cores);
		
		
		final int maxPartialResponsesServer;
		final int maxConnectionBitsOnServer; 	
		final int serverRequestUnwrapUnits;
		final int serverResponseWrapUnits;
		final int serverPipesPerOutputEngine;
		final int serverSocketWriters;
		final int groups;
		final int serverInputBlobs; 
		final int serverBlobToEncrypt;
		final int serverBlobToWrite;
		final int serverInputMsg;
		
		if (large) {
			maxPartialResponsesServer     = 1<<8;    // (big memory consumption!!) concurrent partial messages 
			maxConnectionBitsOnServer 	  = 22;       //4M open connections on server	    	
			groups                        = 1;
			
			serverInputMsg                = 80;
			serverInputBlobs              = 1<<16;
			
			serverBlobToEncrypt           = 1<<12;
			serverBlobToWrite             = 1<<12;

		} else {	//small
			maxPartialResponsesServer     = 1<<5;     //32    //9  512B concurrent partial messages 
			maxConnectionBitsOnServer     = 12;       //4K  open connections on server	    	
			groups                        = 1;	
		
			serverInputMsg                = 80;
			serverInputBlobs              = 1<<16;
			
			serverBlobToEncrypt           = 1<<12;
			serverBlobToWrite             = 1<<12;

		}
		
		serverRequestUnwrapUnits      = isTLS?cores/4:4;  //server unwrap units - need more for handshaks and more for posts
		serverResponseWrapUnits 	  = isTLS?cores:8;    //server wrap units
		serverPipesPerOutputEngine 	  = isTLS?1:4;//multiplier against server wrap units for max simultanus user responses.
		serverSocketWriters           = 4;
		
		 int routerCount = 4;	
		 //This must be large enough for both partials and new handshakes.

		ServerCoordinator serverCoord = new ServerCoordinator(groups, bindHost, port, maxConnectionBitsOnServer, maxPartialResponsesServer, routerCount);	
		
		
		
		int serverMsgToEncrypt = 256;
		int serverMsgToWrite   = 1024;
		
		NetGraphBuilder.buildHTTPServerGraph(isTLS, gm, groups, maxPartialResponsesServer, config, serverCoord,
				                             serverRequestUnwrapUnits, serverResponseWrapUnits, serverPipesPerOutputEngine, serverSocketWriters, 
				                            serverInputMsg, serverInputBlobs, serverMsgToEncrypt, serverBlobToEncrypt, serverMsgToWrite, serverBlobToWrite, routerCount );
		return serverCoord;
	}

	public static void startupServer(boolean large, ModuleConfig config, String bindHost, int port) {
		boolean isTLS = true;
		
		startupServer(large, config, bindHost, port, isTLS);
	}

	public static void startupServer(boolean large, ModuleConfig config, String bindHost, int port, boolean isTLS) {
				
		
		if (!isTLS) {
			logger.warn("TLS has been progamatically switched off");
		}
		
		GraphManager gm = new GraphManager();
		GraphManager.addDefaultNota(gm, GraphManager.SCHEDULE_RATE, 100_000);
		///////////////
	    //BUILD THE SERVER
	    ////////////////		
		final ServerCoordinator serverCoord = serverSetup(isTLS, bindHost, port, gm, large, config);
			
		
		
		////////////////
		///FOR DEBUG GENERATE A PICTURE OF THE SERVER
		////////////////
		//do not call the graph is way to big to draw.
		if (!large) {
			//GraphManager.exportGraphDotFile(gm, "PhogLite");			
			final MonitorConsoleStage attach =  MonitorConsoleStage.attach(gm);
			attach.setRecorderOn(false); 
			serverCoord.setFirstUsage(()->{
				System.err.println("server is used now. starting to record behavior.");
				attach.setRecorderOn(true);});
			
			final long limit = System.currentTimeMillis()+30_000;//30 sec
			serverCoord.setLastUsage(()->{
				if ((System.currentTimeMillis()>limit) && attach.isRecorderOn()) {
					System.err.println("stopped recording now");
					attach.setRecorderOn(false);
				}
			
			});
		} else {
			serverCoord.setFirstUsage(()->{
				System.err.println("server is used now, no recording...");});
		}
		
		////////////////
		//CREATE A SCHEDULER TO RUN THE SERVER
		////////////////
		StageScheduler scheduler = new ThreadPerStageScheduler(gm);
				
		//////////////////
		//UPON CTL-C SHUTDOWN OF SERVER DO A CLEAN SHUTDOWN
		//////////////////
	    Runtime.getRuntime().addShutdownHook(new Thread() {
	        public void run() {
  	        		//soft shutdown
	        	    serverCoord.shutdown();
	                try {
						Thread.sleep(500);
					} catch (InterruptedException e) {
						
					}
	                //harder shutdown
  	        		scheduler.shutdown();
  	        		//hard shutdown
	                scheduler.awaitTermination(1, TimeUnit.SECONDS);
	
	        }
	    });
		
	    ///////////////// 
	    //START RUNNING THE SERVER
	    /////////////////        
	    scheduler.startup();
	}

	static List<InetAddress> homeAddresses(boolean noIPV6) {
		List<InetAddress> addrList = new ArrayList<InetAddress>();
		try {
			Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();			
			while (networkInterfaces.hasMoreElements()) {
				NetworkInterface ifc = networkInterfaces.nextElement();
				try {
					if(ifc.isUp()) {						
						Enumeration<InetAddress> addrs = ifc.getInetAddresses();
						while (addrs.hasMoreElements()) {
							InetAddress addr = addrs.nextElement();						
							byte[] addrBytes = addr.getAddress();
							if (noIPV6) {								
								if (16 == addrBytes.length) {
									continue;
								}							
							}							
							if (addrBytes.length==4) {
								if (addrBytes[0]==127 && addrBytes[1]==0 && addrBytes[2]==0 && addrBytes[3]==1) {
									continue;
								}								
							}
							addrList.add(addr);
						}						
					}
				} catch (SocketException e) {
					//ignore
				}
			}			
		} catch (SocketException e1) {
			//ignore.
		}
		
		Comparator<? super InetAddress> comp = new Comparator<InetAddress>() {
			@Override
			public int compare(InetAddress o1, InetAddress o2) {
				return Integer.compare(o2.getAddress()[0], o2.getAddress()[0]);
			} //decending addresses			
		};
		addrList.sort(comp);
		return addrList;
	}
}
