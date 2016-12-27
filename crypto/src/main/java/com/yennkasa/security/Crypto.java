package com.yennkasa.security;

import java.security.Provider;
import java.security.Security;


/**
 * @author by yaaminu on 12/21/16.
 */
public class Crypto {
    static {
        Security.insertProviderAt(new org.spongycastle.jce.provider.BouncyCastleProvider(), 1);
    }

    private static final String TAG = Crypto.class.getSimpleName();

    public static synchronized void initIfRequried() {
        Provider provider = Security.getProvider("SC");
        if (provider == null) {
            Security.insertProviderAt(new org.spongycastle.jce.provider.BouncyCastleProvider(), 1);
        }
    }
}
