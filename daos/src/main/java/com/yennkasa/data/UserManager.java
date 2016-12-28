package com.yennkasa.data;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.util.Pair;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import com.google.i18n.phonenumbers.NumberParseException;
import com.yennkasa.Errors.ErrorCenter;
import com.yennkasa.Errors.YennkasaException;
import com.yennkasa.net.HttpResponse;
import com.yennkasa.net.ParseClient;
import com.yennkasa.net.UserApiV2;
import com.yennkasa.security.RSA;
import com.yennkasa.util.Config;
import com.yennkasa.util.ConnectionUtils;
import com.yennkasa.util.Event;
import com.yennkasa.util.EventBus;
import com.yennkasa.util.FileUtils;
import com.yennkasa.util.GenericUtils;
import com.yennkasa.util.PLog;
import com.yennkasa.util.PhoneNumberNormaliser;
import com.yennkasa.util.TaskManager;
import com.yennkasa.util.ThreadUtils;

import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import io.realm.Realm;
import io.realm.RealmList;
import io.realm.RealmObject;
import io.realm.RealmResults;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Func1;

/**
 * @author by Null-Pointer on 5/27/2015.
 */
public final class UserManager {

    public static final String SILENT = "silent";
    private static final String TAG = UserManager.class.getSimpleName();
    public static final String BLOCKED_USERS = FileUtils.hash(TAG + "blocked.users.messages");
    @SuppressWarnings("unused")
    public static final String KEY = TAG + "blocked.users.messages",
    //            KEY_USER_PASSWORD = "klfiildelklaklier",
    KEY_SESSION_ID = "lfl/-90-09=klvj8ejf",
            KEY_USER_VERIFIED = "vvlaikkljhf",
            DEFAULT_VALUE = "defaultValue",
            sessionPrefFileName = "slfdafks",
            USER_PREFS_FILE_NAME = "userPrefs";

    private static final UserManager INSTANCE = new UserManager();
    private static final String CLEANED_UP = "lkfakfalkfclkieifklaklf";
    public static final String KEY_ACCESS_TOKEN = "accessToken";
    private static final String CHANGE_DP_KEY = "dpChange";
    private static final String NO_DP_CHANGE = "no dp change";
    private static final String PART_2 = FileUtils.hash("kloi3jlalfak982bbc,avqaafafals");
    private static final String PART_1 = FileUtils.hash("vncewe4209ipk;lj82lkja90");
    public static final String MUTED_USERS = "MUTED)USERS";
    public static final String VERIFICAITON_CODE_RECIEVED = ParseClient.VERIFICATION_CODE_RECEIVED;
    public static final String EVENT_SEARCH_RESULTS = "searchResults";
    private final File sessionFile;
    private final Object mainUserLock = new Object();
    private final Exception NO_CONNECTION_ERROR;
    private final UserApiV2 userApi;
    private volatile User mainUser;
    private final File userPrefsLocation;

    /**************************
     * important constants
     *****************************************/
    public static final String ENABLE_SEARCH = "enableSearch";
    public static final String IN_APP_NOTIFICATIONS = "inAppNotifications",
            NEW_MESSAGE_TONE = "newMessageTone", VIBRATE = "vibrateOnNewMessage",
            LIGHTS = "litLightOnNewMessage", DELETE_ATTACHMENT_ON_DELETE = "deleteAttachmentsOnMessageDelete",
            DELETE_OLDER_MESSAGE = "deleteOldMessages",
            AUTO_DOWNLOAD_MESSAGE_WIFI = "autoDownloadMessageWifi", AUTO_DOWNLOAD_MESSAGE_MOBILE = "autoDownloadMessageMobile",
            NOTIFICATION = "Notification", STORAGE = "Storage", NETWORK = "Network";
    public static final String DEFAULT = "default";
    /***********************************************************************/
    private static final Set<String> protectedKeys = new HashSet<>();
    private final Set<String> dpChangeInProgress = new HashSet<>();
    private final Map<Object, Long> rateLimiter = new HashMap<>();

    static {
        Collections.addAll(protectedKeys, IN_APP_NOTIFICATIONS,
                NEW_MESSAGE_TONE, VIBRATE, LIGHTS, DELETE_ATTACHMENT_ON_DELETE, DELETE_OLDER_MESSAGE,
                AUTO_DOWNLOAD_MESSAGE_MOBILE, AUTO_DOWNLOAD_MESSAGE_WIFI, NOTIFICATION, STORAGE, NETWORK);

        if (BuildConfig.DEBUG && protectedKeys.size() != 10) {
            throw new AssertionError();
        }
    }

    private final Lock processLock = new ReentrantLock(true);
    @SuppressWarnings("FieldCanBeLocal")
    private final UserApiV2.Preprocessor preprocessor = new UserApiV2.Preprocessor() {

        //it seems a good solution to query for specific numbers but that is not possible
        //as we don't know how the user has stored the numbers in the people(contact) app
        //for eg +233 20 444 1069 could be stored in multiple unpredictable ways.
        //the contact manager can retrieve all the contacts standardised them to how
        //we store user ids and return them so that we do a quick hit test to map
        // the names to our parse objects(we will not persist those names on the backend)
        @Override
        public void process(final User user) {
            try {
                processLock.lock();
                final String userId = user.getUserId();
                doResolveDp(user);
                if (userId.contains("@")) {//fixme why not just test for null-ness of getAdmin
                    process(user.getAdmin());
                    return;
                }
                Realm realm = newUserRealm();
                try {
                    if (userId.equals(getMainUserId(realm))) {
                        user.setName(Config.getApplicationContext().getString(R.string.you));
                        idsAndNames.put(userId, user.getName());
                        return;
                    } else {
                        idsAndNames.put(userId, user.getName());
                    }
                } finally {
                    realm.close();
                }
                try {
                    ContactsManager.findAllContactsSync(new ContactsManager.Filter<ContactsManager.Contact>() {
                        @Override
                        public boolean accept(ContactsManager.Contact contact) throws AbortOperation {
                            if (contact.numberInIEE_Format.equals(userId)) {
                                user.setName(contact.name);
                                idsAndNames.put(userId, contact.name);
                                user.setInContacts(true);
                                throw new AbortOperation("done"); //contact manager will stop processing contacts
                            }
                            return false; //we don't care about return values
                        }
                    }, null);
                } catch (YennkasaException e) {
                    PLog.f(TAG, e.getMessage());
                }
            } finally {
                processLock.unlock();
            }
        }

        @Override
        public void process(final Collection<User> users) {
            try {
                processLock.lock();
                if (users.isEmpty()) {
                    return;
                }
                for (User user : users) {
                    process(user);
                }
            } finally {
                processLock.unlock();
            }
        }

        private void doResolveDp(User user) {
            final String userId = user.getUserId();
            String userDp = getStringPref(userId + CHANGE_DP_KEY, NO_DP_CHANGE);
            File file = new File(userDp);
            if (file.exists()) { //always users must see what they are changing
                user.setDP(file.getAbsolutePath());
            }
        }
    };

