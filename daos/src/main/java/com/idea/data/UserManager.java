package com.idea.data;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.util.Pair;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import com.google.i18n.phonenumbers.NumberParseException;
import com.idea.Errors.ErrorCenter;
import com.idea.data.settings.PersistedSetting;
import com.idea.net.HttpResponse;
import com.idea.net.PARSE_CONSTANTS;
import com.idea.net.ParseClient;
import com.idea.net.UserApiV2;
import com.idea.util.Config;
import com.idea.util.ConnectionUtils;
import com.idea.util.FileUtils;
import com.idea.util.PLog;
import com.idea.util.PhoneNumberNormaliser;
import com.idea.util.TaskManager;
import com.squareup.okhttp.Call;

import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;
import org.apache.http.protocol.HTTP;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.LockSupport;

import io.realm.Realm;
import io.realm.RealmList;
import io.realm.RealmObject;
import io.realm.RealmResults;
import retrofit.mime.TypedFile;

/**
 * @author by Null-Pointer on 5/27/2015.
 */
public final class UserManager {

    public static final String SILENT = "silent";
    private static final String TAG = UserManager.class.getSimpleName(),
            KEY_USER_PASSWORD = "klfiildelklaklier",
            KEY_SESSION_ID = "lfl/-90-09=klvj8ejf",
            KEY_USER_VERIFIED = "vvlaikkljhf",
            DEFAULT_VALUE = "defaultValue",
            sessionPrefFileName = "slfdafks",
            USER_PREFS_FILE_NAME = "userPrefs";

    private static final UserManager INSTANCE = new UserManager();
    private static final String CLEANED_UP = "lkfakfalkfclkieifklaklf";
    public static final String KEY_ACCESS_TOKEN = "accessToken";
    public static final String CHANGE_DP_KEY = "changeDp";
    public static final String NO_DP_CHANGE = "noDpChange";
    private final File sessionFile;
    private final Object mainUserLock = new Object();
    private final Exception NO_CONNECTION_ERROR;
    private final UserApiV2 userApi;
    private volatile User mainUser;
    private final File userPrefsLocation;

    /**************************
     * important constants
     *****************************************/
    public static final String IN_APP_NOTIFICATIONS = "inAppNotifications",
            NEW_MESSAGE_TONE = "newMessageTone", VIBRATE = "vibrateOnNewMessage",
            LIGHTS = "litLightOnNewMessage", DELETE_ATTACHMENT_ON_DELETE = "deleteAttachmentsOnMessageDelete",
            DELETE_OLDER_MESSAGE = "deleteOldMessages", AUTO_DOWNLOAD_MESSAGE = "autoDownloadMessage",
            NOTIFICATION = "Notification", STORAGE = "Storage", NETWORK = "Network";
    public static final String DEFAULT = "default";
    /***********************************************************************/
    private static final Set<String> protectedKeys = new HashSet<>();
    private final Map<String, String> dpChangeInProgress = new HashMap<>();

    static {
        Collections.addAll(protectedKeys, IN_APP_NOTIFICATIONS,
                NEW_MESSAGE_TONE, VIBRATE, LIGHTS, DELETE_ATTACHMENT_ON_DELETE, DELETE_OLDER_MESSAGE,
                AUTO_DOWNLOAD_MESSAGE, NOTIFICATION, STORAGE, NETWORK);

        if (BuildConfig.DEBUG && protectedKeys.size() != 10) {
            throw new AssertionError();
        }
    }

    @SuppressWarnings("FieldCanBeLocal")
    private final UserApiV2.Preprocessor preprocessor = new UserApiV2.Preprocessor() {

        //good solution will be to query for specific numbers but that is not possible
        //as we don't know how the user has stored the numbers in the people(contact) app
        //for eg +233 20 444 1069 could be stored in multiple unpredictable ways.
        //the contact manager can retrieve all the contacts standardised them to how
        //we store user ids and return them so that we do a quick hit test to map
        // the names to our parse objects(we will not persist those names on the backend)
        @Override
        public void process(final User user) {
            ContactsManager.getInstance().findAllContactsSync(new ContactsManager.Filter<ContactsManager.Contact>() {
                @Override
                public boolean accept(ContactsManager.Contact contact) throws AbortOperation {
                    final String userId = user.getUserId();
                    if (contact.numberInIEE_Format.equals(userId)) {
                        user.setName(contact.name);
                        throw new AbortOperation("done"); //contact manager will stop processing contacts
                    }
                    return false; //we don't care about return values
                }
            }, null);

        }

        @Override
        public void process(final Collection<User> users) {
            ContactsManager.getInstance().findAllContactsSync(new ContactsManager.Filter<ContactsManager.Contact>() {
                Set<String> processed = new HashSet<>(users.size());

                @Override
                public boolean accept(ContactsManager.Contact contact) throws AbortOperation {
                    if (processed.size() == users.size()) {
                        throw new AbortOperation("done");
                    }
                    if (!processed.add(contact.numberInIEE_Format)) {
                        return false; //we don't care about return values
                    }
                    for (User user : users) {
                        final String userId = user.getUserId();
                        if (contact.numberInIEE_Format.equals(userId)) {
                            user.setName(contact.name);
                            break;
                        }
                    }
                    return false; //we don't care about return values
                }
            }, null);
        }
    };

    private UserManager() {
        NO_CONNECTION_ERROR = new Exception(Config.getApplicationContext().getString(R.string.st_unable_to_connect));
        userApi = ParseClient.getInstance(preprocessor);
        userPrefsLocation = Config.getApplicationContext().getDir(USER_PREFS_FILE_NAME, Context.MODE_PRIVATE);
        sessionFile = Config.getApplicationContext().getDir(sessionPrefFileName, Context.MODE_PRIVATE);
    }

    @Deprecated
    public static UserManager getInstance(@SuppressWarnings("UnusedParameters") @NonNull Context context) {
        return INSTANCE;
    }

    public static UserManager getInstance() {
        return INSTANCE;
    }

    public static String getMainUserId() {
        // TODO: 10/25/2015 remove this in production
        if (!INSTANCE.isUserLoggedIn()) {
            throw new IllegalStateException();
        }
        return INSTANCE.getCurrentUser().getUserId();
    }

    private synchronized void saveMainUser(User user) {
        final Context context = Config.getApplicationContext();
        Realm realm = User.Realm(context);
        realm.beginTransaction();
        realm.copyToRealmOrUpdate(user);
        realm.commitTransaction();
        putSessionPref(KEY_SESSION_ID, user.getUserId());
        putSessionPref(KEY_USER_PASSWORD, user.getPassword());
    }


