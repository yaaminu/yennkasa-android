package com.pair.data;

import java.util.Date;

import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;
import io.realm.annotations.RealmClass;

/**
 * @author by Null-Pointer on 5/30/2015.
 */
@RealmClass
public class Conversation extends RealmObject {
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

    public boolean isActive(){
        return this.active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
