package org.kin.rsocket.core.codec;

import org.kin.kinrpc.serialization.hessian2.Hessian2Serialization;
import org.kin.rsocket.core.metadata.RSocketMimeType;

/**
 * @author huangjianqin
 * @date 2021/3/26
 */
public class HessianCodec extends AbstractSerializationCodec {
    protected HessianCodec() {
        super(new Hessian2Serialization());
    }

    @Override
    public RSocketMimeType mimeType() {
        return RSocketMimeType.Hessian;
    }
}
