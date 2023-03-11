package org.kin.rsocket.core.codec;

import org.kin.rsocket.core.RSocketMimeType;
import org.kin.serialization.hessian2.Hessian2Serialization;

/**
 * @author huangjianqin
 * @date 2021/3/26
 */
public class HessianObjectCodec extends AbstractSerializationObjectCodec {
    public HessianObjectCodec() {
        super(new Hessian2Serialization());
    }

    @Override
    public RSocketMimeType mimeType() {
        return RSocketMimeType.HESSIAN;
    }
}
