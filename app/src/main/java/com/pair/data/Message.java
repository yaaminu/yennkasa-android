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

    public static final int PENDING = 1001,
            SENT = 1002,
            RECEIVED = 1003,
            SEEN = 1004,
            SEND_FAILED = 1005;

    @PrimaryKey
    private long id;

    private String from;
    private String to;
    private String messageBody;
    private Date dateComposed;
    private int state;

    public long getId() {
        return id;
    }

    public void setId(long id) {
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
//    int PENDING = 1001,
//            SENT = 1002,
//            RECEIVED = 1003,
//            SEND_FAILED = 1004;
//}