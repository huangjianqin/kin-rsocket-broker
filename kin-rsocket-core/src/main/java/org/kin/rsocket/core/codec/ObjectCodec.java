package org.kin.rsocket.core.codec;

import io.netty.buffer.ByteBuf;
import org.kin.framework.utils.SPI;
import org.kin.rsocket.core.RSocketMimeType;

/**
 * @author huangjianqin
 * @date 2021/3/26
 */
@SPI(alias = "codec")
public interface ObjectCodec {
    /** 默认buffer size */
    int DEFAULT_BUFFER_SIZE = 256;

    /**
     * rsocket mime type
     *
     * @return rsocket mime type
     */
    RSocketMimeType mimeType();

    /**
     * 对服务接口参数进行编码
     */
    ByteBuf encodeParams(Object[] args) throws ObjectCodecException;

    /**
     * 对服务接口参数进行解码
     */
    Object decodeParams(ByteBuf data, Class<?>... targetClasses) throws ObjectCodecException;

    /**
     * 对服务接口返回值进行编码
     */
    ByteBuf encodeResult(Object result) throws ObjectCodecException;

    /**
     * 对服务接口返回值进行解码
     */
    Object decodeResult(ByteBuf data, Class<?> targetClass) throws ObjectCodecException;
}
