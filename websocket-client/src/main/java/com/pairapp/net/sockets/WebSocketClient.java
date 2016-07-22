package com.pairapp.net.sockets;

import android.util.Log;

import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketCloseCode;
import com.neovisionaries.ws.client.WebSocketError;
import com.neovisionaries.ws.client.WebSocketException;
import com.neovisionaries.ws.client.WebSocketFrame;
import com.neovisionaries.ws.client.WebSocketListener;
import com.neovisionaries.ws.client.WebSocketState;
import com.pairapp.net.BuildConfig;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author aminu on 6/19/2016.
 */
public class WebSocketClient {
    private static final String TAG = WebSocketClient.class.getSimpleName();
    public static final int PING_INTERVAL = 1000 * 60 * 3;
    private final Map<String, String> headers;
    private final Logger logger;
    private final ClientListener listener;
    private final NetworkProvider networkProvider;
    private IWebSocket internalWebSocket;
    private final AtomicBoolean selfClosed = new AtomicBoolean(false);
    private final Timer timer;
    private final List<Object> backLog;
    private final URI uri;
    private final int timeout;

    private WebSocketClient(URI uri, int timeout,
                            Logger logger, ClientListener clientListener,
                            NetworkProvider networkProvider, Map<String, String> headers) {
        this.uri = uri;
        this.timeout = timeout;
        this.logger = logger;
        this.listener = clientListener;
        this.networkProvider = networkProvider;
        this.headers = headers;
        timer = new Timer("webSocketClient timer", true);
        backLog = new ArrayList<>(2);
    }


    interface NetworkProvider {
        boolean connected();

        void registerNetworkChangeListener(NetworkChangeListener listener);

        NetworkProvider DEFAULT_PROVIDER = new NetworkProvider() {
            @Override
            public boolean connected() {
                return true;
            }

            @Override
            public void registerNetworkChangeListener(NetworkChangeListener listener) {
                //do nothing with this since we always return connected in connected
            }

        };
    }


    public boolean isConnected() {
        return internalWebSocket.isOpen();
    }

    public synchronized void send(byte[] bytes) {
        if (internalWebSocket.isOpen()) {
            internalWebSocket.send(bytes);
        } else {
            backLog.add(bytes);
        }
    }

    public synchronized void closeConnectionBlocking() {
        if (selfClosed.getAndSet(true)) return;
        if (internalWebSocket.isOpen()) {
            internalWebSocket.sendClose();
        } else {
            internalWebSocket.disconnect(WebSocketCloseCode.ABNORMAL);
        }
    }

    public synchronized void connectBlocking() {
        if (!networkProvider.connected()) {
            logger.Log(Log.VERBOSE, TAG, "not connected to the internet");
            return;
        }
        if (internalWebSocket.isOpen() || internalWebSocket.getState() == WebSocketState.CONNECTING) {
            logger.Log(Logger.W, TAG, "attempted to connect while we are connected or still connecting");
            return;
        }
        setUpWebSocket();
        try {
            internalWebSocket.addListener(webSocketListener);
            internalWebSocket.connect();
            reconnectDelay = DEFAULT_DELAY;
        } catch (WebSocketException e) {
            attemptReconnect();
        }
    }


    private void setUpWebSocket() {
        internalWebSocket.setPingInterval(PING_INTERVAL);
        for (Map.Entry<String, String> header : headers.entrySet()) {
            internalWebSocket.addHeader(header.getKey(), header.getValue());
        }
    }

    public static class Builder {
        private URI uri;
        private Logger logger;
        private ClientListener listener;
        private Map<String, String> headers;
        private NetworkProvider networkProvider;
        private int timeout;

        public Builder endpoint(String uri) {
            ensureNotEmpty(uri);
            try {
                //noinspection unused
                URL foo = new URL(uri); //check if uri is valid
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException(e);
            }
            try {
                this.uri = new URI(uri);
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException(e);
            }
            return this;
        }


        public Builder logger(Logger logger) {
            ensureNotNull(logger);
            this.logger = logger;
            return this;
        }

        public Builder listener(ClientListener listener) {
            ensureNotNull(listener);
            this.listener = listener;
            return this;
        }

        public Builder networkProvider(NetworkProvider networkProvider) {
            ensureNotNull(networkProvider);
            this.networkProvider = networkProvider;
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            ensureNotNull(headers);
            this.headers = headers;
            return this;
        }

        public Builder timeOut(int timeout) {
            if (timeout < 0) {
                throw new IllegalArgumentException("invalid time out");
            }
            this.timeout = timeout;
            return this;
        }

