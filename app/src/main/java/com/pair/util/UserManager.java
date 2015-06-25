package com.pair.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.annotation.NonNull;
import android.util.Log;

import com.pair.adapter.BaseJsonAdapter;
import com.pair.adapter.UserJsonAdapter;
import com.pair.data.Conversation;
import com.pair.data.Message;
import com.pair.data.User;
import com.pair.net.api.UserApi;
import com.pair.pairapp.BuildConfig;
import com.pair.workers.BootReceiver;

import org.apache.http.HttpStatus;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import io.realm.Realm;
import retrofit.Callback;
import retrofit.RequestInterceptor;
import retrofit.RestAdapter;
import retrofit.RetrofitError;
import retrofit.android.AndroidLog;
import retrofit.client.Response;
import retrofit.mime.TypedFile;

/**
 * @author by Null-Pointer on 5/27/2015.
 */
public class UserManager {


    private int loginAttempts = 0;
    private int signUpAttempts = 0;
    private int changeDpAttempts = 0;
    private int refreshAttempts = 0;
    private int getDpAttempts = 0;
    private volatile boolean loginSignUpBusy = false, //login or sign up never should run in parallel
            dpChangeOperationRunning = false,
            refreshOperationRunning = false,
            getDpOperationRunning = false;
    private static final String TAG = UserManager.class.getSimpleName();
    private static final Exception NO_CONNECTION_ERROR = new Exception("not connected to the internet");
    private Context context;
    private UserApi userApi;
    private static UserManager INSTANCE;
    private BaseJsonAdapter<User> adapter;
    private static final String KEY_SESSION_ID = "lfl/-90-09=klvj8ejf";

    public static UserManager getInstance(@NonNull Context context) {
        UserManager localInstance = INSTANCE;
        if (localInstance == null) {
            synchronized (UserManager.class) {
                localInstance = INSTANCE;
                if (localInstance == null) {
                    INSTANCE = localInstance = new UserManager(context);
                }
            }
        }
        return localInstance;
    }

    private UserManager(Context context) {
        this.context = context;
        this.adapter = new UserJsonAdapter();
        RestAdapter restAdapter = new RestAdapter.Builder()
                .setEndpoint(Config.PAIRAPP_ENDPOINT)
                .setRequestInterceptor(INTERCEPTOR)
                .setLogLevel(RestAdapter.LogLevel.FULL)
                .setLog(new AndroidLog(TAG))
                .build();
        this.userApi = restAdapter.create(UserApi.class);
    }


    private void saveUser(User user) {
        Realm realm = Realm.getInstance(context);
        realm.beginTransaction();
        realm.copyToRealmOrUpdate(user);
        realm.commitTransaction();
        realm.close();
        // TODO: 6/25/2015 encrypt the id before storing it
        context.getSharedPreferences(Config.APP_PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_SESSION_ID, user.get_id())
                .commit();
    }

    public User getMainUser() {
        String currUserId = context.getSharedPreferences(Config.APP_PREFS, Context.MODE_PRIVATE)
                .getString(KEY_SESSION_ID, null);
        User copy = null;
        if (currUserId != null) {
            Realm realm = Realm.getInstance(context);
            User user = realm.where(User.class).equalTo("_id", currUserId).findFirst();
            if (user != null) {
                //returning {@code RealmObject} from methods leaks resources since
                // that will prevent us from closing the realm instance. hence we do a shallow copy.
                // downside is changes to this object will not be persisted which is just what we want
                copy = new User(user);
            }
            realm.close();
        }
        return copy;
    }


