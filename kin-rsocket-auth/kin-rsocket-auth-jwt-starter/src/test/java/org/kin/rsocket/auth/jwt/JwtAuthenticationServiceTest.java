package org.kin.rsocket.auth.jwt;

import java.util.UUID;

/**
 * @author huangjianqin
 * @date 2021/4/3
 */
public class JwtAuthenticationServiceTest {
    public static void main(String[] args) throws Exception {
        JwtAuthenticationService jwt = new JwtAuthenticationService(true);
        String jwtStr = jwt.generateCredentials(
                UUID.randomUUID().toString(),
                new String[]{"default"},
                new String[]{"default"},
                new String[]{"internal"},
                null,
                "Mock",
                new String[]{"kin"}
        );
        System.out.println(jwtStr);
    }
}
