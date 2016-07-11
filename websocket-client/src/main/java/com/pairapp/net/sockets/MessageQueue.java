package com.pairapp.net.sockets;

import android.support.annotation.NonNull;

import com.pairapp.util.PLog;

/**
 * @author aminu on 7/9/2016.
 */
public interface MessageQueue<T> {
    boolean remove(@NonNull T t);

    void add(@NonNull T t);

    void onProcessed(@NonNull T t, boolean succeeded);

    boolean clear();

    interface Hooks {
        String TAG = "QueueDataSourceHooks";

        int INVALID_REASON_FOR_TESTING = -1, PROCESSED = 1, FAILED_EXPIRED = 2, FAILED_RETRIES_EXCEEDED = 3, FORCEFULLY_REMOVED = 4;

        void onItemRemoved(Sendable item, int reason);

        void onItemAdded(Sendable item);

        void onBeginProcess(Sendable item);

        Hooks DEFAULT_HOOK = new Hooks() {
            @Override
            public void onItemRemoved(Sendable item, int reason) {
                PLog.d(TAG, "%s removed", item.toString());
            }

            @Override
            public void onItemAdded(Sendable item) {
                PLog.d(TAG, "%s added, will be processed ASAP", item.toString());
            }

            @Override
            public void onBeginProcess(Sendable item) {
                PLog.d(TAG, "about to process item %s", item.toString());
            }
        };
    }
}
