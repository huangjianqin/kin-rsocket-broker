package org.kin.rsocket.core;

/**
 * @author huangjianqin
 * @date 2021/3/27
 */
public class NoAvailableConnectionException extends Exception {
    public NoAvailableConnectionException(String serviceId) {
        super(String.format("Upstream RSocket not found for '%s'", serviceId));
    }
}