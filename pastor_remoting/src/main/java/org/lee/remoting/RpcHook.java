package org.lee.remoting;

import org.lee.remoting.model.RemotingTransporter;

/**
 * @author liqiang
 * @description 钩子函数，在request处理前，和处理后增加操作
 * @time 2017年12月22日
 * @modifytime
 */
public interface RpcHook {

    public void doBefore(String address, RemotingTransporter request);

    public void doAfter(String address, RemotingTransporter request, RemotingTransporter response);
}
