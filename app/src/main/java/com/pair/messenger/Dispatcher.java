package com.pair.messenger;

import com.pair.net.FileApi;

import java.io.Closeable;
import java.util.Collection;

/**
 * @author by Null-Pointer on 5/26/2015.
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
     * Created by Null-Pointer on 5/26/2015.
     */
    interface DispatcherMonitor {
        void onSendFailed(String reason, String objectIdentifier);

        void onSendSucceeded(String objectIdentifier);

        void onAllDispatched();
    }
}
