package org.kin.rsocket.core;

/**
 * @author huangjianqin
 * @date 2021/3/28
 */
public class ReactiveMethodInvokeException extends Exception {
    private static final long serialVersionUID = 3528994034839059835L;

    public ReactiveMethodInvokeException(String message) {
        super(message);
    }

    public ReactiveMethodInvokeException(String message, Throwable cause) {
        super(message, cause);
    }

    public ReactiveMethodInvokeException(Throwable cause) {
        super(cause);
    }
}
