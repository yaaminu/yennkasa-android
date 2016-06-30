package com.pairapp.util;

import java.util.Stack;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author aminu on 6/30/2016.
 */
@SuppressWarnings("ThrowableResultOfMethodCallIgnored")
public class Event {
    private Object tag;
    private Exception error;
    private Object data;
    private volatile boolean isRecycled;
    private static final Stack<Event> objectPool = new Stack<>();
    private volatile boolean isSticky;
    private AtomicInteger listenerCount = new AtomicInteger(0);

    private Event(Object tag, Exception error, Object data, boolean isSticky) {
        this.tag = tag;
        this.error = error;
        this.data = data;
        isRecycled = false;
        this.isSticky = isSticky;
    }

    public synchronized static Event create(Object tag) {
        return create(tag, null);
    }

    public synchronized static Event createSticky(Object tag) {
        return createSticky(tag, null);
    }

    public synchronized static Event create(Object tag, Exception error) {
        return create(tag, error, null);
    }

    public synchronized static Event createSticky(Object tag, Exception error) {
        return createSticky(tag, error, null);
    }

    public synchronized static Event create(Object tag, Exception error, Object data) {
        return doCreate(tag, error, data, false);
    }

    public synchronized static Event createSticky(Object tag, Exception error, Object data) {
        return doCreate(tag, error, data, true);
    }

    public synchronized static Event doCreate(Object tag, Exception error, Object data, boolean isSticky) {
        GenericUtils.ensureNotNull(tag);
        Event event;
        if (!objectPool.isEmpty()) {
            event = objectPool.pop();
            event.tag = tag;
            event.isSticky = isSticky;
            event.error = error;
            event.data = data;
            event.isRecycled = false;
        } else {
            event = new Event(tag, error, data, isSticky);
        }
        return event;
    }

    public void recycle() {
        if (!isRecycled() && !isSticky() && listenerCount.decrementAndGet() <= 0) {
            synchronized (Event.class) {
                if (objectPool.size() < 20) {
                    isRecycled = true;
                    data = null;
                    error = null;
                    tag = null;
                    listenerCount.set(0);
                    objectPool.add(this);
                }
            }
        }
    }

    public boolean isRecycled() {
        return isRecycled;
    }

    @Override
    public boolean equals(Object o) {
        ensureNotRecycled();
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Event event = (Event) o;

        //noinspection SimplifiableIfStatement
        if (!getTag().equals(event.getTag())) return false;
        return getError() != null ? getError().equals(event.getError()) : event.getError() == null &&
                (getData() != null ? getData().equals(event.getData()) : event.getData() == null);

    }

    @Override
    public int hashCode() {
        ensureNotRecycled();
        int result = getTag().hashCode();
        result = 31 * result + (getError() != null ? getError().hashCode() : 0);
        result = 31 * result + (getData() != null ? getData().hashCode() : 0);
        return result;
    }

    public Object getTag() {
        ensureNotRecycled();
        return tag;
    }

    public Exception getError() {
        ensureNotRecycled();
        return error;
    }

    public Object getData() {
        ensureNotRecycled();
        return data;
    }

    private void ensureNotRecycled() {
        if (isRecycled) {
            throw new IllegalStateException("can't use a recycled event");
        }
    }

    public boolean isSticky() {
        return isSticky;
    }

    /*package*/void recycleSticky() {
        if (isRecycled() || !isSticky()) {
            return;
        }
        isSticky = false;
        listenerCount.set(0);
        recycle();
    }

    /*package*/ void setListenerCount(int count) {
        ensureNotRecycled();
        GenericUtils.ensureConditionTrue(count > 0, "invalid number");
        listenerCount.set(count);
    }
}
