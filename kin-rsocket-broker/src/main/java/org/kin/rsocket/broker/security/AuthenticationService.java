package org.kin.rsocket.broker.security;

/**
 * @author huangjianqin
 * @date 2021/3/30
 */
public interface AuthenticationService {
    /**
     * todo
     */
    RSocketAppPrincipal auth(String type, String credentials);

    String generateCredentials(String id, String[] organizations, String[] serviceAccounts, String[] roles, String[] authorities, String sub, String[] audience) throws Exception;
}

