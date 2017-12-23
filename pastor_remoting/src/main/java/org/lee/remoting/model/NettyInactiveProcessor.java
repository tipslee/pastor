package org.lee.remoting.model;

import io.netty.channel.ChannelHandlerContext;

/**
 * @author liqiang
 * @description 针对request处理器
 * @time 2017年12月22日
 * @modifytime
 */
public interface NettyRequestProcessor {

    public RemotingTransporter processRequest(ChannelHandlerContext ctx, RemotingTransporter transporter) throws Exception;
}
