package org.lee.remoting.netty;

import io.netty.channel.Channel;
import org.lee.common.exception.remoting.RemotingSendRequestException;
import org.lee.common.exception.remoting.RemotingTimeoutException;
import org.lee.common.util.Pair;
import org.lee.remoting.model.NettyChannelInactiveProcessor;
import org.lee.remoting.model.NettyRequestProcessor;
import org.lee.remoting.model.RemotingTransporter;

import java.util.concurrent.ExecutorService;

/**
 * @author liqiang
 * @description
 * @time 2017年12月23日
 * @modifytime
 */
public interface RemoteServer extends BaseRemotingService {

    void registerDefaultProcessor(NettyRequestProcessor processor, ExecutorService executorService);

    void registerProcessorTable(NettyRequestProcessor processor, ExecutorService executorService, Byte requestCode);

    void registerInvalidChannelProcessor(NettyChannelInactiveProcessor processor, ExecutorService executorService);

    Pair<NettyRequestProcessor, ExecutorService> getProcessor(Byte requestCode);

    RemotingTransporter invokeSync(final Channel channel, final RemotingTransporter request, final long timeoutMills)
            throws RemotingTimeoutException, RemotingSendRequestException, InterruptedException;



}
