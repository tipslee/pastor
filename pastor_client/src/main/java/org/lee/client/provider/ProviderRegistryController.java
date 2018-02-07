package org.lee.client.provider;

import org.lee.client.provider.flow.controller.ServiceFlowControllerManager;

/**
 * @author liqiang
 * @description provider端向注册中心连接的业务逻辑的控制器
 * @time 2018年02月05日
 * @modifytime
 */
public class ProviderRegistryController {

    private DefaultProvider defaultProvider;

    //provider与注册中心的所有逻辑控制器
//    private RegistryController registryController;
    //provider与monitor端通信的控制器
//    private ProviderMonitorController providerMonitorController;


    //本地服务编织服务管理
    private LocalServerWrapperManager localServerWrapperManager;

    private final ServiceProviderContainer providerContainer;
    private ServiceFlowControllerManager serviceFlowControllerManager = new ServiceFlowControllerManager();

    public ProviderRegistryController(DefaultProvider defaultProvider) {
        this.defaultProvider = defaultProvider;
        providerContainer = new DefaultServiceProviderContainer();
        localServerWrapperManager = new LocalServerWrapperManager(this);
//        registryController = new RegistryController(defaultProvider);
//        providerMonitorController = new ProviderMonitorController(defaultProvider);
    }

    public DefaultProvider getDefaultProvider() {
        return defaultProvider;
    }

    public void setDefaultProvider(DefaultProvider defaultProvider) {
        this.defaultProvider = defaultProvider;
    }

    public LocalServerWrapperManager getLocalServerWrapperManager() {
        return localServerWrapperManager;
    }

    public void setLocalServerWrapperManager(LocalServerWrapperManager localServerWrapperManager) {
        this.localServerWrapperManager = localServerWrapperManager;
    }

    public ServiceProviderContainer getProviderContainer() {
        return providerContainer;
    }

    public ServiceFlowControllerManager getServiceFlowControllerManager() {
        return serviceFlowControllerManager;
    }

    public void setServiceFlowControllerManager(ServiceFlowControllerManager serviceFlowControllerManager) {
        this.serviceFlowControllerManager = serviceFlowControllerManager;
    }
}
