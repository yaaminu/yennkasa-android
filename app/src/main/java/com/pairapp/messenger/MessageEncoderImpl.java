package com.pairapp.messenger;

import com.pairapp.data.Message;
import com.pairapp.data.User;
import com.pairapp.util.Config;

import io.realm.Realm;

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
        Realm realm = User.Realm(Config.getApplicationContext());
        try {
            return messagePacker.pack(Message.toJSON(message), message.getTo(), Message.isGroupMessage(realm, message));
        } finally {
            realm.close();
        }
    }
}