    private UserManager() {
        NO_CONNECTION_ERROR = new Exception(Config.getApplicationContext().getString(R.string.not_connected));
        userApi = ParseClient.getInstance(preprocessor, BuildConfig.VERSION_CODE);
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

    public static String getMainUserId(Realm realm) {
        // TODO: 10/25/2015 remove this in production
        if (!INSTANCE.isUserLoggedIn(realm)) {
            throw new IllegalStateException();
        }
        return INSTANCE.getCurrentUser(realm).getUserId();
    }

    private synchronized void saveMainUser(User user) {
        final Context context = Config.getApplicationContext();
        putSessionPref(KEY_SESSION_ID, user.getUserId());
        Realm realm = User.Realm(context);
        try {
            realm.beginTransaction();
            realm.copyToRealmOrUpdate(user);
            realm.commitTransaction();
        } finally {
            realm.close();
        }
    }

    public static synchronized byte[] getKey() {
        SharedPreferences preferences = Config.getPreferences("lskalkaiakf");
        String part1 = preferences.getString(PART_1, null),
                part2 = preferences.getString(PART_2, null);
        if (part1 == null || part2 == null) {
            genKey();
            return getKey();
        }
        byte[] ret = new byte[64],
                part1Byte = Base64.decode(part1, Base64.DEFAULT),
                part2Byte = Base64.decode(part2, Base64.DEFAULT);

        if (part1Byte.length < 32 || part2Byte.length < 32) {
            genKey();
            return getKey();
        }
        int cursor1 = 0, cursor2 = 0;
        //noinspection ManualArrayCopy
        for (int i = 0; i < 64; i++) { //shuffle the bytes
            ret[i] = i % 2 == 0 ? part1Byte[cursor1++] : part2Byte[cursor2++];
        }
        return ret;
    }

    @SuppressLint("CommitPrefEdits")
    private static synchronized void genKey() {
        SecureRandom random = new SecureRandom();
        SharedPreferences preferences = Config.getPreferences("lskalkaiakf");
        byte[] randBytes = new byte[32];
        random.nextBytes(randBytes);
        String keyString = Base64.encodeToString(randBytes, Base64.DEFAULT);
        random.nextBytes(randBytes);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(PART_1, keyString);
        keyString = Base64.encodeToString(randBytes, Base64.DEFAULT);
        editor.putString(PART_2, keyString)
                .commit();
    }

    public String getCurrentUserAuthToken() {
        ThreadUtils.ensureNotMain();
        return userApi.getAuthToken();
    }

    public User getCurrentUser(Realm realm) {

        synchronized (mainUserLock) {
            if (mainUser != null) {
                return mainUser;
            }
        }
        User user = doGetCurrentUser(realm);
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
        //noinspection ConstantConditions
        return user;
    }

    public boolean isUserLoggedIn(Realm realm) {
        return isEveryThingSetup(realm);
    }

    private boolean isEveryThingSetup(Realm realm) {
        if (!userApi.isUserAuthenticated()) {
            return false;
        }
        final User mainUser = getCurrentUser(realm);
        if (!BuildConfig.DEBUG) {
            if (mainUser == null || !getSessionStringPref(KEY_SESSION_ID, "").equals(mainUser.getUserId())) {
                TaskManager.executeNow(new Runnable() {
                    @Override
                    public void run() {
                        cleanUp();
                    }
                }, false);
                return false;
            }
        } else {
            if (mainUser == null || mainUser.getUserId().isEmpty() ||
                    mainUser.getName().isEmpty() ||
                    mainUser.getCountry().isEmpty() || !getSessionStringPref(KEY_SESSION_ID, "").equals(mainUser.getUserId())) {
                TaskManager.executeNow(new Runnable() {
                    @Override
                    public void run() {
                        cleanUp();
                    }
                }, false);
                return false;
            }
        }
        return true;
    }


    private void putSessionPref(String key, Object value) {
        try {
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
        } catch (Exception e) {
            PLog.d(TAG, e.getMessage(), e.getCause());
            throw new RuntimeException(e.getCause());
        }
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

    public boolean isUserVerified(Realm realm) {
        return isUserLoggedIn(realm) && getSessionBoolPref(KEY_USER_VERIFIED, false);
    }


    private User doGetCurrentUser(Realm realm) {
        String currUserId = getSessionStringPref(KEY_SESSION_ID, null);
        if (currUserId == null) {
            return null;
        }
        return realm.where(User.class).equalTo(User.FIELD_ID, currUserId).findFirst();
    }


    public boolean isCurrentUser(Realm realm, String userId) {
        if (TextUtils.isEmpty(userId)) {
            return false;
        }
        User thisUser = getCurrentUser(realm);
        return ((thisUser != null)) && thisUser.getUserId().equals(userId);
    }

    public void createGroup(Realm realm, final String groupName, final Set<String> membersId, final CreateGroupCallBack callBack) {

        if (!ConnectionUtils.isConnectedOrConnecting()) {
            doNotify(callBack, NO_CONNECTION_ERROR, null);
            return;
        }
        if (isUser(User.generateGroupId(realm, groupName))) {
            doNotify(callBack, new Exception("group with name " + groupName + "already exists"), null);
            return;
        }

        Pair<String, String> errorNamePair = isValidGroupName(realm, groupName);
        if (errorNamePair.second != null) {
            doNotify(callBack, new Exception(errorNamePair.second), null);
            return;
        }
        membersId.add(getMainUserId(realm));
        userApi.createGroup(getCurrentUser(realm).getUserId(), groupName, membersId, new UserApiV2.Callback<User>() {

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
        final Realm realm = newUserRealm();
        try {
            realm.beginTransaction();
            group.setMembers(new RealmList<User>());//required for realm to behave
            User mainUser = getCurrentUser(realm);
            if (mainUser == null) {
                throw new IllegalStateException("no user logged in");
            }
            RealmList<User> members = User.aggregateUsers(realm, membersId, new ContactsManager.Filter<User>() {
                @Override
                public boolean accept(User user) {
                    return user != null && !isGroup(realm, user.getUserId());
                }
            });
            //noinspection ConstantConditions
            group.getMembers().addAll(members);
            group.setAdmin(mainUser);
            group.setType(User.TYPE_GROUP);
            realm.copyToRealmOrUpdate(group);
            realm.commitTransaction();
        } finally {
            realm.close();
        }
    }

    @SuppressWarnings("unused")
    public void removeMembers(Realm realm, final String groupId, final List<String> members, final CallBack callBack) {
        final Exception e = checkPermission(realm, groupId);
        if (e != null) { //unauthorised
            doNotify(e, callBack);
            return;
        }
        if (members.contains(getCurrentUser(realm).getUserId())) {
            if (BuildConfig.DEBUG) {
                throw new IllegalArgumentException("admin cannot remove him/herself");
            }
            doNotify(new Exception("admin cannot remove him/herself"), callBack);
        }

        userApi.removeMembersFromGroup(groupId, getCurrentUser(realm).getUserId(), members, new UserApiV2.Callback<HttpResponse>() {
            @Override
            public void done(Exception e, HttpResponse response) {
                if (e == null) {
                    Realm realm = newUserRealm();
                    try {
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
                        doNotify(null, callBack);
                    } finally {
                        realm.close();
                    }
                } else {
                    doNotify(e, callBack);

                }
            }
        });
    }

    private boolean isUser(String id) {
        Realm realm = newUserRealm();
        try {
            return realm.where(User.class).equalTo(User.FIELD_ID, id).findFirst() != null;
        } finally {
            realm.close();
        }
    }

    public boolean isAdmin(Realm realm, String groupId, String userId) {
        User group = realm.where(User.class).equalTo(User.FIELD_ID, groupId).findFirst();
        if (group == null) {
            realm.close();
            throw new IllegalArgumentException("no group with such id");
        }
        String adminId = group.getAdmin().getUserId();
        return adminId.equals(userId);
    }

    private Exception checkPermission(Realm realm, String groupId) {
        if (!isUser(groupId)) {
            return new IllegalArgumentException("no group with such id");
        }
        if (!ConnectionUtils.isConnectedOrConnecting()) {
            return (NO_CONNECTION_ERROR);
        }
        if (!isAdmin(realm, groupId, getCurrentUser(realm).getUserId())) {
            return new IllegalAccessException(Config.getApplicationContext().getString(R.string.not_permitted_group));
        }
        return null;
    }

    public void addMembersToGroup(Realm realm, final String groupId, final Set<String> membersId, final CallBack callBack) {
        Exception e = checkPermission(realm, groupId);
        if (e != null) {
            doNotify(e, callBack);
            return;
        }
        if (membersId.isEmpty()) {
            doNotify(new Exception("No number to add"), callBack);
            return;
        }
        userApi.addMembersToGroup(groupId, getCurrentUser(realm).getUserId(), membersId, new UserApiV2.Callback<HttpResponse>() {
            @Override
            public void done(Exception e, HttpResponse httpResponse) {
                if (e == null) {
                    Realm realm = newUserRealm();
                    try {
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
                                List<User> members = group.getMembers();
                                members.add(user);
                            }
                        }
                        realm.commitTransaction();
                        doNotify(null, callBack);
                    } finally {
                        realm.close();
                    }
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
                    Realm realm = newUserRealm();
                    try {
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
                    } finally {
                        realm.close();
                    }
                } else {
                    PLog.w(TAG, "failed to fetch group members with reason: " + e.getMessage());
                }
            }
        });
    }

    private void saveFreshUsers(List<User> freshMembers) {
        Realm realm = newUserRealm();
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
                    completeGetGroupInfo(group);
                }
            }
        });
    }

    private void completeGetGroupInfo(User group) {
        Realm realm = newUserRealm();
        try {
            User staleGroup = realm.where(User.class).equalTo(User.FIELD_ID, group.getUserId()).findFirst();
            final User currentUser = getCurrentUser(realm);
            if (currentUser == null) {
                throw new IllegalStateException("main user cannot be null");
            }
            realm.beginTransaction();
            if (staleGroup != null) {
                staleGroup.setName(group.getName());
                staleGroup.setDP(group.getDP());
                staleGroup.setAdmin(realm.copyToRealmOrUpdate(group.getAdmin()));
                PLog.d(TAG, "members of " + staleGroup.getName() + " are: " + staleGroup.getMembers().size());
            } else {
                group.setType(User.TYPE_GROUP);
                group.setMembers(new RealmList<User>());
                List<User> members = group.getMembers();
                members.add(group.getAdmin());
                if (!group.getAdmin().getUserId().equals(currentUser.getUserId())) {
                    members.add(currentUser);
                }
                group = realm.copyToRealmOrUpdate(group);
                PLog.d(TAG, "members of " + group.getName() + " are: " + members.size());
            }
            realm.commitTransaction();
            getGroupMembers(group.getUserId()); //async
        } finally {
            realm.close();
        }
    }

    private void refreshUserDetails(final String userId) {
        if (!ConnectionUtils.isConnectedOrConnecting()) {
            return;
        }
        //update user here
        Worker.refreshUser(userId);
    }

    void doRefreshUserDetails(final String userId) {
        Realm realm = newUserRealm();
        try {
            if (isGroup(realm, userId)) {
                doRefreshGroup(userId);
            } else {
                doRefreshUser(userId);
            }
        } finally {
            realm.close();
        }

        String wasChangingDp = getStringPref(userId + CHANGE_DP_KEY, NO_DP_CHANGE);
        if (!wasChangingDp.equals(NO_DP_CHANGE) && !dpChangeInProgress.contains(userId)) {
            if (new File(wasChangingDp).exists()) {
                changeDp(userId, wasChangingDp, new CallBack() {
                    @Override
                    public void done(Exception e) {
                        PLog.d(TAG, "change dp %s", e == null ? "success" : "failed");
                    }
                });
            }
        }
    }

    public Observable<User> isRegistered(final String phoneNumber) {
        ThreadUtils.ensureNotMain();
        return Observable.create(new Observable.OnSubscribe<User>() {
            @Override
            public void call(final Subscriber<? super User> subscriber) {
                userApi.getUser(phoneNumber, new UserApiV2.Callback<User>() {
                    @Override
                    public void done(Exception e, User user) {
                        if (e == null) {
                            Realm realm = newUserRealm();
                            try {
                                realm.beginTransaction();
                                realm.copyToRealmOrUpdate(user);
                                realm.commitTransaction();
                            } finally {
                                realm.close();
                            }
                            subscriber.onNext(user);
                        } else {
                            subscriber.onError(e);
                        }
                        subscriber.onCompleted();
                    }
                });
            }
        });
    }

    private void doRefreshUser(final String userId) {
        doRefreshUser(userId, null);
    }

    private void doRefreshUser(final String userId, @Nullable final CallBack callback) {
        userApi.getUser(userId, new UserApiV2.Callback<User>() {
            @Override
            public void done(Exception e, User onlineUser) {
                if (e == null) {
                    Realm realm = newUserRealm();
                    try {
                        realm.beginTransaction();
                        realm.copyToRealmOrUpdate(onlineUser);
                        realm.commitTransaction();
                    } finally {
                        realm.close();
                    }
                } else {
                    mapUserToContactName(userId);
                    PLog.w(TAG, "refreshing user failed with reason: " + e.getMessage());
                }
            }
        });
    }

    public boolean isGroup(Realm realm, String userId) {
        return realm.where(User.class).equalTo(User.FIELD_ID, userId).equalTo(User.FIELD_TYPE, User.TYPE_GROUP).findFirst() != null;
    }


    public void fetchGroups(Realm realm) {
        doRefreshGroups(realm);
    }

    void doRefreshGroups(Realm realm) {
        User mainUser = getCurrentUser(realm);
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
        Realm realm = newUserRealm();
        try {
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
                    List<User> members = group.getMembers();
                    members.add(group.getAdmin());

                    //check to ensure that we add main user as a member but only if
                    //his is not the admin.this is avoid adding a duplicate user as
                    //without this check we can add main user twice sinch we have already
                    //added the admin
                    //noinspection ConstantConditions
                    if (!group.getAdmin().getUserId().equals(mainUser.getUserId())) {
                        members.add(mainUser);
                    }
                    realm.copyToRealmOrUpdate(group);
                }
            }
            realm.commitTransaction();
        } finally {
            realm.close();
        }
    }

    public void changeDp(Realm realm, String imagePath, CallBack callBack) {
        this.changeDp(getCurrentUser(realm).getUserId(), imagePath, callBack);
    }


    public void changeDp(final String userId, final String imagePath, final CallBack callback) {
        final File imageFile = new File(imagePath);
        if (!imageFile.exists()) {
            doNotify(new Exception("file " + imagePath + " does not exist"), callback);
            return;
        }
        synchronized (dpChangeInProgress) {
            if (!dpChangeInProgress.add(userId)) {
                PLog.d(TAG, "already changing dp");
                doNotify(new Exception(Config.getApplicationContext().getString(R.string.busy)), callback);
                return;
            }
        }
        // TODO: 11/5/2015 replace this with a job
        doChangeDp(userId, imagePath, callback);
    }

    private void doChangeDp(final String userId, final String imagePath, final CallBack callback) {
        TaskManager.executeNow(new Runnable() {
            @Override
            public void run() {
                final File dpFile = saveDpLocally(userId, new File(imagePath));
                if (dpFile != null) {
                    doNotify(null, callback);
                    Worker.changeDp(userId, dpFile.getAbsolutePath());
                } else {
                    doNotify(new Exception(Config.getApplicationContext().getString(R.string.an_error_occurred)), callback);
                    Intent intent = new Intent(Config.getApplicationContext(), Worker.class);
                    intent.setAction(Worker.CHANGE_DP);
                    intent.putExtra(User.FIELD_ID, userId);
                    intent.putExtra(User.FIELD_DP, imagePath);
                    ErrorCenter.reportError(userId, Config.getApplicationContext().getString(R.string.dp_change_failed), intent);
                    putStandAlonePref(userId + CHANGE_DP_KEY, NO_DP_CHANGE);
                    synchronized (dpChangeInProgress) {
                        dpChangeInProgress.remove(userId);
                    }
                }
            }
        }, false);
    }

    private File saveDpLocally(String userId, File imageFile) {
        StringBuilder name = null;
        try {
            name = new StringBuilder(FileUtils.hashFile(imageFile));
        } catch (IOException e) {
            PLog.f(TAG, e.getMessage(), e);
            return null;
        }
        name.append("_").append(userId.replaceAll("[\\Q@\\E\\s]+", "_"));
        String extension = FileUtils.getExtension(imageFile.getAbsolutePath(), "jpg");
        name.append(".").append(extension);
        PLog.d(TAG, "standardised fileName: %s", name.toString());
        final File dpFile = new File(Config.getAppProfilePicsBaseDir(), name.toString());
        try {
            if (dpFile.exists() && dpFile.getCanonicalPath().equals(imageFile.getCanonicalPath())) {
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

        Realm realm = newUserRealm();
        try {
            User user = realm.where(User.class).equalTo(User.FIELD_ID, userId).findFirst();
            if (user != null) {
                realm.beginTransaction();
                user.setDP(dpFile.getAbsolutePath());
                realm.commitTransaction();
                return dpFile;
            }
            return null;
        } finally {
            realm.close();

        }
    }

    void completeDpChange(Realm realm, final String userId, String dpFile) {
        putStandAlonePref(userId + CHANGE_DP_KEY, dpFile);
        if (!ConnectionUtils.isConnectedOrConnecting()) {
            synchronized (dpChangeInProgress) {
                dpChangeInProgress.remove(userId);
            }
            return;
        }
        String placeHolder = isGroup(realm, userId) ? "groups" : "users";
        userApi.changeDp(placeHolder, userId, new File(dpFile), new UserApiV2.Callback<HttpResponse>() {
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
        doLogIn(user, callback);
    }

    private void doLogIn(final User user, final CallBack callback) {
        userApi.logIn(user, new UserApiV2.Callback<User>() {
            @Override
            public void done(Exception e, User backendUser) {
                if (e == null) {
                    Realm realm = newUserRealm();
                    try {
                        saveMainUser(backendUser);
                        doRefreshGroups(realm); //async
                        doNotify(null, callback);
                    } finally {
                        realm.close();
                    }
                } else {
                    doNotify(e, callback);
                }
            }
        });
    }

    private Realm newUserRealm() {
        return User.Realm(Config.getApplicationContext());
    }

    public String getPrivateKey() {
        String privateKey = getStringPref("user.rsa.private.key.encoded", null);
        if (GenericUtils.isEmpty(privateKey))
            throw new IllegalStateException("private key is null");
        return privateKey;
    }

    @Nullable
    public String publicKeyForUser(String userId) {
        String publicKey = getStringPref("user.rsa.public.key.encoded." + userId, null);
        if (GenericUtils.isEmpty(publicKey)) {
            publicKey = userApi.getPublicKeyForUserSync(userId);
            putSessionPref("user.rsa.public.key.encoded." + userId, publicKey);
        }
        return publicKey;
    }

    public void signUp(final String name, final String phoneNumber, final String countryIso,
                       String city, final CallBack callback) {
        if (!ConnectionUtils.isConnectedOrConnecting()) {
            doNotify(NO_CONNECTION_ERROR, callback);
            return;
        }
        completeSignUp(name, phoneNumber, countryIso, city, callback);
    }

    private void completeSignUp(final String name, final String phoneNumber,
                                final String countryIso, String city, final CallBack callback) {
        if (TextUtils.isEmpty(phoneNumber)) {
            doNotify(new Exception("phone number is invalid"), callback);
        } else if (TextUtils.isEmpty(countryIso)) {
            doNotify(new Exception("ccc is invalid"), callback);

        } else {
            if (!TextUtils.isEmpty(name)) {
                Pair<String, String> errorNamePair = isValidUserName(name);
                if (errorNamePair.second != null) {
                    doNotify(new Exception(errorNamePair.second), callback);
                    return;
                }
            }
            doSignup(name, phoneNumber, countryIso, city, callback);

        }
    }

    private void doSignup(final String name,
                          final String phoneNumber,
                          final String countryIso,
                          String city,
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
        user.setName(name.toLowerCase(Locale.getDefault()));
        user.setCountry(countryIso);
        user.setCity(city.toLowerCase(Locale.getDefault()));
        userApi.registerUser(user, new UserApiV2.Callback<User>() {
            @Override
            public void done(Exception e, User backEndUser) {
                if (e == null) {
                    saveMainUser(backEndUser);
                    doNotify(null, callback);
                } else {
                    doNotify(e, callback);
                }
            }

        });
    }

    public void verifyUser(final Realm realm, final String token, final String pushID, final CallBack callBack) {
        if (isUserVerified(realm)) {
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
        if (!isUserLoggedIn(realm)) {
            throw new IllegalStateException("no user logged for verification");
        }
        final String userId = getCurrentUser(realm).getUserId();
        TaskManager.execute(new Runnable() {
            @Override
            public void run() {
                synchronized (UserManager.class) {
                    KeyPair keyPair = RSA.generatePublicPrivateKeyPair();
                    putPref("user.rsa.private.key.encoded", RSA.encodeRSAPrivateKeyToString(keyPair.getPrivate()));
                    String publicKey = RSA.encodeRSAPublicKeyToString(keyPair.getPublic());
                    putPref("user.rsa.public.key.encoded", publicKey);
                    userApi.verifyUser(userId, token, pushID, publicKey, new UserApiV2.Callback<UserApiV2.SessionData>() {
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
            }
        }, false);
    }

    private void initialiseSettings() {
        try {
            String json = IOUtils.toString(Config.getApplicationContext().getAssets().open("settings.json"), Charsets.UTF_8);
            createPrefs(new JSONArray(json));
        } catch (IOException | JSONException e) {
            throw new RuntimeException(e.getCause());
        }
    }

    public void sendVerificationToken(Realm realm, final CallBack callback) {
        if (!isUserLoggedIn(realm)) {
            throw new IllegalStateException();
        }
        if (BuildConfig.DEBUG && isUserVerified(realm)) {
            throw new IllegalStateException();
        }
        if (!ConnectionUtils.isConnected()) {
            doNotify(NO_CONNECTION_ERROR, callback);
            return;
        }
        userApi.sendVerificationToken(getMainUserId(realm), new UserApiV2.Callback<HttpResponse>() {
            @Override
            public void done(Exception e, HttpResponse aBoolean) {
                doNotify(e, callback);
            }
        });
    }

    public void resendToken(Realm realm, final CallBack callBack) {
        if (!isUserLoggedIn(realm)) {
            throw new IllegalArgumentException(new Exception("no user logged for verification"));
        }
        if (isUserVerified(realm)) {
            doNotify(null, callBack);
            return;
        }
        if (!ConnectionUtils.isConnectedOrConnecting()) {
            doNotify(NO_CONNECTION_ERROR, callBack);
            return;
        }
        userApi.resendToken(getCurrentUser(realm).getUserId(), null, new UserApiV2.Callback<HttpResponse>() {
            @Override
            public void done(Exception e, HttpResponse response) {
                doNotify(e, callBack);
            }
        });
    }

//    @SuppressWarnings("unused")
//    public void logOut(Context context, final CallBack logOutCallback) {
//
//        oops();
//        //TODO logout user from backend
//        String userId = getSessionStringPref(KEY_SESSION_ID, null);
//        if ((userId == null)) {
//            throw new AssertionError("calling logout when no user is logged in"); //security hole!
//        }
//
//        Realm realm = User.Realm(context);
//        // TODO: 6/14/2015 remove this in production code.
//        User user = realm.where(User.class).equalTo(User.FIELD_ID, userId).findFirst();
//        if (user == null) {
//            throw new IllegalStateException("existing session id with no corresponding User in the database");
//        }
//        realm.clear(User.class);
//        realm.clear(Message.class);
//        realm.clear(Conversation.class);
//        realm.close();
//    }


    private void resetRateLimit(Object tag) {
        synchronized (rateLimiter) {
            rateLimiter.remove(tag);
        }
    }

    private boolean rateLimitNotExceeded(Object tag, long interval) {
        if (tag == null) {
            throw new IllegalArgumentException("tag == null");
        }
        if (interval <= 0) {
            throw new IllegalArgumentException("interval <=0 ");
        }
        synchronized (rateLimiter) {
            Long lastUpdated = rateLimiter.get(tag);
            if (lastUpdated != null) {
                long delta = SystemClock.uptimeMillis() - lastUpdated;
                if (delta < interval) {
                    return false;
                }
            }
            rateLimiter.put(tag, SystemClock.uptimeMillis());
        }
        return true;
    }


    public void search(final String query) {
        ThreadUtils.ensureNotMain();
        ContactsManager.query(Config.getApplicationContext(), query)
                .map(new Func1<List<ContactsManager.Contact>, List<User>>() {
                    @Override
                    public List<User> call(List<ContactsManager.Contact> contacts) {
                        Realm realm = User.Realm(Config.getApplicationContext());
                        try {
                            List<User> ret = new ArrayList<>(contacts.size());
                            for (ContactsManager.Contact contact : contacts) {
                                User u = realm.copyFromRealm(realm.where(User.class).equalTo(User.FIELD_ID, contact.numberInIEE_Format).findFirst());
                                if (u == null) {
                                    u = new User();
                                    u.setName(contact.name);
                                    u.setUserId(contact.numberInIEE_Format);
                                    u.setCity("");
                                    u.setDP("");
                                    u.setCountry("");
                                }
                                ret.add(u);
                            }
                            return ret;
                        } finally {
                            realm.close();
                        }
                    }
                })
                .subscribe(new Subscriber<List<User>>() {
                    @Override
                    public void onCompleted() {
                    }

                    @Override
                    public void onError(Throwable e) {
                        EventBus.getDefault().post(Event.create(EVENT_SEARCH_RESULTS, new Exception(e.getMessage(), e), null));
                    }

                    @Override
                    public void onNext(final List<User> users) {
                        EventBus.getDefault().post(Event.create(EVENT_SEARCH_RESULTS, null, users));
                        if (ConnectionUtils.isConnected()) {
                            userApi.search(query, new UserApiV2.Callback<List<User>>() {
                                @Override
                                public void done(Exception e, List<User> users2) {
                                    if (e == null) {
                                        users2.addAll(users);
                                    }
                                    EventBus.getDefault().post(Event.create(EVENT_SEARCH_RESULTS, e == null ? null : e, users2));
                                }
                            });
                        }
                    }
                });
    }


    public void leaveGroup(Realm realm, final String id, final CallBack callBack) {
        if (!isGroup(realm, id) || isAdmin(realm, id)) {
            throw new IllegalArgumentException("not group or you are not the admin");
        }
        if (!ConnectionUtils.isConnectedOrConnecting()) {
            doNotify(NO_CONNECTION_ERROR, callBack);
        }

        userApi.leaveGroup(id, getCurrentUser(realm).getUserId(), null, new UserApiV2.Callback<HttpResponse>() {

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
        Realm realm = newUserRealm();
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
        group.deleteFromRealm();
        realm.commitTransaction();
    }

    private void cleanConvesation(String peerId) {
        Realm conversationRealm = Conversation.Realm(Config.getApplicationContext());
        conversationRealm.beginTransaction();
        conversationRealm.where(Conversation.class).equalTo(Conversation.FIELD_PEER_ID, peerId).findAll().deleteAllFromRealm();
        conversationRealm.commitTransaction();
        conversationRealm.close();
    }

    private void cleanMessages(String peerId) {
        Realm messageRealm = Message.REALM(Config.getApplicationContext());
        try {
            messageRealm.beginTransaction();
            messageRealm.where(Message.class).equalTo(Message.FIELD_TO, peerId).findAll().deleteAllFromRealm();
            messageRealm.commitTransaction();
        } finally {
            messageRealm.close();
        }
    }

    public boolean isAdmin(Realm realm, String id) {
        return isAdmin(realm, id, getCurrentUser(realm).getUserId());
    }

    public String getUserCountryISO(Realm realm) {
        if (!isUserLoggedIn(realm)) {
            throw new IllegalStateException("no user logged in");
        }
        return getCurrentUser(realm).getCountry();
    }

    public void reset(Realm realm, final CallBack callBack) {
        if (isUserVerified(realm)) {
            throw new RuntimeException("use logout instead");
        }
        TaskManager.execute(new Runnable() {
            @Override
            public void run() {
                if (!ConnectionUtils.isConnected()) {
                    doNotify(NO_CONNECTION_ERROR, callBack);
                    return;
                }
                cleanUp();
                doNotify(null, callBack);
            }
        }, true);
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
            realm.close();

            realm = PersistedSetting.REALM(sessionFile);
            clearClass(realm, PersistedSetting.class);
            realm.close();

            realm = Message.REALM(Config.getApplicationContext());
            clearClass(realm, Message.class);
            realm.close();

            realm = Conversation.Realm(Config.getApplicationContext());
            clearClass(realm, Conversation.class);
            realm.close();

            realm = newUserRealm();
            clearClass(realm, User.class);
            realm.close();

            realm = Country.REALM(Config.getApplicationContext());
            clearClass(realm, Country.class);
            realm.close();

            clearAllPrefs();
        }
    }

    @SuppressLint("CommitPrefEdits")
    private void clearAllPrefs() {
        try {
            File directory = new File(Config.getApplication().getFilesDir().getParent(), "shared_prefs");
            if (directory.isDirectory()) {
                String[] files = directory.list();
                if (files != null) {
                    for (String file : files) {
                        if (file.endsWith(".xml")) {
                            // strip off the .xml from the name
                            SharedPreferences preferences = Config.getPreferences(file.substring(0, file.length() - 4));
                            preferences.edit().clear().commit();
                        }
                        if (!new File(directory, file).delete()) {
                            PLog.w(TAG, "failed to delete file: %s", file);
                        } else {
                            PLog.w(TAG, "deleted file: %s", file);
                        }
                    }
                }
            }
            org.apache.commons.io.FileUtils.deleteDirectory(directory);
        } catch (IOException e) {
            PLog.e(TAG, e.getMessage(), e);
            //ignore
        }
    }

    private void clearClass(Realm realm, Class<? extends RealmObject> clazz) {
        realm.beginTransaction();
        realm.delete(clazz);
        realm.commitTransaction();
    }

    public Pair<String, String> isValidGroupName(Realm realm, String proposedName) {
        Context applicationContext = Config.getApplicationContext();
        String errorMessage = null;
        proposedName = proposedName.replaceAll("\\p{Space}+", " ");
        if (proposedName.length() < 5) {
            errorMessage = applicationContext.getString(R.string.name_too_short);
        } else if (proposedName.length() > 30) {
            errorMessage = applicationContext.getString(R.string.group_name_too_long);
        } else if (!Character.isLetter(proposedName.codePointAt(0))) {
            errorMessage = applicationContext.getString(R.string.name_starts_with_non_letter);
        } else if (proposedName.indexOf('@') != -1 || proposedName.indexOf('-') != -1) {
            errorMessage = applicationContext.getString(R.string.invalid_name_format_error);
        } else if (getCurrentUser(realm) != null && UserManager.getInstance().isGroup(realm, User.generateGroupId(realm, proposedName))) {
            errorMessage = Config.getApplicationContext().getString(R.string.group_already_exists).toUpperCase();
        }
        return new Pair<>(proposedName, errorMessage);
    }

    public Pair<String, String> isValidUserName(String proposedName) {
        String errorMessage = null;
        Context applicationContext = Config.getApplicationContext();
        if (proposedName.matches(".*\\p{Space}+.*")) {
            errorMessage = applicationContext.getString(R.string.error_space_in_name);
        } else if (proposedName.length() < 2) {
            errorMessage = applicationContext.getString(R.string.name_too_short);
        } else if (proposedName.length() > 20) {
            errorMessage = applicationContext.getString(R.string.name_too_long);
        } else if (!Character.isLetter(proposedName.codePointAt(0))) {
            errorMessage = applicationContext.getString(R.string.name_starts_with_non_letter);
        } else if (proposedName.contains("@")) {
            errorMessage = applicationContext.getString(R.string.invalid_name_format_error);
        }
        return new Pair<>(proposedName, errorMessage);
    }

    public User fetchUserIfRequired(String userId) {
        Realm realm = newUserRealm();
        try {
            User user = fetchUserIfRequired(realm, userId);
            user = User.copy(user);
            return user;
        } finally {
            realm.close();

        }
    }

    public User fetchUserIfRequired(Realm realm, String userId) {
        return fetchUserIfRequired(realm, userId, false);
    }

    public User fetchUserIfRequired(Realm realm, final String userId, boolean refresh) {
        User peer = realm.where(User.class).equalTo(User.FIELD_ID, userId).findFirst();

        if (peer == null) {
            peer = new User();
            peer.setUserId(userId);
            if (userId.contains("@")) {
                peer.setType(User.TYPE_GROUP);
                peer.setMembers(new RealmList<User>());
                String[] parts = userId.split("\\Q@\\E");
                peer.setName(parts[0]);
                User admin = fetchUserIfRequired(realm, parts[1]);
                peer.setAdmin(admin);
                List<User> members = peer.getMembers();
                members.add(admin);
                if (!isCurrentUser(realm, parts[1])) {
                    //noinspection ConstantConditions,ConstantConditions
                    members.add(getCurrentUser(realm));
                }
                peer.setInContacts(false);
                peer.setDP("avatar_empty");
                refresh(userId, refresh);
            } else {
                peer.setInContacts(false); //we cannot tell for now
                peer.setDP("avatar_empty");
                peer.setType(User.TYPE_NORMAL_USER);
                peer.setName(PhoneNumberNormaliser.toLocalFormat("+" + userId, getUserCountryISO(realm)));
                refresh(userId, refresh);
            }
            realm.beginTransaction();
            realm.copyToRealmOrUpdate(peer);
            realm.commitTransaction();
        } else if (!peer.getInContacts()) {
            refresh(userId, refresh);
        }
        return peer;
    }

    private void refresh(final String userId, final boolean refresh) {
        TaskManager.executeNow(new Runnable() {
            @Override
            public void run() {
                if (refresh && ConnectionUtils.isConnected()) {
                    if (rateLimitNotExceeded("refreshUser" + userId, 2 * 60 * 1000)) {
                        refreshUserDetails(userId);
                        return;
                    }
                    PLog.d(TAG, "not refreshing, user... refreshed not too long ago");
                }
                mapUserToContactName(userId);
            }
        }, true);
    }

    private void mapUserToContactName(String userId) {
        ThreadUtils.ensureNotMain();
        ContactsManager.Contact contact = ContactsManager.findContact(userId);
        if (contact != null) {
            Realm realm = newUserRealm();
            try {
                User user = realm.where(User.class).equalTo(User.FIELD_ID, userId).findFirst();
                if (user != null) {
                    realm.beginTransaction();
                    user.setName(contact.name);
                    idsAndNames.put(userId, contact.name);
                    user.setInContacts(true);
                    realm.commitTransaction();
                }
            } finally {
                realm.close();
            }
        }
    }

    @SuppressWarnings("unused")
    public List<String> allUserIds(Realm realm) {
        try {
            RealmResults<User> users = realm.where(User.class)
                    .equalTo(User.FIELD_TYPE, User.TYPE_NORMAL_USER)
                    .notEqualTo(User.FIELD_ID, getMainUserId(realm)).findAll();
            return User.aggregateUserIds(users, null);
        } finally {
            realm.close();
        }
    }

    public Object putStandAlonePref(String key, Object value) {
        ensureNotProtectedKey(key);
        if (key == null) {
            throw new IllegalArgumentException("null!");
        }
        if (value == null) {
            return null;
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
        try {
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
        } finally {
            realm.close();
        }
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
            @SuppressWarnings("deprecation")
            @Override
            public void run() {
                Realm realm = PersistedSetting.REALM(userPrefsLocation);
                try {
                    realm.beginTransaction();
                    realm.delete(PersistedSetting.class);
                    realm.commitTransaction();
                    initialiseSettings();
                    if (callback != null) {
                        doNotify(null, callback);
                    }
                } finally {
                    realm.close();
                }
            }
        };
        TaskManager.executeNow(runnable, false);
    }


    private final Lock blockLock = new ReentrantLock(true),
            muteLock = new ReentrantLock(true);

    private void ensureNotNull(Object o) {
        if (o == null) {
            throw new IllegalArgumentException("null!");
        }
    }

    public void blockUser(final String peerId) {
        ensureNotNull(peerId);
        try {
            blockLock.lock();
            SharedPreferences preferences = Config.getPreferences(BLOCKED_USERS);
            preferences.edit().putString(BLOCKED_USERS + peerId, peerId).apply();
        } finally {
            blockLock.unlock();
        }
    }

    public boolean isBlocked(String userId) {
        ensureNotNull(userId);
        return Config.getPreferences(BLOCKED_USERS).contains(BLOCKED_USERS + userId);

    }

    public void unBlockUser(String userId) {
        ensureNotNull(userId);
        try {
            blockLock.lock();
            SharedPreferences preferences = Config.getPreferences(BLOCKED_USERS);
            preferences.edit().remove(BLOCKED_USERS + userId).apply();
        } finally {
            blockLock.unlock();
        }
    }


    public boolean isMuted(String peerId) {
        ensureNotNull(peerId);
        return Config.getPreferences(MUTED_USERS).contains(MUTED_USERS + peerId);
    }

    public boolean muteUser(String peerId) {
        ensureNotNull(peerId);
        try {
            muteLock.lock();
            SharedPreferences preferences = Config.getPreferences(MUTED_USERS);
            preferences.edit().putString(MUTED_USERS + peerId, peerId).apply();
        } finally {
            muteLock.unlock();
        }
        return true;
    }

    public boolean unMuteUser(String peerId) {
        ensureNotNull(peerId);
        try {
            muteLock.lock();
            SharedPreferences preferences = Config.getPreferences(MUTED_USERS);
            preferences.edit().remove(MUTED_USERS + peerId).apply();
        } finally {
            muteLock.unlock();
        }
        return true;
    }

    public String getNewAuthTokenSync(Realm realm) throws YennkasaException {
        if (!isUserVerified(realm)) throw new YennkasaException("not registered");
        return userApi.newAuthToken();
    }

    public void updatePushID(String newPushId) throws YennkasaException {
        userApi.updatePushID(newPushId);
    }

    public Pair<String, Long> getSinchRegistrationToken() {
        return userApi.getSinchToken();
    }

    public void enableSearch(final boolean enableSearch, final CallBack callBack) {
        userApi.enableSearch(enableSearch, new UserApiV2.Callback<Boolean>() {
            @Override
            public void done(Exception e, Boolean aBoolean) {
                putPref(ENABLE_SEARCH, enableSearch);
                doNotify(e, callBack);
            }
        });
    }


    public interface CallBack {
        void done(Exception e);
    }

    public interface CreateGroupCallBack {
        void done(Exception e, String groupId);

    }

    public String getName(Realm realm, String userId) {
        if (TextUtils.isEmpty(userId)) {
            throw new IllegalArgumentException("userid == null");
        }
        String userName = idsAndNames.get(userId);
        if (userName == null) {
            User user = realm.where(User.class).equalTo(User.FIELD_ID, userId).findFirst();
            if (user != null) {
                userName = user.getName();
                idsAndNames.put(userId, userName);
            } else {
                userName = PhoneNumberNormaliser.toLocalFormat("+" + userId, getUserCountryISO(realm));
            }
        }
        return userName;
    }

    private final Map<String, String> idsAndNames = new ConcurrentHashMap<>();
}
