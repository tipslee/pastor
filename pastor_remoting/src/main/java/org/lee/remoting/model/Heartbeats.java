package org.lee.remoting.model;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.lee.common.protocal.PastorProtocol;

import static org.lee.common.protocal.PastorProtocol.HEAD_LENGTH;
import static org.lee.common.protocal.PastorProtocol.HEARTBEAT;
import static org.lee.common.protocal.PastorProtocol.MAGIC;

/**
 * 
 * @author BazingaLyn
 * @description
 * @time
 * @modifytime
 */
@SuppressWarnings("deprecation")
public class Heartbeats {

    private static final ByteBuf HEARTBEAT_BUF;
    
    static {
        ByteBuf buf = Unpooled.buffer(HEAD_LENGTH);
        buf.writeShort(MAGIC);
        buf.writeByte(0);
        buf.writeByte(HEARTBEAT);
        buf.writeLong(0);
        buf.writeInt(0);
        buf.writeByte(PastorProtocol.UNCOMPRESS);
        HEARTBEAT_BUF = Unpooled.unmodifiableBuffer(Unpooled.unreleasableBuffer(buf));
    }

    /**
     * Returns the shared heartbeat content.
     */
    public static ByteBuf heartbeatContent() {
        return HEARTBEAT_BUF.duplicate();
    }
}
