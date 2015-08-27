package com.pair.net;

import java.util.Collection;

/**
 * @author by Null-Pointer on 5/26/2015.
 */
public interface Dispatcher<T> {
    void dispatch(T t);

    void dispatch(Collection<T> t);
    boolean cancelDispatchMayPossiblyFail(T t);

    void addMonitor(DispatcherMonitor monitor);

    void removeMonitor(DispatcherMonitor monitor);
    /**
     * Created by Null-Pointer on 5/26/2015.
     */
    interface DispatcherMonitor {
        void onSendFailed(String reason, String objectIdentifier);

        void onSendSucceeded(String objectIdentifier);

        void onAllDispatched();
    }
}
