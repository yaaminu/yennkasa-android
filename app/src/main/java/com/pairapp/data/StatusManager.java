package com.pairapp.data;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.pairapp.messenger.MessagePacker;
import com.pairapp.net.sockets.Sendable;
import com.pairapp.net.sockets.Sender;
import com.pairapp.util.Event;
import com.pairapp.util.EventBus;
import com.pairapp.util.GenericUtils;
import com.pairapp.util.SimpleDateUtil;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import rx.Observable;
import rx.functions.Action1;

/**
 * @author aminu on 6/29/2016.
 */
public class StatusManager {

    public static final String ON_USER_ONLINE = "onUserOnline";
    public static final String ON_USER_OFFLINE = "onUserOffline";
    public static final String ON_USER_STOP_TYPING = "onUserStopTyping";
    public static final String ON_USER_TYPING = "onUserTyping";
    public static final String MONITORTYPING_COLLAPSE_KEY = "monitortyping";
    public static final String CURRENT_USER_STATUS_COLLAPSE_KEY = "currentUserStatus";
    public static final int WAIT_MILLIS_TYPING_ANNOUNCEMENT = 0;
    public static final int WAIT_MILLIS_STATUS_ANNOUNCMENT = 1000;
    public static final int WAIT_MILLIS_MONITOR_USER = 2000;
    private volatile boolean isCurrentUserOnline = false;
    private volatile String typingWith;

    private Sender sender;
    private MessagePacker encoder;
    private final EventBus broadcastBus;

    private StatusManager(@NonNull Sender sender, @NonNull MessagePacker encoder, @NonNull EventBus broadcastBus) {
        this.sender = sender;
        this.encoder = encoder;
        this.broadcastBus = broadcastBus;
    }

    public static StatusManager create(@NonNull Sender sender, @NonNull MessagePacker encoder, @NonNull EventBus broadcastBus) {
        GenericUtils.ensureNotNull(sender, encoder, broadcastBus);
        return new StatusManager(sender, encoder, broadcastBus);
    }

    public synchronized void announceStatusChange(boolean online) {
        if (this.isCurrentUserOnline == online) return; //bounce duplicate announcements
        isCurrentUserOnline = online;
        sender.sendMessage(createSendable(CURRENT_USER_STATUS_COLLAPSE_KEY, encoder.createStatusMessage(isCurrentUserOnline), WAIT_MILLIS_STATUS_ANNOUNCMENT));
    }

    public synchronized void announceStartTyping(@NonNull String userId) {
        GenericUtils.ensureNotEmpty(userId);
        typingWith = userId;
        if (onlineSet.contains(userId) || userId.split(":").length > 1 /*groups*/) {
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

    public synchronized void announceStopTyping(@NonNull String userId) {
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

    public synchronized void handleStatusAnnouncement(@NonNull String userId, boolean isOnline) {
        GenericUtils.ensureNotEmpty(userId);
        if (isOnline) {
            if (onlineSet.add(userId) && userId.equals(typingWith)) {
                announceStartTyping(typingWith);//notify the client that we are writing to it
            }
        } else {
            onlineSet.remove(userId);
            typingSet.remove(userId);
        }
        broadcastBus.post(Event.create(isOnline ? ON_USER_ONLINE : ON_USER_OFFLINE, null, userId));
    }

    public synchronized void handleTypingAnnouncement(@NonNull final String userId, boolean isTyping) {
        GenericUtils.ensureNotEmpty(userId);
        if (isTyping) {
            typingSet.put(userId, System.currentTimeMillis());
            String userIdPart = userId.split(":")[0]; //if the typing is to a group
            if (!isOnline(userIdPart)) {
                onlineSet.add(userIdPart);
            }
        } else {
            typingSet.remove(userId);
        }
        broadcastBus.post(Event.create(isTyping ? ON_USER_TYPING : ON_USER_STOP_TYPING, null, userId));
        if (isTyping) {
            Observable.timer(30, TimeUnit.SECONDS).subscribe(new Action1<Long>() {
                @Override
                public void call(Long aLong) {
                    synchronized (StatusManager.this) {
                        Long then = typingSet.get(userId);
                        if (then != null && System.currentTimeMillis() - then > 30000/*30 seconds*/) {
                            handleTypingAnnouncement(userId, false);
                        }
                    }
                }
            });
        }
    }

    public void startMonitoringUser(@NonNull String userId) {
        sender.sendMessage(createSendable(userId + MONITORTYPING_COLLAPSE_KEY, encoder.createMonitorMessage(userId, true), WAIT_MILLIS_MONITOR_USER));
        broadcastBus.postSticky(Event.createSticky(isTypingToUs(userId) ? ON_USER_TYPING : isOnline(userId) ? ON_USER_ONLINE : ON_USER_OFFLINE, null, userId));
    }

    public void stopMonitoringUser(@NonNull String userId) {
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
