package org.lee.client.metrics;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author liqiang
 * @description
 * @time 2018年02月08日
 * @modifytime
 */
public class ServiceMeterManager {

    private static ConcurrentMap<String, Meter> globalMeterManager = new ConcurrentHashMap<String, Meter>();

    public static Integer calcServiceSuccessRate(String serviceName) {
        Meter meter = globalMeterManager.get(serviceName);
        if(meter == null){
            return 0;
        }
        int callCount = meter.getCallCount().intValue();
        int failCount = meter.getFailedCount().intValue();
        //如果调用的此时是0.默认成功率是100%
        if(callCount == 0){
            return 100;
        }
        return (100 *(callCount - failCount ) / callCount);
    }

    /**
     * 增加一次调用次数
     * @param serviceName
     */
    public static void incrementCallTimes(String serviceName){

        Meter meter = globalMeterManager.get(serviceName);

        if(meter == null){
            meter = new Meter(serviceName);
            globalMeterManager.put(serviceName, meter);
        }
        meter.getCallCount().incrementAndGet();

    }

    /**
     * 增加一次调用失败次数
     * @param serviceName
     */
    public static void incrementFailTimes(String serviceName){

        Meter meter = globalMeterManager.get(serviceName);

        if(meter == null){
            meter = new Meter(serviceName);
            globalMeterManager.put(serviceName, meter);
        }
        meter.getFailedCount().incrementAndGet();
    }

    /**
     * 累加某个服务的调用时间
     * @param serviceName
     * @param byteSize
     */
    public static void incrementTotalTime(String serviceName,Long timecost){

        Meter meter = globalMeterManager.get(serviceName);

        if(meter == null){
            meter = new Meter(serviceName);
            globalMeterManager.put(serviceName, meter);
        }
        meter.getTotalCallTime().addAndGet(timecost);
    }

    /**
     * 累加某个服务的请求入参的大小
     * @param serviceName
     * @param byteSize
     */
    public static void incrementRequestSize(String serviceName,int byteSize){

        Meter meter = globalMeterManager.get(serviceName);

        if(meter == null){
            meter = new Meter(serviceName);
            globalMeterManager.put(serviceName, meter);
        }
        meter.getTotalRequestSize().addAndGet(byteSize);
    }


    public static ConcurrentMap<String, Meter> getGlobalMeterManager() {
        return globalMeterManager;
    }

}
