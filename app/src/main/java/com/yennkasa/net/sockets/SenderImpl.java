package com.yennkasa.net.sockets;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Base64;

import com.yennkasa.BuildConfig;
import com.yennkasa.Errors.YennkasaException;
import com.yennkasa.messenger.MessagePacker.MessagePackerException;
import com.yennkasa.messenger.MessengerBus;
import com.yennkasa.security.MessageEncryptor;
import com.yennkasa.util.Config;
import com.yennkasa.util.ConnectionUtils;
import com.yennkasa.util.Event;
import com.yennkasa.util.EventBus;
import com.yennkasa.util.GenericUtils;
import com.yennkasa.util.PLog;
import com.yennkasa.util.SimpleDateUtil;
import com.yennkasa.util.Task;
import com.yennkasa.util.TaskManager;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import io.realm.Realm;

import static com.yennkasa.messenger.MessagePacker.MessagePackerException.DECRYPTION_FAILED;
import static com.yennkasa.messenger.MessengerBus.CONNECTED;
import static com.yennkasa.messenger.MessengerBus.CONNECTING;
import static com.yennkasa.messenger.MessengerBus.DISCONNECTED;
import static com.yennkasa.messenger.MessengerBus.PAIRAPP_CLIENT_LISTENABLE_BUS;
import static com.yennkasa.messenger.MessengerBus.SOCKET_CONNECTION;

/**
 * @author aminu on 7/2/2016.
 */
public class SenderImpl implements Sender {

