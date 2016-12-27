package com.yennkasa.messenger;

import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.yennkasa.net.sockets.Sendable;
import com.yennkasa.net.sockets.Sender;
import com.yennkasa.util.Event;
import com.yennkasa.util.EventBus;
import com.yennkasa.util.GenericUtils;
import com.yennkasa.util.PLog;
import com.yennkasa.util.SimpleDateUtil;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
    public static final int WAIT_MILLIS_TYPING_ANNOUNCEMENT = 0;
    public static final int WAIT_MILLIS_STATUS_ANNOUNCMENT = 0;
    public static final int WAIT_MILLIS_MONITOR_USER = 0;
    public static final int INACTIVITY_THRESHOLD = 35000; //35 seconds
    private static final long ONLINE_ANNOUNCEMENT_INTERVAL = 30000;//30 seconds
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

    private StatusManager(@NonNull Sender sender, @NonNull MessagePacker encoder, @NonNull EventBus broadcastBus) {
        this.sender = sender;
        this.encoder = encoder;
        this.broadcastBus = broadcastBus;
        this.handler = new Handler();
    }

    static StatusManager create(@NonNull Sender sender, @NonNull MessagePacker encoder, @NonNull EventBus broadcastBus) {
        GenericUtils.ensureNotNull(sender, encoder, broadcastBus);
        return new StatusManager(sender, encoder, broadcastBus);
    }

    synchronized void announceStatusChange(boolean currentUserStatus) {
        if (this.currentUserStatus == currentUserStatus) return; //bounce duplicate announcements
        this.currentUserStatus = currentUserStatus;
        sender.sendMessage(createSendable(CURRENT_USER_STATUS_COLLAPSE_KEY, encoder.createStatusMessage(this.currentUserStatus), WAIT_MILLIS_STATUS_ANNOUNCMENT));
    }

    synchronized void announceStartTyping(@NonNull String userId) {
        GenericUtils.ensureNotEmpty(userId);
        typingWith = userId;
        if (onlineSet.contains(userId) || userId.split(":").length > 1 /*groups*/) { //only notify online users
            sender.sendMessage(createSendable(userId + MONITORTYPING_COLLAPSE_KEY, encoder.createTypingMessage(userId, true), WAIT_MILLIS_TYPING_ANNOUNCEMENT));
        }
    }

    private Sendable createSendable(String collapseKey, byte[] data, int waitMillis) {
        return new Sendable.Builder()
                .collapseKey(collapseKey)
                .startProcessingAt(System.currentTimeMillis() + waitMillis)
                .maxRetries(Sendable.RETRY_FOREVER)
                .surviveRestarts(false)
                .validUntil(System.currentTimeMillis() + SimpleDateUtil.ONE_HOUR)
                .data(sender.bytesToString(data))
                .build();
    }

    synchronized void announceStopTyping(@NonNull String userId) {
        GenericUtils.ensureNotEmpty(userId);
        if (userId.equals(typingWith)) {
            typingWith = null;
        }
        if (onlineSet.contains(userId) || userId.split(":").length > 1 /*groups*/) {
            //its hard to say this is a programmatic error. so we allow the masseage to pass through
            //even though we don't know whether this user is typing with "userId" or not.
            sender.sendMessage(createSendable(userId + MONITORTYPING_COLLAPSE_KEY, encoder.createTypingMessage(userId, false), WAIT_MILLIS_TYPING_ANNOUNCEMENT));
        }
    }

    @Nullable
    public synchronized String getCurrentUserTypingWith() {
        return typingWith;
    }

    private final Set<String> onlineSet = new HashSet<>(4);
    private final Map<String, Long> typingSet = new HashMap<>(4);

    synchronized void handleStatusAnnouncement(@NonNull final String userId, boolean isOnline) {
        GenericUtils.ensureNotEmpty(userId);
        if (isOnline && !isOnline(userId)) {
            updateAndMarkAsOnline(userId);
            if (userId.equals(typingWith)) {
                //noinspection ConstantConditions //typingWith cannot be null
                announceStartTyping(typingWith);//notify the client that we are writing to it
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (StatusManager.this) {
                            Long then = typingSet.get(userId);
                            if (then != null && System.currentTimeMillis() - then >= INACTIVITY_THRESHOLD) {
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

    private synchronized void updateAndMarkAsOnline(@NonNull String userId) {
        if (onlineSet.add(userId)) {
            PLog.d(TAG, "announcing that %s os online", userId);
            broadcastBus.post(Event.create(ON_USER_ONLINE, null, userId));
        } else {
            PLog.d(TAG, "%s is already online not publishing", userId);
        }
    }


    synchronized void handleTypingAnnouncement(@NonNull final String userId, boolean isTyping) {
        GenericUtils.ensureNotEmpty(userId);
        if (isTyping) {
            typingSet.put(userId, System.currentTimeMillis());
            String userIdPart = userId.split(":")[0]; //if the typing is to a group
            if (!isOnline(userIdPart)) {
                updateAndMarkAsOnline(userId);
            }
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    synchronized (StatusManager.this) {
                        Long then = typingSet.get(userId);
                        if (then != null && System.currentTimeMillis() - then >= INACTIVITY_THRESHOLD) {
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
        sender.sendMessage(createSendable(userId + MONITORTYPING_COLLAPSE_KEY, encoder.createMonitorMessage(userId, true), WAIT_MILLIS_MONITOR_USER));
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
        sender.sendMessage(createSendable(userId + MONITORTYPING_COLLAPSE_KEY, encoder.createMonitorMessage(userId, false), WAIT_MILLIS_MONITOR_USER));
    }

    public synchronized boolean isOnline(@NonNull String userId) {
        return onlineSet.contains(userId);
    }

    public synchronized boolean isTypingToUs(@NonNull String userId) {
        return typingSet.containsKey(userId);
    }

    public synchronized boolean isTypingToGroup(@NonNull String userId, String groupId) {
        return typingSet.containsKey(userId + ":" + groupId);
    }

}
