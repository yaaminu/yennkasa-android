package com.pairapp.net.sockets;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.pairapp.net.sockets.QueueDataSource.QueueItemCleanedListener;
import com.pairapp.util.GenericUtils;
import com.pairapp.util.PLog;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author aminu on 7/9/2016.
 */
class MessageQueueImpl implements MessageQueue<Sendable> {

    public static final String TAG = MessageQueueImpl.class.getSimpleName();
    public static final int PAUSED_PROCESSING = 1;
    public static final int PROCESSING = 2;
    public static final int STOPPED_PROCESSING = 3;
    private final QueueDataSource itemsStore;
    private final Hooks hooks;
    private final Consumer consumer;
    private volatile int currentlyProcessing;
    private volatile boolean initialised;
    private volatile boolean started;
    private int runningState;

    private Executor executor;
    @SuppressWarnings("FieldCanBeLocal")
    private final QueueItemCleanedListener queueItemCleanedListener = new QueueItemCleanedListener() {
        @Override
        public void onExpiredItemsRemoved(List<Sendable> items) {
            for (Sendable item : items) {
                hooks.onItemRemoved(item, getReason(item));
            }
        }

        int getReason(Sendable item) {
            if (item.isExpired()) {
                return MessageQueue.Hooks.FAILED_EXPIRED;
            } else if (item.exceededRetries()) {
                return MessageQueue.Hooks.FAILED_RETRIES_EXCEEDED;
            } else {
                return MessageQueue.Hooks.FORCEFULLY_REMOVED;
            }
        }
    };

    MessageQueueImpl(QueueDataSource queueDataSource, Hooks hooks, Consumer consumer) {
        GenericUtils.ensureNotNull(hooks, queueDataSource);
        this.itemsStore = queueDataSource;
        this.hooks = hooks;
        this.consumer = consumer;
        this.currentlyProcessing = 0;
    }

    void initBlocking() {
        initBlocking(false);
    }

    //for testing
    void initBlocking(boolean asyncExecution) {
        if (runningState == STOPPED_PROCESSING) {
            throw new IllegalStateException("stopped!");
        }
        if (initialised) {
            PLog.w(TAG, "already initialised");
            return;
        }

        initialised = true;
        itemsStore.registerCallback(queueItemCleanedListener);
        itemsStore.init();
        if (asyncExecution) {
            executor = new ThreadPoolExecutor(2, Math.max(consumer.highWaterMark() / 4, 4), 5,
                    TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(128));
        } else {
            executor = new Executor() {
                @Override
                public void execute(@NonNull Runnable command) {
                    command.run();
                }
            };
        }
    }

    public synchronized void start() {
        if (!isInitialised()) throw new IllegalStateException("not initialised");
        if (isStarted()) throw new IllegalStateException("already started");
        if (runningState == STOPPED_PROCESSING) throw new IllegalStateException("stopped");
        started = true;
        runningState = PROCESSING;
        processItems();
    }

    private void processItems() {
        Sendable item = next();
        while (item != null) {
            processNext(item);
            item = next();
        }
        PLog.d(TAG, "next() got nothing. going idle");
    }

    private void processNext(final Sendable item) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                consumer.consume(item);
            }
        });
    }


    synchronized void pauseProcessing() {
        ensureStateValid();
        if (runningState == STOPPED_PROCESSING) {
            throw new IllegalStateException("cannot pause a stopped queue");
        }
        runningState = PAUSED_PROCESSING;
    }

    synchronized void resumeProcessing() {
        ensureStateValid();
        if (runningState == STOPPED_PROCESSING) {
            throw new IllegalStateException("cannot resume a stopped queue");
        }
        runningState = PROCESSING;
        processItems();
    }

    synchronized void stopProcessing() {
        ensureStateValid();
        if (runningState == STOPPED_PROCESSING) {
            PLog.w(TAG, "queue already stopped");
            return;
        }
        runningState = STOPPED_PROCESSING;
        started = false;
    }

    @Nullable
    /*package access for for testing*/ synchronized Sendable next() {
        ensureStateValid();
        if (runningState == PROCESSING && currentlyProcessing < consumer.highWaterMark()) {
            Sendable item = itemsStore.nextItem();
            if (item != null) {
                currentlyProcessing++;
                hooks.onBeginProcess(item);
            }
            return item;
        }
        PLog.d(TAG, "currently processing messages has reached the high water mark. high water mark is %s", consumer.highWaterMark());
        return null;
    }

    @Override
    public synchronized void onProcessed(@NonNull Sendable item, boolean succeeded) {
        ensureStateValid();
        PLog.v(TAG, "processed %s ", item.toString());
        currentlyProcessing--;
        if (succeeded) {
            removeItem(item, Hooks.PROCESSED);
        } else { //failed to process
            if (item.exceededRetries() || item.isExpired()) {
                removeItem(item, item.exceededRetries() ? MessageQueue.Hooks.FAILED_RETRIES_EXCEEDED : MessageQueue.Hooks.FAILED_EXPIRED);
            } else {
                addItem(item, false); //retry
            }
        }
        if (isStarted() && runningState == PROCESSING) {
            processItems(); //enqueue other tasks
        }
    }

    public int getPending() {
        ensureStateValid();
        return itemsStore.pending().size();
    }

    public int getProcessing() {
        ensureStateValid();
        return itemsStore.processing().size();
    }

    public int highWaterMark() {
        ensureStateValid();
        return consumer.highWaterMark();
    }

    private void removeItem(Sendable item, int reason) {
        itemsStore.removeItem(item);
        hooks.onItemRemoved(item, reason);
    }

    @Override
    public boolean remove(@NonNull Sendable item) {
        ensureStateValid();
        if (itemsStore.removeItem(item)) {
            removeItem(item, Hooks.FORCEFULLY_REMOVED);
            return true;
        }
        return false;
    }

    private synchronized void addItem(Sendable item, boolean isNew) {
        itemsStore.addItem(item);
        if (isNew) {
            hooks.onItemAdded(item);
        }
    }

    @Override
    public synchronized void add(@NonNull Sendable sendable) {
        ensureStateValid();
        addItem(sendable, true);
        if (isStarted() && runningState == PROCESSING) {
            processItems();
        }
    }

    @Override
    public boolean clear() {
        ensureStateValid();
        List<Sendable> items = itemsStore.pending();
        for (Sendable item : items) {
            hooks.onItemRemoved(item, MessageQueue.Hooks.FORCEFULLY_REMOVED);
        }
        items = itemsStore.processing();
        for (Sendable item : items) {
            hooks.onItemRemoved(item, MessageQueue.Hooks.FORCEFULLY_REMOVED);
        }
        return itemsStore.clearQueue();
    }

    interface Consumer {
        int DEFAULT_HIGH_WATER_MARK = 8;

        void consume(Sendable item);

        int highWaterMark();
    }

    private void ensureStateValid() {
        if (!isInitialised()) {
            throw new IllegalStateException("not initialised");
        } else if (!isStarted()) {
            throw new IllegalStateException("not started");
        } else if (runningState == STOPPED_PROCESSING) {
            throw new IllegalStateException("stopped");
        }
    }

    public boolean isInitialised() {
        return initialised;
    }

    public boolean isStarted() {
        return started;
    }
}


