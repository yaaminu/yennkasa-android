package com.pairapp.net.sockets;

import android.content.Context;
import android.util.Base64;

import com.pairapp.messenger.MessengerBus;
import com.pairapp.util.Config;
import com.pairapp.util.Event;
import com.pairapp.util.GenericUtils;
import com.pairapp.util.PLog;

import java.io.File;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.realm.Realm;

/**
 * @author aminu on 7/2/2016.
 */
public class SenderImpl implements Sender {

    public static final String TAG = "senderImpl";
    private final MessageParser parser;
    private PairappSocket pairappSocket;
    private final MessageQueueImpl messageQueue;
    private volatile boolean started = false;
    @SuppressWarnings("FieldCanBeLocal")
    private final MessageQueueImpl.Consumer consumer = new MessageQueueImpl.Consumer() {
        @Override
        public void consume(Sendable item) {
            pairappSocket.send(stringToBytes(item.getData()));
        }

        @Override
        public int highWaterMark() {
            return pairappSocket.isConnected() ? 30 : 0;
        }
    };
    @SuppressWarnings("FieldCanBeLocal")
    private final MessageQueueItemDataSource.RealmProvider realmProvider = new MessageQueueItemDataSource.RealmProvider() {
        @Override
        public Realm getRealm() {
            return Sendable.Realm(MESSAGE_QUEUE_ITEM_FOLDER);
        }
    };
    private final HooksImpl hooks = new HooksImpl();

    private static final File MESSAGE_QUEUE_ITEM_FOLDER
            = Config.getApplicationContext().getDir("message.queue.items.realm", Context.MODE_PRIVATE);

    public SenderImpl(Map<String, String> opts, MessageParser parser) {
        this.parser = parser;
        pairappSocket = PairappSocket.create(opts, listener);
        MessageQueueItemDataSource queueDataSource = new MessageQueueItemDataSource(realmProvider);
        this.messageQueue = new MessageQueueImpl(queueDataSource, hooks, consumer/*are we letting this escape?*/);
    }

    public synchronized void start() {
        if (this.started) {
            throw new IllegalStateException("can\'t use a stopped sender");
        }
        started = true;
        pairappSocket.init();
        this.messageQueue.initBlocking(true);
        this.messageQueue.start();
    }

    @Override
    public void sendMessage(Sendable sendable) {
        if (!this.started) {
            throw new IllegalStateException("not started");
        }
        messageQueue.add(sendable);
    }

    @Override
    public void addSendListener(SendListener sendListener) {
        hooks.addSendListener(sendListener);
    }

    @Override
    public void removeSendListener(SendListener sendListener) {
        hooks.unRegisterSendListener(sendListener);
    }

    @Override
    public void shutdownSafely() {
        if (started) {
            started = false;
            if (messageQueue.isStarted()) {
                if (pairappSocket.isConnected()) {
                    pairappSocket.disConnectBlocking();
                }
                messageQueue.stopProcessing();
            }
        } else {
            throw new IllegalStateException("not started");
        }
    }

    @Override
    public String bytesToString(byte[] data) {
        return encodeToString(data);
    }

    @Override
    public byte[] stringToBytes(String data) {
        return encodeToBytes(data);
    }

    private class HooksImpl implements MessageQueue.Hooks {

        final Set<SendListener> sendListeners;

        public HooksImpl() {
            this.sendListeners = new HashSet<>(2);
        }

        public void addSendListener(SendListener sendListener) {

            GenericUtils.ensureNotNull(sendListener);
            synchronized (sendListeners) {
                sendListeners.add(sendListener);
            }
        }

        public void unRegisterSendListener(SendListener sendListener) {
            GenericUtils.ensureNotNull(sendListener);
            sendListeners.remove(sendListener);
        }

        @Override
        public void onItemRemoved(Sendable item, int reason) {
            byte[] data = encodeToBytes(item.getData());
            if (reason == PROCESSED) {
                PLog.d(TAG, "successfully sent item %s", item.toString());
                synchronized (sendListeners) {
                    for (SendListener sendListener : sendListeners) {
                        sendListener.onSentSucceeded(data);
                    }
                }
            } else {
                PLog.d(TAG, "failed to send item %s", item.toString());
                synchronized (sendListeners) {
                    for (SendListener sendListener : sendListeners) {
                        sendListener.onSendFailed(data);
                    }
                }
            }
        }

        @Override
        public void onItemAdded(Sendable item) {
        }

        @Override
        public void onBeginProcess(Sendable item) {

        }
    }

    @SuppressWarnings("FieldCanBeLocal")
    private final PairappSocket.PairappSocketListener listener = new PairappSocket.PairappSocketListener() {
        @Override
        public void onMessage(byte[] bytes) {
            parser.feed(bytes);
        }

        @Override
        public void onMessage(String message) {
            onMessage(message.getBytes());
        }

        @Override
        public void onOpen() {
            MessengerBus.get(MessengerBus.PAIRAPP_CLIENT_LISTENABLE_BUS).postSticky(Event.createSticky(MessengerBus.SOCKET_CONNECTION, null, 2));
            if (!started) {
                throw new IllegalStateException("not started");
            }
            if (messageQueue.isStarted()) {
                messageQueue.resumeProcessing();
            }
        }

        @Override
        public void onClose(int code, String reason) {
            //should we allow ourselves to be usable again?
        }

        @Override
        public void onClose() {
            //should we allow ourselves to be usable again?
        }

        @Override
        public void onError(Exception e) {

        }

        @Override
        public void onConnecting() {
            MessengerBus.get(MessengerBus.PAIRAPP_CLIENT_LISTENABLE_BUS).postSticky(Event.createSticky(MessengerBus.SOCKET_CONNECTION, null, 1));
            // TODO: 6/20/2016 fire an event that we are connecting
        }

        @Override
        public void onSendError(boolean isBinary, byte[] data) {
            Realm realm = realmProvider.getRealm();
            try {
                Sendable item = realm.where(Sendable.class).equalTo(Sendable.FIELD_DATA, bytesToString(data)).findFirst();
                if (item != null) {
                    messageQueue.onProcessed(realm.copyFromRealm(item), false);
                } else {
                    PLog.w(TAG, "item removed from queue while it was being processed");
                }
            } finally {
                realm.close();
            }
        }

        @Override
        public void onSendSuccess(boolean isBinary, byte[] data) {
            Realm realm = realmProvider.getRealm();
            try {
                Sendable item = realm.where(Sendable.class).equalTo(Sendable.FIELD_DATA, encodeToString(data)).findFirst();
                if (item != null) {
                    messageQueue.onProcessed(realm.copyFromRealm(item), true);
                } else {
                    PLog.w(TAG, "item removed from queue while it was being processed");
                }
            } finally {
                realm.close();
            }
        }

        @Override
        public void onDisConnectedUnexpectedly() {
            MessengerBus.get(MessengerBus.PAIRAPP_CLIENT_LISTENABLE_BUS).postSticky(Event.createSticky(MessengerBus.SOCKET_CONNECTION, null, 0));
            if (messageQueue.isStarted()) {
                messageQueue.pauseProcessing();
            }
        }
    };

    private byte[] encodeToBytes(String data) {
        return Base64.decode(data, Base64.DEFAULT);
    }

    private String encodeToString(byte[] data) {
        return Base64.encodeToString(data, Base64.DEFAULT);
    }
}
