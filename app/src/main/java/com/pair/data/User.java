package com.pair.data;

import io.realm.RealmList;
import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;
import io.realm.annotations.RealmClass;

/**
 * @author by Null-Pointer on 5/27/2015.
 */
@SuppressWarnings("unused")
@RealmClass
public class User extends RealmObject {
    public static final int TYPE_GROUP = 0x3e8;
    public static final int TYPE_NORMAL_USER = 0x3e9;
    @PrimaryKey
    private String _id;

    private String gcmRegId;
    private String name;
    private String password;
    private String status, DP;
    private long lastActivity, accountCreated;
    private RealmList<User> members; //a group will be a user with its members represented by this field.
    private RealmList<User> admins; // this represents admins for a group
    private int type;

    //required no-arg c'tor
    public User() {
    }

    //copy constructor
    public User(User other) {
        //realm forces us to use setters and getters everywhere for predictable results
        //null check because is good not to throw in constructors(not so sure if its true)
        if (other != null) {
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

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public RealmList<User> getAdmins() {
        return admins;
    }

    public void setAdmins(RealmList<User> admins) {
        this.admins = admins;
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

    public RealmList<User> getMembers() {
        return members;
    }

    public void setMembers(RealmList<User> members) {
        this.members = members;
    }
}
