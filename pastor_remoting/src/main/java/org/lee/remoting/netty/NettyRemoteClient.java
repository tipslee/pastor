package org.lee.remoting.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timer;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.internal.PlatformDependent;
import org.lee.common.exception.remoting.RemotingException;
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
import org.lee.remoting.netty.idle.ConnectIdleTrigger;
import org.lee.remoting.netty.watcher.ConnectionWatchdog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.lee.common.util.Constants.WRITER_IDLE_TIME_SECONDS;

/**
 * @author liqiang
 * @description
 * @time 2017年12月24日
 * @modifytime
 */
public class NettyRemoteClient extends NettyRemotingBase implements RemoteClient {

    private static final Logger logger = LoggerFactory.getLogger(NettyRemoteClient.class);


    private Bootstrap bootstrap;
    private EventLoopGroup workerGroup;
    private int workerThread;
    private volatile ByteBufAllocator allocator;

    private DefaultEventExecutorGroup defaultEventExecutorGroup;

    private final NettyClientConfig config;
    private volatile int writeBufferHighWaterMark = -1;
    private volatile int writeBufferLowWaterMark = -1;

    private Timer timer = new HashedWheelTimer();

    private boolean isReConnect = true;

    private final ConnectIdleTrigger idleTrigger = new ConnectIdleTrigger();

    public NettyRemoteClient(NettyClientConfig clientConfig) {
        this.config = clientConfig;
        if (config != null) {
            workerThread = config.getClientWorkerThreads();
            writeBufferHighWaterMark = config.getWriteBufferHighWaterMark();
            writeBufferLowWaterMark = config.getWriteBufferLowWaterMark();
        }
        publicExecutor = Executors.newFixedThreadPool(4, new ThreadFactory() {
            private AtomicInteger threadIndex = new AtomicInteger(0);
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "NettyClientPublicExecutor_" + this.threadIndex.incrementAndGet());
            }
        });

        init();
    }

    @Override
    public void init() {
        bootstrap = new Bootstrap();
        workerGroup = new NioEventLoopGroup(workerThread, new DefaultThreadFactory("client worker group"));
        bootstrap.group(workerGroup);

        ((NioEventLoopGroup) workerGroup).setIoRatio(100);
        allocator = new PooledByteBufAllocator(PlatformDependent.directBufferPreferred());

        bootstrap.option(ChannelOption.ALLOCATOR, allocator).option(ChannelOption.MESSAGE_SIZE_ESTIMATOR, DefaultMessageSizeEstimator.DEFAULT)
                .option(ChannelOption.SO_REUSEADDR, true).option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) SECONDS.toMillis(3));

        bootstrap.option(ChannelOption.SO_KEEPALIVE, true).option(ChannelOption.TCP_NODELAY, true).option(ChannelOption.ALLOW_HALF_CLOSURE, false);

        if (writeBufferLowWaterMark >= 0 && writeBufferHighWaterMark > 0) {
            WriteBufferWaterMark waterMark = new WriteBufferWaterMark(writeBufferLowWaterMark, writeBufferHighWaterMark);
            bootstrap.option(ChannelOption.WRITE_BUFFER_WATER_MARK, waterMark);
        }

    }

    @Override
    public void start() {
        defaultEventExecutorGroup = new DefaultEventExecutorGroup(Constants.AVAILABLE_PROCESSORS,
                new ThreadFactory() {
                    private AtomicInteger threadIndex = new AtomicInteger(0);

                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r, "NettyClientWorkerThread_" + this.threadIndex.incrementAndGet());
                    }
                });
        bootstrap.channel(NioSocketChannel.class);
        final ConnectionWatchdog watchdog = new ConnectionWatchdog(bootstrap, timer) {
            @Override
            public ChannelHandler[] handlers() {
                return new ChannelHandler[] {
                        this,
                        new RemoteTransporterEncoder(),
                        new RemoteTransporterDecoder(),
                        new IdleStateHandler(0, WRITER_IDLE_TIME_SECONDS, 0, TimeUnit.SECONDS),
                        idleTrigger,
                        new NettyClientHandler()
                };
            }
        };
        watchdog.setReconnect(isReConnect);
        bootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline().addLast(defaultEventExecutorGroup, watchdog.handlers());
            }
        });

    }

    class NettyClientHandler extends SimpleChannelInboundHandler<RemotingTransporter> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, RemotingTransporter msg) throws Exception {
            processMessageReceived(ctx, msg);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            processInactiveChannel(ctx);
        }

    }

    @Override
    public void shutDown() {
        try {
            timer.stop();
            timer = null;

            workerGroup.shutdownGracefully();
            if (defaultEventExecutorGroup != null) {
                defaultEventExecutorGroup.shutdownGracefully();
            }
            if (publicExecutor != null) {
                publicExecutor.shutdown();
            }
        } catch (Exception e) {
            logger.error("NettyRemotingClient shutdown exception, ", e);
        }

    }

    @Override
    public void registerRpcHook(RpcHook rpcHook) {

    }


    @Override
    public RpcHook getRpcHook() {
        return null;
    }

    @Override
    public RemotingTransporter invokeSync(String address, RemotingTransporter request, long timeMillis) throws RemotingTimeoutException, RemotingSendRequestException, RemotingException, InterruptedException {
        return null;
    }

    @Override
    public void registerProcessor(byte requestCode, NettyRequestProcessor processor, ExecutorService executorService) {
        if (processor == null) {
            return;
        }
        if (executorService == null) {
            executorService = publicExecutor;
        }
        super.processorTable.put(requestCode, new Pair<NettyRequestProcessor, ExecutorService>(processor, executorService));

    }

    @Override
    public void registerInvalidProcessor(NettyInactiveProcessor processor, ExecutorService executorService) {
        if (executorService == null) {
            executorService = publicExecutor;
        }
        super.defaultInactiveProcessor = new Pair<>(processor, executorService);
    }

    @Override
    public boolean isChannelWritable(String address) {
        return false;
    }

    @Override
    public void setReconnect(boolean isReconnect) {
        this.isReConnect = isReconnect;
    }

}
