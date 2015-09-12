package com.pair.util;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Looper;
import android.util.Log;
import com.github.nkzawa.emitter.Emitter;
import com.pair.Config;
import com.pair.data.User;
import com.pair.data.UserManager;
import com.pair.net.sockets.SocketIoClient;
import com.pair.pairapp.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Set;

import io.realm.Realm;

/**
 * @author Null-Pointer on 9/9/2015.
 */
public class LiveCenter {

    private static final Set<String> activeUsers = new HashSet<>(), typing = new HashSet<>();
    private static final String TAG = "livecenter", TYPING = "typing", TRACK_USER = "trackUser";
    public static final String IS_ONLINE = "isOnline";
    private static String ACTIVE_PEER = "";
    private static WorkerThread WORKER_THREAD;
    private static SocketIoClient liveClient;

    private static final Object TYP_ACT_LOCK = new Object(); //TYPingACTiveLock

    private static WeakReference<TypingListener> typingListener;

    public interface TypingListener {
        void onTyping(String userId);

        void onStopTyping(String userId);
    }

    private static final Emitter.Listener ONLINE_RECEIVER = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            updateUserStatus(args[0]);
        }
    }, TYPING_RECEIVER = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            //mark user as typing
            updateTyping(args[0]);
        }
    };

    private static void updateTyping(Object obj) {
        try {
            JSONObject object = new JSONObject(obj.toString());
            String typingUser = object.getString("from");
            boolean isTyping = object.optBoolean("isTyping");
            synchronized (TYP_ACT_LOCK) {
                if (isTyping) {
                    typing.add(typingUser);
                    notifyListener(typingUser, true);
                } else {
                    typing.remove(typingUser);
                    notifyListener(typingUser, false);
                }
            }
        } catch (JSONException e) {
            throw new RuntimeException(e.getCause());
        }
    }

    private static Handler mainThreadHandler = new Handler(Looper.getMainLooper());
    private static void notifyListener(final String userId,final boolean isTyping) {
        if (typingListener != null && typingListener.get() != null) {
            final TypingListener typingListener = LiveCenter.typingListener.get();
            mainThreadHandler.post(new Runnable(){
            	public void run(){
                  if (isTyping) {
                    typingListener.onTyping(userId);
                  } else {
                    typingListener.onStopTyping(userId);
                  }
            	}
            });
        }
    }

    private static void updateUserStatus(Object arg) {
        try {
            JSONObject object = new JSONObject(arg.toString());
            String userId = object.getString("userId");
            boolean isOnline = object.getBoolean("isOnline");
            synchronized (TYP_ACT_LOCK) {
                if (isOnline) {
                    activeUsers.add(userId);
                } else {
                    activeUsers.remove(userId);
                    //if user is not online then he can't be online too
                    typing.remove(userId);
                }
            }
            Context applicationContext = Config.getApplicationContext();
            Realm realm = User.Realm(applicationContext);
            User user = realm.where(User.class).equalTo(User.FIELD_ID, userId).findFirst();
            realm.beginTransaction();
            user.setStatus(applicationContext.getString(isOnline ? R.string.st_online : R.string.st_offline));
            realm.commitTransaction();
            realm.close();
        } catch (JSONException e) {
            throw new RuntimeException(e.getCause());
        }
    }

    public synchronized static void start() {
        if (WORKER_THREAD == null || !WORKER_THREAD.isAlive()) {
            WORKER_THREAD = new WorkerThread();
            WORKER_THREAD.start();
        }
    }

    public synchronized static void stop() {
        if (WORKER_THREAD != null && WORKER_THREAD.isAlive()) {
            Message message = Message.obtain();
            message.what = WorkerThread.STOP;
            WORKER_THREAD.handler.sendMessage(message);
        }
    }

    private static void doStart() {
        liveClient = SocketIoClient.getInstance(Config.PAIRAPP_ENDPOINT + "/live", UserManager.getMainUserId());
        liveClient.registerForEvent(TYPING, TYPING_RECEIVER);
        liveClient.registerForEvent(IS_ONLINE, ONLINE_RECEIVER);
        activeUsers.clear();
        typing.clear();
    }

    private static void doStop() {
        liveClient.unRegisterEvent(IS_ONLINE, ONLINE_RECEIVER);
        liveClient.unRegisterEvent(TYPING, TYPING_RECEIVER);
        liveClient.close();
    }

    public static void trackUser(String userId) {
        ThreadUtils.ensureMain();
        ACTIVE_PEER = userId;
        if (WORKER_THREAD != null && WORKER_THREAD.isAlive()) {
            Message msg = Message.obtain();
            msg.what = WorkerThread.TRACK_USER;
            msg.obj = userId;
            WORKER_THREAD.handler.sendMessage(msg);
        } else {
            start();
        }
    }

    public static void doNotTrackUser(String userId) {
         ACTIVE_PEER = "";
        ThreadUtils.ensureMain();
        if (WORKER_THREAD != null && WORKER_THREAD.isAlive()) {
            Message msg = Message.obtain();
            msg.what = WorkerThread.DO_NOT_TRACK_USER;
            msg.obj = userId;
            WORKER_THREAD.handler.sendMessage(msg);
        }
    }

    public static void notifyTyping(String userId) {
        ThreadUtils.ensureMain();
        if(ACTIVE_PEER.isEmpty() || !ACTIVE_PEER.equals(userId)){
            throw new IllegalArgumentException("unknown peer");
        }
        if(!isOnline(userId)){
            Log.i(TAG,"peer offline, not dispatching typing event");
            return;
        }
        if (WORKER_THREAD != null && WORKER_THREAD.isAlive()) {
            Message msg = Message.obtain();
            msg.what = WorkerThread.NOTIFY_TYPING;
            msg.obj = userId;
            WORKER_THREAD.handler.sendMessage(msg);
        } else {
            start();
        }
    }

    public static void notifyNotTyping(String userId) {
        ThreadUtils.ensureMain();
        if(!ACTIVE_PEER.equals(userId)){
           throw new IllegalArgumentException("unknown user");
        }
        if(!isOnline(userId)){
            Log.i(TAG,"peer offline, not dispatching typing event");
            return;
        }

        if (WORKER_THREAD != null && WORKER_THREAD.isAlive()) {
            Message msg = Message.obtain();
            msg.what = WorkerThread.NOTIFY_NOT_TYPING;
            msg.obj = userId;
            WORKER_THREAD.handler.sendMessage(msg);
        }
    }

    public static void registerTypingListener(TypingListener listener) {
        ThreadUtils.ensureMain();
        if (listener == null) {
            throw new IllegalArgumentException("listener cannot be null");
        }
        typingListener = new WeakReference<>(listener);
    }

    public static void unRegisterTypingListener(TypingListener listener) {
        ThreadUtils.ensureMain();
        if (listener == null) {
            throw new IllegalArgumentException("listener cannot be null");
        }
        if (typingListener != null && typingListener.get() != null) {
            if (typingListener.get() != listener) {
                throw new IllegalArgumentException("listener not registered");
            }
        }
        typingListener = null;
    }

    public static boolean isOnline(String userId) {
        synchronized (TYP_ACT_LOCK) {
            return activeUsers.contains(userId);
        }
    }

    public static boolean isTyping(String userId) {
        synchronized (TYP_ACT_LOCK) {
            return typing.contains(userId);
        }
    }

    private static final class WorkerThread extends HandlerThread {

        private static final int START = 0x1, STOP = 0x2, TRACK_USER = 0x3, NOTIFY_TYPING = 0x4, NOTIFY_NOT_TYPING = 0x5, DO_NOT_TRACK_USER = 0x6;
        private Handler handler;

        public WorkerThread() {
            super(TAG, WorkerThread.NORM_PRIORITY);
            handler = new Handler(new Handler.Callback() {
                @Override
                public boolean handleMessage(Message msg) {
                    switch (msg.what) {
                        case START:
                            LiveCenter.doStart();
                            break;
                        case STOP:
                            LiveCenter.doStop();
                            quit();
                            break;
                        case TRACK_USER:
                            doTrackUser(((String) msg.obj));
                            break;
                        case NOTIFY_TYPING:
                            doNotifyTyping(((String) msg.obj), true);
                            break;
                        case NOTIFY_NOT_TYPING:
                            doNotifyTyping(((String) msg.obj), false);
                            break;
                        case DO_NOT_TRACK_USER:
                            stopTrackingUser(((String) msg.obj));
                        default:
                            return true;
                    }
                    return true;
                }
            });
        }

        private void doNotifyTyping(String to, boolean isTyping) {
            if(!isOnline(to)){
              Log.d(TAG,"user offline, typing event not dispatched");
              return;
            }
            try {
                JSONObject object = new JSONObject();
                object.put("to", to);
                object.put("from", UserManager.getMainUserId());
                object.put("isTyping", isTyping);
                liveClient.broadcast(TYPING, object);
            } catch (JSONException e) {
                throw new RuntimeException(e.getCause());
            }
        }

        private void doTrackUser(String userId) {
            try {
                JSONObject object = new JSONObject();
                object.put("to", userId);
                liveClient.broadcast(IS_ONLINE, object);
                liveClient.broadcast(LiveCenter.TRACK_USER, object);
            } catch (JSONException e) {
                throw new RuntimeException(e.getCause());
            }
        }

        private void stopTrackingUser(String userId) {
            try {
                JSONObject object = new JSONObject();
                object.put("to", userId);
                liveClient.broadcast("unTrackUser", object);
            } catch (JSONException e) {
                throw new RuntimeException(e.getCause());
            }
        }

        @Override
        protected void onLooperPrepared() {
            Message msg = Message.obtain();
            msg.what = START;
            handler.sendMessage(msg);
        }
    }
}
