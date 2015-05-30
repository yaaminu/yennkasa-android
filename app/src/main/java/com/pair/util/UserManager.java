package com.pair.util;

import android.app.Application;
import android.content.Context;
import android.support.annotation.NonNull;

import com.google.gson.JsonObject;
import com.pair.adapter.BaseJsonAdapter;
import com.pair.adapter.UserJsonAdapter;
import com.pair.data.User;
import com.pair.net.api.UserApi;

import io.realm.Realm;
import io.realm.RealmResults;
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


    private void saveUser(User user){
        Realm realm = Realm.getInstance(context);
        realm.beginTransaction();
        realm.copyToRealm(user);
        realm.commitTransaction();
        realm.close();
    }

    public User getCurrentUser() {
        Realm realm = Realm.getInstance(context);
        RealmResults<User> results =  realm.where(User.class).findAll();
        if(results.size() > 1){
            throw new IllegalStateException("Multiple users cannot exist");
        }else if(results.size() < 1){
            return null;
        }
        User user =results.first();
        //returning {@code RealmObject} from methods leaks resources as
        // that will prevent us from closing realm hence we do a shallow copy.
        // downside is changes to this object will not be saved which is just what we want
        User copy = new User(user);
        realm.close();
        return copy;
    }

    public void logIn(User user, final LoginCallback callback) {
        logIn(adapter.toJson(user), callback);
    }

    public void logIn(JsonObject user, final LoginCallback callback) {
        userApi.logIn(user, new Callback<User>() {

            @Override
            public void success(User user, Response response) {
                saveUser(user);
                callback.done(null);
            }

            @Override
            public void failure(RetrofitError retrofitError) {
                callback.done(retrofitError);
            }
        });
    }

    public void signUp(User user, final SignUpCallback callback) {
        signUp(adapter.toJson(user), callback);
    }

    public void signUp(JsonObject user, final SignUpCallback callback) {
        userApi.registerUser(user, new Callback<User>() {
            @Override
            public void success(User user, Response response) {
                saveUser(user);
                callback.done(null);
            }

            @Override
            public void failure(RetrofitError retrofitError) {
                callback.done(retrofitError);
            }
        });

    }

    public void LogOut(Context context) {
        //TODO logout user from backend
        Realm realm = Realm.getInstance(context);
        realm.where(User.class).findFirst().removeFromRealm();
        realm.close();
    }

    public  interface LoginCallback {
        void done(Exception e);
    }

    public  interface SignUpCallback {
        void done(Exception e);
    }
}
