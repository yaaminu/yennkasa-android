package com.yennkasa.ui;

import android.support.annotation.NonNull;

import com.yennkasa.data.Conversation;
import com.yennkasa.data.User;

import io.realm.Realm;

/**
 * Created by yaaminu on 1/12/17.
 */
public interface UserProvider {

    @NonNull
    User currentUser();

    boolean hideNotice();

    @NonNull
    Realm realm();

    @NonNull
    Conversation getConversation();

    @NonNull
    Realm conversationRealm();
}
