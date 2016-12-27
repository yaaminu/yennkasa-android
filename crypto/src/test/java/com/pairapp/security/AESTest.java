package com.pairapp.security;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.security.SecureRandom;

/**
 * Created by yaaminu on 12/22/16.
 */
public class AESTest {

    public static final String TMP_FOO_TXT = "/tmp/foo.txt";
    public static final String TMP_ENCRYPTED_TXT = "/tmp/encrypted.txt";
    public static final String TMP_DECRYPTED_TXT = "/tmp/decrypted.txt";

    @BeforeClass
    public static void setup() throws Exception {
        FileWriter fileWriter = new FileWriter(TMP_FOO_TXT);
        fileWriter.write("testing file content");
        fileWriter.flush();
        fileWriter.close();
    }

    @AfterClass
    public static void tearDown() throws Exception {
        //noinspection ResultOfMethodCallIgnored
        new File(TMP_FOO_TXT).delete();
        //noinspection ResultOfMethodCallIgnored
        new File(TMP_ENCRYPTED_TXT).delete();
    }

    @Test
    public void aesDecryptWithSHA1Integrity() throws Exception {
        byte[] key = new byte[16], iv = new byte[16];
        SecureRandom random = new SecureRandom();
        random.nextBytes(key);
        InputStream inputStream = new FileInputStream(new File(TMP_FOO_TXT));
        OutputStream outputStream = new FileOutputStream(TMP_ENCRYPTED_TXT);
        AES.aesEncryptWithSHA1Integrity(key, iv, inputStream, outputStream);
        inputStream.close();
        outputStream.close();
        //close to make sure output buffer is flushed


        //if  they are not the same, there will be exception since
        //integrity check will fail
        File decrypted = new File(TMP_DECRYPTED_TXT);
        File encrypted = new File(TMP_ENCRYPTED_TXT);
        AES.aesDecryptWithSHA1Integrity(key, encrypted, decrypted);
    }

    @Test
    public void aesEncryptWithSHA1Integrity() throws Exception {
        aesDecryptWithSHA1Integrity(); //same
    }

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
