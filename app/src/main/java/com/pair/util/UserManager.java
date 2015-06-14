package com.pair.util;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.gson.JsonObject;
import com.pair.adapter.BaseJsonAdapter;
import com.pair.adapter.UserJsonAdapter;
import com.pair.data.Conversation;
import com.pair.data.Message;
import com.pair.data.User;
import com.pair.net.api.UserApi;
import com.pair.workers.BootReceiver;

import java.util.List;

import io.realm.Realm;
import retrofit.Callback;
import retrofit.RestAdapter;
import retrofit.RetrofitError;
import retrofit.android.AndroidLog;
import retrofit.client.Response;

/**
 * Created by Null-Pointer on 5/27/2015.
 */
public class UserManager {

    private static final String TAG = UserManager.class.getSimpleName();
    private Context context;
    private UserApi userApi;
    private static UserManager INSTANCE;
    private BaseJsonAdapter<User> adapter;
    private volatile boolean busy = false;
    private static final String KEY_SESSION_ID = "session";


    public static UserManager getInstance(@NonNull Application context) {
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

    private UserManager(Application context) {
        this.context = context;
        this.adapter = new UserJsonAdapter();
        RestAdapter restAdapter = new RestAdapter.Builder()
                .setEndpoint(Config.PAIRAPP_ENDPOINT)
                .setLogLevel(RestAdapter.LogLevel.FULL)
                .setLog(new AndroidLog(TAG))
                .build();
        this.userApi = restAdapter.create(UserApi.class);
    }


    private void saveUser(User user) {
        Realm realm = Realm.getInstance(context);
        realm.beginTransaction();
        realm.copyToRealm(user);
        realm.commitTransaction();
        realm.close();
        context.getSharedPreferences(Config.APP_PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_SESSION_ID, user.get_id())
                .commit();
    }

    public User getCurrentUser() {
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

    public void logIn(User user, final LoginCallback callback) {
        logIn(adapter.toJson(user), callback);
    }

    public void logIn(JsonObject user, final LoginCallback callback) {
        if (busy) {
            return;
        }
        busy = true;

        userApi.logIn(user, new Callback<User>() {
            @Override
            public void success(User user, Response response) {
                busy = false;
                saveUser(user);
                // TODO: 6/14/2015 enable boot receiver
                Config.enableComponent(BootReceiver.class);
                callback.done(null);
            }

            @Override
            public void failure(RetrofitError retrofitError) {
                busy = false;
                callback.done(retrofitError);
            }
        });
    }

    public void signUp(User user, final SignUpCallback callback) {
        signUp(adapter.toJson(user), callback);
    }

    public void signUp(JsonObject user, final SignUpCallback callback) {
        if (busy) {
            return;
        }
        busy = true;
        userApi.registerUser(user, new Callback<User>() {
            @Override
            public void success(User user, Response response) {
                busy = false;
                saveUser(user);
                // TODO: 6/14/2015 enable boot receiver
                Config.enableComponent(BootReceiver.class);
                callback.done(null);
            }

            @Override
            public void failure(RetrofitError retrofitError) {
                busy = false;
                callback.done(retrofitError);
            }
        });

    }

    public void LogOut(Context context, final LogOutCallback logOutCallback) {
        //TODO logout user from backend
        SharedPreferences sharedPreferences = context.getSharedPreferences(Config.APP_PREFS, Context.MODE_PRIVATE);
        String userId = sharedPreferences.getString(KEY_SESSION_ID, null);
        if ((userId == null)) {
            throw new AssertionError("calling logout when no user is logged in");
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
        cleanUpRealm();
        GcmHelper.unRegister(context, new GcmHelper.UnregisterCallback() {
            @Override
            public void done(Exception e) {
                if (e == null) {
                    // TODO: 6/14/2015 disable boot receiver
                    Config.disableComponent(BootReceiver.class);
                }
                logOutCallback.done(e);
            }
        });
    }

    public void fetchFriends(final List<String> array, final FriendsFetchCallback callback) {
        userApi.fetchFriends(array, new Callback<List<User>>() {
            @Override
            public void success(List<User> users, Response response) {
                callback.done(null, users);
            }

            @Override
            public void failure(RetrofitError retrofitError) {
                Log.e(TAG, retrofitError.getMessage());
                if (retrofitError.getKind().equals(RetrofitError.Kind.HTTP)
                        || retrofitError.getKind().equals(RetrofitError.Kind.NETWORK)
                        ) {
                    callback.done(retrofitError, null);
                } else if (retrofitError.getKind().equals(RetrofitError.Kind.UNEXPECTED)) {
                    if (ConnectionHelper.isConnectedOrConnecting(context)) {
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
}
