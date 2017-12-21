package org.lee.remoting.model;

import org.lee.common.protocal.PastorProtocol;
import org.lee.common.transport.body.CommonCustomBody;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by liqiang on 2017/12/20.
 * 网络传输统一对象
 */
public class RemotingTransporter extends ByteHolder {

    private static final AtomicInteger requestId = new AtomicInteger(0);

    /**
     * 请求类型标识
     * 假设code==1，消费者订阅服务
     * 假设code==2，生产者生产服务等
     */
    private byte code;

    /**
     * 消息体
     */
    private CommonCustomBody customHeader;
    /**
     * 请求时间
     */
    private transient long timestamp;

    /**
     * 请求的id
     */
    private long opaque = requestId.getAndIncrement();

    /**
     * 传输类型
     * REQUEST，RESPONSE等
     */
    private byte transporterType;

    public RemotingTransporter() {
    }

    /**
     * 创建一个请求传输对象
     *
     * @param code               请求的类型
     * @param commonCustomHeader 请求的正文
     * @return
     */
    public static RemotingTransporter createRequestTransporter(byte code, CommonCustomBody commonCustomHeader) {
        RemotingTransporter remotingTransporter = new RemotingTransporter();
        remotingTransporter.setCode(code);
        remotingTransporter.customHeader = commonCustomHeader;
        remotingTransporter.transporterType = PastorProtocol.REQUEST_REMOTING;
        return remotingTransporter;
    }

    /**
     * 创建一个响应对象
     *
     * @param code               响应对象的类型
     * @param commonCustomHeader 响应对象的正文
     * @param opaque             此响应对象对应的请求对象的id
     * @return
     */
    public static RemotingTransporter createResponseTransporter(byte code, CommonCustomBody commonCustomHeader, long opaque) {
        RemotingTransporter remotingTransporter = new RemotingTransporter();
        remotingTransporter.setCode(code);
        remotingTransporter.customHeader = commonCustomHeader;
        remotingTransporter.setOpaque(opaque);
        remotingTransporter.transporterType = PastorProtocol.RESPONSE_REMOTING;
        return remotingTransporter;
    }

    public byte getCode() {
        return code;
    }

    public void setCode(byte code) {
        this.code = code;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public long getOpaque() {
        return opaque;
    }

    public void setOpaque(long opaque) {
        this.opaque = opaque;
    }

    public byte getTransporterType() {
        return transporterType;
    }

    public void setTransporterType(byte transporterType) {
        this.transporterType = transporterType;
    }

    public CommonCustomBody getCustomHeader() {
        return customHeader;
    }

    public void setCustomHeader(CommonCustomBody customHeader) {
        this.customHeader = customHeader;
    }
}
