package com.yennkasa.net.sockets;

import com.neovisionaries.ws.client.WebSocketException;
import com.neovisionaries.ws.client.WebSocketListener;
import com.neovisionaries.ws.client.WebSocketState;

/**
 * @author aminu on 7/11/2016.
 */
public interface IWebSocket {

    boolean send(byte[] message);

    void connect() throws WebSocketException;

    void sendClose(int closeCode);

    void addListener(WebSocketListener listener);

    void removeListener(WebSocketListener listener);

    boolean isOpen();

    void disconnect(int closeCode);

    WebSocketState getState();

    void setPingInterval(int pingInterval);

    void addHeader(String key, String value);

    void removeHeaders(String key);

    void sendClose();

    void sendHeartbeat();
}
