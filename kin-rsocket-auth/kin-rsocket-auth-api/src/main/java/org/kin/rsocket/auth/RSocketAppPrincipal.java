package org.kin.rsocket.auth;

import java.security.Principal;
import java.util.*;

/**
 * 接入认证信息
 *
 * @author huangjianqin
 * @date 2021/3/30
 */
public interface RSocketAppPrincipal extends Principal {
    RSocketAppPrincipal DEFAULT = new RSocketAppPrincipal() {
        private final String tokenId = UUID.randomUUID().toString();

        @Override
        public String getTokenId() {
            return tokenId;
        }

        @Override
        public String getSubject() {
            return "MockApp";
        }

        @Override
        public List<String> getAudience() {
            return Collections.singletonList("mock_owner");
        }

        @Override
        public Set<String> getRoles() {
            return Collections.unmodifiableSet(new HashSet<>(Collections.singletonList("admin")));
        }

        @Override
        public Set<String> getAuthorities() {
            return Collections.emptySet();
        }

        @Override
        public Set<String> getServiceAccounts() {
            return Collections.unmodifiableSet(new HashSet<>(Collections.singletonList("default")));
        }

        @Override
        public Set<String> getOrganizations() {
            return Collections.unmodifiableSet(new HashSet<>(Collections.singletonList("default")));
        }

        @Override
        public String getName() {
            return getSubject();
        }
    };

    /**
     * get token id
     *
     * @return token id
     */
    String getTokenId();

    /**
     * application
     *
     * @return subject
     */
    String getSubject();

    /**
     * @return audience
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
