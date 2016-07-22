package com.pairapp.net.sockets;

/**
 * @author aminu on 7/11/2016.
 */
interface ClientListener {
    void onMessage(byte[] bytes);

    void onMessage(String message);

    void onOpen();

    void onClose(int code, String reason);

    void onClose();

    void onError(Exception e);

    void onSendError(boolean isBinary, byte[] data);

    void onSendSuccess(boolean isBinary, byte[] data);

    void onConnecting();

    void onDisConnectedUnexpectedly();

    void onReconnectionTakingTooLong();
}
