package org.lee.client.provider;

import io.netty.channel.Channel;
import org.lee.client.provider.model.DefaultProviderInactiveProcessor;
import org.lee.client.provider.model.ServiceWrapper;
import org.lee.common.exception.remoting.RemotingException;
import org.lee.common.protocal.PastorProtocol;
import org.lee.common.serialization.SerializerHolder;
import org.lee.common.transport.body.AckCustomBody;
import org.lee.common.transport.body.ManagerServiceCustomBody;
import org.lee.common.transport.body.PublishServiceCustomBody;
import org.lee.common.util.NamedThreadFactory;
import org.lee.common.util.Pair;
import org.lee.remoting.model.RemotingTransporter;
import org.lee.remoting.netty.NettyClientConfig;
import org.lee.remoting.netty.NettyRemoteClient;
import org.lee.remoting.netty.NettyRemoteServer;
import org.lee.remoting.netty.NettyServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.*;

import static org.lee.common.protocal.PastorProtocol.AUTO_DEGRADE_SERVICE;
import static org.lee.common.protocal.PastorProtocol.DEGRADE_SERVICE;

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
    private NettyRemoteServer nettyRemotingVipServer;
    private ProviderRegistryController providerController;// provider端向注册中心连接的业务逻辑的控制器
    private ProviderRPCController providerRPCController;  // consumer端远程调用的核心控制器
    private ExecutorService remotingExecutor; //RPC处理线程池
    private ExecutorService remotingVipExecutor; //VIP处理线程池
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

    public DefaultProvider() {
        this.clientConfig = new NettyClientConfig();
        this.serverConfig = new NettyServerConfig();
        providerController = new ProviderRegistryController(this);
        providerRPCController = new ProviderRPCController(this);
        initialize();
    }


    public DefaultProvider(NettyClientConfig clientConfig, NettyServerConfig serverConfig) {
        this.clientConfig = clientConfig;
        this.serverConfig = serverConfig;
        providerController = new ProviderRegistryController(this);
        providerRPCController = new ProviderRPCController(this);
        initialize();
    }

    public void initialize() {
        this.nettyRemotingClient = new NettyRemoteClient(this.clientConfig);
        this.nettyRemotingServer = new NettyRemoteServer(this.serverConfig);
        this.nettyRemotingVipServer = new NettyRemoteServer(this.serverConfig);

        this.remotingExecutor = Executors.newFixedThreadPool(serverConfig.getServerWorkerThreads(), new NamedThreadFactory("providerExecutorThread_"));
        this.remotingVipExecutor = Executors.newFixedThreadPool(serverConfig.getServerWorkerThreads() / 2, new NamedThreadFactory("providerExecutorThread_"));

        this.registerProcessor();

        scheduledExecutorService.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                if (!ProviderStateIsHealthy) {
                    try {
                        DefaultProvider.this.publishedAndStartProvider();
                    } catch (Exception e) {
                        logger.warn("schedule publish failed [{}]", e.getMessage());
                    }
                }

            }
        },60, 60, TimeUnit.SECONDS);

        this.scheduledExecutorService.scheduleAtFixedRate(new Runnable() {

            @Override
            public void run() {
                try {
                    logger.info("ready send message");
                    DefaultProvider.this.providerController.getRegistryController().checkPublishFailMessage();
                } catch (InterruptedException | RemotingException e) {
                    logger.warn("schedule republish failed [{}]", e.getMessage());
                }
            }
        }, 1, 1, TimeUnit.MINUTES);

        //清理所有的服务的单位时间的失效过期的统计信息
        this.scheduledExecutorService.scheduleAtFixedRate(new Runnable() {

            @Override
            public void run() {
                logger.info("ready prepare send Report");
                DefaultProvider.this.providerController.getServiceFlowControllerManager().clearAllServiceNextMinuteCallCount();
            }
        }, 5, 45, TimeUnit.SECONDS);

        // 如果监控中心的地址不是null，则需要定时发送统计信息
        this.scheduledExecutorService.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                DefaultProvider.this.providerController.getProviderMonitorController().sendMetricsInfo();
            }
        }, 5, 60, TimeUnit.SECONDS);

        //每隔60s去校验与monitor端的channel是否健康，如果不健康，或者inactive的时候，则重新去链接
        this.scheduledExecutorService.scheduleAtFixedRate(new Runnable() {

            @Override
            public void run() {
                try {
                    DefaultProvider.this.providerController.getProviderMonitorController().checkMonitorChannel();
                } catch (InterruptedException e) {
                    logger.warn("schedule check monitor channel failed [{}]", e.getMessage());
                }
            }
        }, 30, 60, TimeUnit.SECONDS);

        //检查是否有服务需要自动降级
        this.scheduledExecutorService.scheduleAtFixedRate(new Runnable() {

            @Override
            public void run() {
                DefaultProvider.this.providerController.checkAutoDegrade();
            }
        }, 30, 60, TimeUnit.SECONDS);
    }

    /**
     * 注册处理器
     */
    private void registerProcessor() {
        DefaultProviderRegistryProcessor defaultProviderRegistryProcessor = new DefaultProviderRegistryProcessor(this);
        // provider端作为client端去连接registry注册中心的处理器
        this.nettyRemotingClient.registerProcessor(PastorProtocol.DEGRADE_SERVICE, defaultProviderRegistryProcessor, null);
        this.nettyRemotingClient.registerProcessor(PastorProtocol.AUTO_DEGRADE_SERVICE, defaultProviderRegistryProcessor, null);
        // provider端连接registry链接inactive的时候要进行的操作(设置registry的状态为不健康，告之registry重新发送服务注册信息)
        this.nettyRemotingClient.registerInvalidProcessor(new DefaultProviderInactiveProcessor(this), null);
        // provider端作为netty的server端去等待调用者连接的处理器，此处理器只处理RPC请求
        //TODO 处理prc请求Processor，未写
        this.nettyRemotingServer.registerDefaultProcessor(new DefaultProviderRPCProcessor(this), this.remotingExecutor);
        this.nettyRemotingVipServer.registerDefaultProcessor(new DefaultProviderRPCProcessor(this), this.remotingVipExecutor);



    }


    @Override
    public void start() throws InterruptedException, RemotingException {
        logger.info("---------Provider Start--------------");
        //编织服务
        this.publishRemotingTransporters = providerController.getLocalServerWrapperManager().wrapperRegisterInfo(this.exposePort, this.obj);

        logger.info("registry center address [{}] servicePort [{}] service [{}]", this.registryAddress, this.exposePort, this.publishRemotingTransporters);

        // 记录发布的信息的记录，方便其他地方做使用
        initGlobalService();

        nettyRemotingClient.start();

        try {
            // 发布服务
            this.publishedAndStartProvider();
            logger.info("######### provider start successfully..... ########");
        } catch (Exception e) {
            logger.error("publish service to registry failed [{}]",e.getMessage());
        }

        int _port = this.exposePort;

        if(_port != 0){

            this.serverConfig.setListenPort(exposePort);
            this.nettyRemotingServer.start();

            int vipPort = _port - 2;
            this.serverConfig.setListenPort(vipPort);
            this.nettyRemotingVipServer.start();
        }

        if (monitorAddress != null) {
            initMonitorChannel();
        }

    }

    public void initMonitorChannel() throws InterruptedException {
        monitorChannel = this.connectionToMonitor();
    }

    private Channel connectionToMonitor() throws InterruptedException {
        return this.nettyRemotingClient.createNewChannel(monitorAddress);
    }

    @Override
    public void publishedAndStartProvider() throws InterruptedException, RemotingException {
        logger.info("--------------publish service");

        this.providerController.getRegistryController().publishedAndStartProvider();

        ProviderStateIsHealthy =true;
    }

    private void initGlobalService() {
        List<RemotingTransporter> list = this.publishRemotingTransporters;
        if (null != list && !list.isEmpty()) {
            for (RemotingTransporter remotingTransporter : list) {
                PublishServiceCustomBody customBody = (PublishServiceCustomBody) remotingTransporter.getCustomHeader();
                String serviceName = customBody.getServiceProviderName();
                this.globalPublishService.put(serviceName, customBody);
            }
        }
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

    public RemotingTransporter handlerDegradeServiceRequest(RemotingTransporter request, Channel channel, byte degradeType) {
        AckCustomBody ackCustomBody = new AckCustomBody(request.getOpaque(), false);
        RemotingTransporter transporter = RemotingTransporter.createResponseTransporter(PastorProtocol.ACK, ackCustomBody, request.getOpaque());

        if (publishRemotingTransporters == null || publishRemotingTransporters.size() == 0) {
            return transporter;
        }
        // 请求体
        ManagerServiceCustomBody subcribeRequestCustomBody = SerializerHolder.serializerImpl().readObject(request.getBytes(), ManagerServiceCustomBody.class);
        // 服务名
        String serviceName = subcribeRequestCustomBody.getSerivceName();

        boolean hasService = false;
        for (RemotingTransporter service : publishRemotingTransporters) {
            PublishServiceCustomBody serviceCustomBody = (PublishServiceCustomBody) service.getCustomHeader();
            if (serviceCustomBody.getServiceProviderName().equals(serviceName) && serviceCustomBody.isSupportDegradeService()) {
                hasService = true;
                break;
            }
        }
        if (hasService) {
            Pair<DefaultServiceProviderContainer.CurrentServiceState, ServiceWrapper> pair = this.providerController.getProviderContainer().lookupService(serviceName);
            if (pair != null) {
                DefaultServiceProviderContainer.CurrentServiceState state = pair.getKey();
                if (DEGRADE_SERVICE == degradeType) {
                    state.getHasDegrade().set(!state.getHasDegrade().get());
                } else if (AUTO_DEGRADE_SERVICE == degradeType) {
                    state.getIsAutoDegrade().set(true);
                }
            }
            ackCustomBody.setSuccess(true);
        }
        return transporter;
    }

    public void setProviderStateIsHealthy(boolean providerStateIsHealthy) {
        ProviderStateIsHealthy = providerStateIsHealthy;
    }

    public List<RemotingTransporter> getPublishRemotingTransporters() {
        return publishRemotingTransporters;
    }

    public String getRegistryAddress() {
        return registryAddress;
    }

    public NettyRemoteClient getNettyRemotingClient() {
        return nettyRemotingClient;
    }

    public Channel getMonitorChannel() {
        return monitorChannel;
    }

    public String getMonitorAddress() {
        return monitorAddress;
    }

    public ProviderRegistryController getProviderController() {
        return providerController;
    }

    public ConcurrentMap<String, PublishServiceCustomBody> getGlobalPublishService() {
        return globalPublishService;
    }
}
