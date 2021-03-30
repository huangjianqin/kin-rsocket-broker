package org.kin.rsocket.broker.security;

import java.security.Principal;
import java.util.List;
import java.util.Set;

/**
 * 接入认证信息
 *
 * @author huangjianqin
 * @date 2021/3/30
 */
public interface RSocketAppPrincipal extends Principal {
    /**
     * get token id
     *
     * @return token id
     */
    String getTokenId();

    /**
     * todo
     *
     * @return
     */
    String getSubject();

    /**
     * @return
     */
    List<String> getAudience();

    /**
     * @return 角色s
     */
    Set<String> getRoles();

    /**
     * @return
     */
    Set<String> getAuthorities();

    /**
     * @return accounts
     */
    Set<String> getServiceAccounts();

    /**
     * @return organizations
     */
    Set<String> getOrganizations();

}
