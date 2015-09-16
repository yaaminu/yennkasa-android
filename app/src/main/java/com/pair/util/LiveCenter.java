package com.pair.util;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
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
 * This class is the heart of all real-time events of the program.
 * it tracks what active friends are doing. like typing,online,onPhone, etc
 *
 * @author Null-Pointer on 9/9/2015.
 */
public class LiveCenter {

    private static final String TAG = "livecenter", TYPING = "typing",
            TRACK_USER = "trackUser", IS_ONLINE = "isOnline",
            UN_TRACK_USER = "unTrackUser", PROPERTY_TO = "to",
            PROPERTY_FROM = "from", PROPERTY_IS_TYPING = "isTyping",
            EVENT_CHAT_ROOM = "chatRoom";


    private static final Set<String> activeUsers = new HashSet<>(),
            typing = new HashSet<>(),
            PEERS_IN_CHATROOM = new HashSet<>();

    public static final String PROPERTY_IN_CHAT_ROOM = "inChatRoom";
    private static WorkerThread WORKER_THREAD;
    private static SocketIoClient liveClient;

    private static final Object TYPING_AND_ACTIVE_USERS_LOCK = new Object();

    private static WeakReference<TypingListener> typingListener;

    /**
     * an interface one must implement if one wants to be notified of typing events
     * you may not implement this interface using an anonymous inner class as the listner
     * is referenced internally as a weakReference. at any point in time, there may be at
     * most one listener.
     */
    public interface TypingListener {
        void onTyping(String userId);

        void onStopTyping(String userId);

        void onUserStatusChanged(String userId, boolean isOnline);
    }

