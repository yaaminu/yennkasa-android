package com.idea.util;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.text.TextUtils;

import com.github.nkzawa.emitter.Emitter;
import com.idea.Errors.PairappException;
import com.idea.data.UserManager;
import com.idea.net.sockets.SocketIoClient;
import com.idea.pairapp.BuildConfig;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class is the heart of all real-time events of the program.
 * it tracks what active friends are doing. like typing,online,onPhone, etc
 *
 * @author Null-Pointer on 9/9/2015.
 */
public class LiveCenter {

    private static final String TAG = "livecenter";
    public static final Emitter.Listener CHAT_ROOM_RECEIVER = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            PLog.i(TAG, "chatroom event: " + args[0]);
            try {
                JSONObject object = new JSONObject(args[0].toString());
                String userId = object.getString(SocketIoClient.PROPERTY_FROM);
                boolean inChatRoom = object.getBoolean(SocketIoClient.PROPERTY_IN_CHAT_ROOM);
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
    public static final Emitter.Listener disConnectReceiver = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            synchronized (TYPING_AND_ACTIVE_USERS_LOCK) {
                connected.set(false);
                activeUsers.clear();
                typing.clear();
            }
        }
    };

    private static Emitter.Listener connectReceiver = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            synchronized (TYPING_AND_ACTIVE_USERS_LOCK) {
                connected.set(true);
                Set<String> tmp = activeUsers;
                activeUsers = new HashSet<>();
                for (String activeUser : tmp) {
                    trackUser(activeUser);
                }
            }
        }
    };


    /**
     * increment the number of unread messages for user with id {@code peerId} by {@code messageCount}
     *
     * @param peerId       the id of the peer in subject, may not be null or empty
     * @param messageCount the number of unread messages to be added. may not be {@code < 1}
     * @see #incrementUnreadMessageForPeer(String)
     */
    @SuppressLint("CommitPrefEdits")
    public static void incrementUnreadMessageForPeer(String peerId, int messageCount) {
        if (TextUtils.isEmpty(peerId)) {
            throw new IllegalArgumentException("null peer id");
        }
        if (messageCount < 1) {
            throw new IllegalArgumentException("invalid message count");
        }

        SharedPreferences preferences = getPreferences();
        int existing = preferences.getInt(peerId, 0) + messageCount;
        preferences.edit().putInt(peerId, existing).commit();
    }

    private static SharedPreferences getPreferences() {
        return Config.getPreferences("unreadMessages" + TAG);
    }

    /**
     * @see #incrementUnreadMessageForPeer(String, int)
     */
    public static void incrementUnreadMessageForPeer(String peerId) {
        incrementUnreadMessageForPeer(peerId, 1);
    }

    /**
     * gets number of unread messages from user with id {@code peerId}
     *
     * @param peerId the id of the peer in subject, may not be null or empty
     * @return the number of unread messages
     */
    public static int getUnreadMessageFor(String peerId) {
        if (TextUtils.isEmpty(peerId)) {
            throw new IllegalArgumentException("null peerId");
        }
        return getPreferences().getInt(peerId, 0);
    }

    /**
     * return  the number of all unread messages
     *
     * @return number of all unread messages
     */
    public static int getTotalUnreadMessages() {
//        synchronized (unReadMessageLock) {
//            return totalUnreadMessages;
//        }
        int total = 0;
        SharedPreferences preferences = getPreferences();
        Set<String> keys = preferences.getAll().keySet();
        int temp;
        for (String key : keys) {
            temp = preferences.getInt(key, 0);
            if (BuildConfig.DEBUG) {
                if (temp < 0) {
                    throw new IllegalStateException("negative count");
                }
            }
            total += temp;
        }
        return total;
    }

    /**
     * invalidate unread messages count for user with id {@code peerId}
     *
     * @param peerId the id of the  user in subject may not be null or empty
     */
    @SuppressLint("CommitPrefEdits")
    public static void invalidateNewMessageCount(String peerId) {
        if (TextUtils.isEmpty(peerId)) {
            throw new IllegalArgumentException("null peerId");
        }
        final SharedPreferences preferences = getPreferences();
        preferences.edit().remove(peerId).commit();
    }

    private static Set<String> activeUsers = new HashSet<>();
    private static final Set<String>
            typing = new HashSet<>(),
            PEERS_IN_CHATROOM = new HashSet<>();
    private static final Object TYPING_AND_ACTIVE_USERS_LOCK = new Object();
    private static WorkerThread WORKER_THREAD;
    private static SocketIoClient liveClient;
    private static WeakReference<LiveCenterListener> typingListener;
    private static final Emitter.Listener ONLINE_RECEIVER = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            PLog.d(TAG, "online reciever: " + args[0].toString());
            updateUserStatus(args[0]);
        }
    }, TYPING_RECEIVER = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            PLog.d(TAG, "typing reciever: " + args[0].toString());
            //mark user as typing
            updateTyping(args[0]);
        }
    };

    private static void updateTyping(Object obj) {
        try {
            JSONObject object = new JSONObject(obj.toString());
            String typingUser = object.getString(SocketIoClient.PROPERTY_FROM);
            boolean isTyping = object.optBoolean(SocketIoClient.PROPERTY_IS_TYPING);
            synchronized (TYPING_AND_ACTIVE_USERS_LOCK) {
                PLog.d(TAG, "typing event");
                if (isTyping) {
                    typing.add(typingUser);
                    if (!activeUsers.contains(typingUser)) {
                        activeUsers.add(typingUser);
                        notifyUserStatusChanged(typingUser, true);
                    }
                } else {
                    typing.remove(typingUser);
                }
            }
            notifyListener(typingUser, isTyping);
        } catch (JSONException e) {
            throw new RuntimeException(e.getCause());
        }
    }

    private static void notifyListener(final String userId, final boolean isTyping) {
        if (typingListener != null && typingListener.get() != null) {
            final LiveCenterListener liveCenterListener = LiveCenter.typingListener.get();
            TaskManager.executeOnMainThread(new Runnable() {
                public void run() {
                    if (isTyping) {
                        liveCenterListener.onTyping(userId);
                    } else {
                        liveCenterListener.onStopTyping(userId);
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
                    //if user is not online then he can't be typing too
                    typing.remove(userId);
                }
                notifyUserStatusChanged(userId, isOnline);
            }
//            Context applicationContext = Config.getApplicationContext();
//            Realm realm = User.Realm(applicationContext);
//            User user = realm.where(User.class).equalTo(User.FIELD_ID, userId).findFirst();
//            realm.beginTransaction();
//            user.setStatus(applicationContext.getString(isOnline ? R.string.st_online : R.string.st_offline));
//            realm.commitTransaction();
//            realm.close();
        } catch (JSONException e) {
            // FIXME: 11/8/2015 handle exception
//            throw new RuntimeException(e.getCause());
            PLog.d(TAG, e.getMessage(), e.getCause());
        }
    }

    private static void notifyUserStatusChanged(final String userId, final boolean isOnline) {
        TaskManager.executeOnMainThread(new Runnable() {
            @Override
            public void run() {
                if (typingListener != null) {
                    LiveCenterListener listener = typingListener.get();
                    if (listener != null) {
                        listener.onUserStatusChanged(userId, isOnline);
                    }
                }
            }
        });
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
     * stops the {@link LiveCenter} class. after a call to this method,
     * this class will no more be usable until one calls {@link #start()}
     */
    public synchronized static void stop() {
        if (WORKER_THREAD != null && WORKER_THREAD.isAlive()) {
            Message message = Message.obtain();
            message.what = WorkerThread.STOP;
            WORKER_THREAD.handler.sendMessage(message);
        }
    }

    private static final AtomicBoolean connected = new AtomicBoolean(false);

    private static void doStart() {
        liveClient = SocketIoClient.getInstance(Config.getLiveEndpoint(), UserManager.getMainUserId());
        liveClient.registerForEvent(SocketIoClient.TYPING, TYPING_RECEIVER);
        liveClient.registerForEvent(SocketIoClient.IS_ONLINE, ONLINE_RECEIVER);
        liveClient.registerForEvent(SocketIoClient.EVENT_CHAT_ROOM, CHAT_ROOM_RECEIVER);
        liveClient.registerForEvent(SocketIoClient.CONNECT, connectReceiver);
        liveClient.registerForEvent(SocketIoClient.DISCONNECT, disConnectReceiver);
        synchronized (TYPING_AND_ACTIVE_USERS_LOCK) {
            activeUsers.clear();
            typing.clear();
        }
        synchronized (PEERS_IN_CHATROOM) {
            PEERS_IN_CHATROOM.clear();
        }
    }

    private static void doStop() {
        liveClient.unRegisterEvent(SocketIoClient.IS_ONLINE, ONLINE_RECEIVER);
        liveClient.unRegisterEvent(SocketIoClient.TYPING, TYPING_RECEIVER);
        liveClient.unRegisterEvent(SocketIoClient.EVENT_CHAT_ROOM, CHAT_ROOM_RECEIVER);
        liveClient.unRegisterEvent(SocketIoClient.CONNECT, connectReceiver);
        liveClient.unRegisterEvent(SocketIoClient.DISCONNECT, disConnectReceiver);
        synchronized (TYPING_AND_ACTIVE_USERS_LOCK) {
            activeUsers.clear();
            typing.clear();
        }
        synchronized (PEERS_IN_CHATROOM) {
            PEERS_IN_CHATROOM.clear();
        }
        liveClient.close();
    }

    /**
     * gives the {@link LiveCenter} a hint that this user is now active to the user
     * at our end here and that {@link LiveCenter} should monitor this user for typing events, etc.This is
     * different from the user been online this may be called after a call to {@link #start()}
     * and never before also one may not call this method after call to {@link #stop()}.
     * in all such situations,this method will fail silently
     *
     * @param userId the userId of the user to track
     */
    public static void trackUser(String userId) {
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
     * different from the user been offline this may be called after a call to {@link #start()}
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
            Config.setCurrentActivePeer(userId);
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
            Config.setCurrentActivePeer(null);
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
     * @throws IllegalStateException if the call is not made on the main thread
     */
    public static void notifyTyping(String userId) {
        ThreadUtils.ensureMain();
        synchronized (PEERS_IN_CHATROOM) {
            if (!PEERS_IN_CHATROOM.contains(userId)) {
                PLog.i(TAG, "peer not in chat room stopping dispatch");
                return;
            }
        }
        if (!isOnline(userId)) {
            synchronized (PEERS_IN_CHATROOM) {
                PEERS_IN_CHATROOM.remove(userId);
            }
            PLog.i(TAG, "peer offline, not dispatching typing event");
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
     * @throws IllegalStateException if the call is not made on the main thread
     */
    public static void notifyNotTyping(String userId) {
        ThreadUtils.ensureMain();
        synchronized (PEERS_IN_CHATROOM) {
            if (!PEERS_IN_CHATROOM.contains(userId)) {
                PLog.i(TAG, "peer not in chat room stopping dispatch");
                return;
            }
        }
        if (!isOnline(userId)) {
            synchronized (PEERS_IN_CHATROOM) {
                PEERS_IN_CHATROOM.remove(userId);
            }
            PLog.i(TAG, "peer offline, not dispatching typing event");
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
     * you may not pass anonymous instances.
     *
     * @param listener the listener to be registered, may not be {@code null}
     * @throws IllegalStateException    if the call is not made on the UI thread
     * @throws IllegalArgumentException if the listener to be registered is null
     */
    public static void registerTypingListener(LiveCenterListener listener) {
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
     */
    public static void unRegisterTypingListener(LiveCenterListener listener) {
        ThreadUtils.ensureMain();
        if (listener == null) {
            throw new IllegalArgumentException("listener cannot be null");
        }
        if (typingListener != null && typingListener.get() != null) {
            if (typingListener.get() != listener) {
                return;
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

    /**
     * @return set of all users with unread messages. if there is no user with unread messages an empty set is
     * returned
     */
    public static Set<String> getAllPeersWithUnreadMessages() {
        SharedPreferences data = getPreferences();
        Map<String, ?> all = data.getAll();
        if (all != null) {
            return Collections.unmodifiableSet(all.keySet());
        }
        return Collections.emptySet();
    }

    private static final Object progressLock = new Object();
    private static final Map<Object, Integer> tagProgressMap = new HashMap<>();


    /**
     * acquires a tag for publishing progress updates.
     * one must {code release} this tag when done with it.
     *
     * @param tag the tag to identify this task. may not be null
     * @throws PairappException if the tag is already in use
     * @see #releaseProgressTag(Object)
     */
    public static void acquireProgressTag(Object tag) throws PairappException {
        if (tag == null) {
            throw new IllegalArgumentException("tag == null");
        }
        synchronized (progressLock) {
            if (tagProgressMap.containsKey(tag)) {
                throw new PairappException("tag in use");
            }
            tagProgressMap.put(tag, 0);
            List<WeakReference<ProgressListener>> listeners = progressListeners.get(tag);
            if (listeners != null) {
                notifyListeners(listeners, tag, 0);
            }
            notifyListeners(allProgressListeners, tag, 0);
        }
    }

    /**
     * tells whether this tag is acquired or not. this method is not necessarily consistent with
     * {@link #acquireProgressTag(Object)} i.e the fact this returns true does not guarantee that
     * the tag will be available for acquisition since there could be concurrent contention for that same tag.
     *
     * @param tag the tag to check on
     * @return false if {@code tag == null} or the tag is not acquired other wise true
     * @see #acquireProgressTag(Object)
     */
    public static boolean isAcquired(Object tag) {
        synchronized (progressLock) {
            return tag != null && tagProgressMap.containsKey(tag);
        }
    }

    /**
     * release a tag acquired by{@link #acquireProgressTag(Object)}
     * it's safe to call this even if the tag is not acquired.
     *
     * @param tag the tag to be released. may not be null
     */
    public static void releaseProgressTag(Object tag) {
        if (tag == null) {
            throw new IllegalArgumentException("tag == null");
        }
        synchronized (progressLock) {
            if (tagProgressMap.remove(tag) != null) {
                List<WeakReference<ProgressListener>> listeners = progressListeners.get(tag);
                if (listeners != null) {
                    for (WeakReference<ProgressListener> weakReferenceListener : listeners) {
                        ProgressListener listener = weakReferenceListener.get();
                        if (listener != null) {
                            listener.doneOrCancelled(tag);
                        }
                    }
                }
                notifyListenersDoneOrCancelled(tag);
            }
        }
    }


    /**
     * updates the progress for the task identified by {@code tag}
     * on must ensure this method is always called on  a single thread in a life-time of a task.
     * this is to ensure that progress updates are received in the order they were published.
     * if the progress is less than the existing progress which may be normally caused by unordered publishing of progress
     * updates an exception is thrown. also the progress must not be less than 0.
     * <p/>
     * clients must ensure they have acquired this tag before they update its progress
     *
     * @param tag      the tag that identifies this task may not be null
     * @param progress the new progress. this may not be less than the existing progress or 0
     */
    public static void updateProgress(Object tag, int progress) {
        if (progress < 0) {
            throw new IllegalArgumentException("progress is negative");
        }
        if (tag == null) {
            throw new IllegalArgumentException("tag is null");
        }
        synchronized (progressLock) {
            if (tagProgressMap.containsKey(tag)) {

                int previous = tagProgressMap.get(tag);
                if (previous > 0 && previous > progress) {
                    throw new IllegalStateException("progress can only be incremented");
                }
                tagProgressMap.put(tag, progress);
                List<WeakReference<ProgressListener>> listeners = progressListeners.get(tag);
                if (listeners != null) {
                    notifyListeners(listeners, tag, progress);
                }
                notifyListeners(allProgressListeners, tag, progress);
            } else {
                throw new IllegalArgumentException("tag unknown");
            }
        }
    }


    private static void notifyListeners(Collection<WeakReference<ProgressListener>> listeners, Object tag, int progress) {
        for (WeakReference<ProgressListener> weakReferenceListener : listeners) {
            ProgressListener listener = weakReferenceListener.get();
            if (listener != null) {
                listener.onProgress(tag, progress);
            }
        }
    }

    private static void notifyListenersDoneOrCancelled(Object tag) {
        for (WeakReference<ProgressListener> weakReferenceListener : allProgressListeners) {
            ProgressListener listener = weakReferenceListener.get();
            if (listener != null) {
                listener.doneOrCancelled(tag);
            }
        }
    }

    /**
     * gets the progress for task identified by task
     *
     * @param tag the tag that identifies this task
     * @return the progress for this task or -1 if there is no task identified by this tag
     */
    public static int getProgress(Object tag) {
        if (tag == null) {
            throw new IllegalArgumentException("tag == null");
        }
        Integer progress;
        synchronized (progressLock) {
            progress = tagProgressMap.get(tag);
        }
        if (progress == null) {
            return -1;
        }
        return progress;
    }

    private static final Map<Object, List<WeakReference<ProgressListener>>> progressListeners = new HashMap<>();

    /**
     * registers a listener for the task identified by this tag. there need not be a task  identified by
     * this tag before one listen
     * <em>note that all listeners are kept internally as weak references so you may not pass anonymous instances</em>
     * <em>also listeners are not necessary called on the android main thread but rather on the thread  on which the progress was reported</em>
     *
     * @param tag      the tag to watch for, may not be null.
     * @param listener the listener may not be null
     */
    public static void listenForProgress(Object tag, ProgressListener listener) {
        if (tag == null || listener == null) {
            throw new IllegalArgumentException("null!");
        }

        synchronized (progressLock) {
            boolean alreadyRegistered = false;
            List<WeakReference<ProgressListener>> listeners = progressListeners.get(tag);
            if (listeners == null) {
                listeners = new ArrayList<>();
                progressListeners.put(tag, listeners);
            } else {
                for (WeakReference<ProgressListener> weakReference : listeners) {
                    final ProgressListener listener1 = weakReference.get();
                    if (listener1 == listener) {
                        alreadyRegistered = true;
                        break;
                    }
                }
            }
            if (!alreadyRegistered) {
                listeners.add(new WeakReference<>(listener));
            } else {
                PLog.w(TAG, "listener already registered");
            }
            notifyListener(tag, listener);
        }
    }

    private static void notifyListener(Object tag, ProgressListener listener) {
        Integer progress = tagProgressMap.get(tag);
        if (progress != null) {
            listener.onProgress(tag, progress);
        }
    }

    private static Set<WeakReference<ProgressListener>> allProgressListeners = new HashSet<>();

    /**
     * registers a listener for all events(progress) one must {@link #stopListeningForAllProgress(ProgressListener)}
     * when they no more need to listen to events.
     * <p/>
     * note that listeners are kept internally as weak references so you may not pass anonymous instances
     *
     * @param listener the listener to be registered may not be null
     * @see {@link #listenForProgress(Object, ProgressListener)}
     */
    public static void listenForAllProgress(ProgressListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener == null!");
        }
        synchronized (progressLock) {
            boolean alreadyRegistered = false;
            for (WeakReference<ProgressListener> weakReference : allProgressListeners) {
                final ProgressListener listener1 = weakReference.get();
                if (listener1 == listener) {
                    alreadyRegistered = true;
                    break;
                }
            }
            if (!alreadyRegistered) {
                allProgressListeners.add(new WeakReference<>(listener));
            }
            for (Object o : tagProgressMap.keySet()) {
                Integer progress = tagProgressMap.get(o);
                if (progress != null) {
                    listener.onProgress(o, progress);
                }
            }
        }
    }

    /**
     * @see {@link #stopListeningForProgress(Object, ProgressListener)}
     */
    public static void stopListeningForAllProgress(ProgressListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener == null");
        }
        synchronized (progressLock) {
            for (WeakReference<ProgressListener> allProgressListener : allProgressListeners) {
                ProgressListener listener1 = allProgressListener.get();
                if (listener1 == listener) {
                    allProgressListeners.remove(allProgressListener);
                    return;
                }
            }
        }

        PLog.w(TAG, "listener unknown");
    }

    /**
     * stops listening for progress on tag.
     *
     * @param tag      the task identifier for the task to stop listening for progress.may not be null
     * @param listener listener to unregister may not be null
     */
    public static void stopListeningForProgress(Object tag, ProgressListener listener) {
        if (tag == null || listener == null) {
            throw new IllegalArgumentException("null!");
        }
        synchronized (progressLock) {
            List<WeakReference<ProgressListener>> listeners = progressListeners.get(tag);
            for (WeakReference<ProgressListener> progressListenerWeakReference : listeners) {
                ProgressListener pListener = progressListenerWeakReference.get();
                if (pListener != null) {
                    if (pListener == listener) {
                        listeners.remove(progressListenerWeakReference);
                        return;
                    }
                }
            }
            PLog.d(TAG, "listener unknown");
        }
    }

    /**
     * listener for progress changes for a given task
     */
    public interface ProgressListener {
        /**
         * called whenever a progress is published from a task that has the identifier, tag.
         * <em>note that this method is not necessary called on the main thread so if you need to touch ui elements you must
         * check the thread to ensure you are on the main thread
         * </em>
         *
         * @param tag      the tag identifier for the task reporting th progress
         * @param progress the progress been reported
         */
        void onProgress(Object tag, int progress);

        /**
         * notifies listeners that the task identified by this tag is complete or cancelled.
         * it's up to listeners to check whether the task was really complete or cancelled.
         *
         * @param tag tag that identifies the task in question
         */
        void doneOrCancelled(Object tag);
    }


    /**
     * an interface one must implement if one wants to be notified of typing events
     * you may not implement this interface using an anonymous inner class as the listener
     * is referenced internally as a weakReference. at any point in time, there must be at
     * most one listener.
     */
    public interface LiveCenterListener {
        void onTyping(String userId);

        void onStopTyping(String userId);

        void onUserStatusChanged(String userId, boolean isOnline);
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

        public WorkerThread() {
            super(TAG, WorkerThread.NORM_PRIORITY);
        }

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

        private void doNotifyLeftOrJoinChatRoom(String userId, boolean inChatRoom) {
            if (!isOnline(userId)) {
                return;
            }
            try {
                JSONObject object = new JSONObject();
                object.put(SocketIoClient.PROPERTY_FROM, UserManager.getMainUserId());
                object.put(SocketIoClient.PROPERTY_TO, userId);
                object.put(SocketIoClient.PROPERTY_IN_CHAT_ROOM, inChatRoom);
                liveClient.send(SocketIoClient.EVENT_CHAT_ROOM, object);
            } catch (JSONException e) {
                throw new RuntimeException(e.getCause());
            }
        }

        private void doNotifyTyping(String to, boolean isTyping) {
            if (!isOnline(to)) {
                PLog.d(TAG, "user offline, typing event not dispatched");
                return;
            }
            try {
                JSONObject object = new JSONObject();
                object.put(SocketIoClient.PROPERTY_TO, to);
                object.put(SocketIoClient.PROPERTY_FROM, UserManager.getMainUserId());
                object.put(SocketIoClient.PROPERTY_IS_TYPING, isTyping);
                liveClient.send(SocketIoClient.TYPING, object);
            } catch (JSONException e) {
                throw new RuntimeException(e.getCause());
            }
        }

        private void doTrackUser(String userId) {
            try {
                JSONObject object = new JSONObject();
                object.put(SocketIoClient.PROPERTY_TO, userId);
                liveClient.send(SocketIoClient.TRACK_USER, object);
            } catch (JSONException e) {
                throw new RuntimeException(e.getCause());
            }
        }

        private void stopTrackingUser(String userId) {
            try {
                JSONObject object = new JSONObject();
                object.put(SocketIoClient.PROPERTY_TO, userId);
                liveClient.send(SocketIoClient.UN_TRACK_USER, object);
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
