package com.idea.messenger;


import com.github.nkzawa.emitter.Emitter;
import com.idea.data.Message;
import com.idea.data.MessageJsonAdapter;
import com.idea.data.UserManager;
import com.idea.net.sockets.SocketIoClient;
import com.idea.util.Config;
import com.idea.util.PLog;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;

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

    private final Emitter.Listener ON_MESSAGE_STATUS = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            try {
                JSONObject object = new JSONObject(args[0].toString());
                int status = object.getInt(SocketIoClient.MSG_STS_STATUS);
                String messageId = object.getString(SocketIoClient.MSG_STS_MESSAGE_ID);
                if (status == Message.STATE_SENT) {
                    PLog.i(TAG, "message sent");
                    onSent(messageId);
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
    private final SocketIoClient socketIoClient;

    private SocketsIODispatcher(Map<String, String> credentials) {
        super(credentials);
        socketIoClient = SocketIoClient.getInstance(Config.getMessageEndpoint(), UserManager.getMainUserId());
        socketIoClient.registerForEvent(SocketIoClient.EVENT_MSG_STATUS, ON_MESSAGE_STATUS);
    }

    /**
     * create a new an instance of {@link SocketsIODispatcher}.
     * <p/>
     * remember to close it when you are done.
     *
     * @return an instance of this class.
     */
    static Dispatcher<Message> newInstance(Map<String, String> credentials) {
        return new SocketsIODispatcher(credentials);
    }

    @Override
    protected void dispatchToGroup(Message message, List<String> members) {
        oops();
    }

    @Override
    protected void dispatchToUser(Message message) {
        socketIoClient.send(MessageJsonAdapter.INSTANCE.toJSON(message));
    }

    @Override
    public void close() {
        super.close();
        socketIoClient.unRegisterEvent(SocketIoClient.EVENT_MSG_STATUS, ON_MESSAGE_STATUS);
        socketIoClient.close();
    }

    private void oops() {
        throw new UnsupportedOperationException();
    }
}