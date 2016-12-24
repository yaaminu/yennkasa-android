package com.pairapp.security;

import org.junit.Assert;
import org.junit.Test;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.Random;

/**
 * Created by yaaminu on 12/22/16.
 */
public class RSATest {
    @Test
    public void testEncodeRSAPublicKeyToString() throws Exception {
        String asString = RSA.encodeRSAPublicKeyToString(publicKey);
        Assert.assertEquals(asString, RSA.encodeRSAPublicKeyToString(publicKey));
        Assert.assertEquals(RSA.encodeRSAPublicKeyToString(RSA.getRSAPublicKeyFromString(asString)),
                RSA.encodeRSAPublicKeyToString(publicKey));

    }

    @Test
    public void testEncodeRSAPrivateKeyToString() throws Exception {
        String asString = RSA.encodeRSAPrivateKeyToString(privateKey);
        Assert.assertEquals(asString, asString);
        Assert.assertEquals(RSA.encodeRSAPrivateKeyToString(RSA.getRSAPrivateKeyFromString(asString)),
                RSA.encodeRSAPrivateKeyToString(privateKey));
    }

    private static PrivateKey privateKey;
    private static PublicKey publicKey;

    static {
        KeyPair keyPair = RSA.generatePublicPrivateKeyPair();
        privateKey = keyPair.getPrivate();
        publicKey = keyPair.getPublic();
    }

    @Test
    public void testRSAEncrypt() throws Exception {
        SecureRandom random = new SecureRandom();
        byte[] input = new byte[16];
        random.nextBytes(input);
        byte[] encrypted = RSA.rsaEncrypt(publicKey, input);
        byte[] actual = RSA.rsaDecrypt(privateKey, encrypted);
        Assert.assertArrayEquals(input, actual);
    }

    @Test
    public void testRSADecrypt() throws Exception {
        Random random = new SecureRandom();
        byte[] input = new byte[16];
        random.nextBytes(input);
        byte[] encrypted = RSA.rsaEncrypt(publicKey, input);
        byte[] actual = RSA.rsaDecrypt(privateKey, encrypted);
        Assert.assertArrayEquals(input, actual);
    }

    @Test
    public void getRSAPublicKeyFromString() throws Exception {
        String publicKeyAsString = RSA.encodeRSAPublicKeyToString(publicKey);
        PublicKey retrievedKey = RSA.getRSAPublicKeyFromString(publicKeyAsString);
        String clear = "hello world";
        byte[] encryptedWithPublicKey = RSA.rsaEncrypt(publicKey, clear.getBytes());
        byte[] encryptedWithRetrievedKey = RSA.rsaEncrypt(retrievedKey, clear.getBytes());


        //we should be able to decrypt all of them to prove that the retreived is equivalent to
        // the public key
        byte[] decrypted = RSA.rsaDecrypt(privateKey, encryptedWithPublicKey);
        byte[] decrypted2 = RSA.rsaDecrypt(privateKey, encryptedWithRetrievedKey);
        Assert.assertArrayEquals(decrypted, decrypted2);
        Assert.assertEquals(new String(decrypted), clear);
        Assert.assertEquals(new String(decrypted2), clear);
    }

    @Test
    public void getRSAPrivateKeyFromString() throws Exception {
        String privateKeyAsString = RSA.encodeRSAPrivateKeyToString(privateKey);
        PrivateKey retrievedKey = RSA.getRSAPrivateKeyFromString(privateKeyAsString);
        String clear = "hello world";
        byte[] encryptedWithPublicKey = RSA.rsaEncrypt(publicKey, clear.getBytes());


        //we should be able to decrypt all of them to prove that the retreived is equivalent to
        // the public key
        byte[] decrypted = RSA.rsaDecrypt(privateKey, encryptedWithPublicKey);
        byte[] decrypted2 = RSA.rsaDecrypt(retrievedKey, encryptedWithPublicKey);
        Assert.assertArrayEquals(decrypted, decrypted2);
        Assert.assertEquals(new String(decrypted), clear);
        Assert.assertEquals(new String(decrypted2), clear);
    }

}
