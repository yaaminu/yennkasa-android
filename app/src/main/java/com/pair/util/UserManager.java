package com.pair.util;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;

import com.google.gson.JsonObject;
import com.pair.adapter.BaseJsonAdapter;
import com.pair.adapter.UserJsonAdapter;
import com.pair.data.User;
import com.pair.net.api.UserApi;
import com.pair.pairapp.BuildConfig;

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
                // that will prevent us from closing realm. hence we do a shallow copy.
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
                callback.done(null);
            }

            @Override
            public void failure(RetrofitError retrofitError) {
                busy = false;
                callback.done(retrofitError);
            }
        });

    }

    public void LogOut(Context context) {
        //TODO logout user from backend
        SharedPreferences sharedPreferences = context.getSharedPreferences(Config.APP_PREFS, Context.MODE_PRIVATE);
        String userId = sharedPreferences.getString(KEY_SESSION_ID, null);
        if ((userId == null) && BuildConfig.DEBUG) { //crash early
            throw new IllegalStateException("calling logout when no user is logged in");
        }
        sharedPreferences
                .edit()
                .remove(KEY_SESSION_ID)
                .commit();
        Realm realm = Realm.getInstance(context);
        User user = realm.where(User.class).equalTo("_id", userId).findFirst();
        if ((user == null) && BuildConfig.DEBUG) {
            throw new IllegalStateException("existing session id with now corresponding User in the database");
        }
        user.removeFromRealm();
        realm.close();
    }

    public interface LoginCallback {
        void done(Exception e);
    }

    public interface SignUpCallback {
        void done(Exception e);
    }
}
