package org.lee.common.transport.body;

import org.lee.common.exception.remoting.RemotingCommmonCustomException;

import java.util.concurrent.atomic.AtomicLong;

/**
 * @author liqiang
 * @description
 * @time 2018年02月09日
 * @modifytime
 */
public class RequestCustomBody implements CommonCustomBody {
    private static final AtomicLong idGenerator = new AtomicLong(0);
    private final long invokeId;
    private String serviceName;
    private Object[] args;
    private long timeStamp; //调用时间

    public RequestCustomBody() {
        invokeId = idGenerator.incrementAndGet();
    }

    public RequestCustomBody(long invokeId) {
        this.invokeId = invokeId;
    }

    @Override
    public void checkFields() throws RemotingCommmonCustomException {

    }

    public long getInvokeId() {
        return invokeId;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public Object[] getArgs() {
        return args;
    }

    public void setArgs(Object[] args) {
        this.args = args;
    }

    public long getTimeStamp() {
        return timeStamp;
    }

    public void setTimeStamp(long timeStamp) {
        this.timeStamp = timeStamp;
    }
}
