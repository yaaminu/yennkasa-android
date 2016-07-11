package com.pairapp.messenger;

import com.pairapp.net.sockets.MessageParser;

/**
 * @author aminu on 7/1/2016.
 */
class MessageParserImpl implements MessageParser {

    private final MessagePacker messagePacker;

    public MessageParserImpl(MessagePacker messagePacker) {
        this.messagePacker = messagePacker;
    }

    @Override
    public void feed(byte[] bytes) {
        messagePacker.unpack(bytes);
    }
}
