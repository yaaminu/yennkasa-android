package com.pair.workers;

import android.content.Context;

import com.pair.data.Conversation;

import io.realm.Realm;

/**
 * @author Null-Pointer on 6/10/2015.
 */
public class RealmHelper {
    public static void runRealmOperation(final Context context) {
        //helper method for cleaning up real and seeding it with data
        Realm realm = Realm.getInstance(context);
        realm.beginTransaction();
        realm.where(Conversation.class).findAll().clear();
        realm.commitTransaction();
        realm.close();
    }
}
