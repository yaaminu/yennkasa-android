package com.pairapp.messenger;

import com.pairapp.R;
import com.pairapp.data.Message;
import com.pairapp.net.FileApi;
import com.pairapp.util.Config;
import com.pairapp.util.PLog;
import com.parse.ParseCloud;
import com.parse.ParseException;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.pairapp.messenger.ParseMessageProvider.FROM;
import static com.pairapp.messenger.ParseMessageProvider.IS_GROUP_MESSAGE;
import static com.pairapp.messenger.ParseMessageProvider.MESSAGE;
import static com.pairapp.messenger.ParseMessageProvider.TO;

/**
 * @author Null-Pointer on 8/29/2015.
 */
class ParseDispatcher extends AbstractMessageDispatcher {

    private static final String TAG = ParseDispatcher.class.getSimpleName();
    private static Dispatcher<Message> INSTANCE;

    private ParseDispatcher(FileApi api, DispatcherMonitor monitor) {
        super(api, monitor);
    }

    /**
     * returns the singleton instance. this is reference counted so
     * remember to pair every call to this method with {@link #close()}
     *
     * @param api a key value pair of credentials for file uploads and other services
     * @return the singleton instace
     */
    static synchronized Dispatcher<Message> getInstance(FileApi api, DispatcherMonitor monitor) {
        refCount.incrementAndGet();
        if (INSTANCE == null || INSTANCE.isClosed()) {
            INSTANCE = new ParseDispatcher(api, monitor);
        }
        return INSTANCE;
    }

    @Override
    public void dispatchToGroup(final Message message, List<String> members) {
        finallyDispatch(message, members);
    }

    @Override
    public void dispatchToUser(final Message message) {
        finallyDispatch(message, message.getTo(), false);
//        throw new UnsupportedOperationException();
    }

    private void finallyDispatch(Message message, List<String> target) {
        // StringBuilder dirtyJson = new StringBuilder("\"[");
        // int counter = 1;
        // target.remove(message.getFrom());
        // for(String recipient:target){
        //     if(counter > 1 && counter++ < target.size()){
        //       dirtyJson.append(",");
        //     }
        //     dirtyJson.append(recipient);
        // }
        // dirtyJson.append("]\"");
        // PLog.d(TAG,"receipeints: %s",dirtyJson.toString());
        finallyDispatch(message, target, true);
    }

    private void finallyDispatch(Message message, Object target, boolean isGroupMessage) {
        JSONObject object = Message.toJsonObject(message);
        object.remove(Message.FIELD_DATE_COMPOSED);
        object.remove(Message.FIELD_TO);
        object.remove(Message.FIELD_STATE);
        if (Message.isTextMessage(message)) {
            object.remove(Message.FIELD_TYPE);
        }        try {
            Map<String, Object> params = new HashMap<>(3);
            params.put(TO, target);
            params.put(IS_GROUP_MESSAGE, isGroupMessage);
            params.put(FROM, message.getFrom());
            params.put(MESSAGE, object.toString());
            ParseCloud.callFunction("pushToSyncMessages", params);
            onSent(message.getId());
        } catch (ParseException e) {
            onFailed(message.getId(), prepareReport(e));
        }
    }

    private static final AtomicInteger refCount = new AtomicInteger(0);

    private String prepareReport(ParseException e) {
        PLog.d(TAG, e.getMessage(), e.getCause());
        if (e.getCode() == ParseException.CONNECTION_FAILED) {
            return Config.getApplicationContext().getString(R.string.st_unable_to_connect);
        }
        return "Sorry an unknown error occurred";
    }

    @Override
    public synchronized boolean isClosed() {
        return refCount.intValue() == 0;
    }

    @Override
    protected boolean doClose() {
        return refCount.decrementAndGet() <= 0;
    }
}