    public static final String TAG = "senderImpl";
    private final MessageParser parser;
    private final Authenticator authenticator;
    @Nullable
    private YennkasaSocket yennkasaSocket;
    @NonNull
    private final MessageQueueImpl messageQueue;
    private volatile boolean started = false, shuttingDown = false;
    @SuppressWarnings("FieldCanBeLocal")
    private final MessageQueueImpl.Consumer consumer = new MessageQueueImpl.Consumer() {
        @Override
        public void consume(Sendable item) {
            if (yennkasaSocket == null || !yennkasaSocket.send(stringToBytes(item.getData()))) {
                messageQueue.onProcessed(item, false);
            }
        }

        @Override
        public int highWaterMark() {
            return !shuttingDown && yennkasaSocket != null && yennkasaSocket.isConnected() ? 1 : 0;
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
        this.authToken = authenticator.getToken();
        GenericUtils.ensureNotEmpty(authToken);
        initialiseSocket();
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

    synchronized private void initialiseSocket() {
        yennkasaSocket = YennkasaSocket.create(Collections.singletonMap("Authorization", authToken), listener);
        shuttingDown = false;
    }

    public synchronized void start() {
        if (this.started) {
            throw new IllegalStateException("can\'t use a stopped sender");
        }
        started = true;
        if (yennkasaSocket != null) {
            yennkasaSocket.init();
        }
        this.messageQueue.initBlocking(true);
        this.messageQueue.start();
    }

    @Override
    public synchronized void sendMessage(Sendable sendable) {
        ensureStarted();
        if (yennkasaSocket == null) {
            if (ConnectionUtils.isConnected()) {
                initialiseSocket();
                yennkasaSocket.init();
            }
        }
        messageQueue.add(sendable);
    }

    private void ensureStarted() {
        if (!this.started) {
            throw new IllegalStateException("not started");
        }
    }

    @Override
    public synchronized boolean unsendMessage(Sendable sendable) {
        ensureStarted();
        if (yennkasaSocket == null) {
            if (ConnectionUtils.isConnected()) {
                initialiseSocket();
                yennkasaSocket.init();
            }
        }
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
        if (BuildConfig.DEBUG) {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public void addSendListener(SendListener sendListener) {
        hooks.addSendListener(sendListener);
    }

    @Override
    public void removeSendListener(SendListener sendListener) {
        hooks.unRegisterSendListener(sendListener);
    }

    /**
     * shuts down safely... all waiting messages if any will be sent across the wire before
     * shutting down.
     */
    @Override
    public synchronized void shutdownSafely() {
        if (started) {
            started = false;
            if (yennkasaSocket != null && yennkasaSocket.isConnected()) {
                if (messageQueue.isStarted() && messageQueue.getPending() > 0) {
                    shuttingDown = true; //this will set highWaterMark to 0 effectively preventing any
                    //messages to be enqueued onto the dispatch queue
                    return;
                }
                yennkasaSocket.disConnectBlocking();
            }
            if (messageQueue.isStarted()) {
                messageQueue.stopProcessing();
            }
        }
    }

    @Override
    public synchronized void disconnectIfRequired() {
        if (!Config.isAppOpen() && (!messageQueue.isStarted() || messageQueue.getPending() == 0)) {
            if (yennkasaSocket != null) {
                if (yennkasaSocket.isConnected()) {
                    yennkasaSocket.disConnectBlocking();
                }
                yennkasaSocket = null;
            }
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

    @Override
    synchronized public void attemptReconnectIfRequired() {
        PLog.d(TAG, "re-attempting connection if required");
        if (!ConnectionUtils.isConnected()) {
            PLog.d(TAG, "not re-connecting because user has no internet connection");
        }
        if (Config.isAppOpen() || (messageQueue.isStarted() && messageQueue.getPending() > 0)) {
            PLog.d(TAG, "re-attempting connection because " + (Config.isAppOpen() ? "app is opened" :
                    "we have more messages to send and we were disconnected unaware user has internet access"));
            if (yennkasaSocket == null) {
                initialiseSocket();
                yennkasaSocket.init();
            } else {
                if (!yennkasaSocket.isConnected()) {
                    yennkasaSocket.reconnect();
                }
            }
        }
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
            if (reason == PROCESSED || reason == WAITING_FOR_ACK) {
                PLog.d(TAG, "successfully sent item %s", item.toString());
                if (reason == WAITING_FOR_ACK) {
                    synchronized (sendListeners) {
                        for (SendListener sendListener : sendListeners) {
                            sendListener.onSentSucceeded(data);
                        }
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
            synchronized (SenderImpl.this) {
                if (shuttingDown && messageQueue.getPending() == 0) {
                    shuttingDown = false;
                    if (yennkasaSocket != null && !yennkasaSocket.isConnected()) {
                        yennkasaSocket.disConnectBlocking();
                        yennkasaSocket = null;
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
    private final YennkasaSocket.YennkasaSocketListener listener = new YennkasaSocket.YennkasaSocketListener() {
        @Override
        public void onMessage(byte[] bytes) {
            try {
                parser.feed(bytes);
            } catch (MessageParser.MessageParserException e) {
                PLog.f(TAG, e.getMessage(), e);
                if (BuildConfig.DEBUG) {
                    throw new RuntimeException(e);
                }
                if (e.getCause() instanceof MessagePackerException) {
                    if (((MessagePackerException) e.getCause()).getErrorCode() == DECRYPTION_FAILED) {
                        //this is an encryptionException!!!
                        MessageEncryptor.EncryptionException ew = (MessageEncryptor.EncryptionException)
                                e.getCause().getCause();
                        if (ew.getErrorCode() == MessageEncryptor.EncryptionException.INVALID_PUBLIC_KEY) {
                            // TODO: 12/22/16 this could be because the sender used our stale public key
                            //the sender must detect this by checking peridically from the server for
                            //changing public keys. and updating it's local cache, and resending dropped messages
                            //if possible
                        }
                    }
                }
            }
            messageQueue.ackWaitingItems();
        }

        @Override
        public void onMessage(String message) {
            onMessage(message.getBytes());
        }

        @Override
        public void onOpen() {
            ensureStarted();
            MessengerBus.get(PAIRAPP_CLIENT_LISTENABLE_BUS).postSticky(Event.createSticky(SOCKET_CONNECTION, null, CONNECTED));
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
            if (messageQueue.isStarted()) {
                messageQueue.pauseProcessing();
            }
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
                        yennkasaSocket = null;
                        System.gc();
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
            if (messageQueue.isStarted()) {
                messageQueue.pauseProcessing();
            }
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
        public synchronized void ackAllWaitingMessages() {
            messageQueue.ackWaitingItems();
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
                messageQueue.reScheduleAllProcessingItemsForProcessing();
                messageQueue.pauseProcessing();
            }
        }
    };

    public interface Authenticator {

        @NonNull
        String getToken();

        @NonNull
        String requestNewToken() throws YennkasaException;
    }

    private byte[] encodeToBytes(String data) {
        return Base64.decode(data, Base64.DEFAULT);
    }

    private static String encodeToString(byte[] data) {
        return Base64.encodeToString(data, Base64.DEFAULT);
    }

    @Nullable
    private String authToken;

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
                        SenderImpl.this.authToken = (String) event.getData();
                        GenericUtils.ensureNotEmpty(authToken);
                        initialiseSocket();
                        assert yennkasaSocket != null;
                        yennkasaSocket.init();
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
