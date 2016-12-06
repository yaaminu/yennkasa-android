package com.pairapp.net.sockets;

import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketException;
import com.neovisionaries.ws.client.WebSocketFactory;
import com.neovisionaries.ws.client.WebSocketListener;
import com.neovisionaries.ws.client.WebSocketState;

import java.io.IOException;
import java.net.URI;

/**
 * @author aminu on 7/11/2016.
 */
class WebSocketImpl implements IWebSocket {
    public static final WebSocketFactory SOCKET_FACTORY = new WebSocketFactory();
    public static final int QUEUE_SIZE = 3;
    private final WebSocket webSocket;

    public WebSocketImpl(URI uri, int timeout) throws IOException {
        webSocket = SOCKET_FACTORY.createSocket(uri, timeout).setFrameQueueSize(QUEUE_SIZE);
    }

    @Override
    public boolean send(byte[] message) {
        if(webSocket.isOpen() && webSocket.getSocket() != null && webSocket.getSocket().isConnected()) {
            webSocket.sendBinary(message);
            return true;
        }
        return false;
    }

    @Override
    public void connect() throws WebSocketException {
        webSocket.connect();
    }

    @Override
    public void sendClose(int closeCode) {
        webSocket.sendClose(closeCode);
    }

    @Override
    public void addListener(WebSocketListener listener) {
        webSocket.addListener(listener);
    }

    @Override
    public void removeListener(WebSocketListener listener) {
        webSocket.removeListener(listener);
    }

    @Override
    public boolean isOpen() {
        return webSocket.isOpen();
    }

    @Override
    public void disconnect(int closeCode) {
        webSocket.disconnect(closeCode);
    }

    @Override
    public WebSocketState getState() {
        return webSocket.getState();
    }

    @Override
    public void setPingInterval(int pingInterval) {
        webSocket.setPingInterval(pingInterval);
    }

    @Override
    public void addHeader(String key, String value) {
        webSocket.addHeader(key, value);
    }

    @Override
    public void removeHeaders(String key) {
        webSocket.removeHeaders(key);
    }

    @Override
    public void sendClose() {
        webSocket.sendClose();
    }
}
