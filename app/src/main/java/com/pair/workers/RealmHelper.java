package com.pair.workers;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.pair.adapter.MessageJsonAdapter;
import com.pair.data.Message;
import com.pair.data.User;
import com.pair.messenger.MessageProcessor;
import com.pair.util.Config;
import com.pair.util.UserManager;

import java.util.Date;

import io.realm.Realm;
import io.realm.RealmList;

/**
 * @author Null-Pointer on 6/10/2015.
 */
public class RealmHelper {
    public static final String TAG = RealmHelper.class.getSimpleName();
    // FIXME: 6/16/2015 remove this helper class
    public static void runRealmOperation(final Context context) {
        //helper method for cleaning up realm and seeding it with data
        Realm realm = Realm.getInstance(context);
        realm.beginTransaction();
        realm.where(Message.class).findAll().clear();
        realm.commitTransaction();
        realm.close();
    }

    private static void seedIncomingMessages() {
        Realm realm = Realm.getInstance(Config.getApplicationContext());
        realm.beginTransaction();
        User user = UserManager.getInstance(Config.getApplication()).getMainUser();
        RealmList<Message> messages = new RealmList<>();
        for (int i = 0; i < 10; i++) {
            Message message = realm.createObject(Message.class);
            message.setTo(user.get_id());
            message.setFrom("0266349205");
            message.setMessageBody("message body " + i);
            message.setType(Message.TYPE_TEXT_MESSAGE);
            message.setId(Message.generateIdPossiblyUnique());
            message.setState(Message.STATE_PENDING);
            message.setDateComposed(new Date());
            messages.add(message);
        }
        realm.commitTransaction();
        realm.close();
    }

    private static void seedOutgoingMessages() {
        Realm realm = Realm.getInstance(Config.getApplicationContext());
        realm.beginTransaction();
        User user = UserManager.getInstance(Config.getApplication()).getMainUser();
        RealmList<Message> messages = new RealmList<>();
        for (int i = 0; i < 10; i++) {
            Message message = realm.createObject(Message.class);
            message.setTo("0266349205");
            message.setFrom(user.get_id());
            message.setMessageBody("message body " + i);
            message.setType(Message.TYPE_TEXT_MESSAGE);
            message.setId(Message.generateIdPossiblyUnique());
            message.setState(Message.STATE_PENDING);
            message.setDateComposed(new Date());
            messages.add(message);
        }
        realm.commitTransaction();
        realm.close();
    }

    private static void testMessageProcessr(User user, RealmList<Message> messages) {
        Realm realm = Realm.getInstance(Config.getApplicationContext());
        JsonArray array = MessageJsonAdapter.INSTANCE.toJson(messages);
        for (JsonElement jsonElement : array) {
            Context context = Config.getApplicationContext();
            Bundle bundle = new Bundle();
            bundle.putString("message", jsonElement.toString());
            Intent intent = new Intent(context, MessageProcessor.class);
            intent.putExtras(bundle);
            realm.where(Message.class).equalTo("to", user.get_id()).findAll().clear();
            context.startService(intent);
        }
    }

    private static void cleanUsers() {
        Realm realm = Realm.getInstance(Config.getApplicationContext());
        realm.beginTransaction();
        realm.where(User.class).findAll().clear();
        realm.commitTransaction();
    }
}
