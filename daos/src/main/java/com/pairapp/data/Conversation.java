package com.pairapp.data;

import android.content.Context;

import com.pairapp.util.SimpleDateUtil;

import java.util.Date;

import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;
import io.realm.annotations.RealmClass;

import static com.pairapp.data.Message.TYPE_DATE_MESSAGE;

/**
 * @author by Null-Pointer on 5/30/2015.
 */
@SuppressWarnings("unused")
@RealmClass
public class Conversation extends RealmObject {


    public static final String FIELD_SUMMARY = "summary",
            FIELD_ACTIVE = "active",
            FIELD_LAST_MESSAGE = "lastMessage",
            FIELD_LAST_ACTIVE_TIME = "lastActiveTime",
            FIELD_PEER_ID = "peerId";
    @PrimaryKey
    private String peerId; //other peer in chat
    private String summary;
    private Date lastActiveTime;
    private Message lastMessage;
    private boolean active;

    public Message getLastMessage() {
        return lastMessage;
    }

    public void setLastMessage(Message lastMessage) {
        this.lastMessage = lastMessage;
    }

    public String getPeerId() {
        return peerId;
    }

    public void setPeerId(String peer) {
        this.peerId = peer;
    }

    public Date getLastActiveTime() {
        return lastActiveTime;
    }

    public void setLastActiveTime(Date lastActiveTime) {
        this.lastActiveTime = lastActiveTime;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public boolean isActive() {
        return this.active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @SuppressWarnings("ConstantConditions")
    public synchronized static boolean newSession(Realm realm, Conversation conversation) {
        if (conversation == null) {
            throw new IllegalArgumentException("conversation is null");
        }

        Date now = new Date();
        String formatted = SimpleDateUtil.formatSessionDate(now);
        Message message = realm.where(Message.class)
                .equalTo(Message.FIELD_ID, conversation.getPeerId() + formatted)
                .findFirst();
        if (message == null) { //session not yet set up!
            message = realm.createObject(Message.class);
            message.setId(conversation.getPeerId() + formatted);
            message.setMessageBody(formatted);
            message.setTo(UserManager.getInstance().getCurrentUser().getUserId());
            message.setFrom(conversation.getPeerId());
            message.setDateComposed(now);
            message.setType(TYPE_DATE_MESSAGE);
            return true;
        }
        return false;
    }

    public synchronized static void newConversation(Context context, String peerId) {
        newConversation(context, peerId, false);
    }

    public synchronized static void newConversation(Context context, String peerId, boolean active) {
        Realm realm = Realm(context);
        newConversation(realm, peerId, active);
        realm.close();
    }

    public synchronized static Conversation newConversation(Realm realm, String peerId) {
        return newConversation(realm, peerId, false);
    }

    public synchronized static Conversation newConversation(Realm realm, String peerId, boolean active) {
        Conversation newConversation = realm.where(Conversation.class).equalTo(Conversation.FIELD_PEER_ID, peerId).findFirst();
        realm.beginTransaction();
        if (newConversation == null) {
            newConversation = realm.createObject(Conversation.class);
            newConversation.setActive(active);
            newConversation.setPeerId(peerId);
            newConversation.setLastActiveTime(new Date());
            newConversation.setSummary("no message");
        }
        newSession(realm, newConversation);
        realm.commitTransaction();
        return newConversation;
    }

    public synchronized static Conversation newConversationWithoutSession(Realm realm, String peerId, boolean active) {
        Conversation newConversation = realm.where(Conversation.class).equalTo(Conversation.FIELD_PEER_ID, peerId).findFirst();
        if (newConversation == null) {
            realm.beginTransaction();
            newConversation = realm.createObject(Conversation.class);
            newConversation.setActive(active);
            newConversation.setPeerId(peerId);
            newConversation.setLastActiveTime(new Date());
            newConversation.setSummary("no message");
            realm.commitTransaction();
        }
        return newConversation;
    }

    public static Realm Realm(Context context) {
        return Message.REALM(context);
    }

    public static Conversation copy(Conversation conversation) {
        Conversation clone = new Conversation();
        clone.setActive(conversation.isActive());
        clone.setLastActiveTime(conversation.getLastActiveTime());
        clone.setPeerId(conversation.getPeerId());
        clone.setSummary(conversation.getSummary());
        clone.setLastMessage(conversation.getLastMessage());
        return clone;
    }
}
