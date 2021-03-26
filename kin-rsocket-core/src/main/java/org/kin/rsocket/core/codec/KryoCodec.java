package org.kin.rsocket.core.codec;

import org.kin.kinrpc.serialization.kryo.KryoSerialization;
import org.kin.rsocket.core.metadata.RSocketMimeType;

/**
 * @author huangjianqin
 * @date 2021/3/26
 */
public class KryoCodec extends AbstractSerializationCodec {
    protected KryoCodec() {
        super(new KryoSerialization());
    }

    @Override
    public RSocketMimeType mimeType() {
        return RSocketMimeType.Java_Object;
    }
}
