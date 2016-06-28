package com.pairapp.messenger;

import com.pairapp.data.Message;
import com.pairapp.net.FileApi;
import com.pairapp.net.sockets.PairappSocket;
import com.pairapp.util.FileUtils;

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

    private final PairappSocket pairappSocket;
    private final MessageEncoder messageEncoder;
    private final Map<String, String> idMaps;

    private final PairappSocket.SendListener sendListener = new PairappSocket.SendListener() {
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
            , DispatcherMonitor monitor, PairappSocket socket
            , MessageEncoder codec) {
        return new WebSocketDispatcher(fileApi, monitor, socket, codec);
    }

    private WebSocketDispatcher(FileApi fileApi,
                                DispatcherMonitor monitor, PairappSocket socket, MessageEncoder messageEncoder) {
        super(fileApi, monitor);
        this.pairappSocket = socket;
        this.messageEncoder = messageEncoder;
        idMaps = new ConcurrentHashMap<>(4);
        socket.addSendListener(sendListener);
    }

    private final AtomicBoolean closed = new AtomicBoolean(false);

    @Override
    protected boolean doClose() {
        pairappSocket.unRegisterSendListener(sendListener);
        closed.set(true);
        return true;
    }

    @Override
    protected void dispatchToGroup(Message message, List<String> members) {
        dispatchToUser(message);
    }

    @Override
    protected void dispatchToUser(Message message) {
        if (pairappSocket.isConnected()) {
            byte[] encoded = messageEncoder.encode(message);
            idMaps.put(FileUtils.hash(encoded), message.getId());
            pairappSocket.send(encoded);
        } else {
            onFailed(message.getId(), ERR_USER_OFFLINE);
        }
    }

    @Override
    public boolean isClosed() {
        return closed.get();
    }

    interface MessageEncoder {
        byte[] encode(Message message);
    }
}
