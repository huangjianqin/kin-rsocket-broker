package org.kin.rsocket.core.codec;

import io.netty.buffer.ByteBuf;
import org.kin.framework.utils.SPI;
import org.kin.rsocket.core.RSocketMimeType;

/**
 * @author huangjianqin
 * @date 2021/3/26
 */
@SPI
public interface Codec {
    /**
     * rsocket mime type
     *
     * @return rsocket mime type
     */
    RSocketMimeType mimeType();

    /**
     * 对服务接口参数进行编码
     */
    ByteBuf encodeParams(Object[] args) throws CodecException;

    /**
     * 对服务接口参数进行解码
     */
    Object decodeParams(ByteBuf data, Class<?>... targetClasses) throws CodecException;

    /**
     * 对服务接口返回值进行编码
     */
    ByteBuf encodeResult(Object result) throws CodecException;

    /**
     * 对服务接口返回值进行解码
     */
    Object decodeResult(ByteBuf data, Class<?> targetClass) throws CodecException;
}
