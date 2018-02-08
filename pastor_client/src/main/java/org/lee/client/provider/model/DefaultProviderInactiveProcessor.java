package org.lee.client.provider.model;

import io.netty.channel.ChannelHandlerContext;
import org.lee.client.provider.DefaultProvider;
import org.lee.remoting.model.NettyChannelInactiveProcessor;

/**
 * 
 * @author BazingaLyn
 * @description provider的netty inactive触发的事件
 * @time
 * @modifytime
 */
public class DefaultProviderInactiveProcessor implements NettyChannelInactiveProcessor {

	private DefaultProvider defaultProvider;

	public DefaultProviderInactiveProcessor(DefaultProvider defaultProvider) {
		this.defaultProvider = defaultProvider;
	}

	@Override
	public void processInactiveChannel(ChannelHandlerContext ctx) {
		defaultProvider.setProviderStateIsHealthy(false);
	}

}
