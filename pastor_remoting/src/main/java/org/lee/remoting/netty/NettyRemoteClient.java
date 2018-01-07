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
import org.lee.remoting.ConnectionUtils;
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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
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

    private final Lock lockChannelTables = new ReentrantLock();
    private static final long LockTimeoutMillis = 3000;


    private Timer timer = new HashedWheelTimer();

    private boolean isReConnect = true;

    private final ConnectIdleTrigger idleTrigger = new ConnectIdleTrigger();

    private RpcHook rpcHook;

    private final ConcurrentHashMap<String, ChannelWrapper> channelWrapperMap = new ConcurrentHashMap<String, ChannelWrapper>();

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
                        new RemoteTransporterDecoder(),
                        new RemoteTransporterEncoder(),
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


    @Override
    public RemotingTransporter invokeSync(String address, RemotingTransporter request, long timeMillis) throws RemotingTimeoutException, RemotingSendRequestException, RemotingException, InterruptedException {
        Channel channel = this.getAndCreateNewChannel(address);
        if (channel != null && channel.isActive()) {
            try {
                if (this.rpcHook != null) {
                    rpcHook.doBefore(address, request);
                }
                RemotingTransporter response = super.invokeSync(channel, request, timeMillis);
                if (this.rpcHook != null) {
                    rpcHook.doAfter(address, request, response);
                }
                return response;
            } catch (RemotingSendRequestException e) {
                logger.warn("invokeSync: send request exception, so close the channel[{}]", address);
                this.closeChannel(address, channel);
                throw e;
            } catch (RemotingTimeoutException e) {
                logger.warn("invokeSync: wait response timeout exception, the channel[{}]", address);
                throw e;
            }
        } else {
            this.closeChannel(address, channel);
            throw new RemotingException(address + " connection exception");
        }
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
            for (ChannelWrapper cw : this.channelWrapperMap.values()) {
                this.closeChannel(null, cw.getChannel());
            }
            this.channelWrapperMap.clear();

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
        this.rpcHook = rpcHook;
    }

    @Override
    public RpcHook getRpcHook() {
        return this.rpcHook;
    }

    private void closeChannel(String addr, Channel channel) {
        if (channel == null) {
            return;
        }
        final String addrRemote = addr == null ? ConnectionUtils.parseChannelRemoteAddr(channel) : addr;
        try {
            if (this.lockChannelTables.tryLock(LockTimeoutMillis, TimeUnit.MILLISECONDS)) {
                try {
                    ChannelWrapper preCw = channelWrapperMap.get(addrRemote);
                    boolean isRemoveChannelWrapper = true;
                    if (preCw == null) {
                        logger.info("closeChannel: the channel[{}] has been removed from the channel table before", addrRemote);
                        isRemoveChannelWrapper = false;
                    } else if (preCw.getChannel() != channel) {
                        logger.info("closeChannel: the channel[{}] has been closed before, and has been created again, nothing to do.", addrRemote);
                        isRemoveChannelWrapper = false;
                    }
                    if (isRemoveChannelWrapper) {
                        this.channelWrapperMap.remove(addr);
                        logger.info("closeChannel: the channel[{}] was removed from channel table", addrRemote);
                    }
                    ConnectionUtils.closeChannel(channel);
                } catch (Exception e) {
                    logger.error("closeChannel: close the channel exception", e);
                } finally {
                    this.lockChannelTables.unlock();
                }
            } else {
                logger.warn("closeChannel: try to close channel, but timeout, mills[{}]", LockTimeoutMillis);
            }
        } catch (Exception e) {
            logger.error("closeChannel exception", e);
        }
    }

    private Channel getAndCreateNewChannel(String address) throws InterruptedException {
        Channel channel = null;
        if (address == null) {
            logger.warn("address is null");
            return channel;
        }
        ChannelWrapper channelWrapper = channelWrapperMap.get(address);
        if (channelWrapper != null && channelWrapper.isOk()) {
            channel = channelWrapper.getChannel();
            return channel;
        }
        channel = this.createNewChannel(address);

        return channel;
    }


    public Channel createNewChannel(String address) throws InterruptedException {
        Channel channel = null;
        ChannelWrapper channelWrapper = channelWrapperMap.get(address);
        if (channelWrapper != null && channelWrapper.isOk()) {
            channel = channelWrapper.getChannel();
            return channel;
        }
        if (lockChannelTables.tryLock(LockTimeoutMillis, MILLISECONDS)) {
            try {
                boolean isCreateNewChannel = true;
                ChannelWrapper cw = channelWrapperMap.get(address);
                if (cw != null) {
                    if (cw.isOk()) {
                        return cw.getChannel();
                    } else if (!cw.getChannelFuture().isDone()) {
                        isCreateNewChannel = false;
                    } else {
                        isCreateNewChannel = true;
                        channelWrapperMap.remove(address);
                    }
                }
                if (isCreateNewChannel) {
                    ChannelFuture future = this.bootstrap.connect(ConnectionUtils.string2SocketAddress(address));
                    channelWrapperMap.put(address, new ChannelWrapper(future));
                }
            } finally {
                lockChannelTables.unlock();
            }
        }
        channelWrapper = channelWrapperMap.get(address);
        if (channelWrapper != null) {
            ChannelFuture channelFuture = channelWrapper.getChannelFuture();
            if (channelFuture.awaitUninterruptibly(this.config.getConnectTimeoutMillis(), MILLISECONDS)) {
                if (channelWrapper.isOk()) {
                    return channelWrapper.getChannel();
                } else {
                    logger.warn("createChannel: connect remote host[" + address + "] failed, " + channelFuture.toString(), channelFuture.cause());
                }
            } else {
                logger.warn("createChannel: connect remote host[{}] timeout {}ms, {}", address, this.config.getConnectTimeoutMillis(),
                        channelFuture.toString());
            }
        }
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
        ChannelWrapper cw = channelWrapperMap.get(address);
        if (cw != null && cw.isOk()) {
            return cw.isWritable();
        } else {
            return false;
        }
    }

    public Bootstrap getBootstrap() {
        return bootstrap;
    }

    @Override
    public void setReconnect(boolean isReconnect) {
        this.isReConnect = isReconnect;
    }


    class ChannelWrapper {
        private final ChannelFuture channelFuture;

        ChannelWrapper(ChannelFuture channelFuture) {
            this.channelFuture = channelFuture;
        }

        public boolean isOk() {
            return channelFuture.channel() != null && channelFuture.channel().isActive();
        }

        public boolean isWritable() {
            return channelFuture.channel() != null && channelFuture.channel().isWritable();
        }

        public Channel getChannel() {
            return channelFuture.channel();
        }

        public ChannelFuture getChannelFuture() {
            return channelFuture;
        }
    }

}
