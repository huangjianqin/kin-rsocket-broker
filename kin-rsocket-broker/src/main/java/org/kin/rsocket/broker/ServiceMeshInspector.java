package org.kin.rsocket.broker;

import org.kin.rsocket.auth.RSocketAppPrincipal;

import java.util.HashSet;
import java.util.Set;

/**
 * service mesh 拦截器, 用于校验认证是否通过
 *
 * @author huangjianqin
 * @date 2021/3/30
 */
public class ServiceMeshInspector {
    private static final String SEPARATOR = ":";
    /** 白名单 */
    private Set<Integer> whiteRelationBitmap = new HashSet<>();
    /** 是否需要验证 */
    private final boolean authRequired;

    public ServiceMeshInspector() {
        this(true);
    }

    public ServiceMeshInspector(boolean authRequired) {
        this.authRequired = authRequired;
    }

    /**
     * 是否通过验证
     */
    public boolean isAllowed(RSocketAppPrincipal requesterPrincipal, String routing, RSocketAppPrincipal responderPrincipal) {
        if (!authRequired) {
            return true;
        }
        //org & service account relation
        int relationHashCode = (requesterPrincipal.hashCode() + SEPARATOR + responderPrincipal.hashCode()).hashCode();
        if (whiteRelationBitmap.contains(relationHashCode)) {
            return true;
        }
        //acl mapping
        int aclHashCode = (requesterPrincipal.hashCode() + SEPARATOR + routing + SEPARATOR + responderPrincipal.hashCode()).hashCode();
        if (whiteRelationBitmap.contains(aclHashCode)) {
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
                whiteRelationBitmap.add(relationHashCode);
                return true;
            }
        }
        return false;
    }

    /**
     * @return 白名单数量
     */
    public Integer size() {
        return whiteRelationBitmap.size();
    }
}
