package uw.mydb.util;

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

public class CachingSha2PasswordPlugin {

    static final Logger log = LoggerFactory.getLogger(CachingSha2PasswordPlugin.class);

    public static final String PROTOCOL_PLUGIN_NAME = "caching_sha2_password"; // caching_sha2_password

    public static byte[] scrambleCachingSha2(String password, byte[] seed) {
        if (password == null || password.length() == 0) {
            log.warn("password is empty");
            return new byte[0];
        }
        try {
            return SecurityUtils.scrambleCachingSha2(password.getBytes(), seed);
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
        SecurityUtils.xorString(input, mysqlScrambleBuff, seed.getBytes(), input.length);
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

        int offset = key.indexOf("\n") + 1;
        int len = key.indexOf("-----END PUBLIC KEY-----") - offset;

        // TODO: use standard decoders with Java 6+
        byte[] certificateData = Base64Decoder.decode(key.getBytes(), offset, len);

        X509EncodedKeySpec spec = new X509EncodedKeySpec(certificateData);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return (RSAPublicKey) kf.generatePublic(spec);
    }


}
