package org.kin.rsocket.auth;

/**
 * 生成证书所需参数
 *
 * @author huangjianqin
 * @date 2022/8/30
 */
public final class CredentialParam {
    private String id;
    private String[] organizations;
    private String[] serviceAccounts;
    private String[] roles;
    private String[] authorities;
    private String sub;
    private String[] audience;

    //setter && getter
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String[] getOrganizations() {
        return organizations;
    }

    public void setOrganizations(String[] organizations) {
        this.organizations = organizations;
    }

    public String[] getServiceAccounts() {
        return serviceAccounts;
    }

    public void setServiceAccounts(String[] serviceAccounts) {
        this.serviceAccounts = serviceAccounts;
    }

    public String[] getRoles() {
        return roles;
    }

    public void setRoles(String[] roles) {
        this.roles = roles;
    }

    public String[] getAuthorities() {
        return authorities;
    }

    public void setAuthorities(String[] authorities) {
        this.authorities = authorities;
    }

    public String getSub() {
        return sub;
    }

    public void setSub(String sub) {
        this.sub = sub;
    }

    public String[] getAudience() {
        return audience;
    }

    public void setAudience(String[] audience) {
        this.audience = audience;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** builder **/
    public static class Builder {
        private final CredentialParam credentialParam = new CredentialParam();

        public Builder id(String id) {
            credentialParam.id = id;
            return this;
        }

        public Builder organizations(String[] organizations) {
            credentialParam.organizations = organizations;
            return this;
        }

        public Builder serviceAccounts(String[] serviceAccounts) {
            credentialParam.serviceAccounts = serviceAccounts;
            return this;
        }

        public Builder roles(String[] roles) {
            credentialParam.roles = roles;
            return this;
        }

        public Builder authorities(String[] authorities) {
            credentialParam.authorities = authorities;
            return this;
        }

        public Builder sub(String sub) {
            credentialParam.sub = sub;
            return this;
        }

        public Builder audience(String[] audience) {
            credentialParam.audience = audience;
            return this;
        }

        public CredentialParam build() {
            return credentialParam;
        }
    }
}
