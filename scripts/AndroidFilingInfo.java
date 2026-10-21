import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.HexFormat;

// 只解析 keytool 导出的公开 X.509 证书；本程序不接受密钥库或密码。
public final class AndroidFilingInfo {
    public static void main(String[] args) {
        try {
            if (args.length != 3) throw new IllegalArgumentException();
            X509Certificate certificate;
            try (var input = Files.newInputStream(Path.of(args[0]))) {
                var certificates = CertificateFactory.getInstance("X.509").generateCertificates(input);
                if (certificates.size() != 1) throw new IllegalArgumentException();
                certificate = (X509Certificate) certificates.iterator().next();
            }
            if (!(certificate.getPublicKey() instanceof RSAPublicKey rsa)) {
                System.err.println("备案信息导出失败：所选证书不是 RSA 证书。");
                System.exit(1);
                return;
            }
            certificate.checkValidity();
            if (rsa.getModulus().bitLength() < 2048 ||
                    certificate.getSubjectX500Principal().getName().toLowerCase(java.util.Locale.ROOT).contains("cn=android debug")) {
                System.err.println("备案信息导出失败：不能将 Debug 或弱密钥证书作为正式备案身份。");
                System.exit(1);
                return;
            }
            // BigInteger 表示原始正整数，toString(16) 不包含 ASN.1 的符号填充 00。
            String modulusHex = rsa.getModulus().toString(16).toUpperCase(java.util.Locale.ROOT);
            String modulusDecimal = rsa.getModulus().toString(10);
            byte[] der = certificate.getEncoded();
            String md5 = HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("MD5").digest(der));
            String sha256 = HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(der));
            String report = String.join(System.lineSeparator(),
                "礼账 Android APP 备案特征信息（仅公开证书资料，待本人核对）",
                "Generated At (UTC): " + Instant.now(),
                "Package Name: com.yangsong.lizhang",
                "Key Alias: " + args[1],
                "Key Algorithm: RSA",
                "Key Size: " + rsa.getModulus().bitLength(),
                "RSA Modulus: " + modulusDecimal,
                "RSA Modulus Format: Decimal (base 10)",
                "RSA Modulus Hex: " + modulusHex,
                "RSA Modulus Decimal: " + modulusDecimal,
                "Certificate MD5: " + md5,
                "Certificate SHA-256: " + sha256,
                "Certificate Subject: " + certificate.getSubjectX500Principal().getName(),
                "Certificate Issuer: " + certificate.getIssuerX500Principal().getName(),
                "Certificate Validity: " + certificate.getNotBefore().toInstant() + " / " + certificate.getNotAfter().toInstant(),
                "",
                "填写说明：包名填写 Package Name；MD5 填写 Certificate MD5 的 32 位十六进制，无冒号或空格。",
                "公钥字段填写完整 RSA 模数；RSA Modulus 默认为十进制，Hex 为相同正整数的十六进制。",
                "请依阿里云当前字段格式选择十进制或十六进制，不混用进制，不填写 Base64 证书全文。",
                "SHA-256 为同一张 X.509 证书 DER 编码的 64 位十六进制指纹；MD5 不是 APK 文件摘要。",
                "模数未包含 ASN.1 为正数编码而添加的符号填充 00。所有特征值均来自本次同一张证书。",
                "证书不包含 Android 包名；上述包名来自项目约定，尚未通过最终 APK 清单核验。",
                "本报告不能证明所选身份已获本人批准，也不表示备案、产品测试或发布已完成。",
                "未来最终 APK 须用 apkanalyzer 核对包名，并用 apksigner verify --print-certs 比对同一证书 SHA-256。",
                "阿里云官方字段说明：https://help.aliyun.com/zh/icp-filing/basic-icp-service/user-guide/fill-in-app-feature-information",
                "");
            Files.writeString(Path.of(args[2]), report, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // 只显示固定错误，避免将本机环境或底层异常拼入日志。
            System.err.println("备案信息导出失败：请在本机核对公开证书格式、有效期及输出目录。");
            System.exit(1);
        }
    }
}
