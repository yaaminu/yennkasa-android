package com.idea.data;

import android.content.Context;
import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
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
    public static final String FIELD_ID = "userId",
            FIELD_NAME = "name",
            FIELD_ADMIN = "admin",
            FIELD_MEMBERS = "members",
            FIELD_TYPE = Message.FIELD_TYPE,
            FIELD_LAST_ACTIVITY = "lastActivity",
            FIELD_PASSWORD = "password",
            FIELD_COUNTRY = "country",
            FIELD_HAS_CALL = "hasCall";
    @PrimaryKey
    private String userId;

    private String name, password, DP, country;
    private long lastActivity, accountCreated;
    private RealmList<User> members; //a group will be a user with its members represented by this field.
    private User admin; // this represents admins for a group
    private int type;
    private boolean hasCall;

    //required no-arg c'tor
    public User() {
    }

    //copy constructor
    @Deprecated
    public User(User other) {
        //realm forces us to use setters and getters everywhere for predictable results
        //null check because is good not to throw in constructors(not so sure if its true)
        if (other != null) {
            this.setUserId(other.getUserId());
            this.setAccountCreated(other.getAccountCreated());
            this.setPassword(other.getPassword());
            this.setLastActivity(other.getLastActivity());
            this.setName(other.getName());
            this.setType(other.getType());
            this.setAdmin(other.getAdmin());
            this.setDP(other.getDP());
            this.setCountry(other.getCountry());
            this.setMembers(other.getMembers());
            this.setHasCall(other.getHasCall());
        }
    }

    public boolean getHasCall() {
        return hasCall;
    }

    public void setHasCall(boolean hasCall) {
        this.hasCall = hasCall;
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

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
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


    public RealmList<User> getMembers() {
        return members;
    }

    public void setMembers(RealmList<User> members) {
        this.members = members;
    }

    public static boolean isGroup(User user) {
        return user.getType() == TYPE_GROUP;
    }

    public static User copy(User other) {
        if (other == null) {
            throw new IllegalArgumentException("null!");
        }

        User clone = new User();
        clone.setUserId(other.getUserId());
        clone.setAccountCreated(other.getAccountCreated());
        clone.setPassword(other.getPassword());
        clone.setLastActivity(other.getLastActivity());
        clone.setName(other.getName());
        clone.setType(other.getType());
        clone.setAdmin(other.getAdmin());
        clone.setMembers(other.getMembers());
        clone.setDP(other.getDP());
        clone.setCountry(other.getCountry());
        clone.setHasCall(other.getHasCall());
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

    public static List<User> copy(Iterable<User> users, ContactsManager.Filter<User> filter) {
        if (users == null) throw new IllegalArgumentException("users may not be null!");
        List<User> copied = new ArrayList<>(5);
        for (User user : users) {
            if (filter != null && !filter.accept(user)) {
                continue;
            }
            copied.add(User.copy(user));
        }
        return copied;
    }

    public static synchronized String generateGroupId(String groupName) {
        return groupName + "@" + UserManager.getInstance().getCurrentUser().getUserId();
    }

    @Nullable
    public static RealmList<User> aggregateUsers(Realm realm, Collection<String> membersId, ContactsManager.Filter<User> filter) {
        RealmList<User> members = new RealmList<>();
        for (String id : membersId) {
            User user = realm.where(User.class).equalTo(FIELD_ID, id).findFirst();
            if (filter == null || filter.accept(user)) {
                members.add(user);
            }
        }
        return members;
    }


    public static List<String> aggregateUserIds(Collection<User> users, ContactsManager.Filter<User> filter) {
        List<String> members = new ArrayList<>();
        for (User user : users) {
            String userId = user.getUserId();
            if (filter == null || filter.accept(user)) {
                members.add(userId);
            }
        }
        return members;
    }

    public static Realm Realm(Context context) {
        return Realm.getInstance(context);
    }
}
