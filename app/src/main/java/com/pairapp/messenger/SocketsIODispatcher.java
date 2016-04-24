package com.pairapp.messenger;


import com.github.nkzawa.emitter.Emitter;
import com.pairapp.data.Message;
import com.pairapp.data.UserManager;
import com.pairapp.net.FileApi;
import com.pairapp.net.sockets.SocketIoClient;
import com.pairapp.util.Config;
import com.pairapp.util.PLog;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * an implementation of {@link Dispatcher} that uses web sockets(socketIo) to be
 * precise. clients must remember to {@code close()} it when they are done with it.
 * <p/>
 * the dispatcher's behaviour is undefined immediately you {@code close()} it and attempt
 * to use it
 *
 * @author by _2am on 9/6/2015.
 */

class SocketsIODispatcher extends AbstractMessageDispatcher {
    private static final String TAG = SocketsIODispatcher.class.getSimpleName();
    private static final String ERR_SOCKET_DICONNECTED = "socket disconnected";

    private final Emitter.Listener ON_MESSAGE_STATUS = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            try {
                JSONObject object = new JSONObject(args[0].toString());
                int status = object.getInt(Message.MSG_STS_STATUS);
                String messageId = object.getString(Message.MSG_STS_MESSAGE_ID);
                if (status == Message.STATE_SENT) {
                    PLog.i(TAG, "message sent");
//                    onSent(messageId);
                } else if (status == Message.STATE_RECEIVED) {
                    PLog.i(TAG, "message delivered");
                    onDelivered(messageId);
                } else if (status == Message.STATE_SEND_FAILED) {
                    PLog.i(TAG, "message dispatch failed");
                    onFailed(messageId, ERR_USER_OFFLINE);
                }
            } catch (JSONException e) {
                throw new RuntimeException();
            }
        }
    };
    private SocketIoClient socketIoClient;

    private static SocketsIODispatcher INSTANCE;

    private SocketsIODispatcher(FileApi api, DispatcherMonitor monitor) {
        super(api, monitor);
        socketIoClient = SocketIoClient.getInstance(Config.getMessageEndpoint(), UserManager.getMainUserId());
        socketIoClient.registerForEvent(Message.EVENT_MSG_STATUS, ON_MESSAGE_STATUS);
    }

    /**
     * returns the singleton instance. this is reference counted so
     * remember to pair every call to this method with {@link #close()}
     *
     * @param api a key value pair of credentials for file uploads and other services
     * @return the singleton instance
     */
    static synchronized Dispatcher<Message> getInstance(FileApi api, DispatcherMonitor monitor) {
        refCount.incrementAndGet();
        if (INSTANCE == null || INSTANCE.isClosed()) {
            INSTANCE = new SocketsIODispatcher(api, monitor);
        }
        return INSTANCE;
    }

    @Override
    protected void dispatchToGroup(Message message, List<String> members) {
        oops();
    }

    @Override
    protected void dispatchToUser(Message message) {
        if (!socketIoClient.isConnected()) {
            onFailed(message.getId(), ERR_SOCKET_DICONNECTED);
        }
        socketIoClient.send(Message.EVENT_MESSAGE, Message.toJSON(message));
        // optimistic notification even though we are not sure user has received the message or not
        onSent(message.getId());
    }

    private static final AtomicInteger refCount = new AtomicInteger(0);

    @Override
    protected boolean doClose() {
        if (refCount.decrementAndGet() <= 0) {
            socketIoClient.unRegisterEvent(Message.EVENT_MSG_STATUS, ON_MESSAGE_STATUS);
            socketIoClient.close();
            return true;
        }
        return false;
    }

    public synchronized final boolean isClosed() {
        return refCount.intValue() == 0;
    }

    private void oops() {
        throw new UnsupportedOperationException();
    }
}
