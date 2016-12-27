package com.yennkasa.security;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Created by yaaminu on 12/22/16.
 */
class SHA1 {
    static {
        Crypto.initIfRequried();
    }

    /**
     * calculate the sha1 digest of {@code input}
     *
     * @param input the input  data
     * @return sha1 digest of the {@code input}
     */
    public static byte[] sha1(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("sha1");
            digest.reset();
            //re-use param input
            input = digest.digest(input);
            return input;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e.getCause());
        }
    }

    /**
     * calculate the sha1 digest of {@code input} by salting with
     * {@code salt}
     *
     * @param input the input  data
     * @param salt  the salt to use
     * @return sha1 digest of the {@code input}
     */
    public static byte[] sha1(byte[] input, byte[] salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("sha1");
            digest.reset();
            //re-use param input
            digest.update(input);
            input = digest.digest(salt);
            return input;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e.getCause());
        }
    }
}
