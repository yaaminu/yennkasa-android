package com.pairapp.net.sockets;

import com.pairapp.util.Config;
import com.pairapp.util.ConnectionUtils;
import com.pairapp.util.GenericUtils;
import com.pairapp.util.PLog;
import com.pairapp.util.ThreadUtils;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author aminu on 6/19/2016.
 */
public class PairappSocket {
    public static final String TAG = PairappSocket.class.getSimpleName();

    private final MessageParser parser;
    private final WebSocketClient webSocketClient;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final Set<SendListener> sendListeners;

    private PairappSocket(Map<String, String> headers, MessageParser parser) {
        this.parser = parser;
        webSocketClient = new WebSocketClient.Builder()
                .endpoint(Config.getMessageEndpoint())
                .headers(headers)
                .listener(listener)
                .networkProvider(networkProvider)
                .logger(logger)
                .timeOut(5000)
                .build();
        this.sendListeners = new HashSet<>(2);
    }

    public void addSendListener(SendListener sendListener) {
        if (!initialized.get()) {
            throw new IllegalStateException("not initialised");
        }
        GenericUtils.ensureNotNull(sendListener);
        synchronized (sendListeners) {
            sendListeners.add(sendListener);
        }
    }

    public void unRegisterSendListener(SendListener sendListener) {
        if (!initialized.get()) {
            throw new IllegalStateException("not initialised");
        }
        GenericUtils.ensureNotNull(sendListener);
        sendListeners.remove(sendListener);
    }

    public void init() {
        ThreadUtils.ensureNotMain();
        if (initialized.getAndSet(true)) {
            PLog.w(TAG, "already initialized");
            return;
        }
        ConnectionUtils.registerConnectivityListener(connectivityChangeListener);
        webSocketClient.connectBlocking();
    }

    public boolean isConnected() {
        return initialized.get() && webSocketClient.isConnected();
    }

    public void disConnectBlocking() {
        ThreadUtils.ensureNotMain();
        if (!initialized.getAndSet(false)) {
            throw new IllegalStateException("not initialised");
        }
        webSocketClient.closeConnectionBlocking();
        synchronized (sendListeners) {
            sendListeners.clear();
        }
    }

    public void send(byte[] bytes) {
        ThreadUtils.ensureNotMain();
        if (initialized.get()) {
            webSocketClient.send(bytes);

        } else {
            throw new IllegalStateException("not initialised, did you forget to call init()");
        }
    }

    public static PairappSocket create(Map<String, String> headers, MessageParser parser) {
        ThreadUtils.ensureNotMain();
        GenericUtils.ensureNotNull(headers, parser);
        return new PairappSocket(headers, parser);
    }

    public interface SendListener {
        void onSentSucceeded(byte[] data);

        void onSendFailed(byte[] data);
    }

    public interface MessageParser {
        void feed(byte[] bytes);
    }

    private final ConnectionUtils.ConnectivityListener connectivityChangeListener = new ConnectionUtils.ConnectivityListener() {
        @Override
        public void onConnectivityChanged(boolean connected) {
            if (changeListener != null) {
                changeListener.notifyNetworkChanged(connected);
            }
        }
    };
    @SuppressWarnings("FieldCanBeLocal")
    private final WebSocketClient.Logger logger = new WebSocketClient.Logger() {
        @Override
        public void Log(int level, String tag, String message, Throwable cause) {
            switch (level) {
                case V:
                    PLog.v(tag, message, cause);
                    break;
                case D:
                    PLog.d(tag, message, cause);
                    break;
                case I:
                    PLog.i(tag, message, cause);
                    break;
                case E:
                    PLog.e(tag, message, cause);
                    break;
                default:
                    throw new AssertionError();
            }
        }

        @Override
        public void Log(int level, String tag, String message, Object... args) {
            switch (level) {
                case V:
                    PLog.v(tag, message, args);
                    break;
                case D:
                    PLog.d(tag, message, args);
                    break;
                case I:
                    PLog.i(tag, message, args);
                    break;
                case E:
                    PLog.e(tag, message, args);
                    break;
                default:
                    throw new AssertionError();
            }
        }
    };

    private WebSocketClient.NetworkChangeListener changeListener;
    @SuppressWarnings("FieldCanBeLocal")
    private final WebSocketClient.NetworkProvider networkProvider = new WebSocketClient.NetworkProvider() {
        @Override
        public boolean connected() {
            return ConnectionUtils.isConnectedOrConnecting();
        }

        @Override
        public void registerNetworkChangeListener(WebSocketClient.NetworkChangeListener listener) {
            PairappSocket.this.changeListener = listener;
        }
    };

    @SuppressWarnings("FieldCanBeLocal")
    private final WebSocketClient.ClientListener listener = new WebSocketClient.ClientListener() {
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
            // TODO: 6/20/2016 fire an event that we are connected
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
            // TODO: 6/20/2016 fire an event that we are connecting
        }

        @Override
        public void onSendError(boolean isBinary, byte[] data) {
            synchronized (sendListeners) {
                for (SendListener sendListener : sendListeners) {
                    sendListener.onSendFailed(data);
                }
            }
        }

        @Override
        public void onSendSuccess(boolean isBinary, byte[] data) {
            synchronized (sendListeners) {
                for (SendListener sendListener : sendListeners) {
                    sendListener.onSentSucceeded(data);
                }
            }
        }

        @Override
        public void onDisConnectedUnexpectedly() {
            // TODO: 6/20/2016 fire an event that we are disconnected
        }
    };
}