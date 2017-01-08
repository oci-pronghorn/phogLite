package com.ociweb;

import com.ociweb.pronghorn.network.module.SimpleRestLogic;
import com.ociweb.pronghorn.network.schema.HTTPRequestSchema;
import com.ociweb.pronghorn.network.schema.ServerResponseSchema;
import com.ociweb.pronghorn.pipe.DataInputBlobReader;
import com.ociweb.pronghorn.pipe.DataOutputBlobWriter;
import com.ociweb.pronghorn.util.Appendables;

public class MathModule implements SimpleRestLogic {

	//example response UTF-8 encoded
	//{"x":9,"y":17,"groovySum":26}
	byte[] part1 = "{\"x\":".getBytes();
	byte[] part2 =          ",\"y\":".getBytes();
	byte[] part3 =                    ",\"groovySum\":".getBytes();
	byte[] part4 =                                      "}".getBytes();
	
	
	
	@Override
	public void process(DataInputBlobReader<HTTPRequestSchema> inputStream,
			DataOutputBlobWriter<ServerResponseSchema> outputStream) {
		// TODO Auto-generated method stub
		
	}
	
	/*	
	
	public String pattern() {
		return "groovyadd/%i%./%i%."; //supports 2 decimal values
	}
	//TODO: add pattern here for extraction.
	
	@Override
	public void process(DataInputBlobReader<HTTPRequestSchema> inputStream,
						DataOutputBlobWriter<ServerResponseSchema> outputStream) {
		
		
		
		
//		//for all types??
//		int aSign   = inputStream.readInt();
//		int aHigh   = inputStream.readInt();
//		int aLow    = inputStream.readInt();
//		int aMeta   = inputStream.readInt();
//		int aLength = aMeta&0xFFFF;
//		int aBase   = aMeta>>16;
//		
//		
//		long b = inputStream.readLong();
//		
//		long sum = a+b;
//		
//		outputStream.write(part1);
//		Appendables.appendValue(outputStream, a);
//		outputStream.write(part2);
//		Appendables.appendValue(outputStream, b);
//		outputStream.write(part3);
//		Appendables.appendValue(outputStream, sum);
//		outputStream.write(part4);
				
		//TODO: how do we tell the downstream what type this is? how is it done by file lookup?
		
	}
*/
}
