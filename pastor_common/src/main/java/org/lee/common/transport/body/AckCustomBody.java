package org.lee.common.transport.body;

import org.lee.common.exception.remoting.RemotingCommmonCustomException;

/**
 * @author liqiang
 * @description ACK消息
 * @time 2018年02月08日
 * @modifytime
 */
public class AckCustomBody implements CommonCustomBody {
    //request请求id
    private long requestId;

    //是否消费处理成功
    private boolean success;

    public AckCustomBody(long requestId, boolean success) {
        this.requestId = requestId;
        this.success = success;
    }

    public long getRequestId() {
        return requestId;
    }

    public void setRequestId(long requestId) {
        this.requestId = requestId;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    @Override
    public String toString() {
        return "AckCustomBody{" +
                "requestId=" + requestId +
                ", success=" + success +
                '}';
    }

    @Override
    public void checkFields() throws RemotingCommmonCustomException {

    }
}
