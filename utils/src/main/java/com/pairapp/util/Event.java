package com.pairapp.util;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.Stack;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author aminu on 6/30/2016.
 */
@SuppressWarnings("ThrowableResultOfMethodCallIgnored")
public class Event {
    private static final String TAG = "Event";
    private Object tag;
    private Exception error;
    private Object data;
    private boolean isRecycled;

    @NonNull
    private static final Stack<Event> objectPool = new Stack<>();
    private volatile boolean isSticky;
    @NonNull
    private final AtomicInteger listenerCount = new AtomicInteger(0);

    private Event(@NonNull Object tag, @Nullable Exception error, @Nullable Object data, boolean isSticky) {
        this.tag = tag;
        this.error = error;
        this.data = data;
        isRecycled = false;
        this.isSticky = isSticky;
    }

    @NonNull
    public static Event create(@NonNull Object tag) {
        return create(tag, null);
    }

    @NonNull
    public static Event createSticky(@NonNull Object tag) {
        return createSticky(tag, null);
    }

    @NonNull
    public static Event create(@NonNull Object tag, @Nullable Exception error) {
        return create(tag, error, null);
    }

    @NonNull
    public static Event createSticky(@NonNull Object tag, @Nullable Exception error) {
        return createSticky(tag, error, null);
    }

    @NonNull
    public static Event create(@NonNull Object tag, @Nullable Exception error, @Nullable Object data) {
        return doCreate(tag, error, data, false);
    }

    @NonNull
    public static Event createSticky(@NonNull Object tag, @Nullable Exception error, @Nullable Object data) {
        return doCreate(tag, error, data, true);
    }

    private static Event doCreate(@NonNull Object tag, @Nullable Exception error, @Nullable Object data, boolean isSticky) {
        GenericUtils.ensureNotNull(tag);
        Event event;
        if (false && !objectPool.isEmpty()) {
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
        PLog.d(TAG, "recycling event %s", this);
        if (false && !isRecycled() && !isSticky() && listenerCount.decrementAndGet() <= 0) {
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

    @NonNull
    public Object getTag() {
        ensureNotRecycled();
        return tag;
    }

    @Nullable
    public Exception getError() {
        ensureNotRecycled();
        return error;
    }

    @Nullable
    public Object getData() {
        ensureNotRecycled();
        return data;
    }

    private synchronized void ensureNotRecycled() {
        if (isRecycled()) {
            throw new IllegalStateException("can't use a recycled event");
        }
    }

    public boolean isSticky() {
        return isSticky;
    }

    /*package*/void recycleSticky() {
        PLog.d(TAG, "recycling sticky event %s", this);
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

    @Override
    public String toString() {
        ensureNotRecycled();
        //noinspection StringBufferReplaceableByString
        return new StringBuilder("tag=>")
                .append(getTag())
                .append(", error=>")
                .append(getError() == null ? "no error" : getError().getClass().getSimpleName() + ":" + getError().getMessage())
                .append(", data=>")
                .append(getData() == null ? "no data" : getData().toString())
                .append(", isSticky=>")
                .append(isSticky())
                .append(", listenersCount=>")
                .append(listenerCount.get())
                .toString();

    }
}
