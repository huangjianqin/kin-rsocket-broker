package org.kin.rsocket.broker;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.kin.rsocket.auth.RSocketAppPrincipal;

import java.util.concurrent.TimeUnit;

/**
 * service mesh 拦截器, 用于校验认证是否通过
 *
 * @author huangjianqin
 * @date 2021/3/30
 */
public final class RSocketServiceMeshInspector {
    private static final String SEPARATOR = ":";
    /** 白名单, 10min没有访问即过期 */
    private final Cache<Integer, Boolean> whiteList = CacheBuilder.newBuilder().expireAfterAccess(10, TimeUnit.MINUTES).build();
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
        if (Boolean.TRUE.equals(whiteList.getIfPresent(relationHashCode))) {
            return true;
        }
        //acl mapping
        int aclHashCode = (requesterPrincipal.hashCode() + SEPARATOR + serverId + SEPARATOR + responderPrincipal.hashCode()).hashCode();
        if (Boolean.TRUE.equals(whiteList.getIfPresent(aclHashCode))) {
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
                whiteList.put(relationHashCode, true);
                return true;
            }
        }
        return false;
    }
}
