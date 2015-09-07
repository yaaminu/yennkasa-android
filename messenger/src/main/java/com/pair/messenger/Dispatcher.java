package com.pair.messenger;

import com.pair.data.net.FileApi;

import java.io.Closeable;
import java.util.Collection;

/**
 * an interface that defines how one can {@code dispatch()}
 * a given data. this can be via a network socket or other means.
 * <p>
 * it forms the basis for all dispatchers.
 * </p>
 *
 * @author by Null-Pointer on 5/26/2015.
 * @see AbstractMessageDispatcher
 * @see ParseDispatcher
 */
interface Dispatcher<T> extends Closeable {
    void dispatch(T t);

    void dispatch(Collection<T> t);

    void dispatch(T t, FileApi.ProgressListener listener);

    void dispatch(Collection<T> t, FileApi.ProgressListener listener);

    boolean cancelDispatchMayPossiblyFail(T t);

    void addMonitor(DispatcherMonitor monitor);

    void removeMonitor(DispatcherMonitor monitor);

    void close();

    /**
     * an interface to be implemented if one wants to monitor
     * a given dispatcher. Example an analytic class can implement
     * this interface and track all messages sent. or a stats class
     * can implement this interface and track the bytes sent by a given
     * dispatcher.
     *
     * @author by Null-Pointer on 5/26/2015.
     */
    interface DispatcherMonitor {
        void onDispatchFailed(String reason, String objectIdentifier);

        void onDispatchSucceed(String objectIdentifier);

        void onAllDispatched();
    }
}
