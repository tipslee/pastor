package org.lee.client.provider;

import io.netty.channel.ChannelHandlerContext;
import org.lee.remoting.model.NettyRequestProcessor;
import org.lee.remoting.model.RemotingTransporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.lee.common.protocal.PastorProtocol.RPC_REQUEST;

/**
 * @author liqiang
 * @description
 * @time 2018年02月08日
 * @modifytime
 */
public class DefaultProviderRPCProcessor implements NettyRequestProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DefaultProviderRPCProcessor.class);

    private DefaultProvider defaultProvider;

    public DefaultProviderRPCProcessor(DefaultProvider defaultProvider) {
        this.defaultProvider = defaultProvider;
    }

    @Override
    public RemotingTransporter processRequest(ChannelHandlerContext ctx, RemotingTransporter request) throws Exception {
        switch (request.getCode()) {
            case RPC_REQUEST:
                //这边稍微特殊处理一下，可以返回null,我们不需要叫外层代码帮我们writeAndFlush 发出请求，因为我们持有channel，这样做rpc可以更加灵活一点
                this.defaultProvider.handleRPCRequest(request, ctx.channel());
                break;
        }
        return null;
    }
}
