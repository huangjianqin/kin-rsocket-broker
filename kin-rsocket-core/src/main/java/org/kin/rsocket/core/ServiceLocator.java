package org.kin.rsocket.core;

import org.kin.framework.utils.MurmurHash3;
import org.kin.rsocket.core.utils.Separators;

import java.util.Objects;

/**
 * 服务定位: group, service full name, version
 * <p>
 * gsv定义: group!service:version
 *
 * @author huangjianqin
 * @date 2021/2/15
 */
public class ServiceLocator {
    private static final String[] EMPTY_TAGS = new String[0];

    /** service分组 */
    private String group = "";
    /** service名 */
    private String service = "";
    /** service版本 */
    private String version = "";
    private String[] tags = EMPTY_TAGS;

    /** 服务唯一标识(str) */
    private String gsv;
    /** 服务唯一标识(整型) */
    private Integer id;

    //----------------------------------------------------------------------------------------------------------------

    /**
     * @return gsv标识
     */
    public static String gsv(String service) {
        return gsv("", service, "");
    }

    /**
     * @return gsv标识
     */
    public static String gsv(String group, String service, String version) {
        StringBuilder sb = new StringBuilder();
        //group
        if (group != null && !group.isEmpty()) {
            sb.append(group).append(Separators.GROUP_SERVICE);
        }
        //service
        sb.append(service);
        //version
        if (version != null && !version.isEmpty()) {
            sb.append(Separators.SERVICE_VERSION).append(version);
        }
        return sb.toString();
    }

    /**
     * @return 服务唯一标识
     */
    public static Integer serviceHashCode(String routingKey) {
        return MurmurHash3.hash32(routingKey);
    }

    public static ServiceLocator of(String service) {
        return of("", service, "", EMPTY_TAGS);
    }

    public static ServiceLocator of(String group, String service, String version) {
        return of(group, service, version, EMPTY_TAGS);
    }

    public static ServiceLocator of(String group, String service, String version, String[] tags) {
        ServiceLocator inst = new ServiceLocator();
        inst.group = group;
        inst.service = service;
        inst.version = version;
        inst.tags = tags;
        inst.gsv = gsv(group, service, version);
        inst.id = MurmurHash3.hash32(inst.gsv);
        return inst;
    }

    public static ServiceLocator parse(String gsv) {
        ServiceLocator serviceLocator = new ServiceLocator();

        serviceLocator.gsv = gsv;
        String temp = gsv;
        if (temp.contains(Separators.GROUP_SERVICE)) {
            int index = temp.indexOf(Separators.GROUP_SERVICE);
            serviceLocator.group = temp.substring(0, index);
            temp = temp.substring(index + 1);
        }
        if (temp.contains(Separators.SERVICE_VERSION)) {
            int index = temp.indexOf(Separators.SERVICE_VERSION);
            serviceLocator.version = temp.substring(index + 1);
            temp = temp.substring(0, index);
        }
        serviceLocator.service = temp;
        serviceLocator.id = MurmurHash3.hash32(gsv);
        return serviceLocator;
    }

    //----------------------------------------------------------------------------------------------------------------
    public boolean hasTag(String tag) {
        if (this.tags != null && this.tags.length > 0) {
            for (String s : tags) {
                if (s.equalsIgnoreCase(tag)) {
                    return true;
                }
            }
        }
        return false;
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

    public String[] getTags() {
        return tags;
    }

    public String getGsv() {
        return this.gsv;
    }

    public Integer getId() {
        return this.id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ServiceLocator that = (ServiceLocator) o;
        return group.equals(that.group) && service.equals(that.service) && version.equals(that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(group, service, version);
    }
}
