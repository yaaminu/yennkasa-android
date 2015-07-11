package com.pair.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.gson.JsonObject;
import com.pair.adapter.BaseJsonAdapter;
import com.pair.adapter.UserJsonAdapter;
import com.pair.data.ContactsManager;
import com.pair.data.Conversation;
import com.pair.data.Message;
import com.pair.data.User;
import com.pair.net.HttpResponse;
import com.pair.net.api.UserApi;
import com.pair.pairapp.BuildConfig;

import org.apache.http.HttpStatus;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.util.List;

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
public class UserManager {

    private static final String TAG = UserManager.class.getSimpleName();
    private static final String KEY_SESSION_ID = "lfl/-90-09=klvj8ejf"; //don't give a clue what this is
    public static final UserManager INSTANCE = new UserManager();

    private volatile int loginAttempts = 0,
            signUpAttempts = 0, changeDpAttempts = 0,
            refreshAttempts = 0,
            getDpAttempts = 0,
            createGroupAttempts = 0;
    private volatile boolean loginSignUpBusy = false, //login or sign up never should run in parallel
            dpChangeOperationRunning = false,
            refreshOperationRunning = false,
            getDpOperationRunning = false,
            groupOperationRunning = false;
    private final Exception NO_CONNECTION_ERROR = new Exception("not connected to the internet");
    private final BaseJsonAdapter<User> adapter = new UserJsonAdapter();

    private final UserApi userApi = new RestAdapter.Builder()
            .setEndpoint(Config.PAIRAPP_ENDPOINT)
            .setRequestInterceptor(Config.INTERCEPTOR)
            .setLogLevel((BuildConfig.DEBUG) ? RestAdapter.LogLevel.FULL : RestAdapter.LogLevel.NONE)
            .setLog(new AndroidLog(TAG))
            .build()
            .create(UserApi.class);

    @Deprecated
    public static UserManager getInstance(@NonNull Context context) {
        return INSTANCE;
    }

    private UserManager() {
    }


    private void saveUserMainUser(User user) {
        final Context context = Config.getApplicationContext();
        Realm realm = Realm.getInstance(context);
        realm.beginTransaction();
        realm.copyToRealmOrUpdate(user);
        realm.commitTransaction();
        // TODO: 6/25/2015 encrypt the id before storing it
        context.getSharedPreferences(Config.APP_PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_SESSION_ID, user.get_id())
                .commit();
    }

    public User getMainUser() {
        Realm realm = Realm.getInstance(Config.getApplicationContext());
        User user = getMainUser(realm);
        if (user != null) {
            //returning {@link RealmObject} from methods leaks resources since
            // that will prevent us from closing the realm instance. hence we do a shallow copy.
            // downside is changes to this object will not be persisted which is just what we want
            user = new User(user);
        }
        realm.close();
        return user == null ? user : new User(user);
    }

    private User getMainUser(Realm realm) {
        final Context context = Config.getApplicationContext();
        String currUserId = context.getSharedPreferences(Config.APP_PREFS, Context.MODE_PRIVATE)
                .getString(KEY_SESSION_ID, null);
        if (currUserId == null) {
            Config.disableComponents();
            return null;
        }
        User user = realm.where(User.class).equalTo("_id", currUserId).findFirst();
        return user;

    }

    public boolean isMainUser(User user) {
        User thisUser = getMainUser();
        return ((!(user == null || thisUser == null)) && thisUser.get_id().equals(user.get_id()));
    }

    public void createGroup(final String groupName, final CallBack callBack) {
        if (!ConnectionHelper.isConnectedOrConnecting()) {
            callBack.done(NO_CONNECTION_ERROR);
        }
        if (isUser(User.generateId(groupName))) {
            //already exist
            callBack.done(new Exception("group already exist"));
            return;
        }
        createGroupAttempts++;
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("name", groupName);
        requestBody.addProperty("createdBy", getMainUser().get_id());
        userApi.createGroup(requestBody, new Callback<User>() {
            @Override
            public void success(User group, Response response) {
                createGroupAttempts = 0;
                Realm realm = Realm.getInstance(Config.getApplicationContext());
                realm.beginTransaction();
                group.setMembers(new RealmList<User>());//required for realm to behave
                User mainUser = getMainUser(realm);
                group.getMembers().add(mainUser);
                group.setAdmin(mainUser);
                group.setType(User.TYPE_GROUP);
                realm.copyToRealmOrUpdate(group);
                realm.commitTransaction();
                realm.close();
                callBack.done(null);
            }

            @Override
            public void failure(RetrofitError retrofitError) {
                Exception e = handleError(retrofitError);
                if (e == null && createGroupAttempts < 3) {
                    createGroup(groupName, callBack);
                } else {
                    createGroupAttempts = 0;
                    Log.i(TAG, "failed to create group");
                    callBack.done(e);
                }
            }
        });
    }

