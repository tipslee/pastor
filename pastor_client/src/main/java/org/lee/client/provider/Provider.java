package org.lee.client.provider;

import io.netty.channel.Channel;
import org.lee.common.exception.remoting.RemotingException;
import org.lee.remoting.model.RemotingTransporter;

/**
 * @author liqiang
 * @description 服务提供者接口
 * @time 2018年02月05日
 * @modifytime
 */
public interface Provider {

    /**
     * 启动服务提供者实例
     * @throws InterruptedException
     * @throws RemotingException
     */
    void start() throws InterruptedException, RemotingException;

    /**
     * 发布服务
     * @throws InterruptedException
     * @throws RemotingException
     */
    void publishedAndStartProvider() throws InterruptedException, RemotingException;

    /**
     * 暴露服务的地址
     * @param exposePort
     * @return
     */
    Provider serviceListenPort(int exposePort);


    /**
     * 设置注册中心地址
     * @param address
     * @return
     */
    Provider registryAddress(String address);

    /**
     * 设置监控中心地址
     * @param address
     * @return
     */
    Provider monitorAddress(String address);

    /**
     * 需要暴露的服务
     * @param service
     * @return
     */
    Provider publishService(Object ...service);

    /**
     * 处理RPC请求
     * @param request
     * @param channel
     */
    void handleRPCRequest(RemotingTransporter request, Channel channel);


}
