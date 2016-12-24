package com.pairapp.security;

import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.spec.AlgorithmParameterSpec;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Created by yaaminu on 12/22/16.
 */
class AES {
    static {
        Crypto.initIfRequried();
    }

    /**
     * encrypt {@code input} using AEC/CBC/PKCS5Padding with {@code key}.
     * <p>
     * always remember that the iv is prepended to the encrypted data
     *
     * @param key   the random key to use
     * @param input the input to be encrypted
     * @return the encrypted data with the IV prepended to it.
     * @throws NoSuchPaddingException
     * @throws NoSuchAlgorithmException
     * @throws InvalidAlgorithmParameterException
     * @throws InvalidKeyException
     * @throws BadPaddingException
     * @throws NoSuchProviderException
     * @throws IllegalBlockSizeException
     */
    static byte[] aesEncrypt(byte[] key, byte[] input) throws NoSuchPaddingException, NoSuchAlgorithmException,
            InvalidAlgorithmParameterException, InvalidKeyException, BadPaddingException, NoSuchProviderException, IllegalBlockSizeException {
        Cipher encryptCipher =
                Cipher.getInstance("AES/CBC/PKCS5Padding", "SC");
        SecretKeySpec secretKeySpec = new SecretKeySpec(key, "AES");
        byte[] iv = new byte[16];
        AlgorithmParameterSpec ivSpec = new IvParameterSpec(iv);
        encryptCipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivSpec);
        byte[] encrypted = encryptCipher.doFinal(input);
        ByteBuffer buffer = ByteBuffer.allocate(encrypted.length + iv.length);
        return buffer.put(encryptCipher.getIV()).put(encrypted).array();
    }

    static byte[] aesDecrypt(byte[] key, byte[] iv, byte[] input) throws NoSuchPaddingException, NoSuchAlgorithmException,
            InvalidAlgorithmParameterException, InvalidKeyException, BadPaddingException,
            NoSuchProviderException, IllegalBlockSizeException {
        Cipher decryptCipher = Cipher.getInstance("AES/CBC/PKCS5Padding", "SC");
        SecretKeySpec secretKeySpec = new SecretKeySpec(key, "AES");
        AlgorithmParameterSpec ivspec = new IvParameterSpec(iv);
        decryptCipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivspec);
        return decryptCipher.doFinal(input);
    }
}
