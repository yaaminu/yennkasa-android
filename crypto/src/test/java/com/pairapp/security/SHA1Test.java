package com.pairapp.security;

import org.junit.Assert;
import org.junit.Test;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Created by yaaminu on 12/22/16.
 */
public class SHA1Test {
    @Test
    public void sha1() throws Exception {
        String input = "Some input";
        byte[] checksum = SHA1.sha1(input.getBytes("UTF-8"));
        Assert.assertArrayEquals(checksum, sha12(input.getBytes()));
    }

    private static byte[] sha12(byte[] input) {
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

}
