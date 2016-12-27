package com.yennkasa.security;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Arrays;

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

    /**
     * decrypt {@code input} using AEC/CBC/PKCS5Padding with {@code key}.
     * <p>
     * always remember that the iv is prepended to the encrypted data
     *
     * @param key   the random key to use
     * @param input the input to be decrypted
     * @return the decrypted.
     * @throws NoSuchPaddingException
     * @throws NoSuchAlgorithmException
     * @throws InvalidAlgorithmParameterException
     * @throws InvalidKeyException
     * @throws BadPaddingException
     * @throws NoSuchProviderException
     * @throws IllegalBlockSizeException
     */
    static byte[] aesDecrypt(byte[] key, byte[] iv, byte[] input) throws NoSuchPaddingException, NoSuchAlgorithmException,
            InvalidAlgorithmParameterException, InvalidKeyException, BadPaddingException,
            NoSuchProviderException, IllegalBlockSizeException {
        Cipher decryptCipher = Cipher.getInstance("AES/CBC/PKCS5Padding", "SC");
        SecretKeySpec secretKeySpec = new SecretKeySpec(key, "AES");
        AlgorithmParameterSpec ivspec = new IvParameterSpec(iv);
        decryptCipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivspec);
        return decryptCipher.doFinal(input);
    }

    /**
     * encrypts all bytes from the stream {@code input} and write the output to {@code out}
     * be aware that this routine appends the IV(16 bytes) and a sha1 checksum(20 bytes) of the
     * encrypted input to the output so the output is at least 36 bytes longer than the input
     * <p>
     * it's the responsibility of the caller to close both streams.
     *
     * @param key   the key to use for encryption
     * @param iv    the container for the IV. the content are not used. it's filled with a
     *              random bytes by the cipher itself
     * @param input source of the byte stream
     * @param out   destination of the encrypted byte stream
     * @throws NoSuchPaddingException
     * @throws NoSuchAlgorithmException
     * @throws InvalidAlgorithmParameterException
     * @throws InvalidKeyException
     * @throws BadPaddingException
     * @throws NoSuchProviderException
     * @throws IllegalBlockSizeException
     * @throws IOException
     * @see {@link #aesDecryptWithSHA1Integrity(byte[], File, File)}
     * @see {@link #aesEncrypt(byte[], byte[])}
     * @see {@link #aesDecrypt(byte[], byte[], byte[])}
     */
    static void aesEncryptWithSHA1Integrity(byte[] key, byte[] iv, InputStream input, OutputStream out) throws NoSuchPaddingException, NoSuchAlgorithmException,
            InvalidAlgorithmParameterException, InvalidKeyException, BadPaddingException,
            NoSuchProviderException, IllegalBlockSizeException, IOException {
        Cipher encrypt = Cipher.getInstance("AES/CBC/PKCS5Padding", "SC");
        SecretKeySpec secretKeySpec = new SecretKeySpec(key, "AES");
        AlgorithmParameterSpec ivspec = new IvParameterSpec(iv);
        encrypt.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivspec);

        byte[] buffer = new byte[1024];
        int read;
        MessageDigest digest = MessageDigest.getInstance("SHA1");
        while ((read = input.read(buffer)) != -1) {
            digest.update(buffer, 0, read);
            byte[] encrypted = encrypt.update(buffer, 0, read);
            if (encrypted != null) {
                out.write(encrypted);
            }
        }
        byte[] bytes = encrypt.doFinal();
        if (bytes != null) {
            out.write(bytes);
        }
        out.write(encrypt.getIV());
        out.write(digest.digest());
        out.flush();
    }

    /**
     * decrypts all bytes from the stream {@code input} and write the output to {@code out}
     * It is the exact opposite of {@link #aesEncryptWithSHA1Integrity(byte[], byte[], InputStream, OutputStream)}
     * in the sense that it expects the sha1 checksum, and the iv at the end of the file.
     * <p>
     * it's the responsibility of the caller to close both streams.
     *
     * @param key    the key to use for decryption
     * @param input  source of the encrypted byte stream
     * @param output destination of the decrypted byte stream
     * @throws NoSuchPaddingException
     * @throws NoSuchAlgorithmException
     * @throws InvalidAlgorithmParameterException
     * @throws InvalidKeyException
     * @throws BadPaddingException
     * @throws NoSuchProviderException
     * @throws IllegalBlockSizeException
     * @throws IOException
     * @see {@link #aesEncryptWithSHA1Integrity(byte[], byte[], InputStream, OutputStream)}
     * @see {@link #aesEncrypt(byte[], byte[])}
     * @see {@link #aesDecrypt(byte[], byte[], byte[])}
     */
    static void aesDecryptWithSHA1Integrity(byte[] key, File input, File output) throws NoSuchPaddingException, NoSuchAlgorithmException,
            InvalidAlgorithmParameterException, InvalidKeyException, BadPaddingException,
            NoSuchProviderException, IllegalBlockSizeException, IOException {
        RandomAccessFile accessFile = new RandomAccessFile(input, "r");
        accessFile.seek(input.length() - 36); //jump to the start of the iv. see how we decryptCypher
        byte[] iv = new byte[16], expectedSha1 = new byte[20];
        accessFile.readFully(iv, 0, iv.length);
        accessFile.readFully(expectedSha1, 0, expectedSha1.length);
        accessFile.seek(0L); //go back to the beginning
        Cipher decryptCypher = Cipher.getInstance("AES/CBC/PKCS5Padding", "SC");
        SecretKeySpec secretKeySpec = new SecretKeySpec(key, "AES");
        AlgorithmParameterSpec ivspec = new IvParameterSpec(iv);
        decryptCypher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivspec);
        FileOutputStream outputStream = new FileOutputStream(output);
        MessageDigest digest = MessageDigest.getInstance("SHA1");
        try {
            long totalToRead = input.length() - 36;
            byte[] buffer = new byte[(int) Math.min(totalToRead, 1024)], decrypted;
            int read, processed = 0;
            while ((read = accessFile.read(buffer)) != -1) {
                if (processed + read > totalToRead) { //we have read past the boundary
                    decrypted = decryptCypher.update(buffer, 0, (int) (totalToRead - processed));
                } else {
                    decrypted = decryptCypher.update(buffer, 0, read);
                }
                if (decrypted != null) {
                    outputStream.write(decrypted);
                    digest.update(decrypted);
                }
                processed += read;
                if (processed >= totalToRead) {
                    break;
                }
            }
            byte[] bytes = decryptCypher.doFinal();
            if (bytes != null) {
                outputStream.write(bytes);
                digest.update(bytes);
            }
            outputStream.flush();
            checkIntegrity(expectedSha1, digest.digest());
        } finally {
            try {
                outputStream.close();
                accessFile.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void checkIntegrity(byte[] expectedSha1, byte[] actual) throws IOException {
        if (!Arrays.equals(expectedSha1, actual)) {
            throw new IOException("integrity check failed");
        }
    }
}
