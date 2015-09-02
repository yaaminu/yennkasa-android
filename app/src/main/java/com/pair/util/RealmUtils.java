package com.pair.util;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import com.google.gson.JsonObject;
import com.pair.Config;
import com.pair.adapter.MessageJsonAdapter;
import com.pair.data.Conversation;
import com.pair.data.Message;
import com.pair.data.User;
import com.pair.data.UserManager;
import com.pair.messenger.MessageProcessor;

import java.util.Date;

import io.realm.Realm;
import io.realm.RealmList;
import io.realm.RealmResults;

/**
 * @author Null-Pointer on 6/10/2015.
 */
public class RealmUtils {
    public static final String TAG = RealmUtils.class.getSimpleName();

    // FIXME: 6/16/2015 remove this helper class
    public static void runRealmOperation(final Context context) {
        //helper method for cleaning up realm and seeding it with data
        Realm realm = Realm.getInstance(context);
        realm.beginTransaction();
        realm.clear(Message.class);
        realm.clear(Conversation.class);
        RealmResults<User> users = realm.where(User.class).findAll();
        for (int i = 0; i < users.size(); i++) {
            User user = users.get(i);
            if (TextUtils.isEmpty(user.getDP()))
                user.setDP("avatar_empty");
        }
        for (int i = 0; i < 10; i++) {
            Log.d(TAG, Base64.encodeToString("http://facebook.com/foo/bar/fooo/faa/foo/baflkfa".getBytes(), Base64.URL_SAFE));
        }
        //try {
//            User user = User.copy(UserManager.getInstance().getCurrentUser());
//            for(int i=0; i<20;i++) {
//                user.setUserId((2348033557792L + i) + "");
//                user.setName("New user " + i);
//                realm.copyToRealm(user);
//            }
//        } catch (Exception e) {

        //    }
        realm.commitTransaction();
        realm.close();
    }

    public static Message seedIncomingMessages() {
        Realm realm = Realm.getInstance(Config.getApplicationContext());
        User thisUser = UserManager.getInstance().getCurrentUser(),
                otherUser = realm.where(User.class).notEqualTo(User.FIELD_ID, thisUser.getUserId()).notEqualTo(User.FIELD_TYPE, User.TYPE_GROUP).findFirst();
        return seedIncomingMessages(otherUser.getUserId(), thisUser.getUserId());
    }

    public static Message seedIncomingMessages(String sender, String recipient) {
        return seedIncomingMessages(sender, recipient, Message.TYPE_PICTURE_MESSAGE,
                "http://files.parsetfss.com/5b50e395-c58d-4d8e-829a-8d98173a63cd/tfss-ab1fcf5e-6b77-4f1a-a6b2-837cfdc331dc-IMG_20150802_162640.jpeg");
    }

    public static Message seedIncomingMessages(String sender, String recipient, int type, String messageBody) {
        Realm realm = Realm.getInstance(Config.getApplicationContext());
        Message message = new Message();
        message.setTo(recipient);
        message.setFrom(sender);
        message.setType(type);
        message.setMessageBody(messageBody);
        message.setId(Message.generateIdPossiblyUnique());
        message.setState(Message.STATE_PENDING);
        message.setDateComposed(new Date());
        realm.close();
        return message;
    }

    private static void seedOutgoingMessages() {
        Realm realm = Realm.getInstance(Config.getApplicationContext());
        realm.beginTransaction();
        User user = UserManager.getInstance(Config.getApplication()).getCurrentUser();
        RealmList<Message> messages = new RealmList<>();
        for (int i = 0; i < 10; i++) {
            Message message = realm.createObject(Message.class);
            message.setTo("0266349205");
            message.setFrom(user.getUserId());
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

    private static void testMessageProcessor(Message messages) {
        JsonObject object = MessageJsonAdapter.INSTANCE.toJson(messages);
        Context context = Config.getApplicationContext();
        Bundle bundle = new Bundle();
        bundle.putString("message", object.toString());
        Intent intent = new Intent(context, MessageProcessor.class);
        intent.putExtras(bundle);
        context.startService(intent);
    }

}
