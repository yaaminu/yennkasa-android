package com.yennkasa.net.sockets;

import java.util.List;

/**
 * @author aminu on 7/10/2016.
 */
interface QueueDataSource {
    Sendable nextItem();

    boolean clearQueue();

    boolean removeItem(Sendable item);

    void addItem(Sendable item);

    void registerCallback(QueueItemCleanedListener queueItemCleanedListener);

    List<Sendable> pending();

    List<Sendable> processing();

    boolean removeByCollpaseKey(String collapseKey);

    void init();

    void markWaitingForAck(Sendable item);

    List<Sendable> waitingForAck();

    interface QueueItemCleanedListener {
        void onExpiredItemsRemoved(List<Sendable> items);
    }
}
