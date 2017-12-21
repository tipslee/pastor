package org.lee.common.spi;

import java.util.ServiceLoader;

/**
 * @author liqiang
 * @description SPI loader
 * @time 2017年12月20日
 * @modifytime
 */
public final class BaseServiceLoader {

    public static <S> S load(Class<S> s) {
        return ServiceLoader.load(s).iterator().next();
    }
}
