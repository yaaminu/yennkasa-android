package com.yennkasa.messenger;

import com.yennkasa.data.Message;
import com.yennkasa.data.User;
import com.yennkasa.util.Config;

import io.realm.Realm;

/**
 * @author aminu on 7/1/2016.
 */
class MessageEncoderImpl implements WebSocketDispatcher.MessageEncoder {
    private static final String TAG = "MessageEncoderImpl";
    private final MessagePacker messagePacker;

    public MessageEncoderImpl(MessagePacker messagePacker) {
        this.messagePacker = messagePacker;
    }

    @Override
    public byte[] encode(Message message) throws MessagePacker.MessagePackerException {
        Realm realm = User.Realm(Config.getApplicationContext());
        try {
            return messagePacker.packNormalMessage(Message.toJSON(message), message.getTo(), Message.isGroupMessage(realm, message));
        } finally {
            realm.close();
        }
    }
}
