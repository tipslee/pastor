package org.lee.remoting.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.internal.PlatformDependent;
import org.lee.common.exception.remoting.RemotingSendRequestException;
import org.lee.common.exception.remoting.RemotingTimeoutException;
import org.lee.common.util.Constants;
import org.lee.common.util.Pair;
import org.lee.remoting.NettyRemotingBase;
import org.lee.remoting.RpcHook;
import org.lee.remoting.model.NettyInactiveProcessor;
import org.lee.remoting.model.NettyRequestProcessor;
import org.lee.remoting.model.RemotingTransporter;
import org.lee.remoting.netty.decode.RemoteTransporterDecoder;
import org.lee.remoting.netty.encode.RemoteTransporterEncoder;
import org.lee.remoting.netty.idle.AcceptorIdleTrigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.lee.common.util.Constants.READER_IDLE_TIME_SECONDS;

/**
 * @author liqiang
 * @description
 * @time 2017年12月23日
 * @modifytime
 */
public class NettyRemoteServer extends NettyRemotingBase implements RemoteServer {
    private static final Logger logger = LoggerFactory.getLogger(NettyRemoteServer.class);
    private ServerBootstrap serverBootstrap;
    private EventLoopGroup boss;
    private EventLoopGroup worker;

    private final NettyServerConfig nettyServerConfig;
    private int nWorkers;
    private int writeBufferLowWaterMark;
    private int writeBufferHighWaterMark;
    protected volatile ByteBufAllocator allocator;

    private DefaultEventExecutorGroup defaultEventExecutorGroup;

    private AcceptorIdleTrigger idleStateTrigger = new AcceptorIdleTrigger();

    private RpcHook rpcHook;


    public NettyRemoteServer(NettyServerConfig config) {
        nettyServerConfig = config;
        if (nettyServerConfig != null) {
            nWorkers = config.getServerWorkerThreads();
            writeBufferHighWaterMark = config.getWriteBufferHighWaterMark();
            writeBufferLowWaterMark = config.getWriteBufferLowWaterMark();
        }
        this.publicExecutor = Executors.newFixedThreadPool(4, new ThreadFactory() {
            private AtomicInteger threadIndex = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "NettyServerPublicExecutor_" + this.threadIndex.incrementAndGet());
            }
        });
        init();
    }

    @Override
    public void init() {
        ThreadFactory bossFactory = new DefaultThreadFactory("netty.boss");
        ThreadFactory workerFactory = new DefaultThreadFactory("netty.worker");
        boss = new NioEventLoopGroup(1, bossFactory);
        worker = new NioEventLoopGroup(nWorkers, workerFactory);

        serverBootstrap = new ServerBootstrap();
        serverBootstrap.group(boss, worker);
        allocator = new PooledByteBufAllocator(PlatformDependent.directBufferPreferred());

        serverBootstrap.childOption(ChannelOption.ALLOCATOR, allocator)
                .childOption(ChannelOption.MESSAGE_SIZE_ESTIMATOR, DefaultMessageSizeEstimator.DEFAULT);

        serverBootstrap.option(ChannelOption.SO_BACKLOG, 32768);
        serverBootstrap.option(ChannelOption.SO_REUSEADDR, true);

        // child options
        serverBootstrap.childOption(ChannelOption.SO_REUSEADDR, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.ALLOW_HALF_CLOSURE, false);


        if (writeBufferLowWaterMark >= 0 && writeBufferHighWaterMark > 0) {
            WriteBufferWaterMark waterMark = new WriteBufferWaterMark(writeBufferLowWaterMark, writeBufferHighWaterMark);
            serverBootstrap.childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, waterMark);
        }
    }

    @Override
    public void start() {
        defaultEventExecutorGroup = new DefaultEventExecutorGroup(Constants.AVAILABLE_PROCESSORS,
                new ThreadFactory() {
                    private AtomicInteger threadIndex = new AtomicInteger(0);

                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r, "NettyServerWorkerThread_" + this.threadIndex.incrementAndGet());
                    }
                });
        serverBootstrap.channel(NioServerSocketChannel.class);
        serverBootstrap.localAddress(this.nettyServerConfig.getListenPort()).childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline().addLast(
                        defaultEventExecutorGroup,
                        new IdleStateHandler(READER_IDLE_TIME_SECONDS, 0, 0, TimeUnit.SECONDS),
                        idleStateTrigger,
                        new RemoteTransporterDecoder()
                        , new RemoteTransporterEncoder()
                        , new NettyServerHandler());
            }
        });
        try {
            logger.info("netty bind [{}] serverBootstrap start...", this.nettyServerConfig.getListenPort());
            this.serverBootstrap.bind().sync();
            logger.info("netty start success at port [{}]", this.nettyServerConfig.getListenPort());
        } catch (InterruptedException e1) {
            logger.error("start serverBootstrap exception [{}]", e1.getMessage());
            throw new RuntimeException("this.serverBootstrap.bind().sync() InterruptedException", e1);
        }

    }

    @Override
    public void shutDown() {
        try {

            this.boss.shutdownGracefully();

            this.worker.shutdownGracefully();

            if (this.defaultEventExecutorGroup != null) {
                this.defaultEventExecutorGroup.shutdownGracefully();
            }
        } catch (Exception e) {
            logger.error("NettyRemotingServer shutdown exception, ", e);
        }

        if (this.publicExecutor != null) {
            try {
                this.publicExecutor.shutdown();
            } catch (Exception e) {
                logger.error("NettyRemotingServer shutdown exception, ", e);
            }
        }
    }

    @Override
    public void registerRpcHook(RpcHook rpcHook) {
        this.rpcHook = rpcHook;
    }

    @Override
    public RpcHook getRpcHook() {
        return rpcHook;
    }

    @Override
    public void registerRequestDefaultProcessor(NettyRequestProcessor processor, ExecutorService executorService) {
        ExecutorService executor = executorService;
        if (executor == null) {
            executor = super.publicExecutor;
        }
        defaultRequestProcessor = new Pair<NettyRequestProcessor, ExecutorService>(processor, executor);
    }

    @Override
    public void registerProcessorTable(NettyRequestProcessor processor, ExecutorService executorService, Byte requestCode) {
        ExecutorService executor = executorService;
        if (executor == null) {
            executor = super.publicExecutor;
        }
        super.processorTable.put(requestCode, new Pair<NettyRequestProcessor, ExecutorService>(processor, executor));
    }

    @Override
    public void registerInvalidChannelProcessor(NettyInactiveProcessor processor, ExecutorService executorService) {
        ExecutorService executor = executorService;
        if (executor == null) {
            executor = super.publicExecutor;
        }
        super.defaultInactiveProcessor = new Pair<NettyInactiveProcessor, ExecutorService>(processor, executor);
    }

    @Override
    public Pair<NettyRequestProcessor, ExecutorService> getProcessor(Byte requestCode) {
        return super.processorTable.get(requestCode);
    }

    @Override
    public RemotingTransporter invokeSync(Channel channel, RemotingTransporter request, long timeoutMills) throws RemotingTimeoutException, RemotingSendRequestException, InterruptedException {
        return invokeSync(channel, request, timeoutMills);
    }

    class NettyServerHandler extends SimpleChannelInboundHandler<RemotingTransporter> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, RemotingTransporter msg) throws Exception {
            processMessageReceived(ctx, msg);
        }

        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            processInactiveChannel(ctx);
        }

    }

}
