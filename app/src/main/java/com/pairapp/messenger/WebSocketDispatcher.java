package com.pairapp.messenger;

import com.pairapp.data.Message;
import com.pairapp.net.FileApi;
import com.pairapp.net.sockets.PairappSocket;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * a dispatcher that uses {@link com.pairapp.net.sockets.WebSocketClient} for sending
 * messages
 *
 * @author aminu on 6/20/2016.
 */
public class WebSocketDispatcher extends AbstractMessageDispatcher {

    private final PairappSocket pairappSocket;
    private final MessageCodec messageCodec;

    private final PairappSocket.SendListener sendListener = new PairappSocket.SendListener() {
        @Override
        public void onSentSucceeded(byte[] data) {
            onSent(messageCodec.decode(data).getId());
        }

        @Override
        public void onSendFailed(byte[] data) {
            onFailed(messageCodec.decode(data).getId(), "error internal");
        }
    };

    public static WebSocketDispatcher create(FileApi fileApi
            , DispatcherMonitor monitor, PairappSocket socket
            , MessageCodec codec) {
        return new WebSocketDispatcher(fileApi, monitor, socket, codec);
    }

    private WebSocketDispatcher(FileApi fileApi,
                                DispatcherMonitor monitor, PairappSocket socket, MessageCodec messageCodec) {
        super(fileApi, monitor);
        this.pairappSocket = socket;
        this.messageCodec = messageCodec;
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
        onFailed(message.getId(), "error unimplemented");
    }

    @Override
    protected void dispatchToUser(Message message) {
        if (pairappSocket.isConnected()) {
            pairappSocket.send(messageCodec.encode(message));
        } else {
            onFailed(message.getId(), ERR_USER_OFFLINE);
        }
    }

    @Override
    public boolean isClosed() {
        return closed.get();
    }

    interface MessageCodec {
        byte[] encode(Message message);

        Message decode(byte[] bytes);
    }
}
