package org.lee.remoting.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.lee.common.exception.remoting.RemotingException;
import org.lee.common.exception.remoting.RemotingSendRequestException;
import org.lee.common.exception.remoting.RemotingTimeoutException;
import org.lee.remoting.NettyRemotingBase;
import org.lee.remoting.RpcHook;
import org.lee.remoting.model.NettyInactiveProcessor;
import org.lee.remoting.model.NettyRequestProcessor;
import org.lee.remoting.model.RemotingTransporter;

import java.util.concurrent.ExecutorService;

/**
 * @author liqiang
 * @description
 * @time 2017年12月24日
 * @modifytime
 */
public class NettyRemoteClient extends NettyRemotingBase implements RemoteClient {

    private Bootstrap bootstrap;
    private EventLoopGroup workerGroup;
    private int workerThread;

    private DefaultEventExecutorGroup defaultEventExecutorGroup;

    private final NettyClientConfig config;
    private volatile int writeBufferHighWaterMark = -1;
    private volatile int writeBufferLowWaterMark = -1;



    public NettyRemoteClient(NettyClientConfig clientConfig) {
        this.config = clientConfig;
        if (config != null) {
            workerThread = config.getClientWorkerThreads();
            writeBufferHighWaterMark = config.getWriteBufferHighWaterMark();
            writeBufferLowWaterMark = config.getWriteBufferLowWaterMark();
        }
        init();
    }

    @Override
    public void init() {
        bootstrap = new Bootstrap();
        workerGroup = new NioEventLoopGroup(workerThread, new DefaultThreadFactory("client worker group"));
        bootstrap.group(workerGroup);


    }

    @Override
    public void start() {

    }

    @Override
    public void shutDown() {

    }

    @Override
    public void registerRpcHook(RpcHook rpcHook) {

    }


    @Override
    public RpcHook getRpcHook() {
        return null;
    }

    @Override
    public RemotingTransporter invokeSync(String address, RemotingTransporter request, long timeMillis) throws RemotingTimeoutException, RemotingSendRequestException, RemotingException, InterruptedException {
        return null;
    }

    @Override
    public void registerProcessor(byte requestCode, NettyRequestProcessor processor, ExecutorService executorService) {

    }

    @Override
    public void registerInvalidProcessor(NettyInactiveProcessor processor, ExecutorService executorService) {

    }

    @Override
    public boolean isChannelWritable(String address) {
        return false;
    }

    @Override
    public void setReconnect(boolean isReconnect) {

    }

}
