package com.pair.data;

import android.content.Context;
import android.text.format.DateUtils;

import com.pair.util.Config;
import com.pair.util.UserManager;

import java.util.Date;

import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;
import io.realm.annotations.RealmClass;

import static com.pair.data.Message.TYPE_DATE_MESSAGE;

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
    public static void newSession(Realm realm, Conversation conversation) {
        if (conversation == null) {
            throw new IllegalArgumentException("conversation is null");
        }
        Context context = Config.getApplicationContext();

        String formatted = DateUtils.formatDateTime(context, new Date().getTime(), DateUtils.FORMAT_NUMERIC_DATE);
        Message message = realm.where(Message.class)
                .equalTo(Message.FIELD_ID, conversation.getPeerId() + formatted)
                .findFirst();
        if (message == null) { //session not yet set up!
            message = realm.createObject(Message.class);
            message.setId(conversation.getPeerId() + formatted);
            message.setMessageBody(formatted);
            message.setTo(UserManager.getInstance().getMainUser().get_id());
            message.setFrom(conversation.getPeerId());
            message.setDateComposed(new Date(System.currentTimeMillis()));
            message.setType(TYPE_DATE_MESSAGE);
        }
    }
}
