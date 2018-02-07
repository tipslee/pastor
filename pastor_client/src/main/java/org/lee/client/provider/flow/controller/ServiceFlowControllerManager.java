package org.lee.client.provider.flow.controller;

import org.lee.common.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author liqiang
 * @description 限流器
 * @time 2018年02月07日
 * @modifytime
 */
public class ServiceFlowControllerManager {
    private static final Logger logger = LoggerFactory.getLogger(ServiceFlowControllerManager.class);

    private static final ConcurrentMap<String, Pair<Long, ServiceFlowController>> globalFlowControllerMap = new ConcurrentHashMap<String, Pair<Long,ServiceFlowController>>();

    public void setServiceLimitVal(String serviceName, Long maxCallCount) {
        ServiceFlowController flowController = new ServiceFlowController();
        Pair<Long, ServiceFlowController> pair = new Pair<Long, ServiceFlowController>();
        pair.setKey(maxCallCount);
        pair.setValue(flowController);
        globalFlowControllerMap.put(serviceName, pair);
    }


    /**
     * 原子增加某个服务的调用次数
     * @param serviceName
     */
    public void incrementCallCount(String serviceName){

        Pair<Long,ServiceFlowController> pair = globalFlowControllerMap.get(serviceName);

        if(null == pair){
            logger.warn("serviceName [{}] matched no flowController",serviceName);
            return;
        }

        ServiceFlowController serviceFlowController = pair.getValue();
        serviceFlowController.incrementAtCurrentMinute();

    }

    /**
     * 查看某个服务是否可用
     * @param serviceName
     * @return
     */
    public boolean isAllow(String serviceName){

        Pair<Long,ServiceFlowController> pair = globalFlowControllerMap.get(serviceName);

        if(null == pair){
            logger.warn("serviceName [{}] matched no flowController",serviceName);
            return false;
        }

        ServiceFlowController serviceFlowController = pair.getValue();
        Long maxCallCount = pair.getKey();

        long hasCallCount = serviceFlowController.getCurrentCallCountAtLastMinute();

        return hasCallCount > maxCallCount ? false :true;

    }

    /**
     * 获取到某个服务的上一分钟的调用次数
     * @param serviceName
     * @return
     */
    public Long getLastMinuteCallCount(String serviceName){
        Pair<Long,ServiceFlowController> pair = globalFlowControllerMap.get(serviceName);

        if(null == pair){
            logger.warn("serviceName [{}] matched no flowController",serviceName);
            return 0l;
        }
        ServiceFlowController serviceFlowController = pair.getValue();
        return serviceFlowController.getLastCallCountAtLastMinute();
    }

    /**
     * 将下一秒的调用次数置为0
     */
    public void clearAllServiceNextMinuteCallCount(){

        for(String service : globalFlowControllerMap.keySet()){

            Pair<Long,ServiceFlowController> pair = globalFlowControllerMap.get(service);
            if(null == pair){
                logger.warn("serviceName [{}] matched no flowController",service);
                continue;
            }
            ServiceFlowController serviceFlowController = pair.getValue();
            serviceFlowController.clearNextMinuteCallCount();
        }
    }




}
