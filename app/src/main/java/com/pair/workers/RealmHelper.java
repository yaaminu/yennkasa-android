package com.pair.workers;

import android.content.Context;

import io.realm.Realm;

/**
 * @author Null-Pointer on 6/10/2015.
 */
public class RealmHelper {
    // FIXME: 6/16/2015 remove this helper class
    public static void runRealmOperation(final Context context) {
        //helper method for cleaning up real and seeding it with data
        Realm realm = Realm.getInstance(context);
        realm.beginTransaction();
//        RealmResults<Message> messages = realm.where(Message.class).findAll();
//        for (int i=0; i<messages.size(); i++) {
//            messages.get(i).setState(Message.STATE_PENDING);
//        }
        realm.commitTransaction();
        realm.close();
    }
}
