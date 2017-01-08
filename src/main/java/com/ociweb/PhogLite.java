package com.ociweb;

import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ociweb.pronghorn.network.ModuleConfig;
import com.ociweb.pronghorn.network.config.HTTPContentTypeDefaults;
import com.ociweb.pronghorn.network.config.HTTPHeaderKeyDefaults;
import com.ociweb.pronghorn.network.config.HTTPRevisionDefaults;
import com.ociweb.pronghorn.network.config.HTTPSpecification;
import com.ociweb.pronghorn.network.config.HTTPVerbDefaults;
import com.ociweb.pronghorn.network.module.FileReadModuleStage;
import com.ociweb.pronghorn.network.schema.HTTPRequestSchema;
import com.ociweb.pronghorn.network.schema.ServerResponseSchema;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeConfig;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;

public class PhogLite {
	//$ java -jar phogLite.jar --s ../src/main/resources/site


	static final Logger logger = LoggerFactory.getLogger(PhogLite.class);
	
	public static void main(String[] args) {
						
		String path = PNetPrototype.getOptArg("-site", "--s", args, PNetPrototype.buildStaticFileFolderPath("site/OCILogo.png"));	
		
		String isTLS = PNetPrototype.getOptArg("-tls", "--t", args, "True");	
		String isLarge = PNetPrototype.getOptArg("-large", "--l", args, "False");	

		String strPort = PNetPrototype.getOptArg("-port", "--p", args, "8443");
		int port = Integer.parseInt(strPort);
		
		String bindHost = PNetPrototype.getOptArg("-host", "--h", args, null);
		
	    boolean large = Boolean.parseBoolean(isLarge);
	    
	    if (null==bindHost) {
		    boolean noIPV6 = true;//TODO: we really do need to add ipv6 support.
		    List<InetAddress> addrList = PNetPrototype.homeAddresses(noIPV6);
			if (addrList.isEmpty()) {
				bindHost = "127.0.0.1";
			} else {
				bindHost = addrList.get(0).toString().replace("/", "");
			}		
	    }
		
		PNetPrototype.startupServer(large, PhogLite.moduleConfig(path), bindHost, port, Boolean.parseBoolean(isTLS));
        
	}

	
	
	///TODO: minimize memory for small
	///TODO: fix trieParser insert of substring starting.
	///TODO: shutdown not happening as desired.
    ///TOOD: need the memory consumed added on to to the graph.
	
    static ModuleConfig moduleConfig(String path) {
						   	
    	
		final File pathRoot = new File(path.replace("target/phogLite.jar!",""));
		
		int moduleCount=0;
		
		int fileServerIdx = -1;
		
		if (pathRoot.isDirectory()) {
			logger.info("reading files from folder {}",pathRoot);
			fileServerIdx = moduleCount;
			moduleCount++;
		}
		
		final int finalModuleCount = moduleCount;
		final int fileServerIndex = fileServerIdx;
		
		//using the basic no-fills API
		ModuleConfig config = new ModuleConfig() {
				    
			//TODO: configure this value for smaller installs shorter?    if this value is too short we have performance stopage at the supervisor.
		    final PipeConfig<ServerResponseSchema> fileServerOutgoingDataConfig = new PipeConfig<ServerResponseSchema>(ServerResponseSchema.instance, 2048, 1<<8);//from modules  to  supervisor
		
		    //must create here to ensure we have the same instance for both the module and outgoing pipes
		    Pipe<ServerResponseSchema>[] staticFileOutputs;
		    
			@Override
			public long addModule(int a, 
					GraphManager graphManager, Pipe<HTTPRequestSchema>[] inputs,
					HTTPSpecification<HTTPContentTypeDefaults, HTTPRevisionDefaults, HTTPVerbDefaults, HTTPHeaderKeyDefaults> spec) {
				
				//
				if (fileServerIndex == a) {
					
					//the file server is stateless therefore we can build 1 instance for every input pipe
					int instances = inputs.length;
					
					staticFileOutputs = new Pipe[instances];
					
					int i = instances;
					while (--i>=0) {
						staticFileOutputs[i] = new Pipe<ServerResponseSchema>(fileServerOutgoingDataConfig);
						FileReadModuleStage.newInstance(graphManager, inputs[i], staticFileOutputs[i], spec, pathRoot);					
					}
					
				}
				
				//add simple lambda based rest/post handler
				//TODO: just enough to avoid stage work.
				
				//return needed headers
				return 0;
			}
		
			@Override
			public CharSequence getPathRoute(int a) {
				if (fileServerIndex == a) {
					return "/%b";
				} else {
					return null;
				}
				
				
			}
			
		//TODO: add input pipes to be defined here as well??
			
			@Override
			public Pipe<ServerResponseSchema>[] outputPipes(int a) {
				if (fileServerIndex == a) {
					if (null==staticFileOutputs) {
						throw new UnsupportedOperationException("the addModule method must be called first");
					}
					return staticFileOutputs;
				} else {
					return null;
				}
			}
		
			@Override
			public int moduleCount() {
				return finalModuleCount;
			}        
		 	
		 };
		return config;
	}

	
}
