package com.pair.util;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;

import com.google.i18n.phonenumbers.NumberParseException;
import com.pair.adapter.BaseJsonAdapter;
import com.pair.adapter.UserJsonAdapter;
import com.pair.data.ContactsManager;
import com.pair.data.Conversation;
import com.pair.data.Message;
import com.pair.data.User;
import com.pair.net.HttpResponse;
import com.pair.net.api.UserApi;
import com.pair.pairapp.BuildConfig;
import com.pair.pairapp.R;

import org.apache.http.HttpStatus;

import java.io.EOFException;
import java.io.File;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.realm.Realm;
import io.realm.RealmList;
import retrofit.Callback;
import retrofit.RestAdapter;
import retrofit.RetrofitError;
import retrofit.android.AndroidLog;
import retrofit.client.Response;
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
    private static final UserManager INSTANCE = new UserManager();

    @SuppressWarnings("ThrowableInstanceNeverThrown")
    private final Exception NO_CONNECTION_ERROR;
    private final BaseJsonAdapter<User> adapter = new UserJsonAdapter();

    private final UserApi userApi = new RestAdapter.Builder()
            .setEndpoint(Config.PAIRAPP_ENDPOINT)
            .setRequestInterceptor(Config.INTERCEPTOR)
            .setLogLevel((BuildConfig.DEBUG) ? RestAdapter.LogLevel.FULL : RestAdapter.LogLevel.NONE)
            .setLog(new AndroidLog(TAG))
            .build()
            .create(UserApi.class);

    @Deprecated
    public static UserManager getInstance(@SuppressWarnings("UnusedParameters") @NonNull Context context) {
        return INSTANCE;
    }

    public static UserManager getInstance() {
        return INSTANCE;
    }

    private UserManager() {
        NO_CONNECTION_ERROR = new Exception(Config.getApplicationContext().getString(R.string.st_unable_to_connect));
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
                .putString(KEY_SESSION_ID, user.get_id())
                .putString(KEY_USER_PASSWORD, user.getPassword())
                .commit();
    }

    public User getMainUser() {
        Realm realm = Realm.getInstance(Config.getApplicationContext());
        User user = getMainUser(realm);
        if (user != null) {
            //returning {@link RealmObject} from methods will leak resources since
            // that will prevent us from closing the realm instance. hence we do a shallow copy.
            // downside is changes to this object will not be persisted which is just what we want
            user = User.copy(user);
        }
        realm.close();
        return user;
    }

    public boolean isUserLoggedIn() {
        return getMainUser() != null;
    }

    public boolean isUserVerified() {
        return isUserLoggedIn() && getSettings().getBoolean(KEY_USER_VERIFIED, false);
    }

    private SharedPreferences getSettings() {
        return Config.getApplicationWidePrefs();
    }

    private User getMainUser(Realm realm) {
        String currUserId = getSettings().getString(KEY_SESSION_ID, null);
        if (currUserId == null) {
            Config.disableComponents();
            return null;
        }
        return realm.where(User.class).equalTo(User.FIELD_ID, currUserId).findFirst();
    }

    private String getUserPassword() {
        String password = getSettings().getString(KEY_USER_PASSWORD, null);
        if (password == null) {
            // TODO: 7/19/2015 logout user and clean up realm as we suspect intruders
            throw new IllegalStateException("session data tampered with");
        }
        return password;
    }

    public boolean isMainUser(String userId) {
        User thisUser = getMainUser();
        return ((!(userId == null || thisUser == null)) && thisUser.get_id().equals(userId));
    }

    public void createGroup(final String groupName, final List<String> membersId, final CreateGroupCallBack callBack) {
        if (!ConnectionUtils.isConnectedOrConnecting()) {
            callBack.done(NO_CONNECTION_ERROR,null);
            return;
        }
        if (isUser(User.generateGroupId(groupName))) {
            //already exist[
            callBack.done(new Exception("group with name " + groupName + "already exists"),null);
            return;
        }
        userApi.createGroup(getMainUser().get_id(), groupName, membersId, new Callback<User>() {
            @Override
            public void success(final User group, Response response) {
                final Handler handler = new Handler(Looper.getMainLooper());
                WORKER.submit(new Runnable() {
                    @Override
                    public void run() {
                        completeGroupCreation(group, membersId);
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                callBack.done(null,group.get_id());
                            }
                        });
                    }
                });
            }

            @Override
            public void failure(RetrofitError retrofitError) {
                Exception e = handleError(retrofitError);
                if (e == null) {
                    createGroup(groupName, membersId, callBack);
                } else {
                    Log.i(TAG, "failed to create group");
                    callBack.done(e,null);
                }
            }
        });
    }

    private void completeGroupCreation(User group, List<String> membersId) {
        Realm realm = Realm.getInstance(Config.getApplicationContext());
        realm.beginTransaction();
        group.setMembers(new RealmList<User>());//required for realm to behave
        User mainUser = getMainUser(realm);
        if (mainUser == null) {
            throw new IllegalStateException("no user logged in");
        }
        RealmList<User> members = User.aggregateUsers(realm, membersId, new ContactsManager.Filter<User>() {
            @Override
            public boolean accept(User user) {
                return user != null && !isGroup(user.get_id());
            }
        });
        group.getMembers().addAll(members);
        group.getMembers().add(mainUser);
        group.setAdmin(mainUser);
        group.setType(User.TYPE_GROUP);
        realm.copyToRealmOrUpdate(group);
        realm.commitTransaction();
        realm.close();
    }

    public void removeMembers(final String groupId, final List<String> members, final CallBack callBack) {
        final Exception e = checkPermission(groupId);
        if (e != null) { //unauthorised
            callBack.done(e);
            return;
        }
        if (members.contains(getMainUser().get_id())) {
            throw new IllegalArgumentException("admin cannot remove him/herself");
        }

        userApi.removeMembersFromGroup(groupId, getMainUser().get_id(), members, new Callback<Response>() {
            @Override
            public void success(Response o, Response response) {
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
                callBack.done(null);
            }

            @Override
            public void failure(RetrofitError retrofitError) {
                Exception e = handleError(retrofitError);
                if (e == null) {
                    removeMembers(groupId, members, callBack);
                } else {
                    callBack.done(e);
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
        String adminId = group.getAdmin().get_id();
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
        if (!isAdmin(groupId, getMainUser().get_id())) {
            return new IllegalAccessException("you don't have the authority to add/remove a member");
        }
        return null;
    }

    public void addMembersToGroup(final String groupId, final List<String> membersId, final CallBack callBack) {
        Exception e = checkPermission(groupId);
        if (e != null) {
            callBack.done(e);
            return;
        }
        userApi.addMembersToGroup(groupId, getMainUser().get_id(), membersId, new Callback<Response>() {
            @Override
            public void success(Response response, Response response2) {
                Realm realm = Realm.getInstance(Config.getApplicationContext());
                realm.beginTransaction();
                final User group = realm.where(User.class).equalTo(User.FIELD_ID, groupId).findFirst();
                final ContactsManager.Filter<User> filter = new ContactsManager.Filter<User>() {
                    @Override
                    public boolean accept(User user) {
                        return (user != null && !group.getMembers().contains(user) && !isGroup(user.get_id()));
                    }
                };
                RealmList<User> newMembers = User.aggregateUsers(realm, membersId, filter);
                group.getMembers().addAll(newMembers);
                realm.commitTransaction();
                realm.close();
                callBack.done(null);
            }

            @Override
            public void failure(RetrofitError retrofitError) {
                Exception e = handleError(retrofitError);
                if (e == null) {
                    addMembersToGroup(groupId, membersId, callBack);
                } else {
                    callBack.done(e);
                }
            }
        });
    }

    private void getGroupMembers(final String id) {
        userApi.getGroupMembers(id, new Callback<List<User>>() {
            @Override
            public void success(final List<User> freshMembers, final Response response) {
                WORKER.submit(new Runnable() {
                    @Override
                    public void run() {
                        updateLocalGroupMembers(freshMembers, id);
                    }
                });
            }

            @Override
            public void failure(final RetrofitError retrofitError) {
                WORKER.submit(new Runnable() {
                    @Override
                    public void run() {
                        if (handleError(retrofitError) == null) {
                            getGroupMembers(id);
                        }
                    }
                });
            }
        });
    }

    private void saveFreshUsers(List<User> freshMembers) {
        Realm realm = Realm.getInstance(Config.getApplicationContext());
        List<User> ret = saveFreshUsers(realm, freshMembers);
        realm.close();
    }

    private List<User> saveFreshUsers(Realm realm, List<User> freshMembers) {
        for (User freshMember : freshMembers) {
            freshMember.setType(User.TYPE_NORMAL_USER);
        }
        realm.beginTransaction();
        List<User> ret = realm.copyToRealmOrUpdate(freshMembers);
        realm.commitTransaction();
        updateUsersLocalNames();
        return ret;
    }

    private void updateLocalGroupMembers(List<User> freshMembers, String id) {
        Realm realm = Realm.getInstance(Config.getApplicationContext());
        try {
            User group = realm.where(User.class).equalTo(User.FIELD_ID, id).findFirst();
            freshMembers = saveFreshUsers(realm, freshMembers);
            realm.beginTransaction();
            group.getMembers().clear();
            group.getMembers().addAll(freshMembers);
            realm.commitTransaction();
        } finally {
            realm.close();
        }
    }

    public void refreshGroup(final String id) {
        if (!isUser(id)) {
            throw new IllegalArgumentException("passed id is invalid");
        }
        doRefreshGroup(id);
    }

    private void doRefreshGroup(String id) {
        getGroupInfo(id); //async
    }

    private void getGroupInfo(final String id) {
        userApi.getGroup(id, new Callback<User>() {
            @Override
            public void success(final User group, Response response) {
                WORKER.submit(new Runnable() {
                    @Override
                    public void run() {
                        completeGetGroupInfo(group, id);
                    }
                });
            }

            @Override
            public void failure(final RetrofitError retrofitError) {
                WORKER.submit(new Runnable() {
                    @Override
                    public void run() {
                        if (handleError(retrofitError) == null) {
                            getGroupInfo(id);
                        }
                    }
                });
            }
        });
    }

    private void completeGetGroupInfo(User group, String id) {
        Realm realm = Realm.getInstance(Config.getApplicationContext());
        User staleGroup = realm.where(User.class).equalTo(User.FIELD_ID, id).findFirst();
        realm.beginTransaction();
        if (staleGroup != null) {
            staleGroup.setName(group.getName());
        } else {
            group.setType(User.TYPE_GROUP);
            group.setMembers(new RealmList<User>());
            group.getMembers().add(group.getAdmin());
            realm.copyToRealm(group);
        }
        realm.commitTransaction();
        realm.close();
        realm = Realm.getInstance(Config.getApplicationContext());
        User g = realm.where(User.class).equalTo(User.FIELD_ID, id).findFirst();
        Log.i(TAG, "members of " + g.getName() + " are: " + g.getMembers().size());
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
            userApi.getUser(userId, new Callback<User>() {
                @Override
                public void success(final User onlineUser, Response response) {
                    WORKER.submit(new Runnable() {
                        @Override
                        public void run() {
                            completeRefresh(onlineUser, userId);
                        }
                    });
                }

                @Override
                public void failure(final RetrofitError retrofitError) {
                    WORKER.submit(new Runnable() {
                        @Override
                        public void run() {
                            Exception e = handleError(retrofitError);
                            if (e == null) {
                                refreshUserDetails(userId);
                            } else {
                                Log.i(TAG, "failed to refresh after 3 attempts");
                            }
                        }
                    });
                }
            });
        }
    }

    private void completeRefresh(User onlineUser, String userId) {
        Realm realm = Realm.getInstance(Config.getApplicationContext());
        realm.beginTransaction();
        User user = realm.where(User.class).equalTo(User.FIELD_ID, userId).findFirst();
        user.setLastActivity(onlineUser.getLastActivity());
        user.setStatus(onlineUser.getStatus());
        user.setName(onlineUser.getName());
        realm.commitTransaction();
        //commit the changes and then
        //check if user is saved locally
        ContactsManager.Contact contact = ContactsManager.INSTANCE.findContactByPhoneSync(user.get_id(), getUserCountryISO());
        if (contact != null) {
            realm.beginTransaction();
            user.setName(contact.name);
            realm.commitTransaction();
        }
        realm.close();
    }

    private final ExecutorService WORKER = Executors.newCachedThreadPool();

    public boolean isGroup(String userId) {
        Realm realm = Realm.getInstance(Config.getApplicationContext());
        try {
            User potentiallyGroup = realm.where(User.class).equalTo(User.FIELD_ID, userId).findFirst();
            return potentiallyGroup != null && (potentiallyGroup.getType() == User.TYPE_GROUP);
        } finally {
            realm.close();
        }
    }

    private void getGroups() {
        User mainUser = getMainUser();
        userApi.getGroups(mainUser.get_id(), new Callback<List<User>>() {
            @Override
            public void success(final List<User> groups, Response response) {
                WORKER.submit(new Runnable() {
                    @Override
                    public void run() {
                        completeGetGroups(groups);
                    }
                });
            }

            @Override
            public void failure(final RetrofitError retrofitError) {
                WORKER.submit(new Runnable() {
                    @Override
                    public void run() {
                        if (handleError(retrofitError) == null) {
                            getGroups();
                        }
                    }
                });
            }
        });
    }

    private void completeGetGroups(List<User> groups) {
        Realm realm = Realm.getInstance(Config.getApplicationContext());
        realm.beginTransaction();
        User mainUser = getMainUser(realm);
        if (mainUser == null) {
            throw new IllegalStateException("no user logged in");
        }
        for (User group : groups) {
            User staleGroup = realm.where(User.class).equalTo(User.FIELD_ID, group.get_id()).findFirst();
            if (staleGroup != null) { //already exist just update
                staleGroup.setName(group.getName()); //admin might have changed name
                staleGroup.setType(User.TYPE_GROUP);
            } else { //new group
                // because the json returned from our backend is not compatible with our schema here
                // the backend always clears the members and type field so we have to set it up down here manually
                group.setType(User.TYPE_GROUP);
                group.setMembers(new RealmList<User>());
                group.getMembers().add(group.getAdmin());
                if (!group.getAdmin().get_id().equals(mainUser.get_id())) {
                    group.getMembers().add(mainUser);
                }
                realm.copyToRealmOrUpdate(group);
            }
        }
        realm.commitTransaction();
        realm.close();
    }

    public void changeDp(String imagePath, CallBack callBack) {
        this.changeDp(getMainUser().get_id(), imagePath, callBack);
    }

    public void changeDp(final String userId, final String imagePath, final CallBack callback) {
        File imageFile = new File(imagePath);
        if (!imageFile.exists()) {
            callback.done(new Exception("file " + imagePath + " does not exist"));
            return;
        }
        if (!ConnectionUtils.isConnectedOrConnecting()) {
            callback.done(NO_CONNECTION_ERROR);
            return;
        }
        Realm realm = Realm.getInstance(Config.getApplicationContext());
        final User user = realm.where(User.class).equalTo(User.FIELD_ID, userId).findFirst();
        if (user == null) {
            throw new IllegalArgumentException("user does not exist");
        }

        String placeHolder = user.getType() == User.TYPE_GROUP ? "groups" : "users";

        realm.close();
        userApi.changeDp(placeHolder, userId, new TypedFile("image/*", imageFile), new Callback<HttpResponse>() {
            @Override
            public void success(HttpResponse response, Response response2) {
                Realm realm = Realm.getInstance(Config.getApplicationContext());
                try {
                    realm.beginTransaction();
                    //noinspection ConstantConditions
                    User user = realm.where(User.class).equalTo("_id", userId).findFirst();
                    if (user != null) {
                        user.setDP(response.getMessage());
                    }
                    realm.commitTransaction();
                } finally {
                    realm.close();
                }
                callback.done(null);
            }

            @Override
            public void failure(RetrofitError retrofitError) {
                Exception e = handleError(retrofitError);
                if (e == null) {
                    changeDp(imagePath, callback); //retry
                } else {
                    callback.done(e); //may be our fault but we have reach maximum retries
                }
            }
        });
    }

    public void logIn(final Activity context, final String phoneNumber, final String password, final String userIso2LetterCode, final CallBack callback) {
        if (!ConnectionUtils.isConnectedOrConnecting()) {
            callback.done(NO_CONNECTION_ERROR);
            return;
        }
        GcmUtils.register(context, new GcmUtils.GCMRegCallback() {
            @Override
            public void done(Exception e, final String regId) {
                if (e == null) {
                    completeLogin(phoneNumber, password, regId, userIso2LetterCode, callback);
                } else {
                    callback.done(e);
                }
            }
        });
    }

    private void completeLogin(String phoneNumber, String password, String gcmRegId, String userIso2LetterCode, CallBack callback) {
        if (TextUtils.isEmpty(phoneNumber)) {
            callback.done(new Exception("invalid phone number"));
            return;
        }
        if (TextUtils.isEmpty(password)) {
            callback.done(new Exception("invalid password"));
            return;
        }
        if (TextUtils.isEmpty(userIso2LetterCode)) {
            callback.done(new Exception("userIso2LetterCode cannot be null"));
            return;
        }
        if (TextUtils.isEmpty(gcmRegId)) {
            callback.done(new Exception("GCM registration id cannot be null"));
            return;
        }

        User user = new User();
        user.setPassword(password);
        try {
            phoneNumber = PhoneNumberNormaliser.toIEE(phoneNumber, userIso2LetterCode);
        } catch (NumberParseException e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, e.getMessage(), e.getCause());
            } else {
                Log.e(TAG, e.getMessage());
            }
            callback.done(new Exception(phoneNumber + " is not a valid phone number"));
            return;
        }
        user.set_id(phoneNumber);
        user.setCountry(userIso2LetterCode);
        user.setGcmRegId(gcmRegId);
        doLogIn(user, userIso2LetterCode, callback);
    }

    //this method must be called on the main thread
    private void doLogIn(final User user, final String countryIso, final CallBack callback) {
        userApi.logIn(adapter.toJson(user), new Callback<User>() {
            @Override
            public void success(User backendUser, Response response) {
                //our backend deletes password fields so we got to use our copy here
                backendUser.setPassword(user.getPassword());
                saveMainUser(backendUser);
                getSettings()
                        .edit()
                        .putBoolean(KEY_USER_VERIFIED, true)
                        .commit();
                getGroups(); //async
                callback.done(null);
            }

            @Override
            public void failure(RetrofitError retrofitError) {
                Exception e = handleError(retrofitError);
                if (e == null) {
                    //not our problem lets try again
                    doLogIn(user, countryIso, callback);
                } else {
                    callback.done(e); //may be our fault but we have ran out of resources
                }
            }
        });
    }


    public void signUp(Activity context, final String name, final String phoneNumber, final String password, final String countryIso, final CallBack callback) {
        if (!ConnectionUtils.isConnectedOrConnecting()) {
            callback.done(NO_CONNECTION_ERROR);
            return;
        }
        GcmUtils.register(context, new GcmUtils.GCMRegCallback() {
            @Override
            public void done(Exception e, String regId) {
                if (e == null) {
                    completeSignUp(name, phoneNumber, password, regId, countryIso, callback);
                } else {
                    callback.done(e);
                }
            }
        });
    }

    private void completeSignUp(final String name, final String phoneNumber, final String password, final String gcmRegId, final String countryIso, final CallBack callback) {
        if (TextUtils.isEmpty(name)) {
            callback.done(new Exception("name is invalid"));
        } else if (TextUtils.isEmpty(phoneNumber)) {
            callback.done(new Exception("phone number is invalid"));
        } else if (TextUtils.isEmpty(password)) {
            callback.done(new Exception("password is invalid"));
        } else if (TextUtils.isEmpty(countryIso)) {
            callback.done(new Exception("ccc is invalid"));
        } else {
            doSignup(name, phoneNumber, password, gcmRegId, countryIso, callback);
        }
    }

    private void doSignup(final String name,
                          final String phoneNumber,
                          final String password,
                          final String gcmRegId,
                          final String countryIso,
                          final CallBack callback) {
        final User user = new User();
        try {
            String thePhoneNumber = PhoneNumberNormaliser.toIEE(phoneNumber, countryIso);
            user.set_id(thePhoneNumber);
        } catch (NumberParseException e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, e.getMessage(), e.getCause());
            } else {
                Log.e(TAG, e.getMessage());
            }
            callback.done(e);
            return;
        }
        user.setPassword(password);
        user.setName(name);
        user.setCountry(countryIso);
        user.setGcmRegId(gcmRegId);
        userApi.registerUser(adapter.toJson(user), new Callback<User>() {
            @Override
            public void success(User backEndUser, Response response) {
                backEndUser.setPassword(user.getPassword());
                saveMainUser(backEndUser);
                callback.done(null);
            }

            @Override
            public void failure(RetrofitError retrofitError) {

                // TODO: 6/25/2015 handle error
                Exception e = handleError(retrofitError);
                if (e == null) {
                    doSignup(name, phoneNumber, password, gcmRegId, countryIso, callback);
                } else {
                    callback.done(e);
                }
            }
        });
    }

    public void verifyUser(final String token, final CallBack callBack) {
        if (isUserVerified()) {
            callBack.done(null);
            return;
        }
        if (!ConnectionUtils.isConnectedOrConnecting()) {
            callBack.done(NO_CONNECTION_ERROR);
            return;
        }
        if (TextUtils.isEmpty(token)) {
            callBack.done(new Exception("invalid token"));
            return;
        }
        if (!isUserLoggedIn()) {
            throw new IllegalArgumentException(new Exception("no user logged for verification"));
        }
        userApi.verifyUser(getMainUser().get_id(), token, new Callback<String>() {
            @Override
            public void success(String accessToken, Response response) {
                getSettings().edit().putBoolean(KEY_USER_VERIFIED, true).commit();
                callBack.done(null);
            }

            @Override
            public void failure(RetrofitError retrofitError) {
                callBack.done(retrofitError);
            }
        });
    }

    public void resendToken(final CallBack callBack) {
        if (!isUserLoggedIn()) {
            throw new IllegalArgumentException(new Exception("no user logged for verification"));
        }
        if (isUserVerified()) {
            callBack.done(null);
            return;
        }
        if (!ConnectionUtils.isConnectedOrConnecting()) {
            callBack.done(NO_CONNECTION_ERROR);
            return;
        }
        userApi.resendToken(getMainUser().get_id(), getUserPassword(), new Callback<Response>() {
            @Override
            public void success(Response response, Response response2) {
                callBack.done(null);
            }

            @Override
            public void failure(RetrofitError retrofitError) {
                callBack.done(retrofitError);
            }
        });
    }

    private void updateUsersLocalNames() {
        WORKER.submit(new Runnable() {
            @Override
            public void run() {
                doUpdateLocalNames();
            }
        });
    }

    private void doUpdateLocalNames() {
        Context context = Config.getApplicationContext();
        Cursor cursor = ContactsManager.INSTANCE.findAllContactsCursor(context);
        String phoneNumber, name;
        User user;
        Realm realm = Realm.getInstance(Config.getApplicationContext());
        try {
            while (cursor.moveToNext()) {
                phoneNumber = cursor.getString(cursor.getColumnIndex(ContactsContract
                        .CommonDataKinds.Phone.NUMBER));
                if (TextUtils.isEmpty(phoneNumber)) {
                    Log.i(TAG, "strange!: no phone number for this contact, ignoring");
                    continue;
                }
                try {
                    phoneNumber = PhoneNumberNormaliser.toIEE(phoneNumber, UserManager.getInstance().getUserCountryISO());
                } catch (NumberParseException e) {
                    Log.e(TAG, "failed to format to IEE number: " + e.getMessage());
                    continue;
                }
                user = realm.where(User.class)
                        .equalTo(User.FIELD_ID, phoneNumber)
                        .findFirst();

                if (user != null) {
                    name = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
                    if (TextUtils.isEmpty(name)) { //some users can store numbers with no name; am a victim :-P
                        name = user.getName();
                    }
                    realm.beginTransaction();
                    user.setName(name);
                    realm.commitTransaction();
                }
            }
        } finally {
            realm.close();
        }
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
                logOutCallback.done(null);
            }
        });
    }

    public void syncContacts(final List<String> array) {
        if (!ConnectionUtils.isConnected()) {
            return;
        }
        userApi.syncContacts(array, new Callback<List<User>>() {
            @Override
            public void success(final List<User> users, Response response) {
                WORKER.submit(new Runnable() {
                    @Override
                    public void run() {
                        saveFreshUsers(users);
                    }
                });
            }

            @Override
            public void failure(RetrofitError retrofitError) {
                if (retrofitError.getKind().equals(RetrofitError.Kind.UNEXPECTED)) {
                    if (ConnectionUtils.isConnectedOrConnecting()) {
                        //try again
                        syncContacts(array);
                    }
                } else if (retrofitError.getKind().equals(RetrofitError.Kind.CONVERSION)) {
                    throw new AssertionError(retrofitError);
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


    // FIXME: 6/25/2015 find a sensible place to keep this error handler so that message dispatcher and others can share it
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
            callBack.done(NO_CONNECTION_ERROR);
        }
        userApi.leaveGroup(id, getMainUser().get_id(), getUserPassword(), new Callback<HttpResponse>() {
            @Override
            public void success(HttpResponse httpResponse, Response response) {
                Realm realm = Realm.getInstance(Config.getApplicationContext());
                try {
                    User group = realm.where(User.class).equalTo("_id", id).findFirst();
                    if (group != null) {
                        realm.beginTransaction();
                        group.removeFromRealm();
                        realm.commitTransaction();
                        callBack.done(null);
                    }
                } finally {
                    realm.close();
                }

            }

            @Override
            public void failure(RetrofitError retrofitError) {
                Exception e = handleError(retrofitError);
                if (e == null) {
                    leaveGroup(id, callBack);
                } else {
                    callBack.done(e);
                }
            }
        });
    }

    public boolean isAdmin(String id) {
        return isAdmin(id, getMainUser().get_id());
    }

    public String getUserCountryISO() {
        if (!isUserLoggedIn()) {
            throw new IllegalStateException("no user logged in");
        }
        return getMainUser().getCountry();
    }

    public void reset() {
        WORKER.submit(new Runnable() {
            @Override
            public void run() {
                if (isUserVerified()) {
                    throw new RuntimeException("use logout instead");
                }
                Realm realm = Realm.getInstance(Config.getApplicationContext());
                try {
                    realm.beginTransaction();
                    realm.where(User.class).findAll().clear();
                    realm.commitTransaction();
                } finally {
                    realm.close();
                }
                cleanUp();
            }
        });
    }

    private void cleanUp() {
        getSettings().edit()
                .remove(KEY_SESSION_ID)
                .remove(KEY_USER_VERIFIED)
                .remove(KEY_USER_PASSWORD)
                .commit();
    }

    public interface CallBack {
        void done(Exception e);
    }

    public interface CreateGroupCallBack{
        void done(Exception e,String groupId);
    }
}
