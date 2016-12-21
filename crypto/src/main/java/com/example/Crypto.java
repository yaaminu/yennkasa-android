package com.example;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.security.spec.RSAKeyGenParameterSpec;


/**
 * @author by yaaminu on 12/21/16.
 */
public class Crypto {
    static {
        Security.insertProviderAt(new org.spongycastle.jce.provider.BouncyCastleProvider(), 1);
    }

    private static final String TAG = Crypto.class.getSimpleName();

    public static class RSA {


        private static final int KEY_SIZE = 1024;

        public static KeyPair generatePublicPrivateKeyPair() {
            try {
                SecureRandom random = new SecureRandom();
                RSAKeyGenParameterSpec spec = new RSAKeyGenParameterSpec(KEY_SIZE, RSAKeyGenParameterSpec.F4);
                KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA", "SC");
                generator.initialize(spec, random);

                return generator.generateKeyPair();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

    }
}
