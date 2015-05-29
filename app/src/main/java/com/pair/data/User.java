package com.pair.data;

import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;
import io.realm.annotations.RealmClass;

/**
 * Created by Null-Pointer on 5/27/2015.
 */
@RealmClass
public class User extends RealmObject {
    @PrimaryKey
    private String _id;

    private String gcmRegId;

    private String name;
    private String password;
    private String status, DP;
    private long lastActivity, accountCreated;

    //required no-arg c'tor
    public User(){}

    public User(User other){
        //realm forces us to use setters and getters everywhere for predictable results
        if(other != null){
            this.set_id(other.get_id());
            this.setAccountCreated(other.getAccountCreated());
            this.setGcmRegId(other.getGcmRegId());
            this.setPassword(other.getPassword());
            this.setStatus(other.getStatus());
            this.setLastActivity(other.getLastActivity());
            this.setDP(other.getDP());
            this.setName(other.getName());
        }
    }
    public String get_id() {
        return _id;
    }

    public void set_id(String _id) {
        this._id = _id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDP() {
        return DP;
    }

    public void setDP(String DP) {
        this.DP = DP;
    }

    public long getLastActivity() {
        return lastActivity;
    }

    public void setLastActivity(long lastActivity) {
        this.lastActivity = lastActivity;
    }

    public long getAccountCreated() {
        return accountCreated;
    }

    public void setAccountCreated(long accountCreated) {
        this.accountCreated = accountCreated;
    }

    public String getGcmRegId() {
        return gcmRegId;
    }

    public void setGcmRegId(String gcmRegId) {
        this.gcmRegId = gcmRegId;
    }
}
