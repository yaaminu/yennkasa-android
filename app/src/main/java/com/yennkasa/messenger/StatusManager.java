package com.yennkasa.messenger;

import android.os.Handler;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.yennkasa.net.sockets.Sendable;
import com.yennkasa.net.sockets.Sender;
import com.yennkasa.util.Config;
import com.yennkasa.util.Event;
import com.yennkasa.util.EventBus;
import com.yennkasa.util.GenericUtils;
import com.yennkasa.util.PLog;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.yennkasa.messenger.MessengerBus.CONNECTED;
import static com.yennkasa.messenger.MessengerBus.SOCKET_CONNECTION;

/**
 * @author aminu on 6/29/2016.
 */
public class StatusManager {
    private static final String TAG = "StatusManager";

    public static final String ON_USER_ONLINE = "onUserOnline";
    public static final String ON_USER_OFFLINE = "onUserOffline";
    public static final String ON_USER_STOP_TYPING = "onUserStopTyping";
    public static final String ON_USER_TYPING = "onUserTyping";
    public static final String MONITORTYPING_COLLAPSE_KEY = "monitortyping";
    public static final String CURRENT_USER_STATUS_COLLAPSE_KEY = "currentUserStatus";
    public static final int INACTIVITY_THRESHOLD = 60000; //60 seconds
    public static final long ONLINE_ANNOUNCEMENT_INTERVAL = 45000;
    private volatile boolean currentUserStatus = false;

    @Nullable
    private volatile String typingWith;

    @NonNull
    private final Sender sender;
    @NonNull
    private final MessagePacker encoder;
    @NonNull
    private final EventBus broadcastBus;
    @NonNull
    private final Handler handler;
    /*for testing*/ final EventBus.EventsListener listener = new EventBus.EventsListener() {
        @Override
        public int threadMode() {
            return EventBus.BACKGROUND;
        }

        @Override
        public void onEvent(EventBus yourBus, Event event) {
            PLog.d(TAG, "Received event %s", event);
            if (event.getTag().equals(MessengerBus.SOCKET_CONNECTION)) {
                synchronized (StatusManager.this) {
                    switch (((int) event.getData())) {
                        case MessengerBus.DISCONNECTED:
                            //we are offline
                            StatusManager.this.currentUserStatus = false;
                            Set<String> copy = Collections.unmodifiableMap(onlineSet).keySet(); //copy to avoid concurrent modification exceptions
                            for (String user : copy) {
                                handleStatusAnnouncement(user, false);
                            }
                            onlineSet.clear(); //clear just to be sure that everything is cleaned
                            //when a user is announced to be offline, he/she is automatically
                            //removed from the typing set too.
//                    for (String userId : typingSet.keySet()) {
//                        handleTypingAnnouncement(userId, false);
//                    }
//                    typingSet.clear();
                            break;
                        case CONNECTED:
                            if (Config.isAppOpen()) {
                                announceStatusChange(true);
                                String currentActivePeer = Config.getCurrentActivePeer();
                                if (currentActivePeer != null) {
                                    startMonitoringUser(currentActivePeer);
                                }
                            }
                            break;
                        default:
                            //do nothing;
                            break;
                    }
                }
            } else {
                throw new AssertionError("unknown event type");
            }
        }

        @Override
        public boolean sticky() {
            return false;
        }
    };

    private StatusManager(@NonNull Sender sender, @NonNull MessagePacker encoder, @NonNull EventBus broadcastBus) {
        this.sender = sender;
        this.encoder = encoder;
        this.broadcastBus = broadcastBus;
        this.handler = new Handler();
    }

    static StatusManager create(@NonNull Sender sender, @NonNull MessagePacker encoder, @NonNull EventBus broadcastBus) {
        GenericUtils.ensureNotNull(sender, encoder, broadcastBus);
        StatusManager statusManager = new StatusManager(sender, encoder, broadcastBus);
        statusManager.register();
        return statusManager;
    }

    private void register() {
        broadcastBus
                .register(listener, SOCKET_CONNECTION);
    }

