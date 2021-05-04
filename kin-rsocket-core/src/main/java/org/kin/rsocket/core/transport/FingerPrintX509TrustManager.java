package org.kin.rsocket.core.transport;

import javax.net.ssl.X509TrustManager;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

/**
 * 对证书进一步保护性处理, 防止证书泄露
 * x509证书通过SHA-256加密后再把每个byte转换成十六进制
 *
 * @author huangjianqin
 * @date 2021/3/27
 */
public final class FingerPrintX509TrustManager implements X509TrustManager {
    private final List<String> fingerPrintsSha256;

    public FingerPrintX509TrustManager(List<String> fingerPrintsSha256) {
        this.fingerPrintsSha256 = new ArrayList<>(fingerPrintsSha256.size());
        for (String fingerPrint : fingerPrintsSha256) {
            this.fingerPrintsSha256.add(fingerPrint.toUpperCase());
        }
    }

    @Override
    public void checkClientTrusted(X509Certificate[] x509Certificates, String s) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String s) throws CertificateException {
        if (chain.length != 1) {
            throw new CertificateException("expected exactly one certificate in the chain.");
        }
        //检查server证书是否一致, server证书需通过SHA-256加密, 再把每个byte转换成十六进制生成'指纹', 提供给client作校验
        chain[0].checkValidity();
        X509Certificate x509Cert = chain[0];
        String certFingerPrint = getFingerPrint("SHA-256", x509Cert);
        if (!fingerPrintsSha256.contains(certFingerPrint.toUpperCase())) {
            throw new CertificateException("invalid fingerprint: " + certFingerPrint);
        }
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
    }

    public static String getFingerPrint(String algorithm, Certificate cert) {
        try {
            byte[] encCertInfo = cert.getEncoded();
            MessageDigest md = MessageDigest.getInstance(algorithm);
            //加密
            byte[] digest = md.digest(encCertInfo);
            //转换成十六进制
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                byte2hex(b, sb);
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException | CertificateEncodingException e) {
            // ignored
        }
        return "";
    }

    private static void byte2hex(byte b, StringBuilder buf) {
        char[] hexChars = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};
        int high = ((b & 0xf0) >> 4);
        int low = (b & 0x0f);
        buf.append(hexChars[high]).append(hexChars[low]);
    }
}
