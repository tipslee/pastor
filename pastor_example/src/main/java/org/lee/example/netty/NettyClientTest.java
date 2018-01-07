package org.lee.example.netty;

import org.lee.remoting.model.RemotingTransporter;
import org.lee.remoting.netty.NettyClientConfig;
import org.lee.remoting.netty.NettyRemoteClient;

/**
 * @author liqiang
 * @description
 * @time 2018年01月07日
 * @modifytime
 */
public class NettyClientTest {

    public static final Byte TEST = new Byte("-1");
    public static void main(String[] args) throws Exception {
        NettyClientConfig config = new NettyClientConfig();
        NettyRemoteClient client = new NettyRemoteClient(config);
        TestCommonCustomBody commonCustomBody = new TestCommonCustomBody(1, "test", new TestCommonCustomBody.ComplexTestObj("attr1", 2));
        client.start();
        RemotingTransporter response =client.invokeSync("127.0.0.1:18001", RemotingTransporter.createRequestTransporter(TEST, commonCustomBody), 3000);
        System.out.println(response);
    }

}
