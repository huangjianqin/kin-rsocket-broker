package org.kin.rsocket.core;

import org.kin.rsocket.core.utils.MurmurHash3;

import java.util.Objects;

/**
 * 服务定位: group, service full name, version
 *
 * @author huangjianqin
 * @date 2021/2/15
 */
public class ServiceLocator {
    public static final String SEPARATOR = "!";

    /** service分组 */
    private final String group;
    /** service名 */
    private final String service;
    /** service版本 */
    private final String version;

    /** 服务唯一标识(str) */
    private final String gsv;
    /** 服务唯一标识(整型) */
    private final Integer id;

    //----------------------------------------------------------------------------------------------------------------

    /**
     * @return gsv标识
     */
    public static String gsv(String group, String service, String version) {
        StringBuilder sb = new StringBuilder();
        //group
        if (group != null && !group.isEmpty()) {
            sb.append(group).append(SEPARATOR);
        }
        //service
        sb.append(service);
        //version
        if (version != null && !version.isEmpty()) {
            sb.append(SEPARATOR).append(version);
        }
        return sb.toString();
    }

    /**
     * @return 服务唯一标识
     */
    public static Integer serviceHashCode(String routingKey) {
        return MurmurHash3.hash32(routingKey);
    }
    //----------------------------------------------------------------------------------------------------------------

    public ServiceLocator(String group, String service, String version) {
        this.group = group;
        this.service = service;
        this.version = version;
        this.gsv = gsv(group, service, version);
        this.id = MurmurHash3.hash32(this.gsv);
    }

    //setter && getter
    public String getGroup() {
        return group;
    }

    public String getService() {
        return service;
    }

    public String getVersion() {
        return version;
    }

    public String getGsv() {
        return this.gsv;
    }

    public Integer getId() {
        return this.id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServiceLocator that = (ServiceLocator) o;
        return group.equals(that.group) && service.equals(that.service) && version.equals(that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(group, service, version);
    }
}
