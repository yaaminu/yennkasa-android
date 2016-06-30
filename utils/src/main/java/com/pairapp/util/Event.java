package com.pairapp.util;

import java.util.Stack;

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

    private Event(Object tag, Exception error, Object data) {
        this.tag = tag;
        this.error = error;
        this.data = data;
        isRecycled = false;
    }

    public synchronized static Event create(Object tag) {
        return create(tag, null);
    }

    public synchronized static Event create(Object tag, Exception error) {
        return create(tag, error, null);
    }

    public synchronized static Event create(Object tag, Exception error, Object data) {
        GenericUtils.ensureNotNull(tag);
        Event event;
        if (!objectPool.isEmpty()) {
            event = objectPool.pop();
            event.tag = tag;
            event.error = error;
            event.data = data;
            event.isRecycled = false;
        } else {
            event = new Event(tag, error, data);
        }
        return event;
    }

    public void recycle() {
        if (!isRecycled()) {
            synchronized (Event.class) {
                if (objectPool.size() < 20) {
                    isRecycled = true;
                    data = null;
                    error = null;
                    tag = null;
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
}
