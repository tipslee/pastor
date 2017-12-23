package org.lee.remoting.netty;

import org.lee.remoting.RpcHook;

/**
 * @author liqiang
 * @description Netty网络通讯端Client端和Server端都需要实现的方法集合
 * @time 2017年12月23日
 * @modifytime
 */
public interface BaseRemotingService {

    public void init();

    public void start();

    public void shutDown();

    public void registerRpcHook(RpcHook rpcHook);
}
