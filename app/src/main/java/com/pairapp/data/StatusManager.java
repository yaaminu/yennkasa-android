package com.pairapp.data;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.pairapp.messenger.MessagePacker;
import com.pairapp.util.Event;
import com.pairapp.util.EventBus;
import com.pairapp.util.GenericUtils;

import java.util.HashSet;
import java.util.Set;

/**
 * @author aminu on 6/29/2016.
 */
public class StatusManager {

    public static final String ANNOUNCE_ONLINE = "announceOnline", ANNOUNCE_TYPING = "announceTyping";
    public static final String ON_USER_ONLINE = "onUserOnline";
    public static final String ON_USER_OFFLINE = "onUserOffline";
    public static final String ON_USER_STOP_TYPING = "onUserStopTyping";
    public static final String ON_USER_TYPING = "onUserTyping";
    private volatile boolean isCurrentUserOnline = false;
    private volatile String typingWith;

    private Sender sender;
    private MessagePacker encoder;
    private final EventBus broadcastBus;


    private StatusManager(String currentUser, EventBus bus) {
        GenericUtils.ensureNotEmpty(currentUser);
        GenericUtils.ensureNotNull(bus);
        try {
            //noinspection ResultOfMethodCallIgnored
            Long.parseLong(currentUser);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(e);
        }
        this.broadcastBus = bus;
    }

    private StatusManager(@NonNull Sender sender, @NonNull MessagePacker encoder, @NonNull EventBus broadcastBus) {
        this.sender = sender;
        this.encoder = encoder;
        this.broadcastBus = broadcastBus;
    }

    public static StatusManager create(@NonNull Sender sender, @NonNull MessagePacker encoder, @NonNull EventBus broadcastBus) {
        GenericUtils.ensureNotNull(sender, encoder, broadcastBus);
        return new StatusManager(sender, encoder, broadcastBus);
    }

    public static StatusManager create(@NonNull String currentUser, @NonNull EventBus bus) {
        return new StatusManager(currentUser, bus);
    }

    public void announceStatusChange(boolean online) {
        if (this.isCurrentUserOnline == online) return;
        isCurrentUserOnline = online;
        sender.sendMessage(encoder.createStatusMessage(isCurrentUserOnline));
    }

    public synchronized void announceStartTyping(@NonNull String userId) {
        GenericUtils.ensureNotEmpty(userId);
        typingWith = userId;
        sender.sendMessage(encoder.createTypingMessage(userId, false));
    }

    public synchronized void announceStopTyping(@NonNull String userId) {
        GenericUtils.ensureNotEmpty(userId);
        if (userId.equals(typingWith)) {
            typingWith = null;
        }
        //its hard to say this is a programmatic error. so we allow the masseage to pass through
        //even though we don't know whether this user is typing with "userId" or not.
        sender.sendMessage(encoder.createTypingMessage(userId, false));
    }

    @Nullable
    public synchronized String getCurrentUserTypingWith() {
        return typingWith;
    }

    private final Set<String> onlineSet = new HashSet<>(), typingSet = new HashSet<>();

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

    public synchronized void handleTypingAnnouncement(@NonNull String userId, boolean isTyping) {
        GenericUtils.ensureNotEmpty(userId);
        if (isTyping) {
            typingSet.add(userId);
            String userIdPart = userId.split(":")[0]; //if the typing is to a group
            if (!isOnline(userIdPart)) {
                onlineSet.add(userIdPart);
            }
        } else {
            typingSet.remove(userId);
        }
        broadcastBus.post(Event.create(isTyping ? ON_USER_TYPING : ON_USER_STOP_TYPING, null, userId));
    }

    public void startMonitoringUser(@NonNull String userId) {
        sender.sendMessage(encoder.createMonitorMessage(userId, true));
    }

    public void stopMonitoringUser(@NonNull String userId) {
        sender.sendMessage(encoder.createMonitorMessage(userId, false));
    }

    public synchronized boolean isOnline(@NonNull String userId) {
        return onlineSet.contains(userId);
    }

    public synchronized boolean isTypingToUs(@NonNull String userId) {
        return typingSet.contains(userId);
    }

    public synchronized boolean isTypingToGroup(@NonNull String userId, String groupId) {
        return typingSet.contains(userId + ":" + groupId);
    }

    public interface Sender {
        void sendMessage(byte[] payload);
    }
}
