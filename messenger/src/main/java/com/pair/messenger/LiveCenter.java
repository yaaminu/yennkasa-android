package com.pair.messenger;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.github.nkzawa.emitter.Emitter;
import com.google.gson.JsonObject;
import com.pair.Config;
import com.pair.data.User;
import com.pair.data.UserManager;
import com.pair.net.sockets.SocketIoClient;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import io.realm.Realm;

/**
 * this class tracks the status of active users
 *
 * @author by Null-Pointer on 9/7/2015.
 */
class LiveCenter {
    private static final String TAG = LiveCenter.class.getSimpleName();
    private static final AtomicBoolean tracking = new AtomicBoolean(false);
    private static final Set<String> active = new HashSet<>(), typing = new HashSet<>();

    private static final Emitter.Listener STATUS_TRACKER = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            //update user status
            try {
                synchronized (active) {
                    updateStatuses(args[0]);
                }
            } catch (JSONException e) {
                Log.e(TAG, e.getMessage(), e.getCause());
                throw new RuntimeException(e.getCause());
            }
        }
    };
    private static WorkerThread workerThread;

    private static void updateStatuses(Object arg) throws JSONException {
        JSONObject object = new JSONObject(arg.toString());
        String status = object.getString("status");
        String userId = object.getString("userId");
        if (status.equals("online")) {
            active.add(userId);
            typing.remove(userId);
        } else if (status.equals("startTyping")) {
            active.add(userId);
            typing.add(userId);
        } else if (status.equals("stopTyping")) {
            active.add(userId);
            typing.remove(userId);
        } else {
            active.remove(userId);
            typing.remove(userId);
        }
        Realm realm = User.Realm(Config.getApplicationContext());
        User user = realm.where(User.class).equalTo(User.FIELD_ID, userId).notEqualTo(User.FIELD_TYPE, User.TYPE_GROUP).findFirst();
        realm.beginTransaction();
        if (user != null) {
            user.setStatus(status);
        }
        realm.commitTransaction();
        realm.close();
    }

    private static SocketIoClient client;

    private LiveCenter() {
    }

    static boolean isOnline(String userId) {
        synchronized (active) {
            return active.contains(userId);
        }
    }

    static boolean isTyping(String userId) {
        synchronized (typing) {
            return typing.contains(userId);
        }
    }

    static synchronized void startTrackingActiveUsers() {
        if (!tracking.get()) {
            workerThread = new WorkerThread();
            workerThread.start();
            tracking.set(true);
        }
    }

    private static void doStartTracking() {
        client = SocketIoClient.getInstance(Config.PAIRAPP_ENDPOINT + "/live", UserManager.getMainUserId());
        List<String> allUsers = UserManager.getInstance().allUserIds();
        for (String userId : allUsers) {
            client.broadcast("join", "{\"room\":\"" + userId + "\"}");
        }
        client.registerForEvent("status", STATUS_TRACKER);
    }

    static synchronized void trackTyping(String peerId) {
        if (tracking.get()) {
            workerThread.trackTyping(peerId);
        } else {
            throw new IllegalStateException("did you startTracking()?");
        }
    }

    static synchronized void stopTrackTyping(String peerId) {
        if (tracking.get()) {
            workerThread.stopTrackTyping(peerId);
        } else {
            throw new IllegalStateException("did you startTracking()?");
        }
    }

    private static void doStopTrackTyping(String peerId) {
        JsonObject data = new JsonObject();
        data.addProperty("from", UserManager.getMainUserId());
        data.addProperty("to", peerId);
        data.addProperty("status", "stopTyping");
        client.broadcast("typing", data);
    }

    private static void doStartTrackingTyping(String peerId) {
        JsonObject data = new JsonObject();
        data.addProperty("from", UserManager.getMainUserId());
        data.addProperty("to", peerId);
        data.addProperty("status", "startTyping");
        client.broadcast("typing", data);
    }


    static synchronized void stopTrackingActiveUsers() {
        if (tracking.getAndSet(false)) {
            workerThread.stopTracking();
        }
    }

    private static void doStopTracking() {
        client.unRegisterEvent("status", STATUS_TRACKER);
        client.close();
        client = null;

    }


    private static void onWorkerThreadReady() {
        workerThread.startTracking();

    }

    private static class WorkerThread extends HandlerThread {
        private static Handler handler;

        public WorkerThread() {
            super(TAG, HandlerThread.NORM_PRIORITY);
        }

        @Override
        protected void onLooperPrepared() {
            Log.i(TAG, "live center handler running");
            handler = new Handler();
            onWorkerThreadReady();
        }

        private void startTracking() {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    doStartTracking();
                }
            });
        }

        private void stopTracking() {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    doStopTracking();
                    quit();
                }
            });
        }

        public void trackTyping(final String peerId) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    doStartTrackingTyping(peerId);
                }
            });
        }

        public void stopTrackTyping(final String peerId) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    doStopTrackTyping(peerId);
                }
            });
        }
    }
}
