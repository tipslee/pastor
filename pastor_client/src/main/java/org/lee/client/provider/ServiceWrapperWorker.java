package org.lee.client.provider;

import org.lee.client.provider.interceptor.ProviderProxyHandler;
import org.lee.client.provider.model.ServiceWrapper;

import java.util.List;

/**
 * @author liqiang
 * @description
 * @time 2018年02月07日
 * @modifytime
 */
public interface ServiceWrapperWorker {

    ServiceWrapperWorker provider(Object serviceProvider);

    ServiceWrapperWorker provider(ProviderProxyHandler proxyHandler, Object serviceProvider);

    List<ServiceWrapper> create();
}
