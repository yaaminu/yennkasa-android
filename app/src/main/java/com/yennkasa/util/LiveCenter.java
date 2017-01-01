package com.yennkasa.util;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.yennkasa.BuildConfig;
import com.yennkasa.Errors.YennkasaException;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * This class is the heart of all real-time events of the program.
 * it tracks what active friends are doing. like typing,online,onPhone, etc
 *
 * @author Null-Pointer on 9/9/2015.
 */
public class LiveCenter {
    private static final String TAG = "livecenter";

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
     * @throws YennkasaException if the tag is already in use
     * @see #releaseProgressTag(Object)
     */
    public static void acquireProgressTag(Object tag) throws YennkasaException {
        if (tag == null) {
            throw new IllegalArgumentException("tag == null");
        }
        synchronized (progressLock) {
            if (tagProgressMap.containsKey(tag)) {
                throw new YennkasaException("tag in use");
            }
            tagProgressMap.put(tag, 0);
            notifyListeners(allProgressListeners, tag, 0);
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
                if (progress == 0 || progress - previous >= 1) {
                    notifyListeners(allProgressListeners, tag, progress);
                } else {
                    PLog.d(TAG, "Not notifying listeners of progress");
                }
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


    private static Set<WeakReference<ProgressListener>> allProgressListeners = new HashSet<>();

    /**
     * registers a listener for all events(progress) one must {@link #stopListeningForAllProgress(ProgressListener)}
     * when they no more need to listen to events.
     * <p/>
     * note that listeners are kept internally as weak references so you may not pass anonymous instances
     *
     * @param listener the listener to be registered may not be null
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
}