    public User getCurrentUser() {
        synchronized (mainUserLock) {
            if (mainUser != null) {
                return mainUser;
            }
        }
        Realm realm = User.Realm(Config.getApplicationContext());
        User user = getCurrentUser(realm);
        if (user != null) {
            //returning {@link RealmObject} from methods will leak resources since
            // that will prevent us from closing the realm instance. hence we do a shallow copy.
            // downside is changes to this object will not be persisted which is just what we want
            synchronized (mainUserLock) {
                mainUser = User.copy(user);
            }
            return mainUser;
        } else {
            cleanUp();
        }
        realm.close();
        //noinspection ConstantConditions
        return user;
    }

    public boolean isUserLoggedIn() {
        return isEveryThingSetup();
    }

    private boolean isEveryThingSetup() {
        final User mainUser = getCurrentUser();
        if (mainUser == null || mainUser.getUserId().isEmpty() || mainUser.getName().isEmpty() || mainUser.getCountry().isEmpty()) {
            TaskManager.execute(new Runnable() {
                @Override
                public void run() {
                    cleanUp();
                }
            });
            return false;
        } else //noinspection ConstantConditions
            if (getSessionStringPref(KEY_SESSION_ID, "").isEmpty()) {
                TaskManager.execute(new Runnable() {
                    @Override
                    public void run() {
                        cleanUp();
                    }
                });
                return false;
            }
        return true;
    }


    private void putSessionPref(String key, Object value) {
        Realm realm = PersistedSetting.REALM(sessionFile);
        realm.beginTransaction();
        PersistedSetting setting = new PersistedSetting();
        setting.setKey(key);
        setting.setStandAlone(true);
        if (!PersistedSetting.put(setting, value))
            throw new RuntimeException();
        realm.copyToRealmOrUpdate(setting);
        realm.commitTransaction();
        realm.close();
    }

    private String getSessionStringPref(String key, String defaultValue) {

        Realm realm = PersistedSetting.REALM(sessionFile);
        try {
            PersistedSetting setting = realm.where(PersistedSetting.class).equalTo(PersistedSetting.FIELD_KEY, key).findFirst();
            if (setting != null) {
                return setting.getStringValue();
            }
            return defaultValue;
        } finally {
            realm.close();
        }
    }

    private boolean getSessionBoolPref(String key, boolean defaultValue) {

        Realm realm = PersistedSetting.REALM(sessionFile);
        try {
            PersistedSetting setting = realm.where(PersistedSetting.class).equalTo(PersistedSetting.FIELD_KEY, key).findFirst();
            if (setting != null) {
                return setting.getBoolValue();
            }
            return defaultValue;
        } finally {
            realm.close();
        }
    }

    @SuppressWarnings("unused")
    private int getSessionIntPref(String key, int defaultValue) {

        Realm realm = PersistedSetting.REALM(sessionFile);
        try {
            PersistedSetting setting = realm.where(PersistedSetting.class).equalTo(PersistedSetting.FIELD_KEY, key).findFirst();
            if (setting != null) {
                return setting.getIntValue();
            }
            return defaultValue;
        } finally {
            realm.close();
        }
    }

    public boolean isUserVerified() {
        return isUserLoggedIn() && getSessionBoolPref(KEY_USER_VERIFIED, false);
    }


    private User getCurrentUser(Realm realm) {
        String currUserId = getSessionStringPref(KEY_SESSION_ID, null);
        if (currUserId == null) {
            return null;
        }
        return realm.where(User.class).equalTo(User.FIELD_ID, currUserId).findFirst();
    }

    private String getUserPassword() {
        String password = getSessionStringPref(KEY_USER_PASSWORD, null);
        if (password == null) {
            // TODO: 7/19/2015 logout user and clean up realm as we suspect intruders
            throw new IllegalStateException("session data tampered with");
        }
        return password;
    }

    public boolean isCurrentUser(String userId) {
        if (TextUtils.isEmpty(userId)) {
            return false;
        }
        User thisUser = getCurrentUser();
        return ((thisUser != null)) && thisUser.getUserId().equals(userId);
    }

    public void createGroup(final String groupName, final Set<String> membersId, final CreateGroupCallBack callBack) {
        if (!ConnectionUtils.isConnectedOrConnecting()) {
            callBack.done(NO_CONNECTION_ERROR, null);
            return;
        }
        if (isUser(User.generateGroupId(groupName))) {
            //already exist[
            callBack.done(new Exception("group with name " + groupName + "already exists"), null);
            return;
        }

        Pair<String, String> errorNamePair = isValidGroupName(groupName);
        if (errorNamePair.second != null) {
            doNotify(callBack, new Exception(errorNamePair.second), null);
            return;
        }
        membersId.add(getMainUserId());
        userApi.createGroup(getCurrentUser().getUserId(), groupName, membersId, new UserApiV2.Callback<User>() {

            @Override
            public void done(Exception e, User group) {
                if (e == null) {
                    completeGroupCreation(group, membersId);
                    doNotify(callBack, null, group.getUserId());
                } else {
                    PLog.i(TAG, "failed to create group");
                    doNotify(callBack, e, null);
                }
            }
        });
    }

    private void completeGroupCreation(User group, Set<String> membersId) {
        Realm realm = User.Realm(Config.getApplicationContext());
        realm.beginTransaction();
        group.setMembers(new RealmList<User>());//required for realm to behave
        User mainUser = getCurrentUser(realm);
        if (mainUser == null) {
            throw new IllegalStateException("no user logged in");
        }
        RealmList<User> members = User.aggregateUsers(realm, membersId, new ContactsManager.Filter<User>() {
            @Override
            public boolean accept(User user) {
                return user != null && !isGroup(user.getUserId());
            }
        });
        //noinspection ConstantConditions
        group.getMembers().addAll(members);
        group.setAdmin(mainUser);
        group.setType(User.TYPE_GROUP);
        realm.copyToRealmOrUpdate(group);
        realm.commitTransaction();
        realm.close();
    }

    @SuppressWarnings("unused")
    public void removeMembers(final String groupId, final List<String> members, final CallBack callBack) {
        final Exception e = checkPermission(groupId);
        if (e != null) { //unauthorised
            doNotify(e, callBack);
            return;
        }
        if (members.contains(getCurrentUser().getUserId())) {
            if (BuildConfig.DEBUG) {
                throw new IllegalArgumentException("admin cannot remove him/herself");
            }
            doNotify(new Exception("admin cannot remove him/herself"), callBack);
        }

        userApi.removeMembersFromGroup(groupId, getCurrentUser().getUserId(), members, new UserApiV2.Callback<HttpResponse>() {
            @Override
            public void done(Exception e, HttpResponse response) {
                if (e == null) {
                    Realm realm = User.Realm(Config.getApplicationContext());
                    realm.beginTransaction();
                    final User group = realm.where(User.class).equalTo(User.FIELD_ID, groupId).findFirst();
                    final ContactsManager.Filter<User> filter = new ContactsManager.Filter<User>() {
                        @Override
                        public boolean accept(User user) {
                            return (user != null && group.getMembers().contains(user));
                        }
                    };
                    RealmList<User> membersToDelete = User.aggregateUsers(realm, members, filter);
                    //noinspection ConstantConditions
                    group.getMembers().removeAll(membersToDelete);
                    realm.commitTransaction();
                    realm.close();
                    doNotify(null, callBack);
                } else {
                    doNotify(e, callBack);

                }
            }
        });
    }

