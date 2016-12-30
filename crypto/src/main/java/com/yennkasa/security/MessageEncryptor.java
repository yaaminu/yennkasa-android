package com.yennkasa.security;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import static com.yennkasa.security.MessageEncryptor.EncryptionException.INVALID_PUBLIC_KEY;
import static com.yennkasa.security.RSA.rsaDecrypt;

/**
 * @author by yaaminu on 12/22/16.
 */

@SuppressWarnings("WeakerAccess")
public final class MessageEncryptor {
    private final PrivatePublicKeySource privatePublicKeySource;
    private final SecureRandom random;
    private static boolean debug;

    public MessageEncryptor(PrivatePublicKeySource privatePublicKeySource, boolean debug) {
        this.privatePublicKeySource = privatePublicKeySource;
        this.random = new SecureRandom();
        MessageEncryptor.debug = debug;
    }

    /**
     * encrypts a message according to the yennkasa end-to-end encryption specification
     *
     * @param recipient the recipient of the message
     * @param input     the message to be  transmitted
     * @return the encrypted output
     * @throws EncryptionException
     */

    public byte[] encrypt(String recipient, byte[] input) throws EncryptionException {
        try {
            PublicKey publicKeyForUser = privatePublicKeySource.getPublicKeyFor(recipient);
            if (publicKeyForUser == null) {
                throw new EncryptionException("no public key for " + recipient, EncryptionException.PUBLIC_KEY_NOT_FOUND);
            }
            byte[] key = new byte[16];
            random.nextBytes(key);

            byte[] encryptedInput = AES.aesEncrypt(key, input);
            byte[] encryptedKey = RSA
                    .rsaEncrypt(publicKeyForUser, key);
            ByteBuffer buffer = ByteBuffer
                    .allocate(encryptedInput.length + encryptedKey.length);
            buffer.put(encryptedKey).put(encryptedInput);
            ByteBuffer finalPayload = ByteBuffer.allocate(buffer.capacity() + 20/*length of sha1 checksum*/);
            byte[] checksum = SHA1.sha1(buffer.array());
            finalPayload.put(checksum).put(buffer.array());
            return finalPayload.array();
        } catch (NoSuchAlgorithmException | NoSuchProviderException | NoSuchPaddingException e) {
            throw new RuntimeException(e);
        } catch (InvalidAlgorithmParameterException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException e) {
            if (debug) throw new RuntimeException(e);
            throw new EncryptionException("failed to encrypt", e, EncryptionException.ERR_UNKNOWN);
        }
    }

    public byte[] decrypt(byte[] input) throws EncryptionException {
        ByteBuffer buffer = ByteBuffer.wrap(input);
        byte[] checksum = new byte[20];
        buffer.get(checksum);
        byte[] encryptedKey = new byte[128];
        buffer.get(encryptedKey);
        byte[] iv = new byte[16];
        buffer.get(iv);
        byte[] payload = new byte[(buffer.array().length) - (checksum.length + encryptedKey.length + iv.length)];
        buffer.get(payload);
        byte[] actualCheckSum = SHA1.sha1(ByteBuffer.allocate(input.length - 20)
                .put(encryptedKey)
                .put(iv)
                .put(payload).array()
        );
        checkSum(checksum, actualCheckSum);
        try {
            PrivateKey privateKeyForThisUser = privatePublicKeySource.getPrivateKeyForThisUser();
            if (privateKeyForThisUser == null) {
                throw new EncryptionException("private key for current user missing",
                        EncryptionException.PRIVATE_KEY_NOT_FOUND);
            }
            return AES.aesDecrypt
                    (rsaDecrypt(privateKeyForThisUser,
                            encryptedKey), iv, payload);
        } catch (InvalidKeyException e) {
            if (debug) throw new RuntimeException(e);
            throw new EncryptionException("invalid public key", INVALID_PUBLIC_KEY);
        } catch (NoSuchAlgorithmException | NoSuchProviderException | NoSuchPaddingException e) {
            throw new RuntimeException(e);
        } catch (InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
            if (debug) throw new RuntimeException(e);
            throw new EncryptionException("failed to encrypt", e, EncryptionException.ERR_UNKNOWN);
        }
    }

    private void checkSum(byte[] expected, byte[] actual) throws EncryptionException {
        if (!Arrays.equals(expected, actual)) {
            throw new EncryptionException("integrity check failed", EncryptionException.INTEGRITY_CHECK_FAILED);
        }
    }

    /**
     * encrypts a file and returns the key index of the random key used
     *
     * @param recipient the receiver of the file
     * @param in        the input file
     * @param out       the output encrypted file
     * @return the encrypted form of the key( key is encryprypted using RSA)
     * @throws IOException
     * @throws EncryptionException
     */
    public byte[] ecryptFile(String recipient, File in, File out) throws IOException, EncryptionException {
        InputStream iStream = new FileInputStream(in);
        OutputStream oStream = new FileOutputStream(out);
        try {
            byte[] key = new byte[16];
            new SecureRandom().nextBytes(key);
            AES.aesEncryptWithSHA1Integrity(key, new byte[16], iStream, oStream);
            return RSA.rsaEncrypt(privatePublicKeySource.getPublicKeyFor(recipient), key);
        } catch (NoSuchPaddingException | NoSuchAlgorithmException |
                InvalidKeyException | InvalidAlgorithmParameterException |
                NoSuchProviderException | BadPaddingException |
                IllegalBlockSizeException e) {
            if (debug) {
                throw new RuntimeException(e);
            }
            throw new EncryptionException(e.getMessage(), EncryptionException.ENCRYPTION_FAILED);
        } catch (IOException e) {
            iStream.close();
            oStream.close();
            throw e;
        }
    }

    /**
     * encrypts a file and returns the key index of the random key used
     *
     * @param encryptedKey the key for decryption.(key is rsaEncrypted)
     * @param in           the input file
     * @param out          the output encrypted file
     * @throws IOException
     * @throws EncryptionException
     */
    public void decryptFile(byte[] encryptedKey, File in, File out) throws IOException, EncryptionException {
        try {
            AES.aesDecryptWithSHA1Integrity(rsaDecrypt(privatePublicKeySource
                            .getPrivateKeyForThisUser(), encryptedKey),
                    in, out);
        } catch (NoSuchPaddingException | NoSuchAlgorithmException |
                InvalidKeyException | InvalidAlgorithmParameterException |
                NoSuchProviderException | BadPaddingException |
                IllegalBlockSizeException e) {
            if (debug) {
                throw new RuntimeException(e);
            }
            throw new EncryptionException(e.getMessage(), EncryptionException.DECRYPTION_FAILED);
        }
    }


    public interface PrivatePublicKeySource {
        PrivateKey getPrivateKeyForThisUser() throws EncryptionException;

        PublicKey getPublicKeyFor(String userId) throws EncryptionException;
    }

    public static class EncryptionException extends Exception {
        public static final int INTEGRITY_CHECK_FAILED = 1,
                PUBLIC_KEY_NOT_FOUND = 2, PRIVATE_KEY_NOT_FOUND = 3,
                INVALID_PUBLIC_KEY = 4;
        public static final int ERR_UNKNOWN = -1;
        public static final int ENCRYPTION_FAILED = 4;
        public static final int DECRYPTION_FAILED = 5;
        private final int errorCode;

        public EncryptionException(String message, int errorCode) {
            super(message);
            this.errorCode = errorCode;
        }

        public EncryptionException(String message, Throwable cause, int errorCode) {
            super(message, cause);
            this.errorCode = errorCode;
        }

        public int getErrorCode() {
            return errorCode;
        }
    }

}
