package org.lee.remoting.netty.idle;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.lee.common.exception.remoting.RemotingNoSighException;

/**
 * @author liqiang
 * @description
 * @time 2017年12月23日
 * @modifytime
 */
@ChannelHandler.Sharable
public class AcceptorIdleHandler extends ChannelInboundHandlerAdapter {

    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.READER_IDLE) {
                throw new RemotingNoSighException("no sign");
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }
}
