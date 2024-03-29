package org.kin.rsocket.auth;

/**
 * @author huangjianqin
 * @date 2021/3/30
 */
public interface AuthenticationService {
    /**
     * 证书校验及缓存证书
     */
    RSocketAppPrincipal auth(String credentials);

    /**
     * 生成证书
     * 对admin提供生成校验证书接口
     */
    default String generateCredentials(CredentialParam param) {
        //默认不要求实现
        throw new UnsupportedOperationException();
    }
}

