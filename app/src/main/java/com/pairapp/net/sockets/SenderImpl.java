package com.pairapp.net.sockets;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Base64;

import com.pairapp.BuildConfig;
import com.pairapp.Errors.PairappException;
import com.pairapp.R;
import com.pairapp.messenger.MessengerBus;
import com.pairapp.util.Config;
import com.pairapp.util.Event;
import com.pairapp.util.EventBus;
import com.pairapp.util.GenericUtils;
import com.pairapp.util.PLog;
import com.pairapp.util.SimpleDateUtil;
import com.pairapp.util.Task;
import com.pairapp.util.TaskManager;
import com.pairapp.util.UiHelpers;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import io.realm.Realm;

import static com.pairapp.messenger.MessengerBus.CONNECTED;
import static com.pairapp.messenger.MessengerBus.CONNECTING;
import static com.pairapp.messenger.MessengerBus.DISCONNECTED;
import static com.pairapp.messenger.MessengerBus.PAIRAPP_CLIENT_LISTENABLE_BUS;
import static com.pairapp.messenger.MessengerBus.SOCKET_CONNECTION;

/**
 * @author aminu on 7/2/2016.
 */
public class SenderImpl implements Sender {

    public static final String TAG = "senderImpl";
    private final MessageParser parser;
    private final Authenticator authenticator;
    @Nullable
    private PairappSocket pairappSocket;
    private final MessageQueueImpl messageQueue;
    private volatile boolean started = false;
    @SuppressWarnings("FieldCanBeLocal")
    private final MessageQueueImpl.Consumer consumer = new MessageQueueImpl.Consumer() {
        @Override
        public void consume(Sendable item) {
            if (pairappSocket != null) {
                pairappSocket.send(stringToBytes(item.getData()));
            } else {
                messageQueue.onProcessed(item, false);
            }
        }

        @Override
        public int highWaterMark() {
            return pairappSocket != null && pairappSocket.isConnected() ? 30 : 0;
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

    @Nullable
    private Task refreshJob;

    public SenderImpl(Authenticator authenticator, MessageParser parser) {
        this.parser = parser;
        this.authenticator = authenticator;
        String token = authenticator.getToken();
        initialiseSocket(token);
        MessageQueueItemDataSource queueDataSource = new MessageQueueItemDataSource(realmProvider);
        this.messageQueue = new MessageQueueImpl(queueDataSource, hooks, consumer/*are we letting this escape?*/);
    }

    public static Sendable createMessageSendable(String messageId, byte[] message) {
        return new Sendable.Builder()
                .data(encodeToString(message))
                .collapseKey(messageId + "message")
                .validUntil(System.currentTimeMillis() + SimpleDateUtil.ONE_HOUR * 12) //12 hours
                .maxRetries(Sendable.RETRY_FOREVER)
                .surviveRestarts(true)
                .startProcessingAt(System.currentTimeMillis())
                .build();
    }

    private void initialiseSocket(String token) {
        GenericUtils.ensureNotEmpty(token);
        pairappSocket = PairappSocket.create(Collections.singletonMap("Authorization", token), listener);
    }

    public synchronized void start() {
        if (this.started) {
            throw new IllegalStateException("can\'t use a stopped sender");
        }
        started = true;
        if (pairappSocket != null) {
            pairappSocket.init();
        }
        this.messageQueue.initBlocking(true);
        // TODO: 11/10/2016 why start the message que here but not when the socketconnection is open?
        this.messageQueue.start();
    }

    @Override
    public void sendMessage(Sendable sendable) {
        ensureStarted();
        messageQueue.add(sendable);
    }

    private void ensureStarted() {
        if (!this.started) {
            throw new IllegalStateException("not started");
        }
    }

    @Override
    public boolean unsendMessage(Sendable sendable) {
        ensureStarted();
        if (messageQueue.remove(sendable)) {
            return true;
        } else {
            messageQueue.add(sendable);
        }
        return false;
    }

    @Override
    public void updateSentMessage(Sendable sendable) {
        ensureStarted();
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
            // TODO: 11/10/2016 why do we have to check if queue is start()ed before disconnecting?
            if (messageQueue.isStarted()) {
                if (pairappSocket != null && pairappSocket.isConnected()) {
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

    @SuppressWarnings("WeakerAccess")
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
            ensureStarted();
            MessengerBus.get(PAIRAPP_CLIENT_LISTENABLE_BUS).postSticky(Event.createSticky(SOCKET_CONNECTION, null, CONNECTED));
            // TODO: 11/10/2016 whey not actually start the message queue here in the first place?
            //like this :
//            if (!messageQueue.isStarted()) {
//                messageQueue.stopProcessing();
//            } else {
//                messageQueue.resumeProcessing();
//            }
            if (messageQueue.isStarted()) {
                messageQueue.resumeProcessing();
            }
        }

        @SuppressWarnings("StatementWithEmptyBody")
        @Override
        public void onClose(int code, String reason) {
            // TODO: 11/10/2016  should we allow ourselves to be usable again?
            // TODO: 11/10/2016 check for reasons why the connection was closed
            // TODO: 11/10/2016 stop the message queue?
            //if it's for authentication reasons, we will have to request it somehow, create a new connection
            //and start processin again
            switch (code) {
                case StatusCodes.BAD_REQUEST:
                    if (BuildConfig.DEBUG) {
                        throw new RuntimeException("client broke protocol");
                    } else {
                        // TODO: 11/13/2016 what shouuld we do? notify user that they should update or ?
                    }
                    break;
                case StatusCodes.UNAUTHORIZED:
                    synchronized (SenderImpl.this) {
                        if (refreshJob != null) {
                            PLog.d(TAG, "another job already running");
                            return;
                        }
                        if (messageQueue.isStarted()) {
                            messageQueue.pauseProcessing();
                        }
                        pairappSocket = null;
                        EventBus.getDefault().register(eventsListener, TokenRefreshJob.TOKEN_NEW_REFRESH);
                        refreshJob = TokenRefreshJob.create(authenticator);
                        TaskManager.runJob(refreshJob);
                    }
                    break;
                default:
                    PLog.d(TAG, "socket disconnected with reason %s and code %d", reason, code);
            }
        }

        @Override
        public void onClose() {
            // TODO: 11/10/2016 stop the message queue?
            // TODO: 11/10/2016  should we allow ourselves to be usable again?
        }

        @Override
        public void onError(Exception e) {

        }

        @Override
        public void onReconnectionTakingTooLong() {
            MessengerBus.get(PAIRAPP_CLIENT_LISTENABLE_BUS)
                    .postSticky(Event.createSticky(SOCKET_CONNECTION, null, DISCONNECTED));
        }

        @Override
        public void onConnecting() {
            MessengerBus.get(PAIRAPP_CLIENT_LISTENABLE_BUS)
                    .postSticky(Event.createSticky(SOCKET_CONNECTION, null, CONNECTING));
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
            MessengerBus.get(PAIRAPP_CLIENT_LISTENABLE_BUS).postSticky(Event.createSticky(SOCKET_CONNECTION, null, DISCONNECTED));
            if (messageQueue.isStarted()) {
                messageQueue.pauseProcessing();
            }
        }
    };

    public interface Authenticator {

        @NonNull
        String getToken();

        @NonNull
        String requestNewToken() throws PairappException;
    }

    private byte[] encodeToBytes(String data) {
        return Base64.decode(data, Base64.DEFAULT);
    }

    private static String encodeToString(byte[] data) {
        return Base64.encodeToString(data, Base64.DEFAULT);
    }

    private final EventBus.EventsListener eventsListener = new EventBus.EventsListener() {
        @Override
        public int threadMode() {
            return EventBus.BACKGROUND;
        }

        @Override
        public void onEvent(EventBus yourBus, Event event) {
            synchronized (SenderImpl.this) {
                if (TokenRefreshJob.TOKEN_NEW_REFRESH.equals(event.getTag())) {
                    if (SenderImpl.this.started) {
                        String newToken = (String) event.getData();
                        GenericUtils.ensureNotEmpty(newToken);
                        initialiseSocket(newToken);
                        assert pairappSocket != null;
                        pairappSocket.init();
                        messageQueue.resumeProcessing();
                    }
                } else {
                    throw new AssertionError();
                }
                yourBus.unregister(TokenRefreshJob.TOKEN_NEW_REFRESH, eventsListener);
                refreshJob = null;
            }
        }

        @Override
        public boolean sticky() {
            return false;
        }
    };
}
