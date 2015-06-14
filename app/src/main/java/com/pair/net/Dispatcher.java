package com.pair.net;

import java.util.Collection;

/**
 * Created by Null-Pointer on 5/26/2015.
 */
public interface Dispatcher<T> {
    void dispatch(T t);

    void dispatch(Collection<T> t);

    boolean cancelDispatchMayPossiblyFail(T t);

    void setDispatcherMonitor(DispatcherMonitor callBack);

    /**
     * Created by Null-Pointer on 5/26/2015.
     */
    interface DispatcherMonitor {
        void onSendFailed(String reason, String messageId);

        void onSendSucceeded(String messageId);

        void onAllDispatched();
    }
}
