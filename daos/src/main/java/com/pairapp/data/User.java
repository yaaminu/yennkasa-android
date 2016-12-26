package com.pairapp.data;

import android.content.Context;
import android.support.annotation.Nullable;

import com.pairapp.Errors.ErrorCenter;
import com.pairapp.util.Config;
import com.pairapp.util.PLog;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import io.realm.Realm;
import io.realm.RealmConfiguration;
import io.realm.RealmList;
import io.realm.RealmObject;
import io.realm.annotations.Index;
import io.realm.annotations.PrimaryKey;
import io.realm.annotations.RealmClass;
import io.realm.exceptions.RealmException;

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
            FIELD_COUNTRY = "country",
            FIELD_IN_CONTACTS = "inContacts",
            FIELD_DP = "DP", FIELD_VERSION = "version";

    private static final String TAG = User.class.getSimpleName();
    @PrimaryKey
    private String userId;

    @Index
    private String name;
    private String DP, country;
    private long lastActivity, accountCreated;
    private RealmList<User> members; //a group will be a user with its members represented by this field.
    private User admin; // this represents admins for a group
    private int type;
    private boolean inContacts;
    private int version;
    private String city;

    //required no-arg c'tor
    public User() {
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getCity() {
        return city;
    }

    public boolean getInContacts() {
        return inContacts;
    }

    public void setInContacts(boolean inContacts) {
        this.inContacts = inContacts;
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

    public void setVersion(int version) {
        this.version = version;
    }

    public int getVersion() {
        return version;
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
        clone.setLastActivity(other.getLastActivity());
        clone.setName(other.getName());
        clone.setType(other.getType());
        clone.setAdmin(other.getAdmin());
        clone.setMembers(other.getMembers());
        clone.setDP(other.getDP());
        clone.setCountry(other.getCountry());
        clone.setInContacts(other.getInContacts());
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
        List<User> copied = new ArrayList<>();
        for (User user : users) {
            try {
                if (filter != null && !filter.accept(user)) {
                    continue;
                }
            } catch (ContactsManager.Filter.AbortOperation e) {
                PLog.d(TAG, "copy operation aborted");
                break;
            }
            copied.add(User.copy(user));
        }
        return copied;
    }

    public static synchronized String generateGroupId(Realm realm, String groupName) {
        return groupName + "@" + UserManager.getInstance().getCurrentUser(realm).getUserId();
    }

    @Nullable
    public static RealmList<User> aggregateUsers(Realm realm, Collection<String> membersId, ContactsManager.Filter<User> filter) {
        RealmList<User> members = new RealmList<>();
        for (String id : membersId) {
            User user = UserManager.getInstance().fetchUserIfRequired(realm, id);
            try {
                if (filter == null || filter.accept(user)) {
                    members.add(user);
                }
            } catch (ContactsManager.Filter.AbortOperation e) {
                PLog.d(TAG, "aggregate operation aborted");
                break;
            }
        }
        return members;
    }


    public static List<String> aggregateUserIds(Collection<User> users, ContactsManager.Filter<User> filter) {
        List<String> members = new ArrayList<>();
        for (User user : users) {
            String userId = user.getUserId();
            try {
                if (filter == null || filter.accept(user)) {
                    members.add(userId);
                }
            } catch (ContactsManager.Filter.AbortOperation e) {
                PLog.d(TAG, "aggregate user operation aborted");
                break;
            }
        }
        return members;
    }

    public static Realm Realm(Context context) {
        File dataFile = context.getDir("users", Context.MODE_PRIVATE);

        try {
            return Realm.getInstance(config/*, UserManager.getKey()*/);
        } catch (RealmException e) {
            ErrorCenter.reportError("realmSecureError", context.getString(R.string.encryptionNotAvailable), null);
            return Realm.getInstance(config);
        }
    }

    // FIXME: 1/14/2016 add key
    private static final RealmConfiguration
            config;

    static {
        File file = Config.getApplicationContext().getDir("data", Context.MODE_PRIVATE);
        config = new RealmConfiguration.Builder()
                .directory(file)
                .name("userstore.realm")
                .schemaVersion(0)
                .deleteRealmIfMigrationNeeded().build();
    }
}
