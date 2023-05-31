package org.kin.rsocket.core.codec;

import org.kin.rsocket.core.RSocketMimeType;
import org.kin.serialization.avro.AvroSerialization;

/**
 * @author huangjianqin
 * @date 2021/3/26
 */
public class AvroObjectCodec extends AbstractSerializationObjectCodec {
    public AvroObjectCodec() {
        super(new AvroSerialization());
    }

    @Override
    public RSocketMimeType mimeType() {
        return RSocketMimeType.AVRO;
    }
}
