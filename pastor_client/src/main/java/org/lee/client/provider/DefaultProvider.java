package org.lee.client.provider;

import io.netty.channel.Channel;
import org.lee.common.exception.remoting.RemotingException;
import org.lee.common.transport.body.PublishServiceCustomBody;
import org.lee.common.util.NamedThreadFactory;
import org.lee.remoting.model.RemotingTransporter;
import org.lee.remoting.netty.NettyClientConfig;
import org.lee.remoting.netty.NettyRemoteClient;
import org.lee.remoting.netty.NettyRemoteServer;
import org.lee.remoting.netty.NettyServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.*;

/**
 * @author liqiang
 * @description 服务提供者默认实现
 * @time 2018年02月05日
 * @modifytime
 */
public class DefaultProvider implements Provider {
    private static final Logger logger = LoggerFactory.getLogger(DefaultProvider.class);

    private NettyClientConfig clientConfig; //作为客户端，连接注册中心配置
    private NettyServerConfig serverConfig; //作为服务提供者，服务端配置
    private NettyRemoteClient nettyRemotingClient; //连接监控服务器的Client
    private NettyRemoteServer nettyRemotingServer; //等待被Consumer连接
    private ProviderRegistryController providerController;// provider端向注册中心连接的业务逻辑的控制器
    private ProviderRPCController providerRPCController;  // consumer端远程调用的核心控制器
    private ExecutorService remotingExecutor; //RPC处理线程池
    private Channel monitorChannel; //监控服务器channel

    /********* 要发布的服务的信息 ***********/
    private List<RemotingTransporter> publishRemotingTransporters;
    /************ 全局发布的信息 ************/
    private ConcurrentMap<String, PublishServiceCustomBody> globalPublishService = new ConcurrentHashMap<String, PublishServiceCustomBody>();
    /***** 注册中心的地址 ******/
    private String registryAddress;
    /******* 服务暴露给consumer的地址 ********/
    private int exposePort;
    /************* 监控中心的monitor的地址 *****************/
    private String monitorAddress;
    /*********** 要提供的服务 ***************/
    private Object[] obj;

    // 当前provider端状态是否健康，也就是说如果注册宕机后，该provider端的实例信息是失效，这是需要重新发送注册信息,因为默认状态下start就是发送，只有channel
    // inactive的时候说明短线了，需要重新发布信息
    private boolean ProviderStateIsHealthy = true;

    // 定时任务执行器
    private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("provider-timer"));


    @Override
    public void start() throws InterruptedException, RemotingException {
        logger.info("---------Provider Start--------------");
        //编织服务
        this.publishRemotingTransporters = providerController.getLocalServerWrapperManager().wrapperRegisterInfo(this.exposePort, this.obj);



    }

    @Override
    public void publishedAndStartProvider() throws InterruptedException, RemotingException {

    }

    @Override
    public Provider serviceListenPort(int exposePort) {
        this.exposePort = exposePort;
        return this;
    }

    @Override
    public Provider registryAddress(String address) {
        this.registryAddress = address;
        return this;
    }

    @Override
    public Provider monitorAddress(String address) {
        this.monitorAddress = address;
        return this;
    }

    @Override
    public Provider publishService(Object... service) {
        this.obj = service;
        return this;
    }

    @Override
    public void handleRPCRequest(RemotingTransporter request, Channel channel) {
        providerRPCController.handleRPCRequest(request, channel);
    }
}
