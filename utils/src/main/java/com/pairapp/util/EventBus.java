package com.pairapp.util;

import android.support.annotation.IntDef;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * author Null-Pointer on 1/13/2016.
 */
public class EventBus {

    private static final String TAG = EventBus.class.getSimpleName();
    private Map<Object, List<WeakReference<EventsListener>>> EVENT_LISTENERS;
    private Map<Object, Event> stickyEvents;
    private final Lock LOCK;

    public EventBus() {
        this(new ReentrantLock());
    }

    public EventBus(Lock lock) {
        GenericUtils.ensureNotNull(lock);
        this.LOCK = lock;
    }

    public boolean postSticky(Event event) {
        GenericUtils.ensureNotNull(event);
        PLog.d(TAG, "received new sticky event %s", event.tag.toString());
        LOCK.lock();
        try {
            if (stickyEvents == null) {
                stickyEvents = new HashMap<>();
            }
            stickyEvents.put(event.tag, event);
            if (EVENT_LISTENERS == null) {
                PLog.d(TAG, "no listener available for event");
                return false;
            }
            return notifyListeners(EVENT_LISTENERS.get(event.tag), event);
        } finally {
            LOCK.unlock();
        }
    }

    public boolean post(Event event) {
        GenericUtils.ensureNotNull(event);
        PLog.d(TAG, "received new event %s", event.tag.toString());
        LOCK.lock();
        try {
            if (EVENT_LISTENERS == null) {
                PLog.d(TAG, "no listener available for event");
                return false;
            }
            return notifyListeners(EVENT_LISTENERS.get(event.tag), event);
        } finally {
            LOCK.unlock();
        }
    }

    private boolean notifyListeners(Collection<WeakReference<EventsListener>> listeners, Event event) {
        boolean hadListener = false;
        if (listeners == null || listeners.isEmpty()) {
            PLog.d(TAG, "no listener available for event %s", event.tag);
        } else {
            for (WeakReference<EventsListener> weakReferenceListener : listeners) {
                EventsListener listener = weakReferenceListener.get();
                if (listener != null) {
                    hadListener = true;
                    notifyListener(listener, event);
                }
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
            final List<WeakReference<EventsListener>> weakReferences = EVENT_LISTENERS.get(event.tag);
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

    public static class Event {
        public final Object tag;
        public final Exception error;
        public final Object data;

        public Event(Object tag, Exception error, Object data) {
            GenericUtils.ensureNotNull(tag);
            this.tag = tag;
            this.error = error;
            this.data = data;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Event event = (Event) o;

            //noinspection SimplifiableIfStatement
            if (!tag.equals(event.tag)) return false;
            return error != null ? error.equals(event.error) : event.error == null &&
                    (data != null ? data.equals(event.data) : event.data == null);

        }

        @Override
        public int hashCode() {
            int result = tag.hashCode();
            result = 31 * result + (error != null ? error.hashCode() : 0);
            result = 31 * result + (data != null ? data.hashCode() : 0);
            return result;
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
        LOCK.lock();
        try {
            if (stickyEvents == null) {
                return;
            }
            Event toBeRemoved = stickyEvents.get(event.tag);
            if (toBeRemoved == event) {
                stickyEvents.remove(event.tag);
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
