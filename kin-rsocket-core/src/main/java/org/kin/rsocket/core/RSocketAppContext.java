package org.kin.rsocket.core;

import org.kin.framework.utils.KinServiceLoader;

import java.util.UUID;

/**
 * @author huangjianqin
 * @date 2021/3/23
 */
public class RSocketAppContext {
    /** spi loader */
    public static final KinServiceLoader LOADER = KinServiceLoader.load();
    /** app uuid */
    public static final String ID = UUID.randomUUID().toString();
}
