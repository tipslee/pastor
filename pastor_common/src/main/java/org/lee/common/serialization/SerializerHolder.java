package org.lee.common.serialization;

import org.lee.common.spi.BaseServiceLoader;

/**
 * @author liqiang
 * @description 序列化的入口,基于SPI方式
 * @time 2017年12月20日
 * @modifytime
 */
public final class SerializerHolder {
    // SPI
    private static final Serializer serializer = BaseServiceLoader.load(Serializer.class);

    public static Serializer serializerImpl() {
        return serializer;
    }

    public static void main(String[] args) {
        System.out.println(SerializerHolder.serializerImpl());
    }

}
