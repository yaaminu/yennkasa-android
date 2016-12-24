package com.pairapp.security;

import org.spongycastle.util.encoders.Base64;

import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAKeyGenParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

import javax.crypto.Cipher;

/**
 * Created by yaaminu on 12/22/16.
 */
public class RSA {

    static {
        Crypto.initIfRequried();
    }

    private static final int KEY_SIZE = 1024;

    /**
     * generates an RSA public key pair
     *
     * @return the keypair
     * @throws RuntimeException on error.
     */
    public static KeyPair generatePublicPrivateKeyPair() {
        try {
            SecureRandom random = new SecureRandom();
            RSAKeyGenParameterSpec spec = new RSAKeyGenParameterSpec(KEY_SIZE, RSAKeyGenParameterSpec.F4);
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA", "SC");
            generator.initialize(spec, random);

            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static byte[] rsaEncrypt(Key publicKey, byte[] input) {
        try {
            Cipher rsaCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA1AndMGF1Padding", "SC");
            rsaCipher.init(Cipher.ENCRYPT_MODE, publicKey);
            return rsaCipher.doFinal(input);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static byte[] rsaDecrypt(Key privateKey, byte[] input) {
        try {
            Cipher rsaCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA1AndMGF1Padding", "SC");
            rsaCipher.init(Cipher.DECRYPT_MODE, privateKey);
            return rsaCipher.doFinal(input);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String encodeRSAPublicKeyToString(PublicKey publicKey) {
        return Base64.toBase64String(publicKey.getEncoded());
    }

    public static String encodeRSAPrivateKeyToString(PrivateKey privateKey) {
        return Base64.toBase64String(privateKey.getEncoded());
    }

    public static PublicKey getRSAPublicKeyFromString(String publicKeyBase64) {
        byte[] publicKeyBytes = Base64.decode(publicKeyBase64);
        return getRSAPublicKeyFromByteArray(publicKeyBytes);
    }

    public static PublicKey getRSAPublicKeyFromByteArray(byte[] publicKeyBytes) {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA", "SC");
            X509EncodedKeySpec x509KeySpec = new X509EncodedKeySpec(publicKeyBytes);
            return keyFactory.generatePublic(x509KeySpec);
        } catch (NoSuchAlgorithmException | NoSuchProviderException | InvalidKeySpecException e) {
            throw new RuntimeException(e);
        }
    }

    public static PrivateKey getRSAPrivateKeyFromString(String privateKeyBase64) {
        return getRSAPrivateKeyFromByteArray(Base64.decode(privateKeyBase64));

    }

    public static PrivateKey getRSAPrivateKeyFromByteArray(byte[] privateKeyByte) {
        try {
            KeyFactory fact = KeyFactory.getInstance("RSA", "SC");
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(privateKeyByte);
            PrivateKey priv = fact.generatePrivate(keySpec);
            Arrays.fill(privateKeyByte, (byte) 0);
            return priv;
        } catch (NoSuchAlgorithmException | NoSuchProviderException | InvalidKeySpecException e) {
            throw new RuntimeException(e);
        }
    }
}
