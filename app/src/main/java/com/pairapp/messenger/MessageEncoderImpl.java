package com.pairapp.messenger;

import com.pairapp.data.Message;

/**
 * @author aminu on 7/1/2016.
 */
class MessageEncoderImpl implements WebSocketDispatcher.MessageEncoder {

    private final MessagePacker messagePacker;

    public MessageEncoderImpl(MessagePacker messagePacker) {
        this.messagePacker = messagePacker;
    }

    @Override
    public byte[] encode(Message message) {
        return messagePacker.pack(Message.toJSON(message), message.getTo(), Message.isGroupMessage(message));
    }
}
