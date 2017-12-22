package org.lee.remoting.netty.watcher;

import io.netty.channel.ChannelHandler;

/**
 * 
 * @author BazingaLyn
 *
 * @time
 */
public interface ChannelHandlerHolder {

	ChannelHandler[] handlers();
	
}
