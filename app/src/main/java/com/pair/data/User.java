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
    public static final String FIELD_ID = "_id",
            FIELD_NAME = "name",
            FIELD_STATUS = "status",
            FIELD_ADMIN = "admin",
            FIELD_MEMBERS = "members",
            FIELD_TYPE = Message.FIELD_TYPE,
            FIELD_LAST_ACTIVITY = "lastActivity",
            FIELD_GCM_REG_ID = "gcmRegId",
            FIELD_ACCOUNT_CREATED = "accountCreated",
            FIELD_PASSWORD = "password",
            FIELD_COUNTRY = "country";
    @PrimaryKey
    private String _id;

    private String gcmRegId, name, password, status, localName, DP, country;
    private long lastActivity, accountCreated;
    private RealmList<User> members; //a group will be a user with its members represented by this field.
    private User admin; // this represents admins for a group
    private int type;

    //required no-arg c'tor
    public User() {
    }

    //copy constructor
    @Deprecated
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
            this.setDP(other.getDP());
            this.setCountry(other.getCountry());
            this.setLocalName(other.getLocalName());
            this.setMembers(other.getMembers());
        }
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getDP() {
        return DP;
    }

    public void setDP(String DP) {
        this.DP = DP;
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

    public String getLocalName() {
        return localName;
    }

    public void setLocalName(String localName) {
        this.localName = localName;
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

    public static User copy(User other) {
        if (other == null) {
            throw new IllegalArgumentException("null!");
        }

        User clone = new User();
        clone.set_id(other.get_id());
        clone.setAccountCreated(other.getAccountCreated());
        clone.setGcmRegId(other.getGcmRegId());
        clone.setPassword(other.getPassword());
        clone.setStatus(other.getStatus());
        clone.setLastActivity(other.getLastActivity());
        clone.setName(other.getName());
        clone.setType(other.getType());
        clone.setAdmin(other.getAdmin());
        clone.setMembers(other.getMembers());
        clone.setLocalName(other.getLocalName());
        clone.setDP(other.getDP());
        clone.setCountry(other.getCountry());
        return clone;
    }

    public static List<User> copy(Iterable<User> users) {
        if (users == null) throw new IllegalArgumentException("users may not be null!");
        List<User> copied = new ArrayList<>(5);
        for (User user : users) {
            copied.add(User.copy(user));
        }
        return copied;
    }

    public static String generateGroupId(String groupName) {
        return groupName + "@" + UserManager.getInstance().getMainUser().get_id();
    }

    @Nullable
    public static RealmList<User> aggregateUsers(Realm realm, List<String> membersId, ContactsManager.Filter<User> filter) {
        RealmList<User> members = new RealmList<>();
        for (String id : membersId) {
            User user = realm.where(User.class).equalTo(FIELD_ID, id).findFirst();
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
