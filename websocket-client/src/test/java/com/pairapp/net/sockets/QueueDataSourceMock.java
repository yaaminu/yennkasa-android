package com.pairapp.net.sockets;

import java.util.ArrayList;
import java.util.List;

/**
 * @author aminu on 7/10/2016.
 */
public class QueueDataSourceMock implements QueueDataSource {


    final List<Sendable> mocks;
    QueueItemCleanedListener queueItemCleanedListener;
    boolean initialised = false;

    public QueueDataSourceMock(List<Sendable> mocks) {
        this.mocks = mocks;
    }

    @Override
    public Sendable nextItem() {
        for (int i = 0; i < mocks.size(); i++) {
            Sendable item = mocks.get(i);
            if (!item.isProcessing()) {
                item.setProcessing(true);
                return item;
            }
        }

        return null;
    }

    @Override
    public boolean clearQueue() {
        if (!mocks.isEmpty()) {
            mocks.clear();
            return true;
        }
        return false;
    }

    @Override
    public boolean removeItem(Sendable item) {
        return mocks.remove(item);
    }

    @Override
    public void addItem(Sendable item) {
        item.setProcessing(false);
        mocks.add(item);
    }

    @Override
    public void registerCallback(QueueItemCleanedListener queueItemCleanedListener) {
        this.queueItemCleanedListener = queueItemCleanedListener;
    }

    @Override
    public List<Sendable> pending() {
        List<Sendable> items = new ArrayList<>();
        for (Sendable mock : mocks) {
            if (!mock.isProcessing()) {
                items.add(mock);
            }
        }
        return items;
    }

    @Override
    public List<Sendable> processing() {
        List<Sendable> items = new ArrayList<>();
        for (Sendable mock : mocks) {
            if (mock.isProcessing()) {
                items.add(mock);
            }
        }
        return items;
    }

    @Override
    public boolean removeByCollpaseKey(String collapseKey) {
        throw new RuntimeException();
    }

    @Override
    public void init() {
        initialised = true;
    }

    boolean isInitialised() {
        return initialised;
    }
}
