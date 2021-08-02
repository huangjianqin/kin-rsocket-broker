package org.kin.rsocket.broker;

import org.kin.rsocket.auth.RSocketAppPrincipal;

import java.util.HashSet;
import java.util.Set;

/**
 * service mesh 拦截器, 用于校验认证是否通过
 * todo 新增白名单移除机制(定时?)
 *
 * @author huangjianqin
 * @date 2021/3/30
 */
public final class RSocketServiceMeshInspector {
    private static final String SEPARATOR = ":";
    /** 白名单 */
    private Set<Integer> whiteList = new HashSet<>();
    /** 是否需要验证 */
    private final boolean authRequired;

    public RSocketServiceMeshInspector() {
        this(true);
    }

    public RSocketServiceMeshInspector(boolean authRequired) {
        this.authRequired = authRequired;
    }

    /**
     * 是否通过验证
     */
    public boolean isAllowed(RSocketAppPrincipal requesterPrincipal, int serverId, RSocketAppPrincipal responderPrincipal) {
        if (!authRequired) {
            return true;
        }
        //org & service account relation
        int relationHashCode = (requesterPrincipal.hashCode() + SEPARATOR + responderPrincipal.hashCode()).hashCode();
        if (whiteList.contains(relationHashCode)) {
            return true;
        }
        //acl mapping
        int aclHashCode = (requesterPrincipal.hashCode() + SEPARATOR + serverId + SEPARATOR + responderPrincipal.hashCode()).hashCode();
        if (whiteList.contains(aclHashCode)) {
            return true;
        }
        boolean orgFriendly = false;
        for (String principalOrg : requesterPrincipal.getOrganizations()) {
            if (responderPrincipal.getOrganizations().contains(principalOrg)) {
                orgFriendly = true;
                break;
            }
        }
        if (orgFriendly) {
            boolean serviceAccountFriendly = false;
            for (String serviceAccount : requesterPrincipal.getServiceAccounts()) {
                if (responderPrincipal.getServiceAccounts().contains(serviceAccount)) {
                    serviceAccountFriendly = true;
                    break;
                }
            }
            if (serviceAccountFriendly) {
                //account + organization都满足
                whiteList.add(relationHashCode);
                return true;
            }
        }
        return false;
    }
}
