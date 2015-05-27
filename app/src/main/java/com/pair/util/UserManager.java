package com.pair.util;

import android.app.Application;
import android.content.Context;
import android.support.annotation.NonNull;

import com.pair.adapter.BaseJsonAdapter;
import com.pair.data.User;
import com.pair.net.HttpResponse;
import com.pair.net.api.UserApi;

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
        RestAdapter restAdapter = new RestAdapter.Builder()
                .setEndpoint(Config.PAIRAPP_ENDPOINT)
                .setLogLevel(RestAdapter.LogLevel.FULL)
                .setLog(new AndroidLog(TAG))
                .build();
        this.userApi = restAdapter.create(UserApi.class);

    }

    public User getCurrentUser() {
        if (context == null) {
            throw new IllegalStateException("User manager must be initialised in the application loader");
        }

        Realm realm = Realm.getInstance(context);
        User user = realm.where(User.class).findFirst();
        return user;
    }

    public void signUp(User user, BaseJsonAdapter<User> adapter, final signUpCallback callback) {
        userApi.registerUser(adapter.toJson(user), new Callback<HttpResponse>() {
            @Override
            public void success(HttpResponse httpResponse, Response response) {
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
    }

    public static interface signUpCallback {
        void done(Exception e);
    }
}
