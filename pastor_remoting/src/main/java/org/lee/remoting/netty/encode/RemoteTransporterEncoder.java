package org.lee.remoting.netty.encode;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import org.lee.common.protocal.PastorProtocol;
import org.lee.common.serialization.SerializerHolder;
import org.lee.remoting.model.RemotingTransporter;

/**
 * Created by liqiang on 2017/12/20.
 */
@ChannelHandler.Sharable
public class RemoteTransporterEncoder extends MessageToByteEncoder<RemotingTransporter> {

    @Override
    protected void encode(ChannelHandlerContext ctx, RemotingTransporter msg, ByteBuf out) throws Exception {
        byte[] body = SerializerHolder.serializerImpl().writeObject(msg);
        byte isCompress = PastorProtocol.UNCOMPRESS;
        out.writeByte(PastorProtocol.MAGIC)
                .writeByte(msg.getTransporterType()) //传输类型
                .writeByte(msg.getCode()) //消息类型
                .writeLong(msg.getOpaque()) //请求ID
                .writeByte(isCompress) //是否压缩
                .writeInt(body.length) //消息长度
                .writeBytes(body);
    }
}