        public WebSocketClient build() {
            ensureNotNull(listener);
            ensureNotNull(this.uri);

            if (networkProvider == null) {
                networkProvider = NetworkProvider.DEFAULT_PROVIDER;
            }
            if (timeout < 0) {
                timeout = 1000;
            }
            if (headers == null) {
                headers = Collections.emptyMap();
            }
            if (logger == null) {
                logger = Logger.DEFAULT_LOGGER;
            }
            WebSocketClient client = new WebSocketClient(
                    this.uri, timeout, logger, listener, networkProvider, headers
            );
            client.networkProvider.registerNetworkChangeListener(client.networkChangeListener);
            try {
                client.internalWebSocket = new WebSocketImpl(client.uri, client.timeout);
                return client;
            } catch (IOException e) {
                throw new AssertionError(e);
            }
        }
    }


    private static void ensureNotEmpty(String text) {
        if (text == null || "".equals(text.trim())) {
            throw new IllegalArgumentException("empty string");
        }
    }

    private static void ensureNotNull(Object o) {
        if (o == null) {
            throw new IllegalArgumentException("null");
        }
    }

    private final WebSocketListener webSocketListener = new WebSocketListener() {
        @Override
        public void onStateChanged(WebSocket websocket, WebSocketState newState) throws Exception {
            logger.Log(Logger.V, TAG, "state changed: new state is %s", newState.toString());
            long oneMinute = 60 * 1000;
            if (newState == WebSocketState.CONNECTING) {
                if (reconnectDelay < oneMinute) {
                    listener.onConnecting();
                } else {
                    listener.onReconnectionTakingTooLong();
                }
            } else if (newState == WebSocketState.CLOSED && !selfClosed.get() && reconnectDelay > oneMinute) {
                listener.onDisConnectedUnexpectedly();
            }
            //report all other sate in their respective callbacks
        }

        @Override
        public void onConnected(WebSocket websocket, Map<String, List<String>> headers) throws Exception {
            logger.Log(Logger.V, TAG, "connected to %s @ %s", websocket.getURI().toString(), new Date().toString());
            synchronized (WebSocketClient.this) {
                int i = 0;
                for (int index = 0; index < backLog.size(); index++) {
                    Object o = backLog.get(i);
                    if (o instanceof String) {
                        internalWebSocket.send(((String) o).getBytes());
                    } else {
                        internalWebSocket.send((byte[]) o);
                    }
                    i++;
                }
                if (!backLog.isEmpty()) {
                    //only remove the messages in the backlog we could send
                    List<Object> subString = backLog.subList(0, i + 1);
                    for (Object o : subString) {
                        backLog.remove(o);
                    }
                }
            }
            listener.onOpen();
        }

        @Override
        public void onConnectError(WebSocket websocket, WebSocketException cause) throws Exception {
            logger.Log(Logger.E, TAG, cause.getMessage(), cause);
            attemptReconnect();
        }

        @Override
        public void onDisconnected(WebSocket websocket, WebSocketFrame serverCloseFrame, WebSocketFrame clientCloseFrame, boolean closedByServer) throws Exception {
            String reason = serverCloseFrame == null ? (clientCloseFrame == null ? "unknown" : clientCloseFrame.getCloseReason()) : serverCloseFrame.getCloseReason();
            logger.Log(Logger.I, TAG, "disconnected by %s, reason %s", closedByServer ? " server" : " client", reason);
            if (closedByServer) {
                if (serverCloseFrame != null) {
                    logger.Log(Logger.D, TAG,
                            "server disconnected us.  with reason, \"%s\" are you sure you are authorized to access this endpoint?",
                            serverCloseFrame.getCloseReason());
                    listener.onClose(serverCloseFrame.getCloseCode(), serverCloseFrame.getCloseReason());
                } else {
                    listener.onClose();
                }
            } else {
                if (!selfClosed.get()) { //this is not a normal close (caused by something like lost network disconnection)
                    logger.Log(Logger.V, TAG, "disconnected unexpectedly");
                    attemptReconnect();
                } else {
                    selfClosed.set(false);
                    timer.cancel();
                    logger.Log(Logger.V, TAG, " socket connection ended ");
                }
            }
        }

        @Override
        public void onFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {
            //do nothing
        }

        @Override
        public void onContinuationFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {
            //do nothing
        }

        @Override
        public void onTextFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {
            //do nothing
        }

        @Override
        public void onBinaryFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {
            //do nothing
        }

        @Override
        public void onCloseFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {
            // TODO: 6/19/2016 what should we do?
        }

        @Override
        public void onPingFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {
            //do nothing
        }

        @Override
        public void onPongFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {
            //do nothing
        }

        @Override
        public void onTextMessage(WebSocket websocket, String text) throws Exception {
            logger.Log(Logger.V, TAG, "text message from server at %s", websocket.getURI().toString());
            logger.Log(Logger.V, TAG, "text message is: \" %s \"", text);
            listener.onMessage(text);
        }

        @Override
        public void onBinaryMessage(WebSocket websocket, byte[] binary) throws Exception {
            logger.Log(Logger.V, TAG, "binary message from server at %s", websocket.getURI().toString());
            logger.Log(Logger.V, TAG, "binary message is: \" %s \" bytes long", binary.length);
            listener.onMessage(binary);
        }

        @Override
        public void onSendingFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {
        }

        @Override
        public void onFrameSent(WebSocket websocket, WebSocketFrame frame) throws Exception {
            if (frame.isDataFrame()) {
                listener.onSendSuccess(frame.isBinaryFrame(), frame.getPayload());
            }
        }

        @Override
        public void onFrameUnsent(WebSocket websocket, WebSocketFrame frame) throws Exception {
        }

        @Override
        public void onError(WebSocket websocket, WebSocketException cause) throws Exception {
            logger.Log(Logger.E, TAG, String.format(" error on web socket with url %s", websocket.getURI().toString()), cause);
            listener.onError(cause);
        }

        @Override
        public void onFrameError(WebSocket websocket, WebSocketException cause, WebSocketFrame frame) throws Exception {

        }

        @Override
        public void onMessageError(WebSocket websocket, WebSocketException cause, List<WebSocketFrame> frames) throws Exception {

        }

        @Override
        public void onMessageDecompressionError(WebSocket websocket, WebSocketException cause, byte[] compressed) throws Exception {

        }

        @Override
        public void onTextMessageError(WebSocket websocket, WebSocketException cause, byte[] data) throws Exception {

        }

        @Override
        public void onSendError(WebSocket websocket, WebSocketException cause, WebSocketFrame frame) throws Exception {
            if (cause.getError() == WebSocketError.IO_ERROR_IN_WRITING) {
                if (frame != null) {
                    listener.onSendError(frame.isBinaryFrame(), frame.getPayload());
                }
            }
        }

        @Override
        public void onUnexpectedError(WebSocket websocket, WebSocketException cause) throws Exception {

        }

        @Override
        public void handleCallbackError(WebSocket websocket, Throwable cause) throws Exception {
            logger.Log(Logger.W, TAG, " call back threw an error", cause);
            if (BuildConfig.DEBUG) {
                throw new RuntimeException(cause);
            }
        }

        @Override
        public void onSendingHandshake(WebSocket websocket, String requestLine, List<String[]> headers) throws Exception {
        }
    };

