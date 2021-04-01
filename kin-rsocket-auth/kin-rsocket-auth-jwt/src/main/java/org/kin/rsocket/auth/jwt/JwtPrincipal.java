package org.kin.rsocket.auth.jwt;

import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.kin.rsocket.auth.RSocketAppPrincipal;

import javax.security.auth.Subject;
import java.util.*;

/**
 * principal with JWT backend
 *
 * @author huangjianqin
 * @date 2021/3/30
 */
public class JwtPrincipal implements RSocketAppPrincipal {
    /** token id */
    private String tokenId;
    /** todo */
    private String subject;
    private List<String> audience;
    /** 角色 */
    private Set<String> roles;
    private Set<String> authorities;
    /** accounts */
    private Set<String> serviceAccounts;
    /** organizations */
    private Set<String> organizations;

    public JwtPrincipal(DecodedJWT decodedJWT) {
        this.tokenId = decodedJWT.getClaim("id").asString();
        this.subject = decodedJWT.getSubject();
        this.audience = decodedJWT.getAudience();
        Map<String, Claim> claims = decodedJWT.getClaims();
        this.serviceAccounts = new HashSet<>(decodedJWT.getClaim("sas").asList(String.class));
        this.organizations = new HashSet<>(decodedJWT.getClaim("orgs").asList(String.class));
        if (claims.containsKey("roles")) {
            this.roles = new HashSet<>(decodedJWT.getClaim("roles").asList(String.class));
        }
        if (claims.containsKey("authorities")) {
            this.authorities = new HashSet<>(decodedJWT.getClaim("authorities").asList(String.class));
        }
    }

    public JwtPrincipal(String id, String subject,
                        List<String> audience, Set<String> roles,
                        Set<String> authorities, Set<String> serviceAccounts,
                        Set<String> organizations) {
        this.tokenId = id;
        this.subject = subject;
        this.audience = audience;
        this.roles = roles;
        this.authorities = authorities;
        this.serviceAccounts = serviceAccounts;
        this.organizations = organizations;
    }

    @Override
    public String getTokenId() {
        return this.tokenId;
    }

    @Override
    public String getName() {
        return subject;
    }

    @Override
    public String getSubject() {
        return subject;
    }

    @Override
    public List<String> getAudience() {
        return audience;
    }

    @Override
    public Set<String> getRoles() {
        return roles == null ? Collections.emptySet() : roles;
    }

    @Override
    public Set<String> getAuthorities() {
        return authorities == null ? Collections.emptySet() : authorities;
    }

    @Override
    public Set<String> getServiceAccounts() {
        return serviceAccounts;
    }

    @Override
    public Set<String> getOrganizations() {
        return organizations;
    }

    @Override
    public boolean implies(Subject subject) {
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JwtPrincipal that = (JwtPrincipal) o;
        return Objects.equals(subject, that.subject);
    }

    @Override
    public int hashCode() {
        return Objects.hash(subject);
    }
}
