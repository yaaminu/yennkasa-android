package com.pair.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.gson.JsonObject;
import com.pair.adapter.BaseJsonAdapter;
import com.pair.adapter.UserJsonAdapter;
import com.pair.data.Conversation;
import com.pair.data.Message;
import com.pair.data.User;
import com.pair.net.api.UserApi;
import com.pair.pairapp.BuildConfig;
import com.pair.workers.BootReceiver;

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

    private static final String TAG = UserManager.class.getSimpleName();
    private final Exception NO_CONNECTION_ERROR = new Exception("not connected to the internet");
    private Context context;
    private UserApi userApi;
    private static UserManager INSTANCE;
    private BaseJsonAdapter<User> adapter;
    private volatile boolean busy = false;
    private static final String KEY_SESSION_ID = "session";

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
        realm.copyToRealm(user);
        realm.commitTransaction();
        realm.close();
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
        return ((user == null || thisUser == null)
                ? false
                : thisUser.get_id().equals(user.get_id()));
    }

    public void refreshUserDetails(User user) {
        //update user here
        userApi.getUser(user.get_id(), new Callback<User>() {
            @Override
            public void success(User user, Response response) {
                Realm realm = Realm.getInstance(Config.getApplicationContext());
                realm.beginTransaction();
                realm.copyToRealmOrUpdate(user);
                realm.commitTransaction();
                realm.close();
                getUserDp(user, new GetDpCallback() {
                    @Override
                    public void done(Exception e, Bitmap bitmap) {
                    }
                });
            }

            @Override
            public void failure(RetrofitError retrofitError) {
            }
        });
    }

    public void changeDp(final String imagePath, final DpChangeCallback callback) {

        final User user = getMainUser();
        if (user == null) {
            throw new IllegalStateException("can't change dp of user that is null");
        }
        userApi.changeDp(user.get_id(), new TypedFile("image/*", new File(imagePath)), user.getPassword(), new Callback<Response>() {
            @Override
            public void success(Response response, Response response2) {
                Realm realm = Realm.getInstance(Config.getApplicationContext());
                realm.beginTransaction();
                user.setDP(imagePath);
                realm.commitTransaction();
                realm.close();
                callback.done(null);
            }

            @Override
            public void failure(RetrofitError retrofitError) {
                callback.done(retrofitError);
            }
        });
    }

    public void getUserDp(final User user, final GetDpCallback callback) {
        userApi.getUserDp(user.get_id(), new Callback<Response>() {
            @Override
            public void success(Response response, Response response2) {
                try {
                    final InputStream in = response.getBody().in();
                    File profileFile = new File(Config.APP_PROFILE_PICS_BASE_DIR, System.currentTimeMillis() + user.get_id());
                    FileHelper.save(profileFile, in);
                    Realm realm = Realm.getInstance(Config.getApplicationContext());
                    realm.beginTransaction();
                    user.setDP(profileFile.getAbsolutePath());
                    realm.commitTransaction();
                    realm.close();
                    Bitmap bitmap = BitmapFactory.decodeFile(profileFile.getAbsolutePath());
                    if (bitmap == null) {
                        callback.done(new Exception("invalid image url"), null);
                    } else {
                        callback.done(null, bitmap);
                    }
                } catch (IOException e) {
                    if (BuildConfig.DEBUG) {
                        Log.e(TAG, e.getMessage(), e.getCause());
                    } else {
                        Log.e(TAG, e.getMessage());
                    }
                    callback.done(e, null);
                }
            }

            @Override
            public void failure(RetrofitError retrofitError) {
                callback.done(retrofitError, null);
            }
        });
    }

    public void logIn(User user, final LoginCallback callback) {
        if (!ConnectionHelper.isConnectedOrConnecting()) {
            callback.done(NO_CONNECTION_ERROR);
        }
        logIn(adapter.toJson(user), callback);
    }

    //this method must be called on the main thread
    public void logIn(JsonObject user, final LoginCallback callback) {
        if (!ConnectionHelper.isConnectedOrConnecting()) {
            callback.done(NO_CONNECTION_ERROR);
        }
        if (busy) {
            return;
        }
        busy = true;

        userApi.logIn(user, new Callback<User>() {
            @Override
            public void success(User user, Response response) {
                busy = false;
                saveUser(user);
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
        if (!ConnectionHelper.isConnectedOrConnecting()) {
            callback.done(NO_CONNECTION_ERROR);
        }
        signUp(adapter.toJson(user), callback);
    }

    public void signUp(JsonObject user, final SignUpCallback callback) {
        if (!ConnectionHelper.isConnectedOrConnecting()) {
            callback.done(NO_CONNECTION_ERROR);
        }
        if (busy) {
            return;
        }
        busy = true;
        userApi.registerUser(user, new Callback<User>() {
            @Override
            public void success(User user, Response response) {
                busy = false;
                saveUser(user);
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
        void done(Exception e, Bitmap bitmap);
    }
}
