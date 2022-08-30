package org.kin.rsocket.auth.jwt;

import org.kin.rsocket.auth.CredentialParam;
import org.kin.rsocket.auth.JwtAuthenticationService;

import java.io.File;
import java.util.UUID;

/**
 * @author huangjianqin
 * @date 2021/4/3
 */
public class JwtAuthenticationServiceTest {
    public static void main(String[] args) throws Exception {
        JwtAuthenticationService jwt = new JwtAuthenticationService("KinRSocketBroker", System.getProperty("user.home").concat(File.separator).concat(".rsocket"));

        CredentialParam param = CredentialParam.builder().id(UUID.randomUUID().toString())
                .organizations(new String[]{"default"})
                .serviceAccounts(new String[]{"default"})
                .roles(new String[]{"internal"})
                .authorities(null)
                .sub("Mock")
                .audience(new String[]{"kin"})
                .build();
        String jwtStr = jwt.generateCredentials(param);
        System.out.println(jwtStr);
    }
}
