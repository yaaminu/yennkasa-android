package com.pair.messenger;


import com.github.nkzawa.emitter.Emitter;
import com.pair.data.Message;
import com.pair.data.MessageJsonAdapter;
import com.pair.data.UserManager;
import com.pair.net.sockets.SocketIoClient;
import com.pair.util.PLog;
import com.pair.util.Config;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

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

    private SocketsIODispatcher() {
        socketIoClient = SocketIoClient.getInstance(Config.PAIRAPP_ENDPOINT + "/message", UserManager.getMainUserId());
        socketIoClient.registerForEvent(SocketIoClient.EVENT_MSG_STATUS, ON_MESSAGE_STATUS);
    }

    /**
     * create a new an instance of {@link SocketsIODispatcher}.
     * <p/>
     * remember to close it when you are done.
     *
     * @return an instance of this class.
     */
    static Dispatcher<Message> newInstance() {
        return new SocketsIODispatcher();
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
