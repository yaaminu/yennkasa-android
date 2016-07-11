package com.pairapp.net.sockets;

/**
 * @author aminu on 7/10/2016.
 */
public interface Sender {
    void sendMessage(Sendable sendable);

    void addSendListener(SendListener sendListener);

    void removeSendListener(SendListener sendListener);

    void shutdownSafely();

    String bytesToString(byte[] data);

    byte[] stringToBytes(String data);
}
