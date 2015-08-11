package com.pair.util;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.google.gson.JsonObject;
import com.pair.adapter.MessageJsonAdapter;
import com.pair.data.Conversation;
import com.pair.data.Message;
import com.pair.data.User;
import com.pair.messenger.MessageProcessor;

import java.util.Date;

import io.realm.Realm;
import io.realm.RealmList;

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
        realm.commitTransaction();
        realm.close();
    }

    public static Message seedIncomingMessages() {
        Realm realm = Realm.getInstance(Config.getApplicationContext());
        User thisUser = UserManager.getInstance().getMainUser(),
                otherUser = realm.where(User.class).notEqualTo(User.FIELD_ID, thisUser.get_id()).notEqualTo(User.FIELD_TYPE, User.TYPE_GROUP).findFirst();
        return seedIncomingMessages(otherUser.get_id(), thisUser.get_id());
    }

    public static Message seedIncomingMessages(String sender, String recipient) {
        return seedIncomingMessages(sender, recipient, Message.TYPE_PICTURE_MESSAGE, "3d89c06a2f21191fcc214a6e4eab5a1f");
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
