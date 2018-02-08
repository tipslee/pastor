package org.lee.client.provider;

import org.lee.client.metrics.ServiceMeterManager;
import org.lee.client.provider.DefaultServiceProviderContainer.CurrentServiceState;
import org.lee.client.provider.flow.controller.ServiceFlowControllerManager;
import org.lee.client.provider.model.ServiceWrapper;
import org.lee.common.util.Pair;

import java.util.List;

/**
 * @author liqiang
 * @description provider端向注册中心连接的业务逻辑的控制器
 * @time 2018年02月05日
 * @modifytime
 */
public class ProviderRegistryController {

    private DefaultProvider defaultProvider;

    //provider与注册中心的所有逻辑控制器
    private RegistryController registryController;
    //provider与monitor端通信的控制器
    private ProviderMonitorController providerMonitorController;


    //本地服务编织服务管理
    private LocalServerWrapperManager localServerWrapperManager;

    private final ServiceProviderContainer providerContainer;
    private ServiceFlowControllerManager serviceFlowControllerManager = new ServiceFlowControllerManager();

    public ProviderRegistryController(DefaultProvider defaultProvider) {
        this.defaultProvider = defaultProvider;
        providerContainer = new DefaultServiceProviderContainer();
        localServerWrapperManager = new LocalServerWrapperManager(this);
        registryController = new RegistryController(defaultProvider);
        providerMonitorController = new ProviderMonitorController(defaultProvider);
    }

    /**
     * 检查服务自动降级
     */
    public void checkAutoDegrade() {
        List<Pair<String, CurrentServiceState>> autoDegradeServices = this.getProviderContainer().getNeedAutoDegradeService();
        if (!autoDegradeServices.isEmpty()) {
            for (Pair<String, CurrentServiceState> pair : autoDegradeServices) {
                String serviceName = pair.getKey();
                //最低可用率
                Integer minSuccessRate = pair.getValue().getMinSuccecssRate();
                //调用的实际成功率
                Integer realSuccessRate = ServiceMeterManager.calcServiceSuccessRate(serviceName);
                //成功率低于最低可用率，进行降级
                if (minSuccessRate > realSuccessRate) {

                    final Pair<CurrentServiceState, ServiceWrapper> _pair = this.defaultProvider.getProviderController().getProviderContainer()
                            .lookupService(serviceName);
                    CurrentServiceState currentServiceState = _pair.getKey();
                    if (!currentServiceState.getHasDegrade().get()) {
                        currentServiceState.getHasDegrade().set(true);
                    }
                }
            }
        }
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

    public RegistryController getRegistryController() {
        return registryController;
    }

    public void setRegistryController(RegistryController registryController) {
        this.registryController = registryController;
    }

    public ProviderMonitorController getProviderMonitorController() {
        return providerMonitorController;
    }

    public void setProviderMonitorController(ProviderMonitorController providerMonitorController) {
        this.providerMonitorController = providerMonitorController;
    }
}