    public static final Emitter.Listener CHAT_ROOM_RECEIVER = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            Log.i(TAG, "chatroom event: " + args[0]);
            try {
                JSONObject object = new JSONObject(args[0].toString());
                String userId = object.getString(PROPERTY_FROM);
                boolean inChatRoom = object.getBoolean(PROPERTY_IN_CHAT_ROOM);
                synchronized (PEERS_IN_CHATROOM) {
                    if (inChatRoom) {
                        PEERS_IN_CHATROOM.add(userId);
                    } else {
                        PEERS_IN_CHATROOM.remove(userId);
                    }
                }
            } catch (JSONException e) {
                throw new RuntimeException(e.getCause());
            }
        }
    };

    private static final Emitter.Listener ONLINE_RECEIVER = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            Log.d(TAG, "online reciever: " + args[0].toString());
            updateUserStatus(args[0]);
        }
    }, TYPING_RECEIVER = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            Log.d(TAG, "typing reciever: " + args[0].toString());
            //mark user as typing
            updateTyping(args[0]);
        }
    };

    private static void updateTyping(Object obj) {
        try {
            JSONObject object = new JSONObject(obj.toString());
            String typingUser = object.getString(PROPERTY_FROM);
            boolean isTyping = object.optBoolean(PROPERTY_IS_TYPING);
            synchronized (TYPING_AND_ACTIVE_USERS_LOCK) {
                Log.d(TAG, "typing event");
                if (isTyping) {
                    typing.add(typingUser);
                } else {
                    typing.remove(typingUser);
                }
            }
            notifyListener(typingUser, isTyping);
        } catch (JSONException e) {
            throw new RuntimeException(e.getCause());
        }
    }

    private static Handler mainThreadHandler = new Handler(Looper.getMainLooper());

    private static void notifyListener(final String userId, final boolean isTyping) {
        if (typingListener != null && typingListener.get() != null) {
            final TypingListener typingListener = LiveCenter.typingListener.get();
            mainThreadHandler.post(new Runnable() {
                public void run() {
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
            final String userId = object.getString("userId");
            final boolean isOnline = object.getBoolean("isOnline");
            synchronized (TYPING_AND_ACTIVE_USERS_LOCK) {
                if (isOnline) {
                    activeUsers.add(userId);
                } else {
                    activeUsers.remove(userId);
                    //if user is not online then he can't be online too
                    typing.remove(userId);
                }
                mainThreadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (typingListener != null) {
                            TypingListener listener = typingListener.get();
                            if (listener != null) {
                                listener.onUserStatusChanged(userId, isOnline);
                            }
                        }
                    }
                });
            }
//            Context applicationContext = Config.getApplicationContext();
//            Realm realm = User.Realm(applicationContext);
//            User user = realm.where(User.class).equalTo(User.FIELD_ID, userId).findFirst();
//            realm.beginTransaction();
//            user.setStatus(applicationContext.getString(isOnline ? R.string.st_online : R.string.st_offline));
//            realm.commitTransaction();
//            realm.close();
        } catch (JSONException e) {
            throw new RuntimeException(e.getCause());
        }
    }


    /**
     * starts the {@link LiveCenter} class. until this method is called
     * this class is not usable.
     */
    public synchronized static void start() {
        if (WORKER_THREAD == null || !WORKER_THREAD.isAlive()) {
            WORKER_THREAD = new WorkerThread();
            WORKER_THREAD.start();
        }
    }

    /**
     * stops the {@link LiveCenter} class. after a call this method,
     * this class will no more be usable until one calls {@link #start()}
     */
    public synchronized static void stop() {
        if (WORKER_THREAD != null && WORKER_THREAD.isAlive()) {
            Message message = Message.obtain();
            message.what = WorkerThread.STOP;
            WORKER_THREAD.handler.sendMessage(message);
        }
    }

    //this method is synchronized because of the
    //activePeers,typing and peers_in_chatRoom fields.
    private synchronized static void doStart() {
        liveClient = SocketIoClient.getInstance(Config.PAIRAPP_ENDPOINT + "/live", UserManager.getMainUserId());
        liveClient.registerForEvent(TYPING, TYPING_RECEIVER);
        liveClient.registerForEvent(IS_ONLINE, ONLINE_RECEIVER);
        liveClient.registerForEvent(EVENT_CHAT_ROOM, CHAT_ROOM_RECEIVER);
        try {
            JSONObject object = new JSONObject();
            object.put(PROPERTY_TO, UserManager.getMainUserId());
            liveClient.broadcast(IS_ONLINE, object);
        } catch (JSONException e) {
            throw new RuntimeException(e.getCause());
        }
        activeUsers.clear();
        typing.clear();
        PEERS_IN_CHATROOM.clear();
    }

    private static void doStop() {
        liveClient.unRegisterEvent(IS_ONLINE, ONLINE_RECEIVER);
        liveClient.unRegisterEvent(TYPING, TYPING_RECEIVER);
        liveClient.unRegisterEvent(EVENT_CHAT_ROOM, CHAT_ROOM_RECEIVER);
        liveClient.close();
    }

    /**
     * gives the {@link LiveCenter} a hint that this user is now active to the user
     * at our end here and that {@link LiveCenter} should monitor this user for typing events, etc.This is
     * different from the user been online this may be called after a call to {@link #start()}
     * and never before also on may not call this method after call to {@link #stop()}.
     * in all such situations,this method will fail silently
     *
     * @param userId the userId of the user to track
     * @throws IllegalStateException if the call is not made on the main thread
     */
    public static void trackUser(String userId) {
        ThreadUtils.ensureMain();
        if (WORKER_THREAD != null && WORKER_THREAD.isAlive()) {
            Message msg = Message.obtain();
            msg.what = WorkerThread.TRACK_USER;
            msg.obj = userId;
            WORKER_THREAD.handler.sendMessage(msg);
        } else {
            start();
        }
    }

    /**
     * gives the {@link LiveCenter} a hint that this user is not active to the user
     * at our end here and that {@link LiveCenter} should stop monitoring this user for typing events, etc.This is
     * different from the user been online this may be called after a call to {@link #start()}
     * and never before also one may not call this method after call to {@link #stop()}.
     * in all such situations,this method will fail silently
     *
     * @param userId the userId of the user to unTrack
     * @throws IllegalStateException if the call is not made on the main thread
     */
    public static void doNotTrackUser(String userId) {
        ThreadUtils.ensureMain();
        if (WORKER_THREAD != null && WORKER_THREAD.isAlive()) {
            Message msg = Message.obtain();
            msg.what = WorkerThread.DO_NOT_TRACK_USER;
            msg.obj = userId;
            WORKER_THREAD.handler.sendMessage(msg);
        }
    }

    /**
     * send a message across to the other user on the other end that
     * this user is now in the chat room.
     *
     * @param userId the id of the recipient of the message
     */
    public static void notifyInChatRoom(String userId) {
        ThreadUtils.ensureMain();
        synchronized (PEERS_IN_CHATROOM) {
            PEERS_IN_CHATROOM.add(userId);
        }
        if (WORKER_THREAD != null && WORKER_THREAD.isAlive()) {
            Message msg = Message.obtain();
            msg.what = WorkerThread.IN_CHAT_ROOM;
            msg.obj = userId;
            WORKER_THREAD.handler.sendMessage(msg);
        }
    }


    /**
     * send a message across to the other user on the other end that
     * this user is no more in the chat room.
     *
     * @param userId the id of the recipient of the message
     */
    public static void notifyLeftChatRoom(String userId) {
        ThreadUtils.ensureMain();
        synchronized (PEERS_IN_CHATROOM) {
            PEERS_IN_CHATROOM.remove(userId);
        }
        if (WORKER_THREAD != null && WORKER_THREAD.isAlive()) {
            Message msg = Message.obtain();
            msg.what = WorkerThread.LEFT_CHAT_ROOM;
            msg.obj = userId;
            WORKER_THREAD.handler.sendMessage(msg);
        }
    }

    /**
     * sends a message across the wire to user with id,userId, that
     * this particular user is typing
     *
     * @param userId the id of the recipient of the message
     * @throws IllegalStateException    if the call is not made on the main thread
     * @throws IllegalArgumentException if this {@code LiveCenter} is unaware this
     *                                  particular user is in the chat room.
     */
    public static void notifyTyping(String userId) {
        ThreadUtils.ensureMain();
        synchronized (PEERS_IN_CHATROOM) {
            if (!PEERS_IN_CHATROOM.contains(userId)) {
                Log.i(TAG, "peer not in chat room stopping dispatch");
                return;
            }
        }
        if (!isOnline(userId)) {
            synchronized (PEERS_IN_CHATROOM) {
                PEERS_IN_CHATROOM.remove(userId);
            }
            Log.i(TAG, "peer offline, not dispatching typing event");
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

    /**
     * sends a message across the wire to user with id,userId, that
     * this particular user is no more typing
     *
     * @param userId the ID of the recipient of the message
     * @throws IllegalStateException    if the call is not made on the main thread
     * @throws IllegalArgumentException if this {@code LiveCenter} is unaware this
     *                                  particular user is in the chat room.
     */
    public static void notifyNotTyping(String userId) {
        ThreadUtils.ensureMain();
        synchronized (PEERS_IN_CHATROOM) {
            if (!PEERS_IN_CHATROOM.contains(userId)) {
                Log.i(TAG, "peer not in chat room stopping dispatch");
                return;
            }
        }
        if (!isOnline(userId)) {
            synchronized (PEERS_IN_CHATROOM) {
                PEERS_IN_CHATROOM.remove(userId);
            }
            Log.i(TAG, "peer offline, not dispatching typing event");
            return;
        }

        if (WORKER_THREAD != null && WORKER_THREAD.isAlive()) {
            Message msg = Message.obtain();
            msg.what = WorkerThread.NOTIFY_NOT_TYPING;
            msg.obj = userId;
            WORKER_THREAD.handler.sendMessage(msg);
        }
    }

    /**
     * registers a typing listener. listeners are stored internally as weakReferences so
     * you may not pass an anonymous classes.
     *
     * @param listener the listener to be registered, may not be {@code null}
     * @throws IllegalStateException    if the call is not made on the UI thread
     * @throws IllegalArgumentException if the listener to be registered is null
     */
    public static void registerTypingListener(TypingListener listener) {
        ThreadUtils.ensureMain();
        if (listener == null) {
            throw new IllegalArgumentException("listener cannot be null");
        }
        typingListener = new WeakReference<>(listener);
    }

    /**
     * unregister a typing listener.
     *
     * @param listener the listener to be unregistered, may not be {@code null}
     * @throws IllegalStateException    if the call is not made on the UI thread
     * @throws IllegalArgumentException if the listener to be unregistered is null
     *                                  or the listener is not already registered
     */
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

    /**
     * checks whether user is online
     *
     * @param userId the id of the user
     * @return true if user is online, false if not
     */
    public static boolean isOnline(String userId) {
        synchronized (TYPING_AND_ACTIVE_USERS_LOCK) {
            return activeUsers.contains(userId);
        }
    }

    /**
     * checks whether user is actively typing or not
     *
     * @param userId the id of the user in question
     * @return true if user is typing false if not
     */
    public static boolean isTyping(String userId) {
        synchronized (TYPING_AND_ACTIVE_USERS_LOCK) {
            return typing.contains(userId);
        }
    }

    private static final class WorkerThread extends HandlerThread implements Handler.Callback {

        private static final int
                START = 0x1,
                STOP = 0x2,
                TRACK_USER = 0x3,
                NOTIFY_TYPING = 0x4,
                NOTIFY_NOT_TYPING = 0x5,
                DO_NOT_TRACK_USER = 0x6,
                IN_CHAT_ROOM = 0x7,
                LEFT_CHAT_ROOM = 0x8;

        private Handler handler;

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
                case IN_CHAT_ROOM:
                    doNotifyLeftOrJoinChatRoom((String) msg.obj, true);
                    break;
                case LEFT_CHAT_ROOM:
                    doNotifyLeftOrJoinChatRoom((String) msg.obj, false);
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

        public WorkerThread() {
            super(TAG, WorkerThread.NORM_PRIORITY);
        }

        private void doNotifyLeftOrJoinChatRoom(String userId, boolean inChatRoom) {
            if (!isOnline(userId)) {
                return;
            }
            try {
                JSONObject object = new JSONObject();
                object.put(PROPERTY_FROM, UserManager.getMainUserId());
                object.put(PROPERTY_TO, userId);
                object.put(PROPERTY_IN_CHAT_ROOM, inChatRoom);
                liveClient.broadcast(EVENT_CHAT_ROOM, object);
            } catch (JSONException e) {
                throw new RuntimeException(e.getCause());
            }
        }

        private void doNotifyTyping(String to, boolean isTyping) {
            if (!isOnline(to)) {
                Log.d(TAG, "user offline, typing event not dispatched");
                return;
            }
            try {
                JSONObject object = new JSONObject();
                object.put(PROPERTY_TO, to);
                object.put(PROPERTY_FROM, UserManager.getMainUserId());
                object.put(PROPERTY_IS_TYPING, isTyping);
                liveClient.broadcast(TYPING, object);
            } catch (JSONException e) {
                throw new RuntimeException(e.getCause());
            }
        }

        private void doTrackUser(String userId) {
            try {
                Realm realm = User.Realm(Config.getApplicationContext());
                User user = realm.where(User.class).equalTo(User.FIELD_ID, userId).findFirst();
                if (user != null) {
                    realm.beginTransaction();
                    if (isOnline(userId)) {
                        user.setStatus(Config.getApplicationContext().getString(R.string.st_online));
                    } else {
                        user.setStatus(Config.getApplicationContext().getString(R.string.st_offline));
                    }
                    realm.commitTransaction();
                }
                realm.close();
                JSONObject object = new JSONObject();
                object.put(PROPERTY_TO, userId);
                liveClient.broadcast(LiveCenter.TRACK_USER, object);
            } catch (JSONException e) {
                throw new RuntimeException(e.getCause());
            }
        }

        private void stopTrackingUser(String userId) {
            try {
                JSONObject object = new JSONObject();
                object.put(PROPERTY_TO, userId);
                liveClient.broadcast(UN_TRACK_USER, object);
            } catch (JSONException e) {
                throw new RuntimeException(e.getCause());
            }
        }

        @Override
        protected void onLooperPrepared() {
            handler = new Handler(getLooper(), this);
            Message msg = Message.obtain();
            msg.what = START;
            handler.sendMessage(msg);
        }
    }
}
