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
     */
    default String generateCredentials(String id,
                                       String[] organizations,
                                       String[] serviceAccounts,
                                       String[] roles,
                                       String[] authorities,
                                       String sub,
                                       String[] audience) throws Exception {
        //默认不要求实现
        throw new UnsupportedOperationException();
    }
}

