package com.pairapp.messenger;

import android.util.Base64;

import com.pairapp.net.sockets.MessageParser;
import com.pairapp.util.PLog;

/**
 * @author aminu on 7/1/2016.
 */
class MessageParserImpl implements MessageParser {

    private static final String TAG = "MessageParserImpl";
    private final MessagePacker messagePacker;

    public MessageParserImpl(MessagePacker messagePacker) {
        this.messagePacker = messagePacker;
    }

    @Override
    public void feed(byte[] bytes) {
        messagePacker.unpack(bytes);
    }

    @Override
    public void feedBase64(String message) {
        PLog.d(TAG, "parser received a base64 message");
        PLog.d(TAG, "message: %s", message);
        feed(Base64.decode(message, Base64.DEFAULT));
    }
}
