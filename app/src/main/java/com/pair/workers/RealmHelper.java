package com.pair.workers;

import android.content.Context;

import com.pair.data.Message;
import com.pair.util.Config;
import com.pair.util.UserManager;

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
        Message message = realm.where(Message.class).findFirst();
        message.setFrom("00000000");
        message.setTo(UserManager.getInstance(Config.getApplication()).getCurrentUser().get_id());
        realm.commitTransaction();
        realm.close();
    }
}
