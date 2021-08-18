package org.kin.rsocket.core;

/**
 * @author huangjianqin
 * @date 2021/3/27
 */
public class NoAvailableConnectionException extends RuntimeException {
    private static final long serialVersionUID = 1527165343683433441L;

    public NoAvailableConnectionException(String serviceId) {
        super(String.format("Upstream RSocket not found for '%s'", serviceId));
    }
}