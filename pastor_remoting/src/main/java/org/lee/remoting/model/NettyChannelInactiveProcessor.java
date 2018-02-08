package org.lee.remoting.model;

import io.netty.channel.ChannelHandlerContext;

/**
 * @author liqiang
 * @description 针对inactive处理器
 * @time 2017年12月22日
 * @modifytime
 */
public interface NettyChannelInactiveProcessor {

    public void processInactiveChannel(ChannelHandlerContext ctx) throws Exception;
}
