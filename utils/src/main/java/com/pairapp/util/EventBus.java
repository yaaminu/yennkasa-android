package com.pairapp.util;

import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * author Null-Pointer on 1/13/2016.
 */
public class EventBus {

    private static final String TAG = EventBus.class.getSimpleName();
    private Map<Object, List<WeakReference<EventsListener>>> EVENT_LISTENERS;
    private Map<Object, Event> stickyEvents;
    private static final Map<Class<?>, EventBus> buses = new ConcurrentHashMap<>(4);
    private final Lock LOCK;


    static {
        buses.put(DEFAULT_CLAZZ.class, new EventBus());
    }

    @Nullable
    public static EventBus getBus(@NonNull Class<?> clazz) {
        GenericUtils.ensureNotNull(clazz);
        return buses.get(clazz);
    }

    public static EventBus getBusOrCreate(Class<?> clazz) {
        return getBusOrCreate(clazz, new ReentrantLock());
    }

    @NonNull
    public static EventBus getBusOrCreate(@NonNull Class<?> clazz, @NonNull Lock customLock) {
        GenericUtils.ensureNotNull(clazz, customLock);
        EventBus bus = buses.get(clazz);
        if (bus == null) {
            synchronized (buses) {
                bus = buses.get(clazz);
                if (bus == null) {
                    bus = new EventBus(customLock);
                    buses.put(clazz, bus);
                }
            }
        }
        return bus;
    }

    //used as the key to the bus map to avoid collisions
    private static final class DEFAULT_CLAZZ {

    }

    public static EventBus getDefault() {
        return getBus(DEFAULT_CLAZZ.class);
    }

    public static void resetBus(Class<?> clazz) {
        if (clazz == null) return;
        if (DEFAULT_CLAZZ.class.equals(clazz)) { //impossible but safety first!!!!!
            throw new AssertionError("attempt to destroy the default bus");
        }
        synchronized (buses) {
            EventBus toDestroy = buses.remove(clazz);
            if (toDestroy != null) {
                try {
                    toDestroy.LOCK.lock();
                    if (toDestroy.EVENT_LISTENERS != null) {
                        toDestroy.EVENT_LISTENERS.clear();
                        toDestroy.EVENT_LISTENERS = null;
                    }
                    if (toDestroy.stickyEvents != null) {
                        toDestroy.stickyEvents.clear();
                        toDestroy.stickyEvents = null;
                    }
                } finally {
                    toDestroy.LOCK.unlock();
                }
            }
        }
    }

    private EventBus() {
        this(new ReentrantLock());
    }

    private EventBus(Lock lock) {
        GenericUtils.ensureNotNull(lock);
        this.LOCK = lock;
    }

    public boolean postSticky(Event event) {
        GenericUtils.ensureNotNull(event);
        if (event.isRecycled()) {
            throw new IllegalArgumentException("event is recycled");
        }
        if (!event.isSticky()) {
            throw new IllegalArgumentException("event is not sticky");
        }
        PLog.d(TAG, "received new sticky event %s", event.getTag().toString());
        LOCK.lock();
        try {
            if (stickyEvents == null) {
                stickyEvents = new HashMap<>();
            }
            stickyEvents.put(event.getTag(), event);
            if (EVENT_LISTENERS == null) {
                PLog.d(TAG, "no listener available for event");
                return false;
            }
            return notifyListeners(EVENT_LISTENERS.get(event.getTag()), event);
        } finally {
            LOCK.unlock();
        }
    }

    public boolean post(Event event) {
        GenericUtils.ensureNotNull(event);
        if (event.isRecycled()) {
            throw new IllegalArgumentException("event is recycled");
        }
        if (event.isSticky()) {
            throw new IllegalArgumentException("event must not be sticky");
        }
        PLog.d(TAG, "received new event %s", event.getTag().toString());
        LOCK.lock();
        try {
            if (EVENT_LISTENERS == null) {
                PLog.d(TAG, "no listener available for event");
                return false;
            }
            return notifyListeners(EVENT_LISTENERS.get(event.getTag()), event);
        } finally {
            LOCK.unlock();
        }
    }

    private boolean notifyListeners(Collection<WeakReference<EventsListener>> listeners, Event event) {
        boolean hadListener = false;
        if (listeners == null || listeners.isEmpty()) {
            PLog.d(TAG, "no listener available for event %s", event.getTag());
        } else {
            List<EventsListener> tmp = new ArrayList<>(listeners.size());
            for (WeakReference<EventsListener> weakReferenceListener : listeners) {
                EventsListener listener = weakReferenceListener.get();
                if (listener != null) {
                    tmp.add(listener);
                }
            }
            if (!tmp.isEmpty()) {
                event.setListenerCount(tmp.size());
                for (EventsListener eventsListener : tmp) {
                    notifyListener(eventsListener, event);
                }
                hadListener = true;
            }
        }
        return hadListener;
    }

    public void register(Object tag, EventsListener listener) {
        GenericUtils.ensureNotNull(tag, listener);
        PLog.d(TAG, "registering listener %s", listener);
        LOCK.lock();
        try {
            if (EVENT_LISTENERS == null) {
                EVENT_LISTENERS = new HashMap<>();
            }
            doRegister(tag, listener);
        } finally {
            LOCK.unlock();
        }
    }

