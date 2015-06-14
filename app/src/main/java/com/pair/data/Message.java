package com.pair.data;

import android.app.Application;
import android.util.Log;

import com.pair.util.Config;
import com.pair.util.UserManager;

import java.util.Date;

import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;
import io.realm.annotations.RealmClass;

/**
 * @author Null-Pointer on 5/26/2015.
 */
@SuppressWarnings("unused")
@RealmClass
public class Message extends RealmObject {

    public static final int STATE_PENDING = 0x3e9,
            STATE_SENT = 0x3ea,
            STATE_RECEIVED = 0x3eb,
            STATE_SEEN = 0x3ec,
            STATE_SEND_FAILED = 0x3ed;

    public static final int TYPE_TEXT_MESSAGE = 0x3ee,
            TYPE_BIN_MESSAGE = 0x3ef,
            TYPE_PICTURE_MESSAGE = 0x3f0,
            TYPE_VIDEO_MESSAGE = 0x3f1,
            TYPE_DATE_MESSAGE = 0x3f2,
            TYPE_TYPING_MESSAGE = 0x3f3;
    private static final String TAG = Message.class.getSimpleName();

    //    public static final String
    @PrimaryKey
    private String id;

    private String from; //sender's id
    private String to; //recipient's id
    private String messageBody;
    private Date dateComposed;
    private int state;
    private int type;

    public Message() {
    } //required no-arg c'tor;

    public Message(Message message) {
        this.from = message.getFrom();
        this.to = message.getTo();
        this.id = message.getId();
        this.type = message.getType();
        this.dateComposed = message.getDateComposed();
        this.messageBody = message.getMessageBody();
        this.state = message.getState();
    }
    public void setType(int type) {
        this.type = type;
    }

    public int getType() {
        return type;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getMessageBody() {
        return messageBody;
    }

    public void setMessageBody(String messageBody) {
        this.messageBody = messageBody;
    }

    public Date getDateComposed() {
        return dateComposed;
    }

    public void setDateComposed(Date dateComposed) {
        this.dateComposed = dateComposed;
    }

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }

    public static String generateIdPossiblyUnique() {
        Application appContext = Config.getApplicationContext();
        Realm realm = Realm.getInstance(appContext);
        long count = realm.where(Message.class).count() + 1;
        String id = count + "@" + UserManager.getInstance(appContext).getCurrentUser().get_id() + "@" + System.currentTimeMillis();
        Log.i(TAG, "generated message id: " + id);
        return id;
    }
}
