package com.pair.data;

import java.util.Date;

import io.realm.RealmObject;
import io.realm.annotations.RealmClass;

/**
 * Created by Null-Pointer on 5/30/2015.
 */
@RealmClass
public class Chat extends RealmObject {
    private User peer; //other peer in chat
    private String summary;
    private Date lastActiveTime;
    private Message lastMessage;

    public Message getLastMessage() {
        return lastMessage;
    }

    public void setLastMessage(Message lastMessage) {
        this.lastMessage = lastMessage;
    }

    public User getPeer() {
        return peer;
    }

    public void setPeer(User peer) {
        this.peer = peer;
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

}
