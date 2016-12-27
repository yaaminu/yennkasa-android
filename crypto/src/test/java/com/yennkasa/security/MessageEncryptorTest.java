package com.yennkasa.security;

import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.security.Key;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * Created by yaaminu on 12/22/16.
 */
public class MessageEncryptorTest {

    private static Key privateKey, publicKey;

    static {
        KeyPair keyPair = RSA.generatePublicPrivateKeyPair();
        privateKey = keyPair.getPrivate();
        publicKey = keyPair.getPublic();
    }

    private final MessageEncryptor.PrivatePublicKeySource keySource = new MessageEncryptor.PrivatePublicKeySource() {
        @Override
        public PrivateKey getPrivateKeyForThisUser() {
            return (PrivateKey) privateKey;
        }

        @Override
        public PublicKey getPublicKeyFor(String userId) {
            return (PublicKey) publicKey;
        }
    };
    private final MessageEncryptor encryptor = new MessageEncryptor(keySource, true);
    private final String recipient = "02044";

    @Test
    public void encrypt() throws Exception {
        String toBeEncrypted = "Hello World";
        testProtocol(toBeEncrypted);
        byte[] encrypted = encryptor.encrypt(recipient, toBeEncrypted.getBytes());
        Assert.assertEquals(new String(encryptor.decrypt(encrypted)),
                toBeEncrypted);
    }

    private void testProtocol(String toBeEncrypted) throws Exception {
        byte[] output = encryptor.encrypt(recipient, toBeEncrypted.getBytes("UTF-8"));
        ByteBuffer buffer = ByteBuffer.wrap(output);
        byte[] checksum = new byte[20];
        buffer.get(checksum);
        byte[] encryptedKey = new byte[128];
        buffer.get(encryptedKey);
        byte[] iv = new byte[16];
        buffer.get(iv);
        byte[] payload = new byte[(buffer.array().length) - (checksum.length + 128 + 16)];
        buffer.get(payload);

        byte[] actualSha1 = SHA1.
                sha1(ByteBuffer.allocate(output.length - 20).put(output, 20, output.length - 20).array());

        Assert.assertArrayEquals(actualSha1, checksum);
        byte[] actualKey = RSA.rsaDecrypt(privateKey, encryptedKey);
        byte[] clearPayload = AES.aesDecrypt(actualKey, iv, payload);
        Assert.assertArrayEquals(clearPayload, toBeEncrypted.getBytes("utf8"));
    }

    @Test
    public void decrypt() throws Exception {
        String clear = "Hello";
        byte[] encrypted = encryptor.encrypt(recipient, clear.getBytes());
        Assert.assertArrayEquals(clear.getBytes("utf8"), encryptor.decrypt(encrypted));
    }

}
