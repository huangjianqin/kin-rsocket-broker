package org.kin.rsocket.auth;

/**
 * 不作任何校验
 *
 * @author huangjianqin
 * @date 2022/8/30
 */
public final class NoneAuthenticationService implements AuthenticationService {
    public static final AuthenticationService INSTANCE = new NoneAuthenticationService();

    private NoneAuthenticationService() {
    }

    @Override
    public RSocketAppPrincipal auth(String credentials) {
        return RSocketAppPrincipal.DEFAULT;
    }
}
