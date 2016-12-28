package com.yennkasa.messenger;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;

import com.yennkasa.data.Message;
import com.yennkasa.data.util.MessageUtils;
import com.yennkasa.net.FileApi;
import com.yennkasa.net.sockets.SendListener;
import com.yennkasa.net.sockets.Sender;
import com.yennkasa.net.sockets.SenderImpl;
import com.yennkasa.security.MessageEncryptor;
import com.yennkasa.util.Config;
import com.yennkasa.util.FileUtils;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * a dispatcher that uses {@link com.yennkasa.net.sockets.WebSocketClient} for sending
 * messages
 *
 * @author aminu on 6/20/2016.
 */
public class WebSocketDispatcher extends AbstractMessageDispatcher {

    public static final String SEND_QUEUE_PREFS = "messagesAwaitingAcks.data";
    private final Sender sender;
    private final MessageEncoder messageEncoder;

    private final SendListener sendListener = new SendListener() {
        @SuppressLint("CommitPrefEdits")
        @Override
        public void onSentSucceeded(byte[] data) {
            String hash = FileUtils.hash(data);
            SharedPreferences preferences = Config.getPreferences(SEND_QUEUE_PREFS);
            onSent(preferences.getString(hash, ""));
            preferences.edit().remove(hash).commit();
        }

        @SuppressLint("CommitPrefEdits")
        @Override
        public void onSendFailed(byte[] data) {
            String hash = FileUtils.hash(data);
            SharedPreferences preferences = Config.getPreferences(SEND_QUEUE_PREFS);
            onFailed(preferences.getString(hash, ""), "error internal");
            preferences.edit().remove(hash).commit();
        }
    };

    public static WebSocketDispatcher create(FileApi fileApi
            , DispatcherMonitor monitor, Sender socket
            , MessageEncoder codec, MessageEncryptor encryptor) {
        return new WebSocketDispatcher(fileApi, monitor, socket, codec, encryptor);
    }

    private WebSocketDispatcher(FileApi fileApi,
                                DispatcherMonitor monitor, Sender socket, MessageEncoder messageEncoder, MessageEncryptor encryptor) {
        super(fileApi, monitor, encryptor);
        this.sender = socket;
        this.messageEncoder = messageEncoder;
        socket.addSendListener(sendListener);
    }

    private final AtomicBoolean closed = new AtomicBoolean(false);

    @Override
    protected boolean doClose() {
        sender.removeSendListener(sendListener);
        closed.set(true);
        return true;
    }

    @Override
    protected void dispatchToGroup(Message message) {
        dispatchToUser(message);
    }

    @SuppressLint("CommitPrefEdits")
    @Override
    protected void dispatchToUser(Message message) {
        byte[] encoded;
        try {
            encoded = messageEncoder.encode(message);
            Config.getPreferences(SEND_QUEUE_PREFS).edit().putString(FileUtils.hash(encoded), message.getId()).commit();
            sender.sendMessage(SenderImpl.createMessageSendable(message.getId(), encoded));
        } catch (MessagePacker.MessagePackerException e) {
            onFailed(message.getId(), MessageUtils.ERROR_ENCODING_FAILED);
        }
    }

    @Override
    public boolean isClosed() {
        return closed.get();
    }

    interface MessageEncoder {
        byte[] encode(Message message) throws MessagePacker.MessagePackerException;
    }
}
