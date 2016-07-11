package com.pairapp.messenger;

import com.pairapp.data.Message;
import com.pairapp.net.FileApi;
import com.pairapp.net.sockets.SendListener;
import com.pairapp.net.sockets.Sendable;
import com.pairapp.net.sockets.Sender;
import com.pairapp.util.FileUtils;
import com.pairapp.util.SimpleDateUtil;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * a dispatcher that uses {@link com.pairapp.net.sockets.WebSocketClient} for sending
 * messages
 *
 * @author aminu on 6/20/2016.
 */
public class WebSocketDispatcher extends AbstractMessageDispatcher {

    private final Sender sender;
    private final MessageEncoder messageEncoder;
    private final Map<String, String> idMaps;

    private final SendListener sendListener = new SendListener() {
        @Override
        public void onSentSucceeded(byte[] data) {
            String hash = FileUtils.hash(data);
            onSent(idMaps.get(hash));
            idMaps.remove(hash);
        }

        @Override
        public void onSendFailed(byte[] data) {
            String hash = FileUtils.hash(data);
            onFailed(idMaps.get(hash), "error internal");
            idMaps.remove(hash);
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
        idMaps = new ConcurrentHashMap<>(4);
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
    protected void dispatchToGroup(Message message, List<String> members) {
        dispatchToUser(message);
    }

    @Override
    protected void dispatchToUser(Message message) {
        byte[] encoded = messageEncoder.encode(message);
        idMaps.put(FileUtils.hash(encoded), message.getId());
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