    private boolean isUser(String id) {
        Realm realm = User.Realm(Config.getApplicationContext());
        boolean isUser = realm.where(User.class).equalTo(User.FIELD_ID, id).findFirst() != null;
        realm.close();
        return isUser;
    }

    public boolean isAdmin(String groupId, String userId) {
        Realm realm = User.Realm(Config.getApplicationContext());
        User group = realm.where(User.class).equalTo(User.FIELD_ID, groupId).findFirst();
        if (group == null) {
            realm.close();
            throw new IllegalArgumentException("no group with such id");
        }
        String adminId = group.getAdmin().getUserId();
        realm.close();
        return adminId.equals(userId);
    }

    private Exception checkPermission(String groupId) {
        if (!isUser(groupId)) {
            return new IllegalArgumentException("no group with such id");
        }
        if (!ConnectionUtils.isConnectedOrConnecting()) {
            return (NO_CONNECTION_ERROR);
        }
        if (!isAdmin(groupId, getCurrentUser().getUserId())) {
            return new IllegalAccessException(Config.getApplicationContext().getString(R.string.not_permitted_group));
        }
        return null;
    }

    public void addMembersToGroup(final String groupId, final Set<String> membersId, final CallBack callBack) {
        Exception e = checkPermission(groupId);
        if (e != null) {
            doNotify(e, callBack);
            return;
        }
        if (membersId.isEmpty()) {
            doNotify(new Exception("No number to add"), callBack);
            return;
        }
        userApi.addMembersToGroup(groupId, getCurrentUser().getUserId(), membersId, new UserApiV2.Callback<HttpResponse>() {
            @Override
            public void done(Exception e, HttpResponse httpResponse) {
                if (e == null) {
                    Realm realm = User.Realm(Config.getApplicationContext());
                    realm.beginTransaction();
                    final User group = realm.where(User.class).equalTo(User.FIELD_ID, groupId).findFirst();

                    Set<String> uniqeSet = new HashSet<>(membersId);

                    RealmList<User> newMembers = User.aggregateUsers(realm, uniqeSet, null);
                    //noinspection ConstantConditions
                    newMembers.addAll(group.getMembers());

                    //noinspection ConstantConditions
                    uniqeSet.clear();
                    group.getMembers().clear();
                    for (User user : newMembers) {
                        if (uniqeSet.add(user.getUserId())) {
                            group.getMembers().add(user);
                        }
                    }
                    realm.commitTransaction();
                    realm.close();
                    doNotify(null, callBack);
                } else {
                    doNotify(e, callBack);
                }
            }
        });
    }

    private void getGroupMembers(final String id) {
        userApi.getGroupMembers(id, new UserApiV2.Callback<List<User>>() {
            @Override
            public void done(Exception e, final List<User> freshMembers) {
                if (e == null) {
                    TaskManager.execute(new Runnable() {
                        @Override
                        public void run() {
                            Realm realm = User.Realm(Config.getApplicationContext());
                            realm.beginTransaction();
                            User group = realm.where(User.class).equalTo(User.FIELD_ID, id).findFirst();
                            group.getMembers().clear();
                            List<User> collection = new ArrayList<>(freshMembers);
                            collection = realm.copyToRealmOrUpdate(collection);

                            Set<String> uniqueMembers = new HashSet<>();
                            List<User> uniqueUsers = new ArrayList<>(uniqueMembers.size() * 2);
                            for (User user : collection) {
                                if (uniqueMembers.add(user.getUserId())) {
                                    uniqueUsers.add(user);
                                }
                            }
                            group.getMembers().clear();
                            group.getMembers().addAll(uniqueUsers);
                            realm.commitTransaction();
                            realm.close();
                        }
                    });
                } else {
                    PLog.w(TAG, "failed to fetch group members with reason: " + e.getMessage());
                }
            }
        });
    }

    private void saveFreshUsers(List<User> freshMembers) {
        Realm realm = User.Realm(Config.getApplicationContext());
        saveFreshUsers(freshMembers, realm);
        realm.close();
    }

    private List<User> saveFreshUsers(List<User> freshMembers, Realm realm) {
        realm.beginTransaction();
        List<User> ret = realm.copyToRealmOrUpdate(freshMembers);
        realm.commitTransaction();
        return ret;
    }

    void doRefreshGroup(String id) {
        getGroupInfo(id); //async
    }

    private void getGroupInfo(final String id) {
        userApi.getGroup(id, new UserApiV2.Callback<User>() {
            @Override
            public void done(Exception e, final User group) {
                if (e == null) {
                    TaskManager.execute(new Runnable() {
                        @Override
                        public void run() {
                            completeGetGroupInfo(group, id);
                        }
                    });
                }
            }
        });
    }

    private void completeGetGroupInfo(User group, String id) {
        Realm realm = User.Realm(Config.getApplicationContext());
        User staleGroup = realm.where(User.class).equalTo(User.FIELD_ID, id).findFirst();
        realm.beginTransaction();
        if (staleGroup != null) {
            staleGroup.setName(group.getName());
            staleGroup.setDP(group.getDP());
        } else {
            group.setType(User.TYPE_GROUP);
            group.setMembers(new RealmList<User>());
            group.getMembers().add(group.getAdmin());
            realm.copyToRealmOrUpdate(group);
        }
        realm.commitTransaction();
        realm.close();
        realm = User.Realm(Config.getApplicationContext());
        User g = realm.where(User.class).equalTo(User.FIELD_ID, id).findFirst();
        PLog.d(TAG, "members of " + g.getName() + " are: " + g.getMembers().size());
        realm.close();
        getGroupMembers(id); //async
    }

    public void refreshGroups() {
        if (!ConnectionUtils.isConnectedOrConnecting()) {
            return;
        }
        Worker.getGroups();
    }

    public void refreshUserDetails(final String userId) {
        if (!ConnectionUtils.isConnectedOrConnecting()) {
            return;
        }
        //update user here
        Worker.refreshUser(userId);
    }

    void doRefreshUserDetails(final String userId) {
        if (isGroup(userId)) {
            doRefreshGroup(userId);
        } else {
            doRefreshUser(userId);
        }

//        TaskManager.execute(new Runnable() {
//            public void run() {
//                String wasChangingDp = getStringPref(userId + CHANGE_DP_KEY, NO_DP_CHANGE);
//                if (!wasChangingDp.equals(NO_DP_CHANGE)) {
//                    if (new File(wasChangingDp).exists()) {
//                        changeDp(userId, wasChangingDp, new CallBack() {
//                            @Override
//                            public void done(Exception e) {
//                                PLog.d(TAG, "change dp %s", e == null ? "success" : "failed");
//                            }
//                        });
//                    }
//                }
//            }
//        });
    }

