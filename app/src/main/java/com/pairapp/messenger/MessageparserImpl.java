package com.pairapp.messenger;

import com.pairapp.net.sockets.PairappSocket;

/**
 * @author aminu on 7/1/2016.
 */
class MessageParserImpl implements PairappSocket.MessageParser {

    private final MessagePacker messagePacker;

    public MessageParserImpl(MessagePacker messagePacker) {
        this.messagePacker = messagePacker;
    }

    @Override
    public void feed(byte[] bytes) {
        messagePacker.unpack(bytes);
    }
}
