package com.pair.messenger;

import android.util.Log;

import com.github.nkzawa.emitter.Emitter;
import com.pair.Config;
import com.pair.data.Message;
import com.pair.data.MessageJsonAdapter;
import com.pair.data.UserManager;
import com.pair.net.sockets.SocketIoClient;

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
            Log.i(TAG, "message delivered");
            try {
                JSONObject object = new JSONObject(args[0].toString());
                onDelivered(object.getString("messageId"));
            } catch (JSONException e) {
                throw new RuntimeException();
            }
        }
    };
    private final SocketIoClient socketIoClient, liveClient;

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

    private SocketsIODispatcher() {
        socketIoClient = SocketIoClient.getInstance(Config.PAIRAPP_ENDPOINT + "/message", UserManager.getMainUserId());
        liveClient = SocketIoClient.getInstance(Config.PAIRAPP_ENDPOINT + "/live", UserManager.getMainUserId());
        liveClient.registerForEvent(SocketIoClient.EVENT_MSG_STATUS, ON_MESSAGE_STATUS);
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
        liveClient.unRegisterEvent(SocketIoClient.EVENT_MSG_STATUS, ON_MESSAGE_STATUS);
        liveClient.close();
        socketIoClient.close();
    }

    private void oops() {
        throw new UnsupportedOperationException();
    }
}
