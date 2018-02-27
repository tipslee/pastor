package org.lee.client.provider;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import org.lee.client.metrics.ServiceMeterManager;
import org.lee.client.provider.DefaultServiceProviderContainer.CurrentServiceState;
import org.lee.client.provider.flow.controller.ServiceFlowControllerManager;
import org.lee.client.provider.model.ServiceWrapper;
import org.lee.common.protocal.PastorProtocol;
import org.lee.common.serialization.SerializerHolder;
import org.lee.common.transport.body.RequestCustomBody;
import org.lee.common.transport.body.ResponseCustomBody;
import org.lee.common.transport.body.ResponseCustomBody.ResultWrapper;
import org.lee.common.util.Pair;
import org.lee.common.util.Status;
import org.lee.common.util.SystemClock;
import org.lee.remoting.model.RemotingTransporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.lee.common.util.Reflects.fastInvoke;
import static org.lee.common.util.Reflects.findMatchingParameterTypes;
import static org.lee.common.util.Status.*;

/**
 * @author liqiang
 * @description 处理consumer rpc请求的核心控制器，并统计处理的次数
 * @time 2018年02月05日
 * @modifytime
 */
public class ProviderRPCController {

    private static final Logger logger = LoggerFactory.getLogger(ProviderRPCController.class);

    private DefaultProvider defaultProvider;

    public ProviderRPCController(DefaultProvider defaultProvider) {
        this.defaultProvider = defaultProvider;
    }

    public void handleRPCRequest(RemotingTransporter request, Channel channel) {
        String serviceName = null;
        RequestCustomBody requestBody = null;
        try {
            byte[] bytes = request.getBytes();
            int requestSize = bytes.length;
            request.setBytes(null);

            requestBody = SerializerHolder.serializerImpl().readObject(bytes, RequestCustomBody.class);
            serviceName = requestBody.getServiceName();
            request.setCustomHeader(requestBody);

            ServiceMeterManager.incrementCallTimes(serviceName);
            ServiceMeterManager.incrementRequestSize(serviceName, requestSize);
        } catch (Exception e) {
            rejected(BAD_REQUEST, channel, request, serviceName);
            return;
        }

        final Pair<CurrentServiceState, ServiceWrapper> pair = defaultProvider.getProviderController().getProviderContainer().lookupService(serviceName);
        if (pair == null || pair.getValue() == null) {
            rejected(SERVICE_NOT_FOUND, channel, request, serviceName);
            return;
        }

        //限流处理
        if (pair.getValue().isFlowController()) {
            ServiceFlowControllerManager flowController = this.defaultProvider.getProviderController().getServiceFlowControllerManager();
            if (!flowController.isAllow(serviceName)) {
                rejected(APP_FLOW_CONTROL, channel, request, serviceName);
                return;
            }
        }
        process(pair, serviceName, request, channel, requestBody.getTimeStamp());
    }

    /**
     * RPC调用核心逻辑
     *
     * @param pair
     * @param serviceName
     * @param request
     * @param channel
     * @param beginTime
     */
    private void process(Pair<CurrentServiceState, ServiceWrapper> pair, final String serviceName, final RemotingTransporter request, Channel channel, final long beginTime) {
        Object invokeResult = null;
        CurrentServiceState serviceState = pair.getKey();
        ServiceWrapper serviceWrapper = pair.getValue();

        Object targetCallObj = serviceWrapper.getServiceProvider();
        Object[] args = ((RequestCustomBody) request.getCustomHeader()).getArgs();

        //判断服务是否已经被设定为自动降级，如果被设置为自动降级且有它自己的mock类的话，则将targetCallObj切换到mock方法上来
        if (serviceState.getHasDegrade().get() && serviceWrapper.getMockDegradeServiceProvider() != null) {
            targetCallObj = serviceWrapper.getMockDegradeServiceProvider();
        }

        String methodName = serviceWrapper.getMethodName();
        List<Class<?>[]> parameterTypesList = serviceWrapper.getParamters();

        Class<?>[] parameterTypes = findMatchingParameterTypes(parameterTypesList, args);
        invokeResult = fastInvoke(targetCallObj, methodName, parameterTypes, args);

        ResultWrapper resultWrapper = new ResultWrapper();
        resultWrapper.setResult(invokeResult);

        ResponseCustomBody responseBody = new ResponseCustomBody(Status.OK.value(), resultWrapper);
        final RemotingTransporter response = RemotingTransporter.createResponseTransporter(PastorProtocol.RPC_RESPONSE, responseBody, request.getOpaque());

        channel.writeAndFlush(response).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                long elapsed = SystemClock.millisClock().now() - beginTime;
                if (future.isSuccess()) {
                    ServiceMeterManager.incrementTotalTime(serviceName, elapsed);
                } else {
                    logger.info("request {} get failed response {}", request, response);
                }
            }
        });
    }

    private void rejected(Status status, Channel channel, final RemotingTransporter request, String serviceName) {
        if(null != serviceName){
            ServiceMeterManager.incrementFailTimes(serviceName);
        }
        ResultWrapper result = new ResultWrapper();
        switch (status) {
            case BAD_REQUEST:
                result.setError("bad request");
            case SERVICE_NOT_FOUND:
                result.setError(((RequestCustomBody) request.getCustomHeader()).getServiceName() +" no service found");
                break;
            case APP_FLOW_CONTROL:
            case PROVIDER_FLOW_CONTROL:
                result.setError("over unit time call limit");
                break;
            default:
                logger.warn("Unexpected status.", status.description());
                return;
        }
        logger.warn("Service rejected: {}.", result.getError());

        ResponseCustomBody responseCustomBody = new ResponseCustomBody(status.value(), result);
        final RemotingTransporter response = RemotingTransporter.createResponseTransporter(PastorProtocol.RPC_RESPONSE, responseCustomBody,
                request.getOpaque());

        channel.writeAndFlush(response).addListener(new ChannelFutureListener() {

            public void operationComplete(ChannelFuture future) throws Exception {
                if (future.isSuccess()) {
                    logger.info("request error {} get success response {}", request, response);
                } else {
                    logger.info("request error {} get failed response {}", request, response);
                }
            }
        });
    }
}
