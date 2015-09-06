package com.pair.data;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import com.google.i18n.phonenumbers.NumberParseException;
import com.pair.Config;
import com.pair.adapter.BaseJsonAdapter;
import com.pair.adapter.UserJsonAdapter;
import com.pair.net.HttpResponse;
import com.pair.net.UserApiV2;
import com.pair.pairapp.BuildConfig;
import com.pair.pairapp.R;
import com.pair.parse_client.ParseClient;
import com.pair.util.ConnectionUtils;
import com.pair.util.FileUtils;
import com.pair.util.GcmUtils;
import com.pair.util.PhoneNumberNormaliser;

import org.apache.http.HttpStatus;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.realm.Realm;
import io.realm.RealmList;
import retrofit.RetrofitError;
import retrofit.mime.TypedFile;

/**
 * @author by Null-Pointer on 5/27/2015.
 */
@SuppressWarnings({"ThrowableResultOfMethodCallIgnored", "TryFinallyCanBeTryWithResources", "unused"})
public class UserManager {

    private static final String TAG = UserManager.class.getSimpleName();
    private static final String KEY_SESSION_ID = "lfl/-90-09=klvj8ejf"; //don't give a clue what this is for security reasons
    private static final String KEY_USER_PASSWORD = "klfiielklaklier"; //and this one too
    public static final String KEY_USER_VERIFIED = "vvlaikkljhf"; // and this


    private volatile User mainUser;
    private final Object mainUserLock = new Object();
    private static final UserManager INSTANCE = new UserManager();

    private final Exception NO_CONNECTION_ERROR;
    private final BaseJsonAdapter<User> adapter = new UserJsonAdapter();


    private final UserApiV2 userApi;
    private final Handler MAIN_THREAD_HANDLER;

    @Deprecated
    public static UserManager getInstance(@SuppressWarnings("UnusedParameters") @NonNull Context context) {
        return INSTANCE;
    }

    public static UserManager getInstance() {
        return INSTANCE;
    }

    private UserManager() {
        NO_CONNECTION_ERROR = new Exception(Config.getApplicationContext().getString(R.string.st_unable_to_connect));
        MAIN_THREAD_HANDLER = new Handler(Looper.getMainLooper());
        userApi = ParseClient.getInstance();
    }

    private void saveMainUser(User user) {
        final Context context = Config.getApplicationContext();
        Realm realm = Realm.getInstance(context);
        realm.beginTransaction();
        realm.copyToRealmOrUpdate(user);
        realm.commitTransaction();
        // TODO: 6/25/2015 encrypt the id and password before storing it
        getSettings()
                .edit()
                .putString(KEY_SESSION_ID, user.getUserId())
                .putString(KEY_USER_PASSWORD, user.getPassword())
                .commit();
    }

    public User getCurrentUser() {
        synchronized (mainUserLock) {
            if (mainUser != null) {
                return mainUser;
            }
        }
        Realm realm = Realm.getInstance(Config.getApplicationContext());
        User user = getCurrentUser(realm);
        if (user != null) {
            //returning {@link RealmObject} from methods will leak resources since
            // that will prevent us from closing the realm instance. hence we do a shallow copy.
            // downside is changes to this object will not be persisted which is just what we want
            synchronized (mainUserLock) {
                mainUser = User.copy(user);
            }
            return mainUser;
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
            return false;
        } else //noinspection ConstantConditions
            if (getSettings().getString(KEY_SESSION_ID, "").isEmpty()) {
                return false;
            }
        return true;
    }

    public boolean isUserVerified() {
        return isUserLoggedIn() && getSettings().getBoolean(KEY_USER_VERIFIED, false);
    }

    private SharedPreferences getSettings() {
        return Config.getApplicationWidePrefs();
    }

    private User getCurrentUser(Realm realm) {
        String currUserId = getSettings().getString(KEY_SESSION_ID, null);
        if (currUserId == null) {
            Config.disableComponents();
            return null;
        }
        User user = realm.where(User.class).equalTo(User.FIELD_ID, currUserId).findFirst();
        if (user == null) {
            Config.disableComponents();
            //we will effectively return null if user is null so no need for a separate return statement
            //return null;  //redundant
        }
        return user;
    }

