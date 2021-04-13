package org.kin.rsocket.core.codec;

import org.kin.kinrpc.serialization.avro.AvroSerialization;
import org.kin.rsocket.core.metadata.RSocketMimeType;

/**
 * @author huangjianqin
 * @date 2021/3/26
 */
public class AvroCodec extends AbstractSerializationCodec {
    public AvroCodec() {
        super(new AvroSerialization());
    }

    @Override
    public RSocketMimeType mimeType() {
        return RSocketMimeType.Avro;
    }
}