    public boolean isMainUser(User user) {
        User thisUser = getMainUser();
        return ((!(user == null || thisUser == null)) && thisUser.get_id().equals(user.get_id()));
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
                    getUserDp(userId, new GetDpCallback() {
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

    public void changeDp(final String imagePath, final DpChangeCallback callback) {
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
        if (user == null) {
            throw new AssertionError("can't change dp of user that is null");
        }
        Realm realm = Realm.getInstance(context);
        realm.beginTransaction();
        user.setDP(imagePath);
        user.setPassword("d"); // FIXME: 6/24/2015 take out this line of code!
        realm.commitTransaction();
        changeDpAttempts++;
        userApi.changeDp(user.get_id(), new TypedFile("image/*", imageFile), user.getPassword(), new Callback<Response>() {
            @Override
            public void success(Response response, Response response2) {
                dpChangeOperationRunning = false;
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
                    callback.done(e == null ? retrofitError : e); //may be our fault but we have ran out of resources
                }
            }
        });
    }

    public void getUserDp(final String userId, final GetDpCallback callback) {
        if (!ConnectionHelper.isConnectedOrConnecting()) {
            callback.done(NO_CONNECTION_ERROR);
            return;
        }

        if (getDpOperationRunning) {
            return;
        }
        getDpOperationRunning = true;
        getDpAttempts++;
        userApi.getUserDp(userId, new Callback<Response>() {
            @Override
            public void success(Response response, Response response2) {
                getDpOperationRunning = false;
                try {
                    final InputStream in = response.getBody().in();
                    Realm realm = Realm.getInstance(Config.getApplicationContext());
                    User user = realm.where(User.class).equalTo("_id", userId).findFirst();
                    if (user == null) {
                        throw new IllegalArgumentException("user does not exist");
                    }
                    File profileFile = new File(user.getDP());
                    FileHelper.save(profileFile, in);
                    realm.close();
                    Bitmap bitmap = BitmapFactory.decodeFile(profileFile.getAbsolutePath());
                    if (bitmap == null) {
                        Log.wtf(TAG, "invalid image url or file");
                        callback.done(new Exception("invalid image url"));
                    } else {
                        callback.done(null);
                    }
                } catch (IOException e) {
                    if (BuildConfig.DEBUG) {
                        Log.e(TAG, e.getMessage(), e.getCause());
                    } else {
                        Log.e(TAG, e.getMessage());
                    }
                    callback.done(e);
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

    public void logIn(User user, final LoginCallback callback) {
        if (!ConnectionHelper.isConnectedOrConnecting()) {
            callback.done(NO_CONNECTION_ERROR);
        }
        doLogIn(user, callback);
    }

    //this method must be called on the main thread
    private void doLogIn(final User user, final LoginCallback callback) {
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
                //our backend deletes password fields so we got to use our copy here
                backendUser.setPassword(user.getPassword());
                saveUser(backendUser);
                Config.enableComponent(BootReceiver.class);
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


    public void signUp(final User user, final SignUpCallback callback) {
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
                backEndUser.setPassword(user.getPassword());
                saveUser(backEndUser);
                Config.enableComponent(BootReceiver.class);
                callback.done(null);
            }

            @Override
            public void failure(RetrofitError retrofitError) {
                loginSignUpBusy = false;
                // TODO: 6/25/2015 handle error
                Exception e = handleError(retrofitError);
                if (e == null && signUpAttempts < 3) {
                    //not our problem lets try again
                    signUp(user, callback);
                } else {
                    signUpAttempts = 0;
                    callback.done(e == null ? retrofitError : e); //may be our fault but we have ran out of resources
                }
            }
        });

    }

    public void LogOut(Context context, final LogOutCallback logOutCallback) {
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
                if (e == null) {
                    cleanUpRealm();
                }
                logOutCallback.done(e);
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
        Realm realm = Realm.getInstance(context);
        realm.beginTransaction();
        realm.clear(User.class);
        realm.clear(Message.class);
        realm.clear(Conversation.class);
        realm.commitTransaction();
    }


    // FIXME: 6/25/2015 find a sensible place to keep this error handlelr so that message dispatcher and others can share it
    private Exception handleError(RetrofitError retrofitError) {
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
        //our we find out
        throw new AssertionError("unknown error kind");
    }


    private static final RequestInterceptor INTERCEPTOR = new RequestInterceptor() {
        @Override
        public void intercept(RequestFacade requestFacade) {
            requestFacade.addHeader("Authorization", "kiiboda+=s3cr3te");
            requestFacade.addHeader("User-Agent", Config.APP_USER_AGENT);
        }
    };

    public interface LoginCallback {
        void done(Exception e);
    }

    public interface LogOutCallback {
        void done(Exception e);
    }

    public interface SignUpCallback {
        void done(Exception e);
    }

    public interface FriendsFetchCallback {
        void done(Exception e, List<User> users);
    }

    public interface DpChangeCallback {
        void done(Exception e);
    }

    public interface GetDpCallback {
        void done(Exception e);
    }
}