    private boolean isUser(String id) {
        Realm realm = Realm.getInstance(Config.getApplicationContext());
        boolean isUser = realm.where(User.class).equalTo("_id", id).findFirst() != null;
        realm.close();
        return isUser;
    }

    public boolean isAdmin(String groupId, String userId) {
        Realm realm = Realm.getInstance(Config.getApplicationContext());
        User group = realm.where(User.class).equalTo("_id", groupId).findFirst();
        if (group == null) {
            realm.close();
            throw new IllegalArgumentException("no group with such id");
        }
        String adminId = group.getAdmin().get_id();
        realm.close();
        return adminId.equals(userId);
    }

    private Exception checkPermission(String groupId) {
        if (!ConnectionHelper.isConnectedOrConnecting()) {
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

    public void addMembers(final String groupId, final List<String> membersId, final CallBack callBack) {
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
                final User group = realm.where(User.class).equalTo("_id", groupId).findFirst();
                final ContactsManager.Filter filter = new ContactsManager.Filter<User>() {
                    @Override
                    public boolean accept(User user) {
                        return (user != null && !group.getMembers().contains(user));
                    }
                };
                RealmList<User> newMembers = aggregateUsers(realm, membersId, filter);
                group.getMembers().addAll(newMembers);
                realm.commitTransaction();
                realm.close();
                callBack.done(null);
            }

            @Override
            public void failure(RetrofitError retrofitError) {
                Exception e = handleError(retrofitError);
                if (e == null) {
                    addMembers(groupId, membersId, callBack);
                } else {
                    callBack.done(e);
                }
            }
        });
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
                final User group = realm.where(User.class).equalTo("_id", groupId).findFirst();
                final ContactsManager.Filter filter = new ContactsManager.Filter<User>() {
                    @Override
                    public boolean accept(User user) {
                        return (user != null && group.getMembers().contains(user));
                    }
                };
                RealmList<User> membersToDelete = aggregateUsers(realm, members, filter);
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

    private void getGroupMembers(final String id) {
        userApi.getGroupMembers(id, new Callback<List<User>>() {
            @Override
            public void success(List<User> users, Response response) {
                Realm realm = Realm.getInstance(Config.getApplicationContext());
                realm.beginTransaction();
                User group = realm.where(User.class).equalTo("_id", id).findFirst();
                RealmList<User> members = group.getMembers();
                members.clear();
                for (User user : users) {
                    if (!isMainUser(user)) { //main user must be updated independently
                        members.add(realm.copyToRealmOrUpdate(user));
                    }
                }
                members.add(getMainUser(realm)); //add main user
                realm.commitTransaction();
                realm.close();
            }

            @Override
            public void failure(RetrofitError retrofitError) {
                if (handleError(retrofitError) == null) {
                    refreshGroup(id);
                }
            }
        });
    }

    private void getGroupDp(String id) {

    }

    public void refreshGroup(final String id) {
        if (!isUser(id)) {
            throw new IllegalArgumentException("passed id is invalid");
        }
        getGroupInfo(id); //async
        getGroupMembers(id); //async
        getGroupDp(id); //async // FIXME: 6/29/2015 implement this method
    }

    private void getGroupInfo(final String id) {
        userApi.getGroup(id, new Callback<User>() {
            @Override
            public void success(User group, Response response) {
                Realm realm = Realm.getInstance(Config.getApplicationContext());
                User staleGroup = realm.where(User.class).equalTo("_id", id).findFirst();
                realm.beginTransaction();
                if (staleGroup != null) {
                    group.setMembers(staleGroup.getMembers());
                } else {
                    group.setType(User.TYPE_GROUP);
                    group.setMembers(new RealmList<User>());
                }
                realm.copyToRealmOrUpdate(group);
                realm.commitTransaction();
                realm.close();
            }

            @Override
            public void failure(RetrofitError retrofitError) {
                if (handleError(retrofitError) == null) {
                    getGroupInfo(id);
                }
            }
        });
    }

