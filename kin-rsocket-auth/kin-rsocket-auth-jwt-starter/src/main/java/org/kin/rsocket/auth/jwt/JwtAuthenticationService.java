package org.kin.rsocket.auth.jwt;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.JWTVerifier;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.kin.rsocket.auth.AuthenticationService;
import org.kin.rsocket.auth.RSocketAppPrincipal;

import java.io.*;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * authentication service with JWT implementation, please refer https://github.com/auth0/java-jwt
 * todo
 *
 * @author huangjianqin
 * @date 2021/3/30
 */
public class JwtAuthenticationService implements AuthenticationService {
    private static final String iss = "KinRSocketBroker";
    /** 公钥文件名 */
    private static final String PUBLIC_KEY_FILE = "jwt_rsa.pub";
    /** 私钥文件名 */
    private static final String PRIVATE_KEY_FILE = "jwt_rsa.key";

    /** 密钥目录 */
    private final File authDir;
    /** jwt校验算法 */
    private final List<JWTVerifier> verifiers = new ArrayList<>();
    /** cache verified principal */
    private Cache<Integer, RSocketAppPrincipal> jwtVerifyCache = CacheBuilder.newBuilder()
            .maximumSize(100_00)
            //30min后移除
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .build();

    public JwtAuthenticationService() throws Exception {
        this(false);
    }

    public JwtAuthenticationService(boolean autoGen) throws Exception {
        this(autoGen, null);
    }

    /**
     * @param autoGen 是否自动生成RSAKeyPairs
     */
    public JwtAuthenticationService(boolean autoGen, File authDir) throws Exception {
        this.authDir = authDir;
        if (autoGen) {
            if (Objects.nonNull(authDir) && !authDir.exists()) {
                authDir.mkdir();
            }
            generateRSAKeyPairs(authDir);
        }

        this.verifiers.add(JWT.require(Algorithm.RSA256(readPublicKey(authDir), null)).withIssuer(iss).build());
    }

    @Override
    public RSocketAppPrincipal auth(String credentials) {
        if (Objects.isNull(credentials) || credentials.isEmpty()) {
            return null;
        }
        //bearer jwt_token
        credentials = credentials.substring(credentials.lastIndexOf(" ") + 1);

        int tokenHashCode = credentials.hashCode();
        RSocketAppPrincipal principal = jwtVerifyCache.getIfPresent(tokenHashCode);
        for (JWTVerifier verifier : verifiers) {
            try {
                principal = new JwtPrincipal(verifier.verify(credentials));
                jwtVerifyCache.put(tokenHashCode, principal);
                break;
            } catch (JWTVerificationException ignore) {
                //do nothing
            }
        }
        return principal;
    }


    @Override
    public String generateCredentials(String id, String[] organizations,
                                      String[] serviceAccounts, String[] roles,
                                      String[] authorities, String sub,
                                      String[] audience) throws Exception {
        Arrays.sort(audience);
        Arrays.sort(organizations);
        JWTCreator.Builder builder = JWT.create()
                .withIssuer(iss)
                .withSubject(sub)
                .withAudience(audience)
                .withIssuedAt(new Date())
                .withClaim("id", id)
                .withArrayClaim("sas", serviceAccounts)
                .withArrayClaim("orgs", organizations);
        if (roles != null && roles.length > 0) {
            Arrays.sort(roles);
            builder = builder.withArrayClaim("roles", roles);
        }
        if (authorities != null && authorities.length > 0) {
            builder = builder.withArrayClaim("authorities", authorities);
        }

        return builder.sign(Algorithm.RSA256(null, readPrivateKey(authDir)));
    }

    /**
     * 读取私钥
     */
    private RSAPrivateKey readPrivateKey(File authDir) throws Exception {
        File keyFile = new File(authDir, PRIVATE_KEY_FILE);
        try (InputStream inputStream = new FileInputStream(keyFile)) {
            byte[] keyBytes = toBytes(inputStream);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(spec);
        }

    }

    /**
     * 读取公钥
     */
    private RSAPublicKey readPublicKey(File authDir) throws Exception {
        File keyFile = new File(authDir, PUBLIC_KEY_FILE);
        try (InputStream inputStream = new FileInputStream(keyFile)) {
            byte[] keyBytes = toBytes(inputStream);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
        }
    }

    /**
     * 将inputStream转换成bytes
     */
    private byte[] toBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        byte[] bytesCache = new byte[4096];
        int bytesRead;
        while ((bytesRead = inputStream.read(bytesCache)) != -1) {
            buffer.write(bytesCache, 0, bytesRead);
        }
        buffer.flush();

        byte[] bytes = buffer.toByteArray();
        //close
        inputStream.close();
        buffer.close();
        return bytes;
    }

    /**
     * 自动生成RSAKey公钥和密钥
     */
    private void generateRSAKeyPairs(File authDir) throws Exception {
        File publicKeyFile = new File(authDir, PUBLIC_KEY_FILE);
        if (publicKeyFile.exists()) {
            //公钥在就不管了
            return;
        }

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair keyPair = kpg.generateKeyPair();
        Key pub = keyPair.getPublic();
        Key pvt = keyPair.getPrivate();
        try (OutputStream out = new FileOutputStream(new File(authDir, PRIVATE_KEY_FILE))) {
            out.write(pvt.getEncoded());
        }
        try (OutputStream out2 = new FileOutputStream(publicKeyFile)) {
            out2.write(pub.getEncoded());
        }
    }
}
