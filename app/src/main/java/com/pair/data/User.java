package com.pair.data;

import android.support.annotation.Nullable;

import com.pair.util.UserManager;

import java.util.ArrayList;
import java.util.List;

import io.realm.Realm;
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
    private String status;
    private long lastActivity, accountCreated;
    private RealmList<User> members; //a group will be a user with its members represented by this field.
    private User admin; // this represents admins for a group
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
            this.setName(other.getName());
            this.setType(other.getType());
            this.setAdmin(other.getAdmin());
            this.setMembers(other.getMembers());
        }
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public User getAdmin() {
        return admin;
    }

    public void setAdmin(User admin) {
        this.admin = admin;
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

    public static String generateId(String groupName) {
        return groupName + "@" + UserManager.INSTANCE.getMainUser().get_id();
    }

    @Nullable
    public static RealmList<User> aggregateUsers(Realm realm, List<String> membersId, ContactsManager.Filter<User> filter) {
        RealmList<User> members = new RealmList<>();
        for (String id : membersId) {
            User user = realm.where(User.class).equalTo("_id", id).findFirst();
            if (filter == null || filter.accept(user)) {
                members.add(user);
            }
        }
        return members;
    }

    public static List<String> aggregateUserIds(RealmList<User> users, ContactsManager.Filter<User> filter) {
        List<String> members = new ArrayList<>();
        for (User user : users) {
            String userId = user.get_id();
            if (filter == null || filter.accept(user)) {
                members.add(userId);
            }
        }
        return members;
    }

}
