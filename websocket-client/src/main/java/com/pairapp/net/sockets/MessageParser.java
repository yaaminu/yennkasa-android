package com.pairapp.net.sockets;

/**
 * @author aminu on 7/10/2016.
 */
public interface MessageParser {
    void feed(byte[] bytes) throws MessageParserException;

    void feedBase64(String message) throws MessageParserException;


    public static class MessageParserException extends Exception {
        public MessageParserException(String message) {
            super(message);
        }

        public MessageParserException(Throwable cause) {
            super(cause);
        }
    }
}
