package com.idea.data;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.google.gson.JsonObject;
import com.idea.messenger.MessageProcessor;
import com.idea.util.Config;

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
        Realm realm = User.Realm(context);
        realm.beginTransaction();
//        realm.clear(Message.class);
//        realm.clear(Conversation.class);
//        RealmResults<User> users = realm.where(User.class).findAll();
//        for (int i = 0; i < users.size(); i++) {
//            User user = users.get(i);
//            if (TextUtils.isEmpty(user.getDP()))
//                user.setDP("avatar_empty");
//        }
//        try {
//            User user = User.copy(UserManager.getInstance().getCurrentUser());
//            int i = 0;
//            while (i++ < 20) {
//                user.setUserId(23326656422L + "" + i);
//                user.setName("@username " + i);
//                user.setType(User.TYPE_NORMAL_USER);
//                user.setDP("avatar_empty");
//                realm.copyToRealm(user);
//            }
//        } catch (Exception e) {
//
//        }
//        try {
//            User group = User.copy(UserManager.getInstance().getCurrentUser());
//            int i = 0;
//            while (i++ < 20) {
//                group.setUserId("groupName@" + "233204441069");
//                group.setName("group " + i);
//                group.setType(User.TYPE_GROUP);
//                group.setAdmin(realm.where(User.class).notEqualTo(User.FIELD_TYPE, User.TYPE_GROUP).findFirst());
//                group.setMembers(new RealmList<User>());
//                RealmResults<User> all = realm.where(User.class).notEqualTo(User.FIELD_TYPE, User.TYPE_GROUP).findAll();
//                List<User> users = User.copy(all);
//                for (User user : users) {
//                    group.getMembers().add(user);
//                }
//                realm.copyToRealmOrUpdate(group);
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
        realm.commitTransaction();
        realm.close();
    }

    public static Message seedIncomingMessages() {
        Realm realm = User.Realm(Config.getApplicationContext());
        User thisUser = UserManager.getInstance().getCurrentUser(),
                otherUser = realm.where(User.class).notEqualTo(User.FIELD_ID, thisUser.getUserId()).notEqualTo(User.FIELD_TYPE, User.TYPE_GROUP).findFirst();
        return seedIncomingMessages(otherUser.getUserId(), thisUser.getUserId());
    }

    public static Message seedIncomingMessages(String sender, String recipient) {
        return seedIncomingMessages(sender, recipient, Message.TYPE_PICTURE_MESSAGE,
                "http://10.0.3.2:5000/fileApi/4ad3ea45e9908775c731a2b060af1267ac251f684ad3ea45e9908775c731a2b060af1267ac251f68_233204441069.jpg");
    }

    public static Message seedIncomingMessages(String sender, String recipient, int type, String messageBody) {
        Message message = new Message();
        message.setTo(recipient);
        message.setFrom(sender);
        message.setType(type);
        message.setMessageBody(messageBody);
        message.setId(Message.generateIdPossiblyUnique());
        message.setState(Message.STATE_PENDING);
        message.setDateComposed(new Date());
        return message;
    }

    private static void seedOutgoingMessages() {
        Realm realm = Message.REALM(Config.getApplicationContext());
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
