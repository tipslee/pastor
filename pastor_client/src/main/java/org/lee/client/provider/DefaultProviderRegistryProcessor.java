package org.lee.client.provider;

import io.netty.channel.ChannelHandlerContext;
import org.lee.remoting.model.NettyRequestProcessor;
import org.lee.remoting.model.RemotingTransporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.lee.common.protocal.PastorProtocol.AUTO_DEGRADE_SERVICE;
import static org.lee.common.protocal.PastorProtocol.DEGRADE_SERVICE;

/**
 * @author liqiang
 * @description
 * @time 2018年02月08日
 * @modifytime
 */
public class DefaultProviderRegistryProcessor implements NettyRequestProcessor {
    private static final Logger logger = LoggerFactory.getLogger(DefaultProviderRegistryProcessor.class);

    private DefaultProvider defaultProvider;

    public DefaultProviderRegistryProcessor(DefaultProvider defaultProvider) {
        this.defaultProvider = defaultProvider;
    }

    @Override
    public RemotingTransporter processRequest(ChannelHandlerContext ctx, RemotingTransporter request) throws Exception {
        switch (request.getCode()) {
            case DEGRADE_SERVICE:
                return this.defaultProvider.handlerDegradeServiceRequest(request, ctx.channel(), DEGRADE_SERVICE);
            case AUTO_DEGRADE_SERVICE:
                return this.defaultProvider.handlerDegradeServiceRequest(request, ctx.channel(), AUTO_DEGRADE_SERVICE);
        }
        return null;
    }
}