    private static final long DEFAULT_DELAY = 1000;
    private long reconnectDelay = DEFAULT_DELAY;
    private Random random;

    private class ReconnectTimerTask extends TimerTask {
        @Override
        public void run() {
            logger.Log(Logger.V, TAG, "attempting reconnection");
            reconnect();
        }
    }

    private void attemptReconnect() {
        WebSocketState state = internalWebSocket.getState();
        if (state == WebSocketState.CONNECTING
                || state == WebSocketState.OPEN
                || state == WebSocketState.CLOSING
                ) {
            logger.Log(Logger.V, TAG, "not reconnecting since we are already closed,connecting or even connected");
            return;
        }
        if (networkProvider.connected()) {
            calculateReconnectTimeout();
            logger.Log(Logger.V, TAG, " attempt reconnection after %s", reconnectDelay);
            timer.schedule(new ReconnectTimerTask(), reconnectDelay);
        } else {
            logger.Log(Logger.V, TAG, " can't reconnect since network provider says we are not connected to the internet");
        }
    }

    private synchronized void reconnect() {
        if (internalWebSocket.isOpen() || internalWebSocket.getState() == WebSocketState.CONNECTING) {
            logger.Log(Logger.V, TAG, "we are connecting or even connected ");
            return;
        }

        try {
            internalWebSocket.removeListener(webSocketListener);
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                internalWebSocket.removeHeaders(entry.getKey());
            }
            internalWebSocket.sendClose(WebSocketCloseCode.ABNORMAL);
            internalWebSocket = new WebSocketImpl(uri, timeout);
            connectBlocking();
        } catch (IOException e) {
            logger.Log(Logger.E, TAG, e.getMessage(), e);
        }
    }

    private synchronized void calculateReconnectTimeout() {
        if (random == null) {
            random = new SecureRandom();
        }
        int deviation = /*milliseconds*/ (int) Math.abs(random.nextDouble() * 1000);
        //LINEAR
        if (reconnectDelay > 1000 * 60 * 30) {
            reconnectDelay += deviation;
        } else {
            reconnectDelay = (2 * reconnectDelay) + deviation;
        }
    }

    private final NetworkChangeListener networkChangeListener = new NetworkChangeListener() {
        @Override
        public void notifyNetworkChanged(boolean connected) {
            logger.Log(Logger.D, TAG, "network: " + (connected ? "" : "dis") + "connected");
            if (connected && !internalWebSocket.isOpen()) {
                reconnectDelay = DEFAULT_DELAY;
                attemptReconnect();
            }
            if (!connected) {
                listener.onDisConnectedUnexpectedly();
            } else {
                if (internalWebSocket.isOpen()) listener.onOpen();
            }
        }
    };

    interface NetworkChangeListener {
        void notifyNetworkChanged(boolean connected);
    }
}
