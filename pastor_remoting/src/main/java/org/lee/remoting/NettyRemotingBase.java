package org.lee.remoting;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import org.lee.common.exception.remoting.RemotingSendRequestException;
import org.lee.common.exception.remoting.RemotingTimeoutException;
import org.lee.common.protocal.PastorProtocol;
import org.lee.common.util.Pair;
import org.lee.remoting.model.NettyRequestProcessor;
import org.lee.remoting.model.RemotingResponse;
import org.lee.remoting.model.RemotingTransporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;

/**
 * @author liqiang
 * @description netty C/S 端的客户端提取，子类去完全netty的一些创建的事情，该抽象类则取完成使用子类创建好的channel去与远程端交互
 * @time 2017年12月21日
 * @modifytime
 */
public abstract class NettyRemotingBase {

    private static final Logger logger = LoggerFactory.getLogger(NettyRemotingBase.class);

    private final ConcurrentMap<Long, RemotingResponse> responseMap = new ConcurrentHashMap<Long, RemotingResponse>();

    //默认的Netty Request处理器Pair
    protected Pair<NettyRequestProcessor, ExecutorService> defaultRequestProcessor;

    protected final Map<Byte, Pair<NettyRequestProcessor, ExecutorService>> processorTable
            = new HashMap<Byte, Pair<NettyRequestProcessor, ExecutorService>>();


    /**
     * 设置钩子函数，增加处理前后操作
     *
     * @return
     */
    public abstract RpcHook getRpcHook();


    /**
     * 向远端发送请求，并等待接收返回
     *
     * @param channel
     * @param request
     * @param timeoutMills
     * @return
     * @throws RemotingTimeoutException
     * @throws RemotingSendRequestException
     * @throws InterruptedException
     */
    protected RemotingTransporter invokeSync(final Channel channel, final RemotingTransporter request, final long timeoutMills) throws RemotingTimeoutException, RemotingSendRequestException, InterruptedException {
        try {
            final RemotingResponse response = new RemotingResponse(request.getOpaque(), timeoutMills, null);
            responseMap.put(request.getOpaque(), response);
            channel.writeAndFlush(request).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (future.isSuccess()) {
                        response.setSendRequestOk(true);
                        return;
                    }
                    //发送异常
                    response.setSendRequestOk(false);
                    response.setCause(future.cause());
                    responseMap.remove(request.getOpaque());
                    response.putResponse(null);
                }
            });
            RemotingTransporter remotingTransporter = response.waitResponse();
            if (remotingTransporter == null) {
                if (response.isSendRequestOk()) {
                    //发送正常，但是没有返回response，说明远端调用超时
                    throw new RemotingTimeoutException(ConnectionUtils.parseChannelRemoteAddr(channel),
                            timeoutMills, response.getCause());
                } else {
                    //发送异常
                    throw new RemotingSendRequestException(ConnectionUtils.parseChannelRemoteAddr(channel),
                            response.getCause());
                }
            }
            return remotingTransporter;
        } finally {
            responseMap.remove(request.getOpaque());
        }


    }

    //channelRead调用方法
    protected void processMessageReceived(ChannelHandlerContext ctx, RemotingTransporter msg) {
        final RemotingTransporter transporter = msg;
        byte tranportType = transporter.getTransporterType();

        switch (tranportType) {
            //服务端收到客户端提交协议
            case PastorProtocol.REQUEST_REMOTING:
                processRequestTransport(ctx, msg);
                break;
            //客户端收到服务端返回的协议
            case PastorProtocol.RESPONSE_REMOTING:
                processResponseTransport(msg);
                break;
            default:
                break;
        }
    }

    /**
     * 处理服务端返回的协议
     *
     * @param responseTransporter
     */
    protected void processResponseTransport(RemotingTransporter responseTransporter) {
        long requestId = responseTransporter.getOpaque();
        final RemotingResponse remotingResponse = responseMap.get(requestId);
        if (remotingResponse != null) {
            remotingResponse.setTransporter(responseTransporter);
            remotingResponse.putResponse(responseTransporter);
            responseMap.remove(requestId);
        } else {
            logger.warn("received response but matched Id is removed from respnseMap");
            logger.warn(responseTransporter.toString());
        }
    }

    /**
     * 服务端处理request
     * @param ctx
     * @param requestTransporter
     */
    protected void processRequestTransport(final ChannelHandlerContext ctx, final RemotingTransporter requestTransporter) {
        final Pair<NettyRequestProcessor, ExecutorService> pair =
                processorTable.get(requestTransporter.getCode()) == null ? defaultRequestProcessor : processorTable.get(requestTransporter.getCode());
        if (pair != null) {
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    try {
                        RpcHook rpcHook = NettyRemotingBase.this.getRpcHook();
                        if (rpcHook != null) {
                            rpcHook.doBefore(ConnectionUtils.parseChannelRemoteAddr(ctx.channel()), requestTransporter);
                        }
                        RemotingTransporter response = pair.getKey().processRequest(ctx, requestTransporter);
                        if (rpcHook != null) {
                            rpcHook.doAfter(ConnectionUtils.parseChannelRemoteAddr(ctx.channel()), requestTransporter, response);
                        }
                        if (response != null) {
                            ctx.writeAndFlush(response).addListener(new ChannelFutureListener() {
                                @Override
                                public void operationComplete(ChannelFuture future) throws Exception {
                                    if (!future.isSuccess()) {
                                        if (!future.isSuccess()) {
                                            logger.error("fail send response ,exception is [{}]", future.cause().getMessage());
                                        }
                                    }
                                }
                            });
                        }
                    } catch (Exception e) {
                        logger.error("processor occur exception", e);
                        final RemotingTransporter remotingTransporter = RemotingTransporter.newInstance(requestTransporter.getOpaque()
                                , PastorProtocol.RESPONSE_REMOTING, PastorProtocol.HANDLER_ERROR, null);
                        ctx.writeAndFlush(remotingTransporter);
                    }
                }
            };
            try {
                pair.getValue().submit(runnable);
            } catch (Exception e) {
                logger.error("server is busy", e);
                final RemotingTransporter remotingTransporter = RemotingTransporter.newInstance(requestTransporter.getOpaque()
                        , PastorProtocol.RESPONSE_REMOTING, PastorProtocol.HANDLER_BUSY, null);
                ctx.writeAndFlush(remotingTransporter);
            }
        }
    }
}
