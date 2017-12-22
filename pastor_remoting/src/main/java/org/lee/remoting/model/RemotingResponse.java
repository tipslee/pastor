package org.lee.remoting.model;

import org.lee.remoting.InvokeCallback;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author liqiang
 * @description Netty统一返回对象
 * @time 2017年12月21日
 * @modifytime
 */
public class RemotingResponse {

    private volatile RemotingTransporter transporter;

    private volatile boolean sendRequestOk;

    private volatile Throwable cause;

    private final long timeoutMills; //超时时间，毫秒数

    private final long opaque; //请求ID

    private final InvokeCallback invokeCallback; //回调函数

    private final long beginTimestamp = System.currentTimeMillis();

    private final CountDownLatch countDown = new CountDownLatch(1);

    public RemotingResponse(long opaque, long timeoutMills, InvokeCallback invokeCallback) {
        this.opaque = opaque;
        this.timeoutMills = timeoutMills;
        this.invokeCallback = invokeCallback;

    }

    public void executeInvokeCallback() {
        if (invokeCallback != null) {
            invokeCallback.operationComplete(this);
        }
    }


    public RemotingTransporter getTransporter() {
        return transporter;
    }

    public void setTransporter(RemotingTransporter transporter) {
        this.transporter = transporter;
    }

    public long getOpaque() {
        return opaque;
    }

    public boolean isSendRequestOk() {
        return sendRequestOk;
    }

    public void setSendRequestOk(boolean sendRequestOk) {
        this.sendRequestOk = sendRequestOk;
    }

    public long getTimeoutMills() {
        return timeoutMills;
    }

    public Throwable getCause() {
        return cause;
    }

    public void setCause(Throwable cause) {
        this.cause = cause;
    }

    public InvokeCallback getInvokeCallback() {
        return invokeCallback;
    }

    public long getBeginTimestamp() {
        return beginTimestamp;
    }

    public RemotingTransporter waitResponse() throws InterruptedException{
        countDown.await(timeoutMills, TimeUnit.MILLISECONDS);
        return transporter;
    }

    public void putResponse(RemotingTransporter transporter) {
        this.transporter = transporter;
        countDown.countDown();
    }
}