    private void doRefreshUser(String userId) {
        userApi.getUser(userId, new UserApiV2.Callback<User>() {
            @Override
            public void done(Exception e, User onlineUser) {
                if (e == null) {
                    Realm realm = User.Realm(Config.getApplicationContext());
                    realm.beginTransaction();
                    realm.copyToRealmOrUpdate(onlineUser);
                    realm.commitTransaction();
                    realm.close();
                } else {
                    PLog.w(TAG, "refreshing user failed with reason: " + e.getMessage());
                }
            }
        });
    }

    public boolean isGroup(String userId) {
        Realm realm = User.Realm(Config.getApplicationContext());
        User user = realm.where(User.class).equalTo(User.FIELD_ID, userId).equalTo(User.FIELD_TYPE, User.TYPE_GROUP).findFirst();
        realm.close();
        return user != null;
    }

    void doRefreshGroups() {
        User mainUser = getCurrentUser();
        userApi.getGroups(mainUser.getUserId(), new UserApiV2.Callback<List<User>>() {
            @Override
            public void done(Exception e, List<User> users) {
                if (e == null) {
                    completeGetGroups(users);
                }
            }
        });
    }

    private void completeGetGroups(List<User> groups) {
        Realm realm = User.Realm(Config.getApplicationContext());
        realm.beginTransaction();
        User mainUser = getCurrentUser(realm);
        for (User group : groups) {
            User staleGroup = realm.where(User.class).equalTo(User.FIELD_ID, group.getUserId()).findFirst();
            if (staleGroup != null) { //already exist just update
                staleGroup.setName(group.getName()); //admin might have changed name
                staleGroup.setDP(group.getDP());
            } else { //new group
                // because the json returned from our backend is not compatible with our schema here
                // the backend always clears the members and type field so we have to set it up down here manually
                group.setType(User.TYPE_GROUP);
                group.setMembers(new RealmList<User>());
                group.getMembers().add(group.getAdmin());

                //check to ensure that we add main user as a member but only if
                //his is not the admin.this is avoid adding a duplicate user as
                //without this check we can add main user twice sinch we have already
                //added the admin
                //noinspection ConstantConditions
                if (!group.getAdmin().getUserId().equals(mainUser.getUserId())) {
                    group.getMembers().add(mainUser);
                }
                realm.copyToRealmOrUpdate(group);
            }
        }
        realm.commitTransaction();
        realm.close();
    }

    public void changeDp(String imagePath, CallBack callBack) {
        this.changeDp(getCurrentUser().getUserId(), imagePath, callBack);
    }


    public void changeDp(final String userId, final String imagePath, final CallBack callback) {
        final File imageFile = new File(imagePath);
        if (!imageFile.exists()) {
            doNotify(new Exception("file " + imagePath + " does not exist"), callback);
            return;
        }
        synchronized (dpChangeInProgress) {
            String inProgress = dpChangeInProgress.get(userId);
            if (inProgress != null && inProgress.equals(imageFile.getAbsolutePath())) {
                PLog.d(TAG, "already changing dp");
                return;
            }
            dpChangeInProgress.put(userId, imageFile.getAbsolutePath());
        }
        Realm realm = User.Realm(Config.getApplicationContext());
        User user = realm.where(User.class).equalTo(User.FIELD_ID, userId).findFirst();
        if (user != null) {
            realm.beginTransaction();
            user.setDP(imageFile.getAbsolutePath());
            realm.commitTransaction();
            doChangeDp(userId, imageFile.getAbsolutePath());
            doNotify(null, callback);
        } else {
            PLog.f(TAG, "user changing dp vanished");
            doNotify(new Exception(Config.getApplicationContext().getString(R.string.an_error_occurred)), callback);
        }
        realm.close();
    }

    private void doChangeDp(final String userId, final String imagePath) {
        TaskManager.executeNow(new Runnable() {
            @Override
            public void run() {
                final File dpFile = saveDpLocally(userId, new File(imagePath));
                if (dpFile != null) {
                    Worker.changeDp(userId, dpFile.getAbsolutePath());
                } else {
                    ErrorCenter.reportError(userId + "changeDp", Config.getApplicationContext().getString(R.string.dp_change_failed));
                    putStandAlonePref(userId + CHANGE_DP_KEY, NO_DP_CHANGE);
                    synchronized (dpChangeInProgress) {
                        dpChangeInProgress.remove(userId);
                    }
                }
            }
        }, true);
    }


    void completeDpChange(final String userId, String dpFile) {
        putStandAlonePref(userId + CHANGE_DP_KEY, dpFile);
        if (!ConnectionUtils.isConnectedOrConnecting()) {
            synchronized (dpChangeInProgress) {
                dpChangeInProgress.remove(userId);
            }
            return;
        }
        String placeHolder = isGroup(userId) ? "groups" : "users";
        userApi.changeDp(placeHolder, userId, new TypedFile("image/*", new File(dpFile)), new UserApiV2.Callback<HttpResponse>() {
            @Override
            public void done(Exception e, final HttpResponse response) {
                synchronized (dpChangeInProgress) {
                    dpChangeInProgress.remove(userId);
                }
                if (e == null) {
                    putStandAlonePref(userId + CHANGE_DP_KEY, NO_DP_CHANGE);
                }
            }
        });
    }

    private File saveDpLocally(String userId, File imageFile) {
        StringBuilder name = new StringBuilder(FileUtils.hashFile(imageFile));
        if (TextUtils.isEmpty(name)) {
            return null;
        }
        name.append("_").append(userId.replaceAll("[\\Q@\\E\\s]+", "_"));
        String extension = FileUtils.getExtension(imageFile.getAbsolutePath(), "jpg");
        name.append(".").append(extension);
        PLog.d(TAG, "standardised fileName: %s", name.toString());
        final File dpFile = new File(Config.getAppProfilePicsBaseDir(), name.toString());
        try {
            if (dpFile.exists() || dpFile.getCanonicalPath().equals(imageFile.getCanonicalPath())) {
                PLog.d(TAG, "not copying dp over, file already exist");
            } else {
                //noinspection ConstantConditions
                FileUtils.copyTo(imageFile, dpFile);
            }
        } catch (IOException e) {
            //we will not cancel the transaction
            PLog.e(TAG, "failed to save user's profile locally: " + e.getMessage());
            return null;
        }

        Realm realm = User.Realm(Config.getApplicationContext());
        User user = realm.where(User.class).equalTo(User.FIELD_ID, userId).findFirst();
        if (user != null) {
            realm.beginTransaction();
            user.setDP(dpFile.getAbsolutePath());
            realm.commitTransaction();
            return dpFile;
        }
        realm.close();
        return null;
    }

