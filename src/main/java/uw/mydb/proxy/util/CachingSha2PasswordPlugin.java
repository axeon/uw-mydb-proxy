package uw.mydb.proxy.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.security.DigestException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;

/**
 * MySQL caching_sha2_password 认证插件工具。
 * <p>
 * 提供：
 * <ul>
 *   <li>{@link #scrambleCachingSha2}：基于 SHA-256 的 scramble 生成（参考 MySQL 源码 {@code Generate_scramble::scramble}）。</li>
 *   <li>{@link #encrypt}/{@link #encryptPassword}：使用服务端 RSA 公钥加密密码（用于 caching_sha2_password 完整认证路径，避免明文传输）。
 *       根据 MySQL 版本（&gt;=8.0.5 使用 OAEPWithSHA-1AndMGF1Padding，更早版本使用 PKCS1Padding）。</li>
 *   <li>{@link #decodeRSAPublicKey}：从 PEM 格式字符串解析 {@link RSAPublicKey}。</li>
 * </ul>
 */
public class CachingSha2PasswordPlugin {

    static final Logger log = LoggerFactory.getLogger(CachingSha2PasswordPlugin.class);

    /**
     * 插件协议名，与 MySQL 服务端 caching_sha2_password 对齐。
     */
    public static final String PROTOCOL_PLUGIN_NAME = "caching_sha2_password"; // caching_sha2_password

    /**
     * 计算 caching_sha2_password 协议下的 scramble（XOR(SHA256(pass), SHA256(SHA256(SHA256(pass)) | nonce))）。
     * 空密码返回空字节数组，SHA-256 不可用时记录 WARN 并返回 null。
     *
     * @param password 明文密码
     * @param seed     服务端 nonce
     * @return scramble 字节（32 字节），失败返回 null
     */
    public static byte[] scrambleCachingSha2(String password, byte[] seed) {
        if (password == null || password.length() == 0) {
            log.warn("password is empty");
            return new byte[0];
        }
        try {
            return SecurityUtils.scrambleCachingSha2(password.getBytes(java.nio.charset.StandardCharsets.UTF_8), seed);
        } catch (DigestException e) {
            log.warn("no such Digest", e);
            return null;
        }

    }

    public static byte[] scrambleCachingSha2(String password, String seed) {
        return scrambleCachingSha2(password, seed.getBytes());
    }

    public static byte[] encrypt(String mysqlVersion, String publicKeyString, String password, String seed, String encoding) throws Exception {
        ServerVersion currentVersion = ServerVersion.parseVersion(mysqlVersion);
        final ServerVersion min = new ServerVersion(8, 0, 5);
        if (currentVersion.compareTo(min) >= 0) {
            return encryptPassword("RSA/ECB/OAEPWithSHA-1AndMGF1Padding", publicKeyString, password, seed, encoding);
        }
        return encryptPassword("RSA/ECB/PKCS1Padding", publicKeyString, password, seed, encoding);

    }

    public static byte[] encryptPassword(String transformation, String publicKeyString, String password, String seed, String encoding)
            throws Exception {
        byte[] input = null;
        input = password != null ? getBytesNullTerminated(password, encoding) : new byte[]{0};
        byte[] mysqlScrambleBuff = new byte[input.length];
        //seed必须用与encoding一致的字符集还原为字节，否则平台默认字符集会破坏原始seed字节。
        SecurityUtils.xorString(input, mysqlScrambleBuff, seed.getBytes(Charset.forName(encoding)), input.length);
        return encryptWithRSAPublicKey(mysqlScrambleBuff, decodeRSAPublicKey(publicKeyString), transformation);
    }

    public static byte[] getBytesNullTerminated(String value, String encoding) {
        Charset cs = Charset.forName(encoding);
        ByteBuffer buf = cs.encode(value);
        int encodedLen = buf.limit();
        byte[] asBytes = new byte[encodedLen + 1];
        buf.get(asBytes, 0, encodedLen);
        asBytes[encodedLen] = 0;

        return asBytes;
    }

    public static byte[] encryptWithRSAPublicKey(byte[] source, RSAPublicKey key, String transformation)
            throws IllegalBlockSizeException, InvalidKeyException, BadPaddingException, NoSuchAlgorithmException, NoSuchPaddingException {
        try {
            Cipher cipher = Cipher.getInstance(transformation);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            return cipher.doFinal(source);
        } catch (Exception e) {
            throw e;
        }
    }

    public static RSAPublicKey decodeRSAPublicKey(String key) throws Exception {

        if (key == null) {
            throw new Exception("Key parameter is null\"");
        }

        // 去除 PEM header/footer 和换行符，提取纯 Base64 数据
        String base64Data = key
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");

        byte[] certificateData = Base64Decoder.decode(base64Data.getBytes(), 0, base64Data.length());

        X509EncodedKeySpec spec = new X509EncodedKeySpec(certificateData);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return (RSAPublicKey) kf.generatePublic(spec);
    }


}
