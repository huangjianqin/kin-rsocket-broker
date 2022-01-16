package org.kin.rsocket.core.codec;

import org.kin.rsocket.core.RSocketMimeType;
import org.kin.serialization.kryo.KryoSerialization;

/**
 * @author huangjianqin
 * @date 2021/3/26
 */
public class KryoCodec extends AbstractSerializationCodec {
    public KryoCodec() {
        super(new KryoSerialization());
    }

    @Override
    public RSocketMimeType mimeType() {
        return RSocketMimeType.JAVA_OBJECT;
    }
}