    public void register(EventsListener listener, Object tag, Object... othertags) {
        GenericUtils.ensureNotNull(listener, tag);
        PLog.d(TAG, "registering listener %s", listener);
        LOCK.lock();
        try {
            if (EVENT_LISTENERS == null) {
                EVENT_LISTENERS = new HashMap<>();
            }
            doRegister(tag, listener);
            if (othertags != null) {
                //noinspection ForLoopReplaceableByForEach
                for (int i = 0; i < othertags.length; i++) {
                    final Object othertag = othertags[i];
                    GenericUtils.ensureNotNull(othertag);
                    doRegister(othertag, listener);
                }
            }
        } finally {
            LOCK.unlock();
        }
    }

    private void doRegister(Object tag, EventsListener listener) {
        boolean alreadyRegistered = false;
        List<WeakReference<EventsListener>> listeners = EVENT_LISTENERS.get(tag);
        if (listeners == null) {
            listeners = new ArrayList<>();
            EVENT_LISTENERS.put(tag, listeners);
        } else {
            for (WeakReference<EventsListener> weakReference : listeners) {
                final EventsListener listener1 = weakReference.get();
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
        if (listener.sticky() && stickyEvents != null) {
            Event event = stickyEvents.get(tag);
            if (event != null) {
                notifyListener(listener, event);
            }
        }
    }

    public void unregister(Object tag, EventsListener listener) {
        GenericUtils.ensureNotNull(tag, listener);
        PLog.d(TAG, "unregistering listener %s", listener);
        LOCK.lock();
        try {
            if (EVENT_LISTENERS == null) {
                return;
            }
            List<WeakReference<EventsListener>> listeners = EVENT_LISTENERS.get(tag);
            if (listeners != null && !listeners.isEmpty()) {
                for (WeakReference<EventsListener> progressListenerWeakReference : listeners) {
                    EventsListener pListener = progressListenerWeakReference.get();
                    if (pListener == null || pListener == listener) {
                        listeners.remove(progressListenerWeakReference);
                        if (listeners.isEmpty()) {
                            PLog.d(TAG, "last listener for %s unregistered", tag);
                            EVENT_LISTENERS.remove(tag);
                            if (EVENT_LISTENERS.isEmpty()) {
                                PLog.d(TAG, "no listener on event bus");
                                EVENT_LISTENERS = null;
                            }
                        }
                        return;
                    }
                }
            }
            PLog.d(TAG, "listener %s unknown", listener);
        } finally {
            LOCK.unlock();
        }
    }

    private void notifyListener(final EventsListener listener, final Event event) {
        switch (listener.threadMode()) {
            case ANY:
                //fall through
            case BACKGROUND:
                if (!ThreadUtils.isMainThread()) { //don't call the listener on the main thread
                    listener.onEvent(this, event);
                    break;
                }
                //fall through
            case MAIN:
                Runnable runnable = new Runnable() {
                    @Override
                    public void run() {
                        listener.onEvent(EventBus.this, event);
                    }
                };
                if (listener.threadMode() == BACKGROUND || listener.threadMode() == ANY) {
                    TaskManager.executeNow(runnable, false);
                } else {
                    TaskManager.executeOnMainThread(runnable);
                }
                break;
            default:
                throw new AssertionError();
        }
    }

    public boolean hasListeners(Event event) {
        GenericUtils.ensureNotNull(event);
        LOCK.lock();
        try {
            if (EVENT_LISTENERS == null) {
                return false;
            }
            final List<WeakReference<EventsListener>> weakReferences = EVENT_LISTENERS.get(event.getTag());
            if (weakReferences == null || weakReferences.isEmpty()) {
                return false;
            }
            boolean hasListeners = false;
            List<WeakReference<EventsListener>> toBeRemoved = new ArrayList<>();
            for (WeakReference<EventsListener> weakReference : weakReferences) {
                if (weakReference.get() != null) {
                    hasListeners = true;
                } else {
                    toBeRemoved.add(weakReference);
                }
            }
            weakReferences.removeAll(toBeRemoved);
            return hasListeners;
        } finally {
            LOCK.unlock();
        }
    }

    public Event getStickyEvent(Object tag) {
        LOCK.lock();
        try {
            if (stickyEvents == null) {
                return null;
            }
            return stickyEvents.get(tag);
        } finally {
            LOCK.unlock();
        }
    }

    public void removeStickyEvent(Event event) {
        if (event == null) {
            return;
        }
        if (!event.isSticky()) {
            return;
        }
        LOCK.lock();
        try {
            if (stickyEvents == null) {
                return;
            }
            Event toBeRemoved = stickyEvents.get(event.getTag());
            if (toBeRemoved == event) {
                stickyEvents.remove(event.getTag());
                event.recycleSticky();
            }
            if (stickyEvents.isEmpty()) {
                stickyEvents = null;
            }
        } finally {
            LOCK.unlock();
        }
    }

    @IntDef({MAIN, ANY, BACKGROUND})
    @interface ThreadMode {
    }

    public static final int MAIN = 0x1,
            ANY = 0x2,
            BACKGROUND = 0x3;

    public interface EventsListener {
        @ThreadMode
        int threadMode();

        void onEvent(EventBus yourBus, Event event);

        boolean sticky();
    }
}
