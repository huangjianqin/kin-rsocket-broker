package org.kin.rsocket.core.utils;

/**
 * @author huangjianqin
 * @date 2021/3/27
 */
public interface Schemas {
    /** local, unit test */
    String LOCAL = "local";
    /** tcp */
    String TCP = "tcp";
    /** secure tcp */
    String TCPS = "tcps";
    /** tcp+tls */
    String TPC_TLS = "tcp+tls";
    /** tls */
    String TLS = "tls";
    /** ws */
    String WS = "ws";
    /** secure ws */
    String WSS = "wss";
    /** http */
    String HTTP = "http";
    /** secure http */
    String HTTPS = "https";
}
