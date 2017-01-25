package com.yennkasa.security;

import java.io.UnsupportedEncodingException;


/**
 * @author by yaaminu on 1/16/17.
 */

public class Base64 {

    public static byte[] encode(byte[] in) {
        return in;
//        return org.spongycastle.util.encoders.Base64.decode(in);
    }

    public static byte[] encode(String in) {
        try {
            return encode(in.getBytes("utf8"));
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public static String encodeToString(byte[] in) {
        return new String(in);
//        return org.spongycastle.util.encoders.Base64.toBase64String(in);
    }

    public static String encodeToString(String in) {
        try {
            return encodeToString(in.getBytes("utf8"));
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] decode(byte[] in) {
        return in;
//        return org.spongycastle.util.encoders.Base64.decode(in);
    }

    public static byte[] decode(String in) {
        return in.getBytes();
//        return org.spongycastle.util.encoders.Base64.decode(in);
    }

}