    @Nullable
    private RealmList<User> aggregateUsers(Realm realm, List<String> membersId, ContactsManager.Filter<User> filter) {
        RealmList<User> members = new RealmList<>();
        for (String id : membersId) {
            User user = realm.where(User.class).equalTo("_id", id).findFirst();
            if (filter.accept(user)) {
                members.add(user);
            }
        }
        return members;
    }

    public void refreshGroups() {
        if (!ConnectionHelper.isConnectedOrConnecting()) {
            return;
        }
        getGroups();
    }

    public void refreshUserDetails(final String userId) {
        if (!ConnectionHelper.isConnectedOrConnecting()) {
            return;
        }
        if (refreshOperationRunning) {
            return;
        }
        refreshOperationRunning = true;
        //update user here
        userApi.getUser(userId, new Callback<User>() {
            @Override
            public void success(User onlineUser, Response response) {
                refreshOperationRunning = false;
                refreshAttempts = 0;
                Realm realm = Realm.getInstance(Config.getApplicationContext());
                realm.beginTransaction();
                User user = realm.where(User.class).equalTo("_id", userId).findFirst();
                user.setLastActivity(onlineUser.getLastActivity());
                user.setStatus(onlineUser.getStatus());
                user.setName(onlineUser.getName());
                String dp = new File(Config.APP_PROFILE_PICS_BASE_DIR, onlineUser.getDP() + ".jpeg").getAbsolutePath();

                if (!dp.equals(user.getDP())) { //new dp!
                    user.setDP(dp);
                }
                realm.commitTransaction();
                if (!new File(user.getDP()).exists()) {
                    getUserDp(userId, new CallBack() {
                        @Override
                        public void done(Exception e) {
                        }
                    });
                }
                realm.close();
            }

            @Override
            public void failure(RetrofitError retrofitError) {
                refreshOperationRunning = false;
                Exception e = handleError(retrofitError);
                if (e == null && refreshAttempts < 3) {
                    refreshUserDetails(userId);
                } else {
                    refreshAttempts = 0;
                    Log.i(TAG, "failed to refresh after 3 attempts");
                }
            }
        });
    }

    private void getGroups() {
        User mainUser = getMainUser();
        if (mainUser == null) {
            throw new IllegalStateException("this operation can only continue only when user is logged in");
        }

        userApi.getGroups(mainUser.get_id(), new Callback<List<User>>() {
            @Override
            public void success(List<User> groups, Response response) {
                Realm realm = Realm.getInstance(Config.getApplicationContext());
                realm.beginTransaction();
                for (User group : groups) {
                    if (isMainUser(group.getAdmin())) {
                        group.setAdmin(getMainUser(realm));
                    }
                    group.setMembers(new RealmList<User>());
                    group.getMembers().add(group.getAdmin());
                    group.setType(User.TYPE_GROUP);
                    realm.copyToRealmOrUpdate(group);
                }
                realm.commitTransaction();
                realm.close();
            }

            @Override
            public void failure(RetrofitError retrofitError) {
                if (handleError(retrofitError) == null) {
                    getGroups();
                }
            }
        });
    }

    public void changeDp(final String imagePath, final CallBack callback) {
        File imageFile = new File(imagePath);
        if (!imageFile.exists()) {
            callback.done(new Exception("path " + imagePath + " does not exist"));
            return;
        }
        if (!ConnectionHelper.isConnectedOrConnecting()) {
            callback.done(NO_CONNECTION_ERROR);
            return;
        }
        if (dpChangeOperationRunning) {
            return;
        }
        dpChangeOperationRunning = true;
        final User user = getMainUser();
        Realm realm = Realm.getInstance(Config.getApplicationContext());
        realm.beginTransaction();
        user.setDP(imagePath);
        user.setPassword("d"); // FIXME: 6/24/2015 take out this line of code!
        realm.commitTransaction();
        realm.close();
        changeDpAttempts++;
        userApi.changeDp(user.get_id(), new TypedFile("image/*", imageFile), new Callback<HttpResponse>() {
            @Override
            public void success(HttpResponse response, Response response2) {
                dpChangeOperationRunning = false;
                changeDpAttempts = 0;
                final String newPath = new File(Config.APP_PROFILE_PICS_BASE_DIR, response.getMessage() + ".jpeg").getAbsolutePath(),
                        oldPath = user.getDP();
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        updateDpPath(oldPath, newPath);
                    }
                }).start();
                callback.done(null);
            }

