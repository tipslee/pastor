package org.lee.example.netty;

import io.netty.channel.ChannelHandlerContext;
import org.lee.common.protocal.PastorProtocol;
import org.lee.common.serialization.SerializerHolder;
import org.lee.remoting.model.NettyRequestProcessor;
import org.lee.remoting.model.RemotingTransporter;
import org.lee.remoting.netty.NettyRemoteServer;
import org.lee.remoting.netty.NettyServerConfig;

import java.util.concurrent.Executors;

public class NettyServerTest {
	
	public static final byte TEST = -1;
	
	public static void main(String[] args) {
		
		NettyServerConfig config = new NettyServerConfig();
		config.setListenPort(18001);
		NettyRemoteServer server = new NettyRemoteServer(config);
		server.registerProcessorTable(new NettyRequestProcessor() {
			
			@Override
			public RemotingTransporter processRequest(ChannelHandlerContext ctx, RemotingTransporter transporter) throws Exception {
				transporter.setCustomHeader(SerializerHolder.serializerImpl().readObject(transporter.getBytes(), TestCommonCustomBody.class));
				System.out.println(transporter);
				transporter.setTransporterType(PastorProtocol.RESPONSE_REMOTING);
				return transporter;
			}
		}, Executors.newCachedThreadPool(), TEST);
		server.start();
	}

}
