package com.yennkasa.net.sockets;

/**
 * @author aminu on 7/10/2016.
 */
public interface SendListener {
    void onSentSucceeded(byte[] data);

    void onSendFailed(byte[] data);
}
