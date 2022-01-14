package org.kin.rsocket.springcloud.gateway.grpc;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCountUtil;
import io.rsocket.Payload;
import org.kin.framework.utils.ExceptionUtils;
import org.kin.rsocket.core.RSocketMimeType;
import org.kin.rsocket.core.metadata.MessageMimeTypeMetadata;
import org.kin.rsocket.core.metadata.RSocketCompositeMetadata;

import javax.annotation.Nullable;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

/**
 * @author huangjianqin
 * @date 2022/1/9
 */
final class PayloadUtils {
    /** 带有protobuf编码的{@link RSocketCompositeMetadata} {@link ByteBuf}内容 */
    private static final ByteBuf COMPOSITE_METADATA_WITH_ENCODING;

    static {
        ByteBuf byteBuf = RSocketCompositeMetadata.of(MessageMimeTypeMetadata.of(RSocketMimeType.PROTOBUF)).getContent();
        COMPOSITE_METADATA_WITH_ENCODING = Unpooled.copiedBuffer(byteBuf);
        ReferenceCountUtil.release(byteBuf);
    }

    /** {@link io.grpc.binarylog.v1.Message#parseFrom(byte[])} lambda代理 */
    private static final Cache<Class<?>, Function<ByteBuffer, Object>> PARSE_FROM_METHOD_CACHE = CacheBuilder.newBuilder().build();

    /**
     * 返回带有protobuf编码的CompositeMetadata bytebuf内容
     */
    static ByteBuf getCompositeMetaDataWithEncoding() {
        return COMPOSITE_METADATA_WITH_ENCODING;
    }

    /**
     * 根据返回值类型{@code responseClass}将{@code payload}反序列化为java实例
     */
    @SuppressWarnings("unchecked")
    @Nullable
    static <T> T payloadToResponseObject(Payload payload, Class<T> responseClass) {
        Function<ByteBuffer, Object> parseFrom = null;
        try {
            parseFrom = PARSE_FROM_METHOD_CACHE.get(responseClass, () -> {
                Method method = responseClass.getMethod("parseFrom", ByteBuffer.class);
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                MethodHandle methodHandle = lookup.unreflect(method);
                MethodType methodType = methodHandle.type();
                try {
                    return (Function<ByteBuffer, Object>) LambdaMetafactory.metafactory(lookup, "apply",
                                    MethodType.methodType(Function.class), methodType.generic(), methodHandle, methodType)
                            .getTarget()
                            .invoke();
                } catch (Throwable e) {
                    ExceptionUtils.throwExt(e);
                }
                return null;
            });
        } catch (ExecutionException e) {
            ExceptionUtils.throwExt(e);
        }

        if (Objects.isNull(parseFrom)) {
            return null;
        }

        return (T) parseFrom.apply(payload.data().nioBuffer());
    }

    private PayloadUtils() {
    }
}