    private String getUserPassword() {
        String password = getSettings().getString(KEY_USER_PASSWORD, null);
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
        membersId.add(getMainUserId());
        userApi.createGroup(getCurrentUser().getUserId(), groupName, membersId, new UserApiV2.Callback<User>() {

            @Override
            public void done(Exception e, User group) {
                if (e == null) {
                    completeGroupCreation(group, membersId);
                    doNotify(callBack, null, group.getUserId());
                } else {
                    Log.i(TAG, "failed to create group");
                    doNotify(callBack, e, null);
                }
            }
        });
    }

    private void completeGroupCreation(User group, Set<String> membersId) {
        Realm realm = Realm.getInstance(Config.getApplicationContext());
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
        group.getMembers().addAll(members);
        group.setAdmin(mainUser);
        group.setType(User.TYPE_GROUP);
        realm.copyToRealmOrUpdate(group);
        realm.commitTransaction();
        realm.close();
    }

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
                    Realm realm = Realm.getInstance(Config.getApplicationContext());
                    realm.beginTransaction();
                    final User group = realm.where(User.class).equalTo(User.FIELD_ID, groupId).findFirst();
                    final ContactsManager.Filter<User> filter = new ContactsManager.Filter<User>() {
                        @Override
                        public boolean accept(User user) {
                            return (user != null && group.getMembers().contains(user));
                        }
                    };
                    RealmList<User> membersToDelete = User.aggregateUsers(realm, members, filter);
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
        Realm realm = Realm.getInstance(Config.getApplicationContext());
        boolean isUser = realm.where(User.class).equalTo(User.FIELD_ID, id).findFirst() != null;
        realm.close();
        return isUser;
    }

    public boolean isAdmin(String groupId, String userId) {
        Realm realm = Realm.getInstance(Config.getApplicationContext());
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
        if (!ConnectionUtils.isConnectedOrConnecting()) {
            return (NO_CONNECTION_ERROR);
        }
        if (!isUser(groupId)) {
            return new IllegalArgumentException("no group with such id");
        }
        if (!isAdmin(groupId, getCurrentUser().getUserId())) {
            return new IllegalAccessException("you don't have the authority to add/remove a member");
        }
        return null;
    }

    public void addMembersToGroup(final String groupId, final Set<String> membersId, final CallBack callBack) {
        Exception e = checkPermission(groupId);
        if (e != null) {
            doNotify(e, callBack);
            return;
        }
        userApi.addMembersToGroup(groupId, getCurrentUser().getUserId(), membersId, new UserApiV2.Callback<HttpResponse>() {
            @Override
            public void done(Exception e, HttpResponse httpResponse) {
                if (e == null) {
                    Realm realm = Realm.getInstance(Config.getApplicationContext());
                    realm.beginTransaction();
                    final User group = realm.where(User.class).equalTo(User.FIELD_ID, groupId).findFirst();
                    final ContactsManager.Filter<User> filter = new ContactsManager.Filter<User>() {
                        @Override
                        public boolean accept(User user) {
                            return (user != null && !group.getMembers().contains(user) && !isGroup(user.getUserId()));
                        }
                    };
                    RealmList<User> newMembers = User.aggregateUsers(realm, membersId, filter);
                    group.getMembers().addAll(newMembers);
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
                    WORKER.submit(new Runnable() {
                        @Override
                        public void run() {
                            Realm realm = User.Realm(Config.getApplicationContext());
                            realm.beginTransaction();
                            User group = realm.where(User.class).equalTo(User.FIELD_ID, id).findFirst();
                            group.getMembers().clear();
                            group.getMembers().addAll(realm.copyToRealmOrUpdate(freshMembers));
                            realm.commitTransaction();
                            realm.close();
                        }
                    });
                } else {
                    Log.w(TAG, "failed to fetch group members with reason: " + e.getMessage());
                }
            }
        });
    }

    private void saveFreshUsers(List<User> freshMembers) {
        Realm realm = Realm.getInstance(Config.getApplicationContext());
        saveFreshUsers(freshMembers, realm);
        realm.close();
    }

    private List<User> saveFreshUsers(List<User> freshMembers, Realm realm) {
        realm.beginTransaction();
        List<User> ret = realm.copyToRealmOrUpdate(freshMembers);
        realm.commitTransaction();
        return ret;
    }

    public void refreshGroup(final String id) {
        if (!isUser(id)) {
            if (BuildConfig.DEBUG) {
                throw new IllegalArgumentException("passed id is invalid");
            }
            return;
        }
        doRefreshGroup(id);
    }

    private void doRefreshGroup(String id) {
        getGroupInfo(id); //async
    }

    private void getGroupInfo(final String id) {
        userApi.getGroup(id, new UserApiV2.Callback<User>() {
            @Override
            public void done(Exception e, final User group) {
                if (e == null) {
                    WORKER.submit(new Runnable() {
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
        Realm realm = Realm.getInstance(Config.getApplicationContext());
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
        realm = Realm.getInstance(Config.getApplicationContext());
        User g = realm.where(User.class).equalTo(User.FIELD_ID, id).findFirst();
        Log.d(TAG, "members of " + g.getName() + " are: " + g.getMembers().size());
        realm.close();
        getGroupMembers(id); //async
    }

    public void refreshGroups() {
        if (!ConnectionUtils.isConnectedOrConnecting()) {
            return;
        }
        getGroups();
    }

    public void refreshUserDetails(final String userId) {
        if (!ConnectionUtils.isConnectedOrConnecting()) {
            return;
        }
        //update user here
        if (isGroup(userId)) {
            doRefreshGroup(userId);
        } else {
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
                        Log.w(TAG, "refreshing user failed with reason: " + e.getMessage());
                    }
                }
            });
        }
    }

    private final ExecutorService WORKER = Executors.newSingleThreadExecutor();

    public boolean isGroup(String userId) {
        Realm realm = User.Realm(Config.getApplicationContext());
        User user = realm.where(User.class).equalTo(User.FIELD_ID, userId).equalTo(User.FIELD_TYPE, User.TYPE_GROUP).findFirst();
        realm.close();
        return user != null;
    }

    private void getGroups() {
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
        Realm realm = Realm.getInstance(Config.getApplicationContext());
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
        if (!ConnectionUtils.isConnectedOrConnecting()) {
            doNotify(NO_CONNECTION_ERROR, callback);
            return;
        }
        Realm realm = Realm.getInstance(Config.getApplicationContext());
        final User user = realm.where(User.class).equalTo(User.FIELD_ID, userId).findFirst();
        if (user == null) {
            Log.w(TAG, "can't change dp for user with id " + userId + " because no such user exists");
            doNotify(null, callback);
            return;
        }

        String placeHolder = User.isGroup(user) ? "groups" : "users";

        realm.close();
        userApi.changeDp(placeHolder, userId, new TypedFile("image/*", imageFile), new UserApiV2.Callback<HttpResponse>() {
            @Override
            public void done(Exception e, final HttpResponse response) {
                if (e == null) {
                    completeDpChangeRequest(response.getMessage(), userId, imageFile, callback);
                } else {
                    doNotify(e, callback); //may be our fault but we have reach maximum retries
                }
            }
        });
    }

    private void completeDpChangeRequest(String dpPath, String userId, File imageFile, CallBack callback) {
        final File dpFile = new File(Config.getAppProfilePicsBaseDir(), imageFile.getName());
        try {
            //noinspection ConstantConditions
            FileUtils.copyTo(imageFile, dpFile);
        } catch (IOException e) {
            //we will not cancel the transaction
            Log.e(TAG, "failed to save user's profile locally: " + e.getMessage());
            doNotify(e, callback);
            return;
        }

        Realm realm = Realm.getInstance(Config.getApplicationContext());
        User user = realm.where(User.class).equalTo(User.FIELD_ID, userId).findFirst();
        if (user != null) {
            realm.beginTransaction();
            user.setDP(dpFile.getAbsolutePath());
            realm.commitTransaction();
        }
        doNotify(null, callback);
    }

    public void logIn(final Activity context, final String phoneNumber, final String userIso2LetterCode, final CallBack callback) {
        if (!ConnectionUtils.isConnectedOrConnecting()) {
            doNotify(NO_CONNECTION_ERROR, callback);
            return;
        }
        completeLogin(phoneNumber, "dummy gcm regid", userIso2LetterCode, callback);
    }

    private void completeLogin(String phoneNumber, String gcmRegId, String userIso2LetterCode, CallBack callback) {
        if (TextUtils.isEmpty(phoneNumber)) {
            doNotify(new Exception("invalid phone number"), callback);
            return;
        }

        if (TextUtils.isEmpty(userIso2LetterCode)) {
            doNotify(new Exception("userIso2LetterCode cannot be empty"), callback);
            return;
        }
        if (TextUtils.isEmpty(gcmRegId)) {
            doNotify(new Exception("GCM registration id cannot be empty"), callback);
            return;
        }

        User user = new User();
        try {
            phoneNumber = PhoneNumberNormaliser.toIEE(phoneNumber, userIso2LetterCode);
        } catch (NumberParseException e) {
            if (com.pair.pairapp.BuildConfig.DEBUG) {
                Log.e(TAG, e.getMessage(), e.getCause());
            } else {
                Log.e(TAG, e.getMessage());
            }
            doNotify(new Exception(String.format(Config.getApplicationContext().getString(R.string.invalid_phone_number), phoneNumber)), callback);
            return;
        }
        user.setUserId(phoneNumber);
        user.setCountry(userIso2LetterCode);
        user.setGcmRegId(gcmRegId);
        String password = Base64.encodeToString(phoneNumber.getBytes(), Base64.DEFAULT);
        user.setPassword(password);
        doLogIn(user, userIso2LetterCode, callback);
    }

    //this method must be called on the main thread
    private void doLogIn(final User user, final String countryIso, final CallBack callback) {
        userApi.logIn(user, new UserApiV2.Callback<User>() {
            @Override
            public void done(Exception e, User backendUser) {
                if (e == null) {
                    backendUser.setPassword(user.getPassword());
                    saveMainUser(backendUser);
                    getSettings()
                            .edit()
                            .putBoolean(KEY_USER_VERIFIED, true)
                            .commit();
                    getGroups(); //async
                    doNotify(null, callback);
                } else {
                    doNotify(e, callback);
                }
            }
        });
    }


    public void signUp(Activity context, final String name, final String phoneNumber, final String countryIso, final CallBack callback) {
        if (!ConnectionUtils.isConnectedOrConnecting()) {
            doNotify(NO_CONNECTION_ERROR, callback);
            return;
        }
        completeSignUp(name, phoneNumber, "kaklfakjfakf", countryIso, callback);
    }

    private void completeSignUp(final String name, final String phoneNumber, final String gcmRegId, final String countryIso, final CallBack callback) {
        if (TextUtils.isEmpty(name)) {
            doNotify(new Exception("name is invalid"), callback);
        } else if (TextUtils.isEmpty(phoneNumber)) {
            doNotify(new Exception("phone number is invalid"), callback);
        } else if (TextUtils.isEmpty(countryIso)) {
            doNotify(new Exception("ccc is invalid"), callback);
        } else {
            doSignup(name, phoneNumber, gcmRegId, countryIso, callback);
        }
    }

    private void doSignup(final String name,
                          final String phoneNumber,
                          final String gcmRegId,
                          final String countryIso,
                          final CallBack callback) {
        String thePhoneNumber;
        try {
            thePhoneNumber = PhoneNumberNormaliser.toIEE(phoneNumber, countryIso);
        } catch (NumberParseException e) {
            Log.e(TAG, e.getMessage());
            doNotify(new Exception(String.format(Config.getApplicationContext().getString(R.string.invalid_phone_number), phoneNumber)), callback);
            return;
        }
        final User user = new User();
        user.setUserId(thePhoneNumber);
        String password = Base64.encodeToString(user.getUserId().getBytes(), Base64.DEFAULT);
        user.setPassword(password);
        user.setName(name);
        user.setCountry(countryIso);
        user.setGcmRegId(gcmRegId);
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
            throw new IllegalStateException(new Exception("no user logged for verification"));
        }
        userApi.verifyUser(getCurrentUser().getUserId(), token, new UserApiV2.Callback<HttpResponse>() {
            @Override
            public void done(Exception e, HttpResponse s) {
                if (e == null) {
                    getSettings().edit().putBoolean(KEY_USER_VERIFIED, true).commit();
                    doNotify(null, callBack);
                } else {
                    doNotify(e, callBack);
                }
            }
        });
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

//    public void generateAndSendVerificationToken(final String number) {
//        new Thread() {
//            @Override
//            public void run() {
//                SecureRandom random = new SecureRandom();
//                int num = random.nextInt() / 10000;
//                num = (num > 0) ? num : num * -1; //convert negative ints to positive ones
//                synchronized (this) {
//                    VERIFICATION_TOKEN = String.valueOf(num);
//                }
//                Log.d(TAG, VERIFICATION_TOKEN);
//                SmsManager.getDefault().sendTextMessage(number, null, VERIFICATION_TOKEN, null, null);
//            }
//        }.start();
//    }
//
//    private synchronized String getVERIFICATION_TOKEN() {
//        return VERIFICATION_TOKEN;
//    }

    public void LogOut(Context context, final CallBack logOutCallback) {
        //TODO logout user from backend
        String userId = getSettings().getString(KEY_SESSION_ID, null);
        if ((userId == null)) {
            throw new AssertionError("calling logout when no user is logged in"); //security hole!
        }

        getSettings()
                .edit()
                .remove(KEY_SESSION_ID)
                .apply();
        Realm realm = Realm.getInstance(context);
        // TODO: 6/14/2015 remove this in production code.
        User user = realm.where(User.class).equalTo(User.FIELD_ID, userId).findFirst();
        if (user == null) {
            throw new IllegalStateException("existing session id with no corresponding User in the database");
        }
        realm.close();
        GcmUtils.unRegister(context, new GcmUtils.UnregisterCallback() {
            @Override
            public void done(Exception e) {
                //we don't care about gcm regid
                cleanUpRealm();
                doNotify(null, logOutCallback);
            }
        });
    }

    public void syncContacts(final List<String> array) {
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

    private void cleanUpRealm() {
        Realm realm = Realm.getInstance(Config.getApplicationContext());
        realm.beginTransaction();
        realm.clear(User.class);
        realm.clear(Message.class);
        realm.clear(Conversation.class);
        realm.commitTransaction();
    }


    // FIXME: 6/25/2015 find a sensible place to keep this error MAIN_THREAD_HANDLER so that message dispatcher and others can share it
    private Exception handleError(RetrofitError retrofitError) {
        if (retrofitError.getCause() instanceof SocketTimeoutException) { //likely that  user turned on data but no plan
            return NO_CONNECTION_ERROR;
        } else if (retrofitError.getCause() instanceof EOFException) { //usual error when we try to connect first time after server startup
            Log.w(TAG, "EOF_EXCEPTION trying again");
            return null;
        }
        if (retrofitError.getKind().equals(RetrofitError.Kind.UNEXPECTED)) {
            Log.w(TAG, "unexpected error, trying again");
            return null;
        } else if (retrofitError.getKind().equals(RetrofitError.Kind.HTTP)) {
            int statusCode = retrofitError.getResponse().getStatus();
            if (statusCode == HttpStatus.SC_INTERNAL_SERVER_ERROR) {
                Log.w(TAG, "internal server error, trying again");
                return null;
            }
            //crash early
            // as far as we know, our backend will only return other status code if its is our fault and that normally should not happen
            Log.wtf(TAG, "internal error, exiting");
            throw new RuntimeException("An unknown internal error occurred");
        } else if (retrofitError.getKind().equals(RetrofitError.Kind.CONVERSION)) { //crash early
            Log.wtf(TAG, "internal error ");
            throw new RuntimeException("poorly encoded json data");
        } else if (retrofitError.getKind().equals(RetrofitError.Kind.NETWORK)) {
            if (ConnectionUtils.isConnectedOrConnecting()) {
                return null;
            }
            //bubble up error and empty
            Log.w(TAG, "no network connection, aborting");
            return NO_CONNECTION_ERROR;
        }

        //may be retrofit added some error kinds in a new version we are not aware of so lets crash to ensure that
        //we find out
        throw new AssertionError("unknown error kind");
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
                    Realm realm = Realm.getInstance(Config.getApplicationContext());
                    try {
                        User group = realm.where(User.class).equalTo("_id", id).findFirst();
                        if (group != null) {
                            cleanMessages();
                            cleanConvesation();
                            removeUser(realm, group);
                            doNotify(null, callBack);
                        }
                    } finally {
                        realm.close();
                    }
                } else {
                    doNotify(e, callBack);
                }
            }

            private void removeUser(Realm realm, User group) {
                realm.beginTransaction();
                group.removeFromRealm();
                realm.commitTransaction();
            }

            private void cleanConvesation() {
                Realm conversationRealm = Conversation.Realm(Config.getApplicationContext());
                conversationRealm.beginTransaction();
                conversationRealm.where(Conversation.class).equalTo(Conversation.FIELD_PEER_ID, id).findAll().clear();
                conversationRealm.commitTransaction();
                conversationRealm.close();
            }

            private void cleanMessages() {
                Realm messageRealm = Message.REALM(Config.getApplicationContext());
                messageRealm.beginTransaction();
                messageRealm.where(Message.class).equalTo(Message.FIELD_TO, id).findAll().clear();
                messageRealm.commitTransaction();
                messageRealm.close();
            }
        });
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
        WORKER.submit(new Runnable() {
            @Override
            public void run() {
                if (isUserVerified()) {
                    throw new RuntimeException("use logout instead");
                }
                if (!ConnectionUtils.isConnected()) {
                    doNotify(NO_CONNECTION_ERROR, callBack);
                    return;
                }
                Realm realm = Realm.getInstance(Config.getApplicationContext());
                try {
                    realm.beginTransaction();
                    realm.clear(User.class);
                    realm.commitTransaction();
                    cleanUp();
                    doNotify(null, callBack);
                } finally {
                    realm.close();
                }
            }
        });
    }

    private void doNotify(final Exception e, final CallBack callBack) {
        MAIN_THREAD_HANDLER.post(new Runnable() {
            @Override
            public void run() {
                callBack.done(e);
            }
        });
    }

    private void doNotify(final CreateGroupCallBack callBack, final Exception e, final String id) {
        MAIN_THREAD_HANDLER.post(new Runnable() {
            @Override
            public void run() {
                callBack.done(e, id);
            }
        });
    }

    public static String getMainUserId() {
        return getInstance().getCurrentUser().getUserId();
    }

    private void cleanUp() {
        getSettings().edit()
                .remove(KEY_SESSION_ID)
                .remove(KEY_USER_VERIFIED)
                .remove(KEY_USER_PASSWORD)
                .commit();
    }

    public void refreshDp(final String id, final CallBack callBack) {
        if (!ConnectionUtils.isConnected()) {
            doNotify(NO_CONNECTION_ERROR, callBack);
            return;
        }
        WORKER.submit(new Runnable() {
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
                            Log.e(TAG, e.getMessage(), e.getCause());
                            doNotify(e, callBack);
                            return;
                        }
                        FileUtils.save(dpPath, user.getDP());
                        updateUserDpInRealm(userId, callBack, encoded);
                    } catch (IOException e2) {
                        doNotify(new Exception(Config.getApplicationContext().getResources().getString(R.string.error_occurred)), callBack);
                    }
                }
            });
        } else {
            updateUserDpInRealm(userId, callBack, encoded);
        }

    }

    @NonNull
    public String encodeDp(String dp) {
        Log.d(TAG, "raw dp: " + dp);
        String encoded = dp;
        if (encoded.startsWith("http")) {
            encoded = Base64.encodeToString(dp.getBytes(), Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING) + ".jpg";
        }
        Log.d(TAG, "encoded dp: " + encoded);
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

    public boolean supportsCalling(String userId) {
        if (isGroup(userId)) {
            return false;
        }

        Realm realm = User.Realm(Config.getApplicationContext());
        try {
            User user = realm.where(User.class).equalTo(User.FIELD_ID, userId).findFirst();
            return user != null && user.getHasCall();
        } finally {
            realm.close();
        }
    }

    public interface CallBack {
        void done(Exception e);
    }

    public interface CreateGroupCallBack {
        void done(Exception e, String groupId);
    }
}