            @Override
            public void failure(RetrofitError retrofitError) {
                dpChangeOperationRunning = false;
                Exception e = handleError(retrofitError);
                if (e == null && changeDpAttempts < 3) {
                    changeDp(imagePath, callback);
                } else {
                    changeDpAttempts = 0;
                    callback.done(e == null ? retrofitError : e); //may be our fault but we have reach maximum retries
                }
            }
        });
    }

    private void updateDpPath(String oldPath, String newPath) {
        try {

            FileHelper.copyTo(oldPath, newPath);
        } catch (IOException e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, e.getMessage(), e.getCause());
            } else {
                Log.e(TAG, e.getMessage());
            }
        }
    }

    private void getUserDp(final String userId, final CallBack callback) {
        if (!ConnectionHelper.isConnectedOrConnecting()) {
            callback.done(NO_CONNECTION_ERROR);
            return;
        }

        if (getDpOperationRunning) {
            return;
        }
        getDpOperationRunning = true;
        getDpAttempts++;
        Realm realm = Realm.getInstance(Config.getApplicationContext());
        User user = realm.where(User.class).equalTo("_id", userId).findFirst();
        if (user.getType() == User.TYPE_GROUP) {

        }
        realm.close();
        doGetUserDp(userId, callback);
    }

    private void doGetUserDp(final String userId, final CallBack callback) {
        userApi.getUserDp(userId, new Callback<Response>() {
            @Override
            public void success(Response response, Response response2) {
                getDpOperationRunning = false;
                getDpAttempts = 0;
                Realm realm = Realm.getInstance(Config.getApplicationContext());
                try {
                    final InputStream in = response.getBody().in();
                    User user = realm.where(User.class).equalTo("_id", userId).findFirst();
                    if (user == null) {
                        throw new IllegalArgumentException("user does not exist");
                    }
                    File profileFile = new File(user.getDP());
                    FileHelper.save(profileFile, in);
                    callback.done(null);
                } catch (IOException e) {
                    if (BuildConfig.DEBUG) {
                        Log.e(TAG, e.getMessage(), e.getCause());
                    } else {
                        Log.e(TAG, e.getMessage());
                    }
                    callback.done(e);
                } finally {
                    realm.close();
                }
            }

            @Override
            public void failure(RetrofitError retrofitError) {
                getDpOperationRunning = false;
                Exception e = handleError(retrofitError);
                if (e == null && getDpAttempts < 3) {
                    getUserDp(userId, callback);
                } else {
                    getDpAttempts = 0;
                    callback.done(e == null ? retrofitError : e); //may be our fault but we have ran out of resources
                }
            }
        });
    }

    public void logIn(User user, final CallBack callback) {
        if (!ConnectionHelper.isConnectedOrConnecting()) {
            callback.done(NO_CONNECTION_ERROR);
        }
        doLogIn(user, callback);
    }

    //this method must be called on the main thread
    private void doLogIn(final User user, final CallBack callback) {
        if (!ConnectionHelper.isConnectedOrConnecting()) {
            callback.done(NO_CONNECTION_ERROR);
            return;
        }
        if (loginSignUpBusy) {
            return;
        }
        loginSignUpBusy = true;
        loginAttempts++;
        userApi.logIn(adapter.toJson(user), new Callback<User>() {
            @Override
            public void success(User backendUser, Response response) {
                loginSignUpBusy = false;
                loginAttempts = 0;
                //our backend deletes password fields so we got to use our copy here
                backendUser.setPassword(user.getPassword());
                saveUserMainUser(backendUser);
                getGroups();
                callback.done(null);
            }

            @Override
            public void failure(RetrofitError retrofitError) {
                loginSignUpBusy = false;
                Exception e = handleError(retrofitError);
                if (e == null && loginAttempts < 3) {
                    //not our problem lets try again
                    doLogIn(user, callback);
                } else {
                    loginAttempts = 0;
                    callback.done(e == null ? retrofitError : e); //may be our fault but we have ran out of resources
                }
            }
        });
    }


    public void signUp(final User user, final CallBack callback) {
        if (!ConnectionHelper.isConnectedOrConnecting()) {
            callback.done(NO_CONNECTION_ERROR);
            return;
        }
        if (loginSignUpBusy) {
            return;
        }
        loginSignUpBusy = true;
        signUpAttempts++;
        userApi.registerUser(adapter.toJson(user), new Callback<User>() {
            @Override
            public void success(User backEndUser, Response response) {
                loginSignUpBusy = false;
                signUpAttempts = 0;
                backEndUser.setPassword(user.getPassword());
                saveUserMainUser(backEndUser);
                callback.done(null);
            }

            @Override
            public void failure(RetrofitError retrofitError) {
                loginSignUpBusy = false;
                // TODO: 6/25/2015 handle error
                Exception e = handleError(retrofitError);
                if (e == null && signUpAttempts < 3) {
                    //not our fault and we have more chance lets try again
                    signUp(user, callback);
                } else {
                    signUpAttempts = 0;
                    callback.done(e == null ? retrofitError : e); //may not be our fault but we have ran out of retries
                }
            }
        });

    }

    public void LogOut(Context context, final CallBack logOutCallback) {
        //TODO logout user from backend
        SharedPreferences sharedPreferences = context.getSharedPreferences(Config.APP_PREFS, Context.MODE_PRIVATE);
        String userId = sharedPreferences.getString(KEY_SESSION_ID, null);
        if ((userId == null)) {
            throw new AssertionError("calling logout when no user is logged in"); //security hole!
        }

        sharedPreferences
                .edit()
                .remove(KEY_SESSION_ID)
                .apply();
        Realm realm = Realm.getInstance(context);
        // TODO: 6/14/2015 remove this in production code.
        User user = realm.where(User.class).equalTo("_id", userId).findFirst();
        if (user == null) {
            throw new IllegalStateException("existing session id with no corresponding User in the database");
        }
        realm.close();
        GcmHelper.unRegister(context, new GcmHelper.UnregisterCallback() {
            @Override
            public void done(Exception e) {
                //we dont care about gcm regid
                cleanUpRealm();
                logOutCallback.done(null);
            }
        });
    }

    public void fetchFriends(final List<String> array, final FriendsFetchCallback callback) {
        if (!ConnectionHelper.isConnected()) {
            callback.done(NO_CONNECTION_ERROR, null);
            return;
        }
        userApi.fetchFriends(array, new Callback<List<User>>() {
            @Override
            public void success(List<User> users, Response response) {
                callback.done(null, users);
            }

            @Override
            public void failure(RetrofitError retrofitError) {
                if (retrofitError.getKind().equals(RetrofitError.Kind.HTTP)
                        || retrofitError.getKind().equals(RetrofitError.Kind.NETWORK)
                        ) {
                    callback.done(retrofitError, null);
                } else if (retrofitError.getKind().equals(RetrofitError.Kind.UNEXPECTED)) {
                    if (ConnectionHelper.isConnectedOrConnecting()) {
                        //try again
                        fetchFriends(array, callback);
                    } else {
                        callback.done(retrofitError, null);
                    }
                } else if (retrofitError.getKind().equals(RetrofitError.Kind.CONVERSION)) {
                    throw new AssertionError(retrofitError);
                } else {
                    callback.done(retrofitError, null);
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
        if (retrofitError.getCause() instanceof SocketTimeoutException) { //likely that no user turned on data but no plan
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
            if (ConnectionHelper.isConnectedOrConnecting()) {
                return null;
            }
            //bubble up error and empty send queue let callers re-dispatch messages again;
            Log.w(TAG, "no network connection, aborting");
            return NO_CONNECTION_ERROR;
        }

        //may be retrofit added some error kinds in a new version we are not aware of so lets crash to ensure that
        //we find out
        throw new AssertionError("unknown error kind");
    }


    public interface FriendsFetchCallback {
        void done(Exception e, List<User> users);
    }

    public interface CallBack {
        void done(Exception e);
    }

    private class GroupFilter implements ContactsManager.Filter<User> {
        private final User group;

        public GroupFilter(User user) {
            this.group = user;
        }

        @Override
        public boolean accept(User user) {
            return (user != null && !group.getMembers().contains(user) && !isAdmin(group.get_id(), user.get_id()));
        }
    }
}
