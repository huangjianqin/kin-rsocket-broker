package org.kin.rsocket.core.codec;

/**
 * @author huangjianqin
 * @date 2021/3/25
 */
public class ObjectCodecException extends RuntimeException {
    private static final long serialVersionUID = -6340553045660876630L;

    public ObjectCodecException(String message) {
        super(message);
    }

    public ObjectCodecException(String message, Throwable cause) {
        super(message, cause);
    }
}
