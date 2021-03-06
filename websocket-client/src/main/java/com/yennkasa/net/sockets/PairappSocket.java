package com.yennkasa.net.sockets;

import com.yennkasa.util.Config;
import com.yennkasa.util.ConnectionUtils;
import com.yennkasa.util.GenericUtils;
import com.yennkasa.util.PLog;
import com.yennkasa.util.ThreadUtils;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author aminu on 6/19/2016.
 */
class YennkasaSocket {
    public static final String TAG = YennkasaSocket.class.getSimpleName();

    private final WebSocketClient webSocketClient;
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    private YennkasaSocket(Map<String, String> headers, YennkasaSocketListener yennkasaSocketListener) {
        webSocketClient = new WebSocketClient.Builder()
                .endpoint(Config.getMessageEndpoint())
                .headers(headers)
                .listener(yennkasaSocketListener)
                .networkProvider(networkProvider)
                .logger(logger)
                .timeOut(5000)
                .build();
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

    public void reconnect() {
        if (!webSocketClient.isConnected()) {
            ConnectionUtils.registerConnectivityListener(connectivityChangeListener);
            webSocketClient.reconnectNow();
        }
    }

    public void disConnectBlocking() {
        ThreadUtils.ensureNotMain();
        if (!initialized.getAndSet(false)) {
            throw new IllegalStateException("not initialised");
        }
        ConnectionUtils.unRegisterConnectivityListener(connectivityChangeListener);
        webSocketClient.closeConnectionBlocking();
    }

    public boolean send(byte[] bytes) {
        ThreadUtils.ensureNotMain();
        if (initialized.get()) {
            return webSocketClient.send(bytes);
        } else {
            throw new IllegalStateException("not initialised, did you forget to call init()");
        }
    }

    public static YennkasaSocket create(Map<String, String> headers, YennkasaSocketListener listener) {
        ThreadUtils.ensureNotMain();
        GenericUtils.ensureNotNull(headers);
        return new YennkasaSocket(headers, listener);
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
    private final Logger logger = new Logger() {
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
                case W:
                    PLog.w(tag, message, args);
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
            YennkasaSocket.this.changeListener = listener;
        }
    };

    interface YennkasaSocketListener extends ClientListener {

    }
}