    public void logIn(final String phoneNumber, final String userIso2LetterCode, final CallBack callback) {
        if (!ConnectionUtils.isConnectedOrConnecting()) {
            doNotify(NO_CONNECTION_ERROR, callback);
            return;
        }
        completeLogin(phoneNumber, userIso2LetterCode, callback);
    }

    private void completeLogin(String phoneNumber, String userIso2LetterCode, CallBack callback) {
        if (TextUtils.isEmpty(phoneNumber)) {
            doNotify(new Exception("invalid phone number"), callback);
            return;
        }

        if (TextUtils.isEmpty(userIso2LetterCode)) {
            doNotify(new Exception("userIso2LetterCode cannot be empty"), callback);
            return;
        }
        User user = new User();
        try {
            phoneNumber = PhoneNumberNormaliser.toIEE(phoneNumber, userIso2LetterCode);
        } catch (NumberParseException e) {
            if (BuildConfig.DEBUG) {
                PLog.e(TAG, e.getMessage(), e.getCause());
            } else {
                PLog.e(TAG, e.getMessage());
            }
            doNotify(new Exception("invalid phone number"), callback);
            return;
        }
        user.setUserId(phoneNumber);
        user.setCountry(userIso2LetterCode);
        String password = Base64.encodeToString(phoneNumber.getBytes(), Base64.DEFAULT);
        user.setPassword(password);
        doLogIn(user, callback);
    }

    //this method must be called on the main thread
    private void doLogIn(final User user, final CallBack callback) {
        userApi.logIn(user, new UserApiV2.Callback<User>() {
            @Override
            public void done(Exception e, User backendUser) {
                if (e == null) {
                    backendUser.setPassword(user.getPassword());
                    saveMainUser(backendUser);
                    doRefreshGroups(); //async
                    doNotify(null, callback);
                } else {
                    doNotify(e, callback);
                }
            }
        });
    }

    public void signUp(final String name, final String phoneNumber, final String countryIso, final CallBack callback) {
        if (!ConnectionUtils.isConnectedOrConnecting()) {
            doNotify(NO_CONNECTION_ERROR, callback);
            return;
        }
        completeSignUp(name, phoneNumber, countryIso, callback);
    }

    private void completeSignUp(final String name, final String phoneNumber, final String countryIso, final CallBack callback) {
        if (TextUtils.isEmpty(name)) {
            doNotify(new Exception("name is invalid"), callback);
        } else if (TextUtils.isEmpty(phoneNumber)) {
            doNotify(new Exception("phone number is invalid"), callback);
        } else if (TextUtils.isEmpty(countryIso)) {
            doNotify(new Exception("ccc is invalid"), callback);

        } else {
            Pair<String, String> errorNamePair = isValidUserName(name);
            if (errorNamePair.second != null) {
                doNotify(new Exception(errorNamePair.second), callback);
            } else {
                doSignup(name, phoneNumber, countryIso, callback);
            }
        }
    }

    private void doSignup(final String name,
                          final String phoneNumber,
                          final String countryIso,
                          final CallBack callback) {
        String thePhoneNumber;
        try {
            thePhoneNumber = PhoneNumberNormaliser.toIEE(phoneNumber, countryIso);
        } catch (NumberParseException e) {
            PLog.e(TAG, e.getMessage());
            doNotify(e, callback);
            return;
        }
        final User user = new User();
        user.setUserId(thePhoneNumber);
        String password = Base64.encodeToString(user.getUserId().getBytes(), Base64.DEFAULT);
        user.setPassword(password);
        user.setName(name);
        user.setCountry(countryIso);
        userApi.registerUser(user, new UserApiV2.Callback<User>() {
            @Override
            public void done(Exception e, User backEndUser) {
                if (e == null) {
                    backEndUser.setPassword(user.getPassword());
                    saveMainUser(backEndUser);
                    doNotify(null, callback);
                } else {
                    doNotify(e, callback);
                }
            }

        });
    }

    public void verifyUser(final String token, final CallBack callBack) {
        if (isUserVerified()) {
            doNotify(null, callBack);
            return;
        }
        if (!ConnectionUtils.isConnectedOrConnecting()) {
            doNotify(NO_CONNECTION_ERROR, callBack);
            return;
        }
        if (TextUtils.isEmpty(token)) {
            doNotify(new Exception("invalid token"), callBack);
            return;
        }
        if (!isUserLoggedIn()) {
            throw new IllegalStateException("no user logged for verification");
        }
        userApi.verifyUser(getCurrentUser().getUserId(), token, new UserApiV2.Callback<UserApiV2.SessionData>() {
            @Override
            public void done(Exception e, UserApiV2.SessionData data) {
                if (e == null) {
                    putSessionPref(KEY_ACCESS_TOKEN, data.accessToken);
                    putSessionPref(KEY_USER_VERIFIED, true);
                    initialiseSettings();
                    doNotify(null, callBack);
                } else {
                    doNotify(e, callBack);
                }
            }
        });
    }

    private void initialiseSettings() {
        try {
            String json = IOUtils.toString(Config.getApplicationContext().getAssets().open("settings.json"), Charsets.UTF_8);
            createPrefs(new JSONArray(json));
        } catch (IOException | JSONException e) {
            throw new RuntimeException(e.getCause());
        }
    }

    public void sendVerificationToken(final CallBack callback) {
        TaskManager.executeNow(new Runnable() {
            @Override
            public void run() {
                if (isUserLoggedIn()) {
                    if (isUserVerified()) {
                        throw new IllegalStateException();
                    }
                    userApi.sendVerificationToken(getMainUserId(), new UserApiV2.Callback<HttpResponse>() {
                        @Override
                        public void done(Exception e, HttpResponse aBoolean) {
                            doNotify(e, callback);
                        }
                    });
                    return;
                }
                throw new IllegalStateException();
            }
        }, true);
    }

    public void resendToken(final CallBack callBack) {
        if (!isUserLoggedIn()) {
            throw new IllegalArgumentException(new Exception("no user logged for verification"));
        }
        if (isUserVerified()) {
            doNotify(null, callBack);
            return;
        }
        if (!ConnectionUtils.isConnectedOrConnecting()) {
            doNotify(NO_CONNECTION_ERROR, callBack);
            return;
        }
        userApi.resendToken(getCurrentUser().getUserId(), getUserPassword(), new UserApiV2.Callback<HttpResponse>() {
            @Override
            public void done(Exception e, HttpResponse response) {
                doNotify(e, callBack);
            }
        });
    }

    @SuppressWarnings("unused")
    public void logOut(Context context, final CallBack logOutCallback) {

        oops();
        //TODO logout user from backend
        String userId = getSessionStringPref(KEY_SESSION_ID, null);
        if ((userId == null)) {
            throw new AssertionError("calling logout when no user is logged in"); //security hole!
        }

        Realm realm = User.Realm(context);
        // TODO: 6/14/2015 remove this in production code.
        User user = realm.where(User.class).equalTo(User.FIELD_ID, userId).findFirst();
        if (user == null) {
            throw new IllegalStateException("existing session id with no corresponding User in the database");
        }
        realm.clear(User.class);
        realm.clear(Message.class);
        realm.clear(Conversation.class);
        realm.close();
    }

    private void oops() {
        throw new UnsupportedOperationException();
    }

    void syncContacts(final List<String> array) {
        if (!ConnectionUtils.isConnected()) {
            return;
        }
        userApi.syncContacts(array, new UserApiV2.Callback<List<User>>() {
            @Override
            public void done(Exception e, List<User> users) {
                if (e == null) {
                    saveFreshUsers(users);
                }
            }
        });
    }

    public void leaveGroup(final String id, final CallBack callBack) {
        if (!isGroup(id) || isAdmin(id)) {
            throw new IllegalArgumentException("not group or you are the admin");
        }
        if (!ConnectionUtils.isConnectedOrConnecting()) {
            doNotify(NO_CONNECTION_ERROR, callBack);
        }
        userApi.leaveGroup(id, getCurrentUser().getUserId(), getUserPassword(), new UserApiV2.Callback<HttpResponse>() {

            @Override
            public void done(Exception e, HttpResponse response) {
                if (e == null) {
                    cleanUserTraces(id);
                    doNotify(null, callBack);
                } else {
                    doNotify(e, callBack);
                }
            }
        });
    }

    private void cleanUserTraces(String id) {
        Realm realm = User.Realm(Config.getApplicationContext());
        try {
            User group = realm.where(User.class).equalTo(User.FIELD_ID, id).findFirst();
            if (group != null) {
                cleanMessages(id);
                cleanConvesation(id);
                removeUser(realm, group);
            }
        } finally {
            realm.close();
        }
    }

    private void removeUser(Realm realm, User group) {
        realm.beginTransaction();
        group.removeFromRealm();
        realm.commitTransaction();
    }

    private void cleanConvesation(String peerId) {
        Realm conversationRealm = Conversation.Realm(Config.getApplicationContext());
        conversationRealm.beginTransaction();
        conversationRealm.where(Conversation.class).equalTo(Conversation.FIELD_PEER_ID, peerId).findAll().clear();
        conversationRealm.commitTransaction();
        conversationRealm.close();
    }

    private void cleanMessages(String peerId) {
        Realm messageRealm = Message.REALM(Config.getApplicationContext());
        messageRealm.beginTransaction();
        messageRealm.where(Message.class).equalTo(Message.FIELD_TO, peerId).findAll().clear();
        messageRealm.commitTransaction();
        messageRealm.close();
    }

    public boolean isAdmin(String id) {
        return isAdmin(id, getCurrentUser().getUserId());
    }

    public String getUserCountryISO() {
        if (!isUserLoggedIn()) {
            throw new IllegalStateException("no user logged in");
        }
        return getCurrentUser().getCountry();
    }

    public void reset(final CallBack callBack) {
        TaskManager.execute(new Runnable() {
            @Override
            public void run() {
                if (isUserVerified()) {
                    throw new RuntimeException("use logout instead");
                }
                if (!ConnectionUtils.isConnected()) {
                    doNotify(NO_CONNECTION_ERROR, callBack);
                    return;
                }
                cleanUp();
                doNotify(null, callBack);
            }
        });
    }

    private void doNotify(final Exception e, final CallBack callBack) {
        TaskManager.executeOnMainThread(new Runnable() {
            @Override
            public void run() {
                callBack.done(e);
            }
        });
    }

    private void doNotify(final CreateGroupCallBack callBack, final Exception e, final String id) {
        TaskManager.executeOnMainThread(new Runnable() {
            @Override
            public void run() {
                callBack.done(e, id);
            }
        });
    }

    private void cleanUp() {
        if (Config.getApplicationWidePrefs().getBoolean(CLEANED_UP, false)) {
            Realm realm = PersistedSetting.REALM(userPrefsLocation);
            clearClass(realm, PersistedSetting.class);
            realm = PersistedSetting.REALM(sessionFile);
            clearClass(realm, PersistedSetting.class);
            realm = Message.REALM(Config.getApplicationContext());
            clearClass(realm, Message.class);
            realm = Conversation.Realm(Config.getApplicationContext());
            clearClass(realm, Conversation.class);
            realm = User.Realm(Config.getApplicationContext());
            clearClass(realm, User.class);
            realm = Country.REALM(Config.getApplicationContext());
            clearClass(realm, Country.class);
            Config.getApplicationWidePrefs().edit().clear().apply();
        }
    }

    private void clearClass(Realm realm, Class<? extends RealmObject> clazz) {
        realm.beginTransaction();
        realm.clear(clazz);
        realm.commitTransaction();
        realm.close();
    }

    public void refreshDp(final String id, final CallBack callBack) {
        if (!ConnectionUtils.isConnected()) {
            doNotify(NO_CONNECTION_ERROR, callBack);
            return;
        }
        TaskManager.execute(new Runnable() {
            @Override
            public void run() {
                Realm realm = User.Realm(Config.getApplicationContext());
                try {
                    User user = realm.where(User.class).equalTo(User.FIELD_ID, id).findFirst();
                    if (user != null) {
                        File dpFile = new File(Config.getAppProfilePicsBaseDir(), user.getDP());
                        if (!dpFile.exists()) {
                            downloadNewDp(user.getUserId(), user.getDP(), callBack);
                        } else {
                            doNotify(null, callBack);
                        }
                    } else {
                        doNotify(new Exception("No such user!"), callBack);
                    }
                } finally {
                    realm.close();
                }
            }
        });
    }

