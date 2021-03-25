package org.kin.rsocket.core.codec;

/**
 * @author huangjianqin
 * @date 2021/3/25
 */
public class CodecException extends RuntimeException {
    public CodecException(String message) {
        super(message);
    }

    public CodecException(String message, Throwable cause) {
        super(message, cause);
    }
}
