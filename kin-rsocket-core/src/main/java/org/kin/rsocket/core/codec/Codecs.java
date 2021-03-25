package org.kin.rsocket.core.codec;

import com.google.common.collect.ImmutableMap;
import io.netty.buffer.ByteBuf;
import org.kin.kinrpc.serialization.Serialization;
import org.kin.kinrpc.serialization.avro.AvroSerialization;
import org.kin.kinrpc.serialization.hessian2.Hessian2Serialization;
import org.kin.kinrpc.serialization.json.JsonSerialization;
import org.kin.kinrpc.serialization.kryo.KryoSerialization;
import org.kin.kinrpc.serialization.protobuf.ProtobufSerialization;
import org.kin.rsocket.core.metadata.RSocketMimeType;

import java.util.Map;

/**
 * rsocket mime type codec
 *
 * @author huangjianqin
 * @date 2021/3/25
 */
public class Codecs {
    private static final Map<RSocketMimeType, Serialization> MIME_TYPE_2_SERIALIZATION;

    static {
        ImmutableMap.Builder<RSocketMimeType, Serialization> builder = ImmutableMap.builder();

        builder.put(RSocketMimeType.Json, new JsonSerialization());
        builder.put(RSocketMimeType.Protobuf, new ProtobufSerialization());
        builder.put(RSocketMimeType.Avro, new AvroSerialization());
        builder.put(RSocketMimeType.Hessian, new Hessian2Serialization());
        builder.put(RSocketMimeType.Text, new TextSerialization());
        builder.put(RSocketMimeType.Binary, new BinarySerialization());
        builder.put(RSocketMimeType.Java_Object, new KryoSerialization());

        //todo CBOR暂未实现

        MIME_TYPE_2_SERIALIZATION = builder.build();
    }

    public ByteBuf encodingParams(Object[] args, RSocketMimeType encodingType) throws CodecException {
        //todo
        return null;
    }

    public Object decodeParams(RSocketMimeType encodingType, ByteBuf data, Class<?>... targetClasses) throws CodecException {
        //todo
        return null;
    }

    public ByteBuf encodingResult(Object result, RSocketMimeType encodingType) throws CodecException {
        //todo
        return null;
    }

    public Object decodeResult(RSocketMimeType encodingType, ByteBuf data, Class<?> targetClass) throws CodecException {
        //todo
        return null;
    }

}
