package org.kin.rsocket.core.codec;

import org.kin.kinrpc.serialization.protobuf.ProtobufSerialization;
import org.kin.rsocket.core.metadata.RSocketMimeType;

/**
 * @author huangjianqin
 * @date 2021/3/26
 */
public class ProtobufCodec extends AbstractSerializationCodec {
    public ProtobufCodec() {
        super(new ProtobufSerialization());
    }

    @Override
    public RSocketMimeType mimeType() {
        return RSocketMimeType.Protobuf;
    }
}
