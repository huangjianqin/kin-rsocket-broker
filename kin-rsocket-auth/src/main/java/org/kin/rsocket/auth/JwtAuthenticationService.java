package org.kin.rsocket.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.JWTVerifier;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

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
 * 单点校验算法
 * 通过算法生成一串token, 然后provider和consumer同时拥有这个token, 在request和response时校验token的合法性
 * 缺点: consumer端也拥有token, 如果泄露, 就gg了
 *
 * @author huangjianqin
 * @date 2021/3/30
 */
public final class JwtAuthenticationService implements AuthenticationService {
    /** 公钥文件名 */
    private static final String PUBLIC_KEY_FILE = "jwt_rsa.pub";
    /** 私钥文件名 */
    private static final String PRIVATE_KEY_FILE = "jwt_rsa.key";

    /** jwt校验算法 */
    private final List<JWTVerifier> verifiers = new ArrayList<>();
    /** cache verified principal */
    private final Cache<Integer, RSocketAppPrincipal> jwtVerifyCache = CacheBuilder.newBuilder()
            .maximumSize(100_00)
            //30min后移除
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .build();
    private final RSAPrivateKey privateKey;

    private final String issuer;

    public JwtAuthenticationService(String issuer) throws Exception {
        this(issuer, null);
    }

    /**
     * @param authDir 密钥目录
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public JwtAuthenticationService(String issuer, String authDir) throws Exception {
        this.issuer = issuer;
        File authDirFile = null;
        if (Objects.nonNull(authDir) && !authDir.isEmpty()) {
            authDirFile = new File(authDir);
        }
        File pubKeyFile = new File(authDir, PUBLIC_KEY_FILE);
        if (!pubKeyFile.exists()) {
            //RSAKey公钥文件不存在, 则自动生成key
            if (Objects.nonNull(authDirFile) && !authDirFile.exists()) {
                authDirFile.mkdir();
            }

            generateRSAKeyPairs(authDirFile);
        }

        //使用暴露的公钥去校验签名
        this.verifiers.add(JWT.require(Algorithm.RSA256(readPublicKey(authDirFile), null)).withIssuer(issuer).build());
        this.privateKey = readPrivateKey(authDirFile);
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
    public String generateCredentials(CredentialParam param) {
        Arrays.sort(param.getAudience());
        Arrays.sort(param.getOrganizations());
        JWTCreator.Builder builder = JWT.create()
                .withIssuer(issuer)
                .withSubject(param.getSub())
                .withAudience(param.getAudience())
                .withIssuedAt(new Date())
                .withClaim("id", param.getId())
                .withArrayClaim("sas", param.getServiceAccounts())
                .withArrayClaim("orgs", param.getOrganizations());

        String[] roles = param.getRoles();
        if (roles != null && roles.length > 0) {
            Arrays.sort(roles);
            builder = builder.withArrayClaim("roles", roles);
        }

        String[] authorities = param.getAuthorities();
        if (authorities != null && authorities.length > 0) {
            builder = builder.withArrayClaim("authorities", authorities);
        }

        //使用私钥生成签名
        return builder.sign(Algorithm.RSA256(null, privateKey));
    }

    /**
     * 读取私钥
     */
    private RSAPrivateKey readPrivateKey(File authDir) throws Exception {
        File keyFile = new File(authDir, PRIVATE_KEY_FILE);
        try (InputStream inputStream = new FileInputStream(keyFile)) {
            byte[] keyBytes = toBytes(inputStream);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            //以pkc8编码
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
            //以x509编码
            //rsa public key 只能以X509EncodedKeySpec和DSAPublicKeySpec编码
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

        //生成的是原始key, 需要套编码才能使用或者组合成KeyPair
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