    synchronized void announceStatusChange(boolean currentUserStatus) {
        this.currentUserStatus = currentUserStatus;
        sender.sendMessage(createSendable(CURRENT_USER_STATUS_COLLAPSE_KEY, encoder.createStatusMessage(this.currentUserStatus)));
        if (currentUserStatus) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() { //for every ONLINE_ANNOUNCEMENT_INTERVAL, repeat the process if we havn't gone offline
                    if (StatusManager.this.currentUserStatus) {
                        announceStatusChange(true);
                    }
                }
            }, ONLINE_ANNOUNCEMENT_INTERVAL);
        }
    }

    synchronized void announceStartTyping(@NonNull String userId) {
        GenericUtils.ensureNotEmpty(userId);
        typingWith = userId;
        if (onlineSet.containsKey(userId) || userId.split(":").length > 1 /*groups*/) { //only notify online users
            sender.sendMessage(createSendable(userId + MONITORTYPING_COLLAPSE_KEY, encoder.createTypingMessage(userId, true)));
        }
    }

    private Sendable createSendable(String collapseKey, byte[] data) {
        return new Sendable.Builder()
                .collapseKey(collapseKey)
                .startProcessingAt(System.currentTimeMillis())
                .maxRetries(1)
                .surviveRestarts(false)
                .validUntil(System.currentTimeMillis() + 30000)
                .data(sender.bytesToString(data))
                .build();
    }

    synchronized void announceStopTyping(@NonNull String userId) {
        GenericUtils.ensureNotEmpty(userId);
        if (userId.equals(typingWith)) {
            typingWith = null;
        }
        if (onlineSet.containsKey(userId) || userId.split(":").length > 1 /*groups*/) {
            //its hard to say this is a programmatic error. so we allow the masseage to pass through
            //even though we don't know whether this user is typing with "userId" or not.
            sender.sendMessage(createSendable(userId + MONITORTYPING_COLLAPSE_KEY, encoder.createTypingMessage(userId, false)));
        }
    }

    @Nullable
    public synchronized String getCurrentUserTypingWith() {
        return typingWith;
    }

    private final Map<String, Long> onlineSet = new HashMap<>(4);
    private final Map<String, Long> typingSet = new HashMap<>(4);

    synchronized void handleStatusAnnouncement(@NonNull final String userId, boolean isOnline) {
        GenericUtils.ensureNotEmpty(userId);
        if (isOnline) {
            updateAndMarkAsOnline(userId);
            if (userId.equals(typingWith)) {
                //noinspection ConstantConditions //typingWith cannot be null
                announceStartTyping(typingWith);//notify the client that we are writing to it
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (StatusManager.this) {
                            Long then = typingSet.get(userId);
                            if (then != null && SystemClock.uptimeMillis() - then >= INACTIVITY_THRESHOLD) {
                                handleTypingAnnouncement(userId, false);
                            }
                        }
                    }
                }, INACTIVITY_THRESHOLD);
            }
        } else {
            if (typingSet.containsKey(userId)) {
                typingSet.remove(userId);
                broadcastBus.post(Event.create(ON_USER_STOP_TYPING, null, userId));
            }
            onlineSet.remove(userId);
            broadcastBus.post(Event.create(ON_USER_OFFLINE, null, userId));
        }
    }

    private synchronized void updateAndMarkAsOnline(@NonNull final String userId) {
        PLog.d(TAG, "user %s is now online", userId);
        onlineSet.put(userId, SystemClock.uptimeMillis());
        broadcastBus.post(Event.create(ON_USER_ONLINE, null, userId));
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                synchronized (StatusManager.this) {
                    Long lastAnnounced = onlineSet.get(userId);
                    if (lastAnnounced == null || SystemClock.uptimeMillis() - lastAnnounced >= INACTIVITY_THRESHOLD) {
                        handleStatusAnnouncement(userId, false);
                    }
                }
            }
        }, INACTIVITY_THRESHOLD);
    }


    synchronized void handleTypingAnnouncement(@NonNull final String userId, boolean isTyping) {
        GenericUtils.ensureNotEmpty(userId);
        if (isTyping) {
            typingSet.put(userId, SystemClock.uptimeMillis());
            String userIdPart = userId.split(":")[0]; //if the typing is to a group
            if (!isOnline(userIdPart)) {
                updateAndMarkAsOnline(userId);
            }
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    synchronized (StatusManager.this) {
                        Long then = typingSet.get(userId);
                        if (then != null && SystemClock.uptimeMillis() - then >= INACTIVITY_THRESHOLD) {
                            handleTypingAnnouncement(userId, false);
                        }
                    }
                }
            }, INACTIVITY_THRESHOLD);
            broadcastBus.post(Event.create(ON_USER_TYPING, null, userId));
        } else {
            typingSet.remove(userId);
            broadcastBus.post(Event.create(ON_USER_STOP_TYPING, null, userId));
        }
    }

    void startMonitoringUser(@NonNull String userId) {
        sender.sendMessage(createSendable(userId + MONITORTYPING_COLLAPSE_KEY, encoder.createMonitorMessage(userId, true)));
        String tag;
        if (isTypingToUs(userId)) {
            tag = ON_USER_TYPING;
        } else if (isOnline(userId)) {
            tag = ON_USER_ONLINE;
        } else {
            tag = ON_USER_OFFLINE;
        }
        broadcastBus.postSticky(Event.createSticky(tag, null, userId));
    }

    void stopMonitoringUser(@NonNull String userId) {
        sender.sendMessage(createSendable(userId + MONITORTYPING_COLLAPSE_KEY, encoder.createMonitorMessage(userId, false)));
    }

    public boolean isOnline(@NonNull String userId) {
        return onlineSet.containsKey(userId) && SystemClock.uptimeMillis() - onlineSet.get(userId) < INACTIVITY_THRESHOLD;
    }

    public boolean isTypingToUs(@NonNull String userId) {
        return typingSet.containsKey(userId) && SystemClock.uptimeMillis() - typingSet.get(userId) < INACTIVITY_THRESHOLD;
    }

    public boolean isTypingToGroup(@NonNull String userId, String groupId) {
        return typingSet.containsKey(userId + ":" + groupId);
    }

}
