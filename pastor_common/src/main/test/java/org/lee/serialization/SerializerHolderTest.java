package org.lee.serialization;

import org.junit.Test;
import org.lee.common.serialization.Serializer;
import org.lee.common.serialization.SerializerHolder;

/**
 * @author liqiang
 * @description
 * @time 2017年12月21日
 * @modifytime
 */
public class SerializerHolderTest {

    @Test
    public void testGetSerialization() {
        Serializer serializer = SerializerHolder.serializerImpl();
        System.out.println(serializer);
    }
}
