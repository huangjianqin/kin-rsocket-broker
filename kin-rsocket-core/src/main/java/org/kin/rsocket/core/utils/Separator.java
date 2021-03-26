package org.kin.rsocket.core.utils;

/**
 * 定义字符串组合分隔符常量
 *
 * @author huangjianqin
 * @date 2021/3/26
 */
public interface Separator {
    /** group与serviceName的分隔符 */
    String GROUP_SERVICE = "!";
    /** serviceName.method与version的分隔符 */
    String SERVICE_VERSION = ":";
    /** serviceName与method的分隔符 */
    String SERVICE_METHOD = ".";
    /** "group!serviceName:version"与"tags"的分隔符 */
    String SERVICE_DEF_TAGS = "?";
    /** tags item 分隔符 */
    String TAG = "&";
}
