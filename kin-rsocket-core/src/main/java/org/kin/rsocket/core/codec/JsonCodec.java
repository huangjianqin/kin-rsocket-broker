package org.kin.rsocket.core.codec;

import org.kin.kinrpc.serialization.json.JsonSerialization;
import org.kin.rsocket.core.metadata.RSocketMimeType;

/**
 * @author huangjianqin
 * @date 2021/3/26
 */
public class JsonCodec extends AbstractSerializationCodec {
    public JsonCodec() {
        super(new JsonSerialization());
    }

    @Override
    public RSocketMimeType mimeType() {
        return RSocketMimeType.Json;
    }
}
