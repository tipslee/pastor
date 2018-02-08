package org.lee.remoting.netty;

import org.lee.common.exception.remoting.RemotingException;
import org.lee.common.exception.remoting.RemotingSendRequestException;
import org.lee.common.exception.remoting.RemotingTimeoutException;
import org.lee.remoting.model.NettyChannelInactiveProcessor;
import org.lee.remoting.model.NettyRequestProcessor;
import org.lee.remoting.model.RemotingTransporter;

import java.util.concurrent.ExecutorService;

/**
 * @author liqiang
 * @description
 * @time 2017年12月24日
 * @modifytime
 */
public interface RemoteClient extends BaseRemotingService {

    public RemotingTransporter invokeSync(final String address, RemotingTransporter request, long timeMillis)
            throws RemotingTimeoutException, RemotingSendRequestException, RemotingException, InterruptedException;

    public void registerProcessor(final byte requestCode, NettyRequestProcessor processor, ExecutorService executorService);

    public void registerInvalidProcessor(NettyChannelInactiveProcessor processor, ExecutorService executorService);

    public boolean isChannelWritable(String address);

    public void setReconnect(boolean isReconnect);



}
