package org.kin.rsocket.gateway.http.converter;

import io.netty.buffer.ByteBuf;
import org.reactivestreams.Publisher;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.AbstractEncoder;
import org.springframework.core.codec.Hints;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.NettyDataBufferFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import reactor.core.publisher.Flux;

import javax.annotation.Nonnull;
import java.util.Map;

/**
 * netty ByteBuf -> spring DataBuffer
 *
 * @author huangjianqin
 * @date 2021/4/20
 */
public class ByteBufEncoder extends AbstractEncoder<ByteBuf> {
    public ByteBufEncoder() {
        super(MimeTypeUtils.ALL);
    }

    @Override
    public boolean canEncode(ResolvableType elementType, MimeType mimeType) {
        Class<?> clazz = elementType.toClass();
        return super.canEncode(elementType, mimeType) && ByteBuf.class.isAssignableFrom(clazz);
    }

    @Nonnull
    @Override
    public Flux<DataBuffer> encode(@Nonnull Publisher<? extends ByteBuf> inputStream,
                                   @Nonnull DataBufferFactory bufferFactory,
                                   @Nonnull ResolvableType elementType,
                                   MimeType mimeType,
                                   Map<String, Object> hints) {
        return Flux.from(inputStream).map((byteBuffer) -> this.encodeValue(byteBuffer, bufferFactory, elementType, mimeType, hints));
    }

    @Nonnull
    @Override
    public DataBuffer encodeValue(@Nonnull ByteBuf byteBuf,
                                  @Nonnull DataBufferFactory bufferFactory,
                                  @Nonnull ResolvableType valueType,
                                  @Nullable MimeType mimeType,
                                  @Nullable Map<String, Object> hints) {
        DataBuffer dataBuffer = ((NettyDataBufferFactory) bufferFactory).wrap(byteBuf);
        if (this.logger.isDebugEnabled() && !Hints.isLoggingSuppressed(hints)) {
            String logPrefix = Hints.getLogPrefix(hints);
            this.logger.debug(logPrefix + "Writing " + dataBuffer.readableByteCount() + " bytes");
        }
        return dataBuffer;
    }
}
