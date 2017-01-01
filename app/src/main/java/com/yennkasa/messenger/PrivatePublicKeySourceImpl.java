package com.yennkasa.messenger;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.util.LruCache;

import com.yennkasa.data.UserManager;
import com.yennkasa.security.MessageEncryptor;
import com.yennkasa.security.RSA;

import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * Created by yaaminu on 12/28/16.
 */
class PrivatePublicKeySourceImpl implements MessageEncryptor.PrivatePublicKeySource {

    private final PrivateKey privateKey;
    private final LruCache<String, PublicKey> publicKeysCache;
    private static PrivatePublicKeySourceImpl instance = null;

    public PrivatePublicKeySourceImpl() {
        this.privateKey = createPrivateKeyFromBase64EncodedString
                (UserManager.getInstance().getPrivateKey());
        this.publicKeysCache = new LruCache<>(6);
    }

    static synchronized PrivatePublicKeySourceImpl getInstance() {
        if (instance == null) {
            instance = new PrivatePublicKeySourceImpl();
        }
        return instance;
    }

    @NonNull
    @Override
    public PrivateKey getPrivateKeyForThisUser() throws MessageEncryptor.EncryptionException {
        return this.privateKey;
    }

    @Override
    @Nullable
    public synchronized PublicKey getPublicKeyFor(String userId) throws MessageEncryptor.EncryptionException {
        PublicKey publicKey = publicKeysCache.get(userId);
        if (publicKey != null) return publicKey;
        String publicKeyString = UserManager.getInstance().publicKeyForUser(userId);
        if (publicKeyString == null) {
            throw new MessageEncryptor.EncryptionException("public key not found", MessageEncryptor.EncryptionException.PUBLIC_KEY_NOT_FOUND);
        }
        publicKey = RSA.getRSAPublicKeyFromString(publicKeyString);
        publicKeysCache.put(userId, publicKey);
        return publicKey;
    }

    private PrivateKey createPrivateKeyFromBase64EncodedString(String privateKeyBase64) {
        return RSA.getRSAPrivateKeyFromString(privateKeyBase64);
    }

    public void onTrimMemory(int level) {
        this.publicKeysCache.evictAll();
    }
}
