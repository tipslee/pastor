package org.lee.remoting.model;

/**
 * Created by liqiang on 2017/12/20.
 * 网络传输对象字节
 */
public class ByteHolder {
    private transient byte[] bytes;

    public byte[] getBytes() {
        return bytes;
    }

    public void setBytes(byte[] bytes) {
        this.bytes = bytes;
    }

    public int holderLength() {
        return bytes == null ? 0 : bytes.length;
    }
}
