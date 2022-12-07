package org.kin.rsocket.core.utils;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.type.TypeFactory;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.format.EventFormat;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.jackson.JsonFormat;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.util.ReferenceCountUtil;
import org.checkerframework.checker.units.qual.C;
import org.kin.framework.utils.ExceptionUtils;
import org.kin.rsocket.core.codec.Codec;
import org.kin.rsocket.core.codec.CodecException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * @author huangjianqin
 * @date 2019-12-28
 */
public class JSON {
    /** 负责cloud event <-> json之间的转换 */
    private static final EventFormat EVENT_FORMAT = EventFormatProvider.getInstance().resolveFormat(JsonFormat.CONTENT_TYPE);
    private static final ObjectMapper PARSER = new ObjectMapper();
    private static final TypeReference<List<JsonNode>> TYPE_JSON_NODE_LIST = new TypeReference<List<JsonNode>>() {
    };

    static {
        PARSER.setTypeFactory(TypeFactory.defaultInstance());
        PARSER.findAndRegisterModules();
        //允许json中含有指定对象未包含的字段
        PARSER.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        //允许序列化空对象
        PARSER.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        //不序列化默认值, 0,false,[],{}等等, 减少json长度
        PARSER.setDefaultPropertyInclusion(JsonInclude.Include.NON_DEFAULT);
        //只认field, 那些get set is开头的方法不生成字段
        PARSER.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        PARSER.setVisibility(PropertyAccessor.SETTER, JsonAutoDetect.Visibility.NONE);
        PARSER.setVisibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.NONE);
        PARSER.setVisibility(PropertyAccessor.IS_GETTER, JsonAutoDetect.Visibility.NONE);
    }

    private JSON() {
    }

    /**
     * 序列化
     *
     * @param obj 序列化实例
     * @return json字符串
     */
    public static String write(Object obj) {
        try {
            return PARSER.writeValueAsString(obj);
        } catch (IOException e) {
            ExceptionUtils.throwExt(new CodecException(e.getMessage()));
        }

        throw new IllegalStateException("encounter unknown error");
    }

    /**
     * 序列化
     *
     * @param obj 序列化实例
     * @return bytes
     */
    public static byte[] writeBytes(Object obj) {
        try {
            return PARSER.writeValueAsBytes(obj);
        } catch (IOException e) {
            ExceptionUtils.throwExt(new CodecException(e.getMessage()));
        }

        throw new IllegalStateException("encounter unknown error");
    }

    /**
     * 解析json
     *
     * @param jsonStr json字符串
     * @param clazz   类型
     */
    public static <T> T read(String jsonStr, Class<T> clazz) {
        try {
            return PARSER.readValue(jsonStr, clazz);
        } catch (JsonProcessingException e) {
            ExceptionUtils.throwExt(new CodecException(e.getMessage()));
        }

        throw new IllegalStateException("encounter unknown error");
    }

    /**
     * 解析json
     *
     * @param bytes json bytes
     * @param clazz 反序列化类型
     */
    public static <T> T read(byte[] bytes, Class<T> clazz) {
        try {
            return PARSER.readValue(bytes, clazz);
        } catch (IOException e) {
            ExceptionUtils.throwExt(new CodecException(e.getMessage()));
        }

        throw new IllegalStateException("encounter unknown error");
    }

    /**
     * 解析含范型参数类型的json
     *
     * @param jsonStr          json字符串
     * @param parametrized     反序列化类
     * @param parameterClasses 范型参数类型
     * @param <T>              类型参数
     */
    public static <T> T read(String jsonStr, Class<T> parametrized, Class<?>... parameterClasses) {
        try {
            JavaType javaType = PARSER.getTypeFactory().constructParametricType(parametrized, parameterClasses);
            return PARSER.readValue(jsonStr, javaType);
        } catch (IOException e) {
            ExceptionUtils.throwExt(new CodecException(e.getMessage()));
        }

        throw new IllegalStateException("encounter unknown error");
    }

    /**
     * 解析含范型参数类型的json
     *
     * @param jsonStr       json字符串
     * @param typeReference 类型
     */
    public static <T> T read(String jsonStr, TypeReference<T> typeReference) {
        try {
            return PARSER.readValue(jsonStr, typeReference);
        } catch (IOException e) {
            ExceptionUtils.throwExt(new CodecException(e.getMessage()));
        }

        throw new IllegalStateException("encounter unknown error");
    }

    /**
     * 解析list json
     *
     * @param jsonStr   json字符串
     * @param itemClass 元素类型
     */
    @SuppressWarnings("unchecked")
    public static <T> List<T> readList(String jsonStr, Class<T> itemClass) {
        return readCollection(jsonStr, ArrayList.class, itemClass);
    }

    /**
     * 解析set json
     *
     * @param jsonStr   json字符串
     * @param itemClass 元素类型
     */
    @SuppressWarnings("unchecked")
    public static <T> Set<T> readSet(String jsonStr, Class<T> itemClass) {
        return readCollection(jsonStr, HashSet.class, itemClass);
    }

    /**
     * 解析集合类json
     *
     * @param jsonStr         json字符串
     * @param collectionClass 集合类型
     * @param itemClass       元素类型
     */
    public static <C extends Collection<T>, T> C readCollection(String jsonStr, Class<C> collectionClass, Class<T> itemClass) {
        JavaType collectionType = PARSER.getTypeFactory().constructCollectionLikeType(collectionClass, itemClass);
        try {
            return PARSER.readValue(jsonStr, collectionType);
        } catch (JsonProcessingException e) {
            ExceptionUtils.throwExt(new CodecException(e.getMessage()));
        }

        throw new IllegalStateException("encounter unknown error");
    }

    /**
     * 解析map json
     *
     * @param jsonStr    json字符串
     * @param keyClass   key类型
     * @param valueClass value类型
     */
    public static <K, V> C readMap(String jsonStr, Class<K> keyClass, Class<V> valueClass) {
        JavaType mapType = PARSER.getTypeFactory().constructMapType(HashMap.class, keyClass, valueClass);
        try {
            return PARSER.readValue(jsonStr, mapType);
        } catch (JsonProcessingException e) {
            ExceptionUtils.throwExt(new CodecException(e.getMessage()));
        }

        throw new IllegalStateException("encounter unknown error");
    }

    /**
     * 将json形式的map数据转换成对象
     */
    public static <C> C convert(Object jsonObj, Class<? extends C> targetClass) {
        return PARSER.convertValue(jsonObj, targetClass);
    }

    /**
     * 从json {@link ByteBuf} 转换成Obj
     */
    public static <T> T read(ByteBuf byteBuf, Class<T> type) {
        try {
            return PARSER.readValue((InputStream) new ByteBufInputStream(byteBuf), type);
        } catch (IOException e) {
            ExceptionUtils.throwExt(new CodecException(e.getMessage()));
        }
        throw new IllegalStateException("encounter unknown error");
    }

    /**
     * 将Obj通过json序列化成{@link ByteBuf}
     */
    public static ByteBuf writeByteBuf(Object object) {
        ByteBuf byteBuf = PooledByteBufAllocator.DEFAULT.buffer(Codec.DEFAULT_BUFFER_SIZE);
        try {
            ByteBufOutputStream bos = new ByteBufOutputStream(byteBuf);
            PARSER.writeValue((OutputStream) bos, object);
            return byteBuf;
        } catch (Exception e) {
            ReferenceCountUtil.safeRelease(byteBuf);
            ExceptionUtils.throwExt(new CodecException(e.getMessage()));
        }
        throw new IllegalStateException("encounter unknown error");
    }

    /**
     * 从json {@link ByteBuf} 字段数据更新现有Obj实例中的字段值
     */
    public static void updateFieldValue(ByteBuf byteBuf, Object object) {
        try {
            PARSER.readerForUpdating(object).readValue((InputStream) new ByteBufInputStream(byteBuf));
        } catch (IOException e) {
            ExceptionUtils.throwExt(new CodecException(e.getMessage()));
        }
    }

    /**
     * 从json字符串字段数据更新现有Obj实例中的字段值
     */
    public static void updateFieldValue(String text, Object object) {
        try {
            PARSER.readerForUpdating(object).readValue(text);
        } catch (JsonProcessingException e) {
            ExceptionUtils.throwExt(new CodecException(e.getMessage()));
        }
    }

    /**
     * 按数组形式读取json, 然后再根据指定class反序列每个item
     */
    public static Object[] readJsonArray(ByteBuf byteBuf, Class<?>[] targetClasses) throws IOException {
        Object[] targets = new Object[targetClasses.length];
        List<JsonNode> jsonNodes = PARSER.readValue(new ByteBufInputStream(byteBuf), TYPE_JSON_NODE_LIST);
        for (int i = 0; i < targetClasses.length; i++) {
            targets[i] = PARSER.treeToValue(jsonNodes.get(i), targetClasses[i]);
        }
        return targets;
    }

    //-----------------------------------------------------------------------cloud event

    /**
     * 将cloud event序列化成json
     *
     * @param cloudEvent cloud event
     * @return cloud event json bytes
     */
    @SuppressWarnings("ConstantConditions")
    public static byte[] serializeCloudEvent(CloudEvent cloudEvent) {
        return EVENT_FORMAT.serialize(cloudEvent);
    }

    /**
     * 从json中反序列化出cloud event
     *
     * @param bytes cloud event json
     * @return cloud event
     */
    @SuppressWarnings("ConstantConditions")
    public static CloudEvent deserializeCloudEvent(String json) {
        return EVENT_FORMAT.deserialize(json.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 从json bytes中反序列化出cloud event
     *
     * @param bytes cloud event json bytes
     * @return cloud event
     */
    @SuppressWarnings("ConstantConditions")
    public static CloudEvent deserializeCloudEvent(byte[] bytes) {
        return EVENT_FORMAT.deserialize(bytes);
    }
}
