package org.kin.rsocket.core.metadata;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.rsocket.metadata.RoutingMetadata;
import io.rsocket.metadata.TaggingMetadataCodec;
import org.kin.rsocket.core.ServiceLocator;
import org.kin.rsocket.core.utils.MurmurHash3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * GSV routing metadata, format as tagging routing data
 * todo 可以考虑压缩字符串, 减少网络传输, 但是得考虑接收方是否能将数据解码成可读, 并方便打日志/debug
 *
 * @author huangjianqin
 * @date 2021/3/24
 */
public class GSVRoutingMetadata implements MetadataAware {
    /** group service version与tags的分隔符 */
    private static final String SEPARATOR = "?";
    /** group 分隔符 */
    private static final String GROUP_SYMBOL = "!";
    /** version 分隔符 */
    private static final String VERSION_SYMBOL = ":";
    /** method 分隔符 */
    private static final String METHOD_SYMBOL = ".";
    /** tags 分隔符 */
    private static final String TAGS_SYMBOL = "&";


    /** group: region, datacenter, virtual group in datacenter */
    private String group;
    /** service name */
    private String service;
    /** method name */
    private String method;
    /** version */
    private String version;
    /** endpoint */
    private String endpoint;
    /** sticky session */
    private boolean sticky;
    /** target instance ID */
    private transient Integer targetId;

    public static GSVRoutingMetadata of(String group, String service, String method, String version) {
        GSVRoutingMetadata metadata = new GSVRoutingMetadata();
        metadata.group = group;
        metadata.service = service;
        metadata.method = method;
        metadata.version = version;
        return metadata;
    }

    /**
     * @param serviceMethodKey service.method
     */
    public static GSVRoutingMetadata of(String group, String serviceMethodKey, String version) {
        String service = "";
        String method = "";
        int methodSymbolPosition = serviceMethodKey.lastIndexOf(METHOD_SYMBOL);
        if (methodSymbolPosition > 0) {
            service = serviceMethodKey.substring(0, methodSymbolPosition);
            method = serviceMethodKey.substring(methodSymbolPosition + 1);
        } else {
            service = serviceMethodKey;
        }
        return of(group, service, method, version);
    }

    public static GSVRoutingMetadata of(ByteBuf content) {
        GSVRoutingMetadata metadata = new GSVRoutingMetadata();
        metadata.load(content);
        return metadata;
    }

    public static GSVRoutingMetadata of(String routingKey) {
        GSVRoutingMetadata metadata = new GSVRoutingMetadata();
        metadata.parseRoutingKey(routingKey);
        return metadata;
    }

    @Override
    public RSocketMimeType mimeType() {
        return RSocketMimeType.Routing;
    }

    @Override
    public ByteBuf getContent() {
        List<String> tags = new ArrayList<>();
        tags.add(genRoutingKey());
        if (endpoint != null && !endpoint.isEmpty()) {
            tags.add("e=" + endpoint);
        }
        if (sticky) {
            tags.add("sticky=1");
        }
        return TaggingMetadataCodec.createTaggingContent(PooledByteBufAllocator.DEFAULT, tags);
    }

    @Override
    public void load(ByteBuf byteBuf) {
        Iterator<String> iterator = new RoutingMetadata(byteBuf).iterator();
        //first tag is routing for service name or method
        if (iterator.hasNext()) {
            parseRoutingKey(iterator.next());
        }
        while (iterator.hasNext()) {
            parseTags(iterator.next());
        }
    }

    /**
     * 解析route key
     * group!service.method:version?tags
     */
    private void parseRoutingKey(String routingKey) {
        String temp = routingKey;
        String tags = null;
        if (routingKey.contains(SEPARATOR)) {
            temp = routingKey.substring(0, routingKey.indexOf(SEPARATOR));
            tags = routingKey.substring(routingKey.indexOf(SEPARATOR) + 1);
        }
        //group parse
        int groupSymbolPosition = temp.indexOf(GROUP_SYMBOL);
        if (groupSymbolPosition > 0) {
            this.group = temp.substring(0, groupSymbolPosition);
            temp = temp.substring(groupSymbolPosition + 1);
        }
        //version
        int versionSymbolPosition = temp.lastIndexOf(VERSION_SYMBOL);
        if (versionSymbolPosition > 0) {
            this.version = temp.substring(versionSymbolPosition + 1);
            temp = temp.substring(0, versionSymbolPosition);
        }
        //service & method
        int methodSymbolPosition = temp.lastIndexOf(METHOD_SYMBOL);
        if (methodSymbolPosition > 0) {
            this.service = temp.substring(0, methodSymbolPosition);
            this.method = temp.substring(methodSymbolPosition + 1);
        } else {
            this.service = temp;
        }
        if (tags != null) {
            String[] tagParts = tags.split(TAGS_SYMBOL);
            for (String tagPart : tagParts) {
                parseTags(tagPart);
            }
        }
    }

    /**
     * 解析tag
     * e=&sticky=1
     */
    private void parseTags(String tag) {
        if (tag.startsWith("e=")) {
            this.endpoint = tag.substring(2);
        } else if (tag.equalsIgnoreCase("sticky=1")) {
            this.sticky = true;
        }
    }

    /**
     * 生成route key
     *
     * @return route key
     */
    public String genRoutingKey() {
        StringBuilder routingBuilder = new StringBuilder();
        //group
        if (group != null && !group.isEmpty()) {
            routingBuilder.append(group).append(GROUP_SYMBOL);
        }
        //service
        routingBuilder.append(service);
        //method
        if (method != null && !method.isEmpty()) {
            routingBuilder.append(METHOD_SYMBOL).append(method);
        }
        //version
        if (version != null && !version.isEmpty()) {
            routingBuilder.append(VERSION_SYMBOL).append(version);
        }
        if (this.sticky || this.endpoint != null) {
            routingBuilder.append(SEPARATOR);
            if (this.sticky) {
                routingBuilder.append("sticky=1").append(TAGS_SYMBOL);
            }
            if (this.endpoint != null) {
                routingBuilder.append("e=").append(endpoint).append(TAGS_SYMBOL);
            }
        }
        return routingBuilder.toString();
    }

    /**
     * 生成service id
     *
     * @return service id
     */
    public Integer id() {
        if (Objects.isNull(targetId)) {
            if (group == null && version == null) {
                targetId = MurmurHash3.hash32(service);
            } else {
                targetId = MurmurHash3.hash32(gsv());
            }
        }
        return targetId;
    }

    /**
     * 生成gsv标识
     *
     * @return gsv标识
     */
    public String gsv() {
        return ServiceLocator.gsv(group, service, version);
    }

    //setter && getter
    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public String getMethod() {
        return this.method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public int getTargetId() {
        return targetId;
    }

    public void setTargetId(int targetId) {
        this.targetId = targetId;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public boolean isSticky() {
        return sticky;
    }

    public void setSticky(boolean sticky) {
        this.sticky = sticky;
    }
}

