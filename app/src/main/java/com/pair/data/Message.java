package com.pair.data;

import java.util.Date;

import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;
import io.realm.annotations.RealmClass;

/**
 * @author Null-Pointer on 5/26/2015.
 */
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

    @PrimaryKey
    private String id;

    private String from; //sender's id
    private String to; //recipient's id
    private String messageBody;
    private Date dateComposed;
    private int state;
    private int type;

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
}

//class MessageConstants {
//    int STATE_PENDING = 1001,
//            STATE_SENT = 1002,
//            STATE_RECEIVED = 1003,
//            STATE_SEND_FAILED = 1004;
//}