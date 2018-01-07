package org.lee.remoting.netty.decode;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;
import org.lee.common.exception.remoting.RemotingContextException;
import org.lee.common.protocal.PastorProtocol;
import org.lee.remoting.model.RemotingTransporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xerial.snappy.Snappy;

import java.util.List;

import static org.lee.common.protocal.PastorProtocol.HEARTBEAT;

/**
 * @author liqiang
 * @description 协议解码器
 * @time 2017年12月21日
 * @modifytime
 */
public class RemoteTransporterDecoder extends ReplayingDecoder<RemoteTransporterDecoder.State> {

    private static final Logger log = LoggerFactory.getLogger(RemoteTransporterDecoder.class);

    private static final int MAX_BODY_LENGTH = 1024 * 1024 * 5;

    private final PastorProtocol header = new PastorProtocol();

    public RemoteTransporterDecoder() {
        super(State.HEADED_MAGIC);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        switch (state()) {
            case HEADED_MAGIC:
                checkMagic(in.readShort());
                checkpoint(State.HEADED_TYPE);
            case HEADED_TYPE:
                header.type(in.readByte());
                checkpoint(State.HEADED_SIGN);
            case HEADED_SIGN:
                header.sign(in.readByte());
                checkpoint(State.HEADED_ID);
            case HEADED_ID:
                header.id(in.readLong());
                checkpoint(State.HEADED_BODY_LENGTH);
            case HEADED_BODY_LENGTH:
                header.bodyLength(in.readInt());
                checkpoint(State.HEADED_COMPRESS);
            case HEADED_COMPRESS:
                header.setCompress(in.readByte());
                checkpoint(State.BODY);
            case BODY:
                if (header.sign() == HEARTBEAT) {
                    break;
                }
                int bodyLength = checkBodyLength(header.bodyLength());
                byte[] bytes = new byte[bodyLength];
                in.readBytes(bytes);
                if (PastorProtocol.COMPRESS == header.compress()) {
                    //解压
                    bytes = Snappy.uncompress(bytes);
                }
                RemotingTransporter transporter = RemotingTransporter.newInstance(header.id(), header.sign(), header.type(), bytes);
                out.add(transporter);
                break;
            default:
                break;
        }
        checkpoint(State.HEADED_MAGIC);

    }

    private int checkBodyLength(int length) throws RemotingContextException {
        if (length > MAX_BODY_LENGTH) {
            throw new RemotingContextException("body of request is bigger than limit value " + MAX_BODY_LENGTH);
        }
        return length;
    }

    private void checkMagic(Short magic) throws RemotingContextException {
        if (PastorProtocol.MAGIC != magic) {
            log.error("Magic is not math");
            throw new RemotingContextException("magic value is not equal " + PastorProtocol.MAGIC);
        }
    }

    enum State {
        HEADED_MAGIC,
        HEADED_TYPE,
        HEADED_SIGN,
        HEADED_ID,
        HEADED_BODY_LENGTH,
        HEADED_COMPRESS,
        BODY
    }
}
