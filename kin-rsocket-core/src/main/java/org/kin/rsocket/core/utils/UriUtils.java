package org.kin.rsocket.core.utils;

import io.netty.handler.codec.http.QueryStringDecoder;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author huangjianqin
 * @date 2021/4/21
 */
public final class UriUtils {
    /** uri中app uuid param key */
    private static final String UUID = "uuid";
    /** uri中app name param key */
    private static final String APP_NAME = "appName";

    private UriUtils() {
    }

    /**
     * 根据URI返回对应的app uuid
     */
    public static String getAppUUID(URI uri) {
        return parseUriParams(uri).getOrDefault(UUID, "");
    }

    /**
     * 解析URI参数
     */
    public static Map<String, String> parseUriParams(URI uri) {
        Map<String, String> params = new HashMap<>(4);
        QueryStringDecoder decoder = new QueryStringDecoder(uri);
        for (Map.Entry<String, List<String>> entry : decoder.parameters().entrySet()) {
            //entry.getValue()是一个List, 只取第一个元素
            params.put(entry.getKey(), entry.getValue().get(0));
        }
        return params;
    }
}
