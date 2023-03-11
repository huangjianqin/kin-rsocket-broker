package org.kin.rsocket.core.codec;

import org.kin.rsocket.core.RSocketMimeType;
import org.kin.serialization.protobuf.ProtobufSerialization;

/**
 * @author huangjianqin
 * @date 2021/3/26
 */
public class ProtobufObjectCodec extends AbstractSerializationObjectCodec {
    public ProtobufObjectCodec() {
        super(new ProtobufSerialization());
    }

    @Override
    public RSocketMimeType mimeType() {
        return RSocketMimeType.PROTOBUF;
    }
}