    private void downloadNewDp(final String userId, String dp, final CallBack callBack) {
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_REMOVED)) {
            //don't even bother to download a new one
            doNotify(new Exception("Storage Media removed"), callBack);
            return;
        }

        //we have to check to make sure the dp has really change
        final String encoded = encodeDp(dp);
        final File dpPath = new File(Config.getAppProfilePicsBaseDir(), encoded);
        if (!dpPath.exists()) {//if the did not change the dp we wont download it again instead we reconstruct it
            userApi.getUser(userId, new UserApiV2.Callback<User>() {
                @Override
                public void done(Exception e, User user) {
                    try {
                        if (e != null) {
                            PLog.e(TAG, e.getMessage(), e.getCause());
                            doNotify(e, callBack);
                            return;
                        }
                        FileUtils.save(dpPath, user.getDP());
                        updateUserDpInRealm(userId, callBack, encoded);
                    } catch (IOException e2) {
                        doNotify(new Exception(Config.getApplicationContext().getString(R.string.an_error_occurred)), callBack);
                    }
                }
            });
        } else {
            updateUserDpInRealm(userId, callBack, encoded);
        }

    }

    @NonNull
    public String encodeDp(String dp) {
        PLog.d(TAG, "raw dp: " + dp);
        String encoded = dp;
        if (encoded.startsWith("http")) {
            encoded = Base64.encodeToString(dp.getBytes(), Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING) + ".jpg";
        }
        PLog.d(TAG, "encoded dp: " + encoded);
        return encoded;
    }

    private void updateUserDpInRealm(String userId, CallBack callBack, String encoded) {
        Realm realm = User.Realm(Config.getApplicationContext());
        User user = realm.where(User.class).equalTo(User.FIELD_ID, userId).findFirst();
        if (user != null) {
            realm.beginTransaction();
            user.setDP(encoded); //update the dp
            realm.commitTransaction();
            doNotify(null, callBack);
        } else {
            doNotify(new Exception("user not available for update!, this is strange!"), callBack);
        }
        realm.close();
    }

    public Pair<String, String> isValidGroupName(String proposedName) {
        Context applicationContext = Config.getApplicationContext();
        String errorMessage = null;
        proposedName = proposedName.replaceAll("\\p{Space}+", " ");
        if (proposedName.length() < 5) {
            errorMessage = applicationContext.getString(R.string.name_too_short);
        } else if (proposedName.length() > 30) {
            errorMessage = applicationContext.getString(R.string.group_name_too_long);
        } else if (!Character.isLetter(proposedName.codePointAt(0))) {
            errorMessage = applicationContext.getString(R.string.name_starts_with_non_letter);
        } else if (!Character.isLetter(proposedName.codePointAt(proposedName.length() - 1))) {
            errorMessage = applicationContext.getString(R.string.name_ends_with_no_letter);
        } else if (proposedName.contains("@")) {
            errorMessage = applicationContext.getString(R.string.invalid_name_format_error);
        } else if (getCurrentUser() != null && UserManager.getInstance().isGroup(User.generateGroupId(proposedName))) {
            errorMessage = Config.getApplicationContext().getString(R.string.group_already_exists, proposedName).toUpperCase();
        }
        return new Pair<>(proposedName, errorMessage);
    }

    public Pair<String, String> isValidUserName(String proposedName) {
        String errorMessage = null;
        Context applicationContext = Config.getApplicationContext();
        if (proposedName.matches(".*\\p{Space}+.*")) {
            errorMessage = applicationContext.getString(R.string.error_space_in_name);
        } else if (proposedName.length() < 5) {
            errorMessage = applicationContext.getString(R.string.name_too_short);
        } else if (proposedName.length() > 15) {
            errorMessage = applicationContext.getString(R.string.name_too_long);
        } else if (!Character.isLetter(proposedName.codePointAt(0))) {
            errorMessage = applicationContext.getString(R.string.name_starts_with_non_letter);
        } else if (!Character.isLetter(proposedName.codePointAt(proposedName.length() - 1))) {
            errorMessage = applicationContext.getString(R.string.name_ends_with_no_letter);
        } else if (proposedName.contains("@")) {
            errorMessage = applicationContext.getString(R.string.invalid_name_format_error);
        }

        return new Pair<>(proposedName, errorMessage);
    }

    public User fetchUserIfRequired(String userId) {
        Realm realm = User.Realm(Config.getApplicationContext());
        User user = fetchUserIfRequired(realm, userId);
        user = User.copy(user);
        realm.close();
        return user;
    }

    public User fetchUserIfRequired(Realm realm, String userId) {
        return fetchUserIfRequired(realm, userId, false);
    }

    public User fetchUserIfRequired(Realm realm, String userId, boolean refresh) {
        User peer = realm.where(User.class).equalTo(User.FIELD_ID, userId).findFirst();

        if (peer == null) {
            if (userId.contains("@")) {
                throw new IllegalStateException("should not happen");
            }
            realm.beginTransaction();
            peer = new User();
            peer.setUserId(userId);
            peer.setHasCall(false); //we cannot tell for now
            peer.setDP(userId);
            peer.setName(PhoneNumberNormaliser.toLocalFormat("+" + userId, getUserCountryISO()));
            peer.setType(User.TYPE_NORMAL_USER);
            realm.copyToRealmOrUpdate(peer);
            realm.commitTransaction();
            refreshUserDetails(userId);
        } else {
            if (refresh) {
                refreshUserDetails(userId);
            }
        }
        return peer;
    }

    @SuppressWarnings("unused")
    public List<String> allUserIds() {
        Realm realm = User.Realm(Config.getApplicationContext());
        try {
            RealmResults<User> users = realm.where(User.class)
                    .equalTo(User.FIELD_TYPE, User.TYPE_NORMAL_USER)
                    .notEqualTo(User.FIELD_ID, getMainUserId()).findAll();
            return User.aggregateUserIds(users, null);
        } finally {
            realm.close();
        }
    }

    public Object putStandAlonePref(String key, Object value) {
        ensureNotProtectedKey(key);
        if (value == null || key == null) {
            throw new IllegalArgumentException("null!");
        }
        Realm realm = PersistedSetting.REALM(userPrefsLocation);

        PersistedSetting persistedSetting = realm.where(PersistedSetting.class).equalTo(PersistedSetting.FIELD_KEY, key).findFirst();
        if (persistedSetting == null) {
            persistedSetting = new PersistedSetting();
            persistedSetting.setStandAlone(true);
            persistedSetting.setKey(key);
        } else if (!persistedSetting.isStandAlone()) {
            throw new IllegalArgumentException("pref already exist and is not standalone");
        }
        realm.beginTransaction();
        if (!PersistedSetting.put(persistedSetting, value)) {
            throw new IllegalArgumentException("unknown data type");
        }
        realm.copyToRealmOrUpdate(persistedSetting);
        realm.commitTransaction();
        return value;
    }

    public Object putPref(String key, Object value) {
        Realm realm = PersistedSetting.REALM(userPrefsLocation);

        try {
            PersistedSetting setting = realm.where(PersistedSetting.class).equalTo(PersistedSetting.FIELD_KEY, key).findFirst();
            if (setting == null) {
                return putStandAlonePref(key, value);
            }
            realm.beginTransaction();
            PersistedSetting.put(setting, value);
            if (setting.getType() == PersistedSetting.TYPE_INTEGER) {
                setting.setSummary(retrieveIntPrefSummary(key, ((Integer) value)));
            }
            realm.commitTransaction();
        } finally {
            realm.close();
        }
        return value;
    }

    public Object putPrefUpdateSummary(String key, Object value, String withSummary) {
        Realm realm = PersistedSetting.REALM(userPrefsLocation);

        try {
            PersistedSetting setting = realm.where(PersistedSetting.class).equalTo(PersistedSetting.FIELD_KEY, key).findFirst();
            if (setting == null) {
                return null;
            }
            realm.beginTransaction();
            if (!PersistedSetting.put(setting, value)) {
                realm.cancelTransaction();
                return null;
            }
            setting.setSummary(withSummary);
            realm.commitTransaction();
        } finally {
            realm.close();
        }
        return value;
    }

    private void ensureNotProtectedKey(String key) {
        if (protectedKeys.contains(key)) {
            throw new IllegalArgumentException("this key is protected");
        }
    }

    @SuppressWarnings("unused")
    public int getIntPref(String key, int defaultValue) {
        Realm realm = PersistedSetting.REALM(userPrefsLocation);
        try {
            PersistedSetting setting = realm.where(PersistedSetting.class)
                    .equalTo(PersistedSetting.FIELD_KEY, key).findFirst();
            if (setting == null) {
                return (int) putStandAlonePref(key, defaultValue);
            }
            return setting.getIntValue();
        } finally {
            realm.close();
        }
    }

    public boolean getBoolPref(String key, boolean defaultValue) {
        Realm realm = PersistedSetting.REALM(userPrefsLocation);
        try {
            PersistedSetting setting = realm.where(PersistedSetting.class)
                    .equalTo(PersistedSetting.FIELD_KEY, key).findFirst();
            if (setting == null) {
                return (boolean) putStandAlonePref(key, defaultValue);
            }
            return setting.getBoolValue();
        } finally {
            realm.close();
        }
    }

    public String getStringPref(String key, String defaultValue) {
        Realm realm = PersistedSetting.REALM(userPrefsLocation);
        try {
            PersistedSetting setting = realm.where(PersistedSetting.class)
                    .equalTo(PersistedSetting.FIELD_KEY, key).findFirst();
            if (setting == null) {
                return (String) putStandAlonePref(key, defaultValue);
            }
            return setting.getStringValue();
        } finally {
            realm.close();
        }
    }


    private void createPrefs(JSONArray array) throws JSONException {

        Realm realm = PersistedSetting.REALM(userPrefsLocation);
        JSONObject cursor;
        List<PersistedSetting> settings = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) {
            cursor = array.getJSONObject(i);
            String key = cursor.getString(PersistedSetting.FIELD_KEY);
            int order = cursor.getInt(PersistedSetting.FIELD_ORDER);
            int type = cursor.getInt(PersistedSetting.FIELD_TYPE);
            if (type == 0) {
                break;
            }
            Object defaultValue = cursor.opt(DEFAULT_VALUE);

            settings.add(createPref(key, type, defaultValue, order));
        }
        realm.beginTransaction();
        realm.copyToRealmOrUpdate(settings);
        realm.commitTransaction();
        realm.close();
    }

    private PersistedSetting createPref(String key, int type, Object value, int order) {
        Context con = Config.getApplicationContext();
        PersistedSetting.Builder builder = new PersistedSetting.Builder(key);

        if (type == PersistedSetting.TYPE_INTEGER) {
            String itemSummary = retrieveIntPrefSummary(key, (Integer) value);
            PLog.d(TAG, "summary for: %s : %s", key, itemSummary);
            builder.summary(itemSummary);
        } else if (type == PersistedSetting.TYPE_LIST_STRING) {
            String itemSummary = getStringPrefSummary(key);
            PLog.d(TAG, "summary for: %s : %s", key, itemSummary);
            builder.summary(itemSummary);
        }

        int titleRes = con.getResources()
                .getIdentifier("string/" + key + "_title", null, con.getPackageName());

        if (titleRes == 0) {
            PLog.f(TAG, "failed to load resource for : %s", key);
            throw new RuntimeException("failed to load resource for " + key);
        }
        String title = con.getString(titleRes);
        builder.title(title)
                .type(type)
                .value(value)
                .order(order);

        return builder.build();
    }

    @NonNull
    private String getStringPrefSummary(String key) {
        Context con = Config.getApplicationContext();
        int summary;
        summary = con.getResources()
                .getIdentifier("string/" + key + "_summary", null, con.getPackageName());
        if (summary == 0) {
            PLog.f(TAG, "could not retrieve summary for: %s", key);
            throw new RuntimeException("failed to retrieve summary");
        }
        return con.getString(summary);
    }

    private String retrieveIntPrefSummary(String key, Integer value) {
        Context con = Config.getApplicationContext();
        int summary;
        summary = con.getResources().getIdentifier("array/" + key + "_options", null, con.getPackageName());
        if (summary == 0) {
            PLog.f(TAG, "could not retrieve summary for: %s", key);
            throw new RuntimeException("summary is required for integer prefs");
        }
        return con.getResources().getStringArray(summary)[value];
    }

    public List<PersistedSetting> userSettings() {
        Realm realm = PersistedSetting.REALM(userPrefsLocation);
        try {
            List<PersistedSetting> setting = PersistedSetting.copy(realm.where(PersistedSetting.class)
                    .equalTo(PersistedSetting.FIELD_STANDALONE, false).findAllSorted(PersistedSetting.FIELD_ORDER));
            for (PersistedSetting persistedSetting : setting) {
                Log.i(TAG, persistedSetting.getKey());
            }
            return setting;
        } finally {
            realm.close();
        }
    }

    public void restoreUserDefaultSettings(CallBack callback) {
        reInitialiseSettings(callback);
    }

    private void reInitialiseSettings(final UserManager.CallBack callback) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                Realm realm = PersistedSetting.REALM(userPrefsLocation);
                realm.beginTransaction();
                realm.clear(PersistedSetting.class);
                realm.commitTransaction();
                initialiseSettings();
                if (callback != null) {
                    doNotify(null, callback);
                }
            }
        };
        if (!TaskManager.executeNow(runnable)) { //express task already full
            TaskManager.execute(runnable);
        }
    }

    public Map<String, String> getUserCredentials() {
        if (!isUserVerified()) {
            throw new IllegalStateException("no user logged in");
        }
        String userId = getCurrentUser().getUserId(),
                accessToken = getSessionStringPref(KEY_ACCESS_TOKEN, "");
        Map<String, String> credentials = new HashMap<>(3);
        credentials.put(KEY_ACCESS_TOKEN, accessToken);
        credentials.put("userId", userId);
        return credentials;
    }

    public void dissolveGroup(User group, final CallBack callback) {
        if (group == null) {
            throw new IllegalArgumentException("group == null");
        }
        //noinspection ThrowableResultOfMethodCallIgnored
        Exception e = checkPermission(group.getUserId());
        if (e != null) {
            doNotify(e, callback);
        }
        final String groupId = group.getUserId();
        userApi.removeGroup(group.getAdmin().getUserId(), group.getUserId(), new UserApiV2.Callback<HttpResponse>() {
            @Override
            public void done(Exception e, HttpResponse aVoid) {
                if (e == null) {
                    cleanUserTraces(groupId);
                }
                doNotify(e, callback);
            }
        });
    }

    public interface CallBack {
        void done(Exception e);
    }

    public interface CreateGroupCallBack {
        void done(Exception e, String groupId);
    }
}
