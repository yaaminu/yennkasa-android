package com.pairapp.security;

import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.security.SecureRandom;

/**
 * Created by yaaminu on 12/22/16.
 */
public class AESTest {
    @Test
    public void aesEncrypt() throws Exception {
        String clear = "some clear test";
        byte[] key = new byte[16];
        SecureRandom random = new SecureRandom();
        random.nextBytes(key);
        byte[] encrypted = AES.aesEncrypt(key, clear.getBytes("UTF-8"));
        ByteBuffer buffer = ByteBuffer.wrap(encrypted);
        byte[] iv = new byte[16];
        buffer.get(iv);
        byte[] actualEncrypted = new byte[encrypted.length - 16]; //minus iv
        buffer.get(actualEncrypted);
        byte[] decrypted = AES.aesDecrypt(key, iv, actualEncrypted);
        Assert.assertArrayEquals(decrypted, clear.getBytes("UTF-8"));
    }

    @Test
    public void aesDecrypt() throws Exception {
        String clear = "some clear test";
        byte[] key = new byte[16];
        SecureRandom random = new SecureRandom();
        random.nextBytes(key);
        byte[] encrypted = AES.aesEncrypt(key, clear.getBytes("UTF-8"));
        ByteBuffer buffer = ByteBuffer.wrap(encrypted);
        byte[] iv = new byte[16];
        buffer.get(iv);
        byte[] actualEncrypted = new byte[encrypted.length - 16]; //minus iv
        buffer.get(actualEncrypted);
        byte[] decrypted = AES.aesDecrypt(key, iv, actualEncrypted);
        Assert.assertArrayEquals(decrypted, clear.getBytes("UTF-8"));
    }

}
