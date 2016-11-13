package com.pairapp.messenger;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;

import com.pairapp.data.Message;
import com.pairapp.net.FileApi;
import com.pairapp.net.sockets.SendListener;
import com.pairapp.net.sockets.Sendable;
import com.pairapp.net.sockets.Sender;
import com.pairapp.util.Config;
import com.pairapp.util.FileUtils;
import com.pairapp.util.SimpleDateUtil;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * a dispatcher that uses {@link com.pairapp.net.sockets.WebSocketClient} for sending
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
            , MessageEncoder codec) {
        return new WebSocketDispatcher(fileApi, monitor, socket, codec);
    }

    private WebSocketDispatcher(FileApi fileApi,
                                DispatcherMonitor monitor, Sender socket, MessageEncoder messageEncoder) {
        super(fileApi, monitor);
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
        byte[] encoded = messageEncoder.encode(message);
        Config.getPreferences(SEND_QUEUE_PREFS).edit().putString(FileUtils.hash(encoded), message.getId()).commit();
        sender.sendMessage(createSendable(message.getTo() + ":" + message.getFrom(), encoded));
    }

    @Override
    public boolean isClosed() {
        return closed.get();
    }

    private Sendable createSendable(String collapseKey, byte[] message) {
        return new Sendable.Builder()
                .data(sender.bytesToString(message))
                .collapseKey(collapseKey + "message")
                .validUntil(System.currentTimeMillis() + SimpleDateUtil.ONE_HOUR * 12) //12 hours
                .maxRetries(Sendable.RETRY_FOREVER)
                .surviveRestarts(true)
                .startProcessingAt(System.currentTimeMillis())
                .build();
    }

    interface MessageEncoder {
        byte[] encode(Message message);
    }
}
