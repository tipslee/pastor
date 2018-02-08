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

    public static ConcurrentMap<String, Meter> getGlobalMeterManager() {
        return globalMeterManager;
    }

}
