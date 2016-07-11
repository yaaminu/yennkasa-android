package com.pairapp.net.sockets;

/**
 * @author aminu on 7/10/2016.
 */
public interface MessageParser {
    void feed(byte[] bytes);
}
