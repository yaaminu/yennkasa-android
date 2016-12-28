package com.yennkasa.security;

import org.spongycastle.util.encoders.Base64;

import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.Security;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;


/**
 * @author by yaaminu on 12/21/16.
 */
public class Crypto {
    static {
        Security.insertProviderAt(new org.spongycastle.jce.provider.BouncyCastleProvider(), 1);
    }

    private static final String TAG = Crypto.class.getSimpleName();

    static synchronized void initIfRequried() {
        Provider provider = Security.getProvider("SC");
        if (provider == null) {
            Security.insertProviderAt(new org.spongycastle.jce.provider.BouncyCastleProvider(), 1);
        }
    }

    public static String hashPassword(char[] password) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return doHash(password, salt);
    }

    private static String doHash(char[] password, byte[] salt) {
        try {
            KeySpec spec = new PBEKeySpec(password, salt, 1000, 128);
            SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            byte[] hash = f.generateSecret(spec).getEncoded();
            return Base64.toBase64String(salt) + "$" + Base64.toBase64String(hash);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean compare(char[] password, String hash) {
        String[] split = hash.split("\\$", 2);
        byte[] salt = Base64.decode(split[0]);
        return doHash(password, salt).equals(hash);
    }
}
