package com.idea.messenger;

import com.idea.data.Message;
import com.idea.data.MessageJsonAdapter;
import com.idea.util.Config;
import com.idea.util.PLog;
import com.parse.ParseCloud;
import com.parse.ParseException;
import com.parse.ParseObject;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.idea.messenger.ParseMessageProvider.EXPIRES;
import static com.idea.messenger.ParseMessageProvider.IS_GROUP_MESSAGE;
import static com.idea.messenger.ParseMessageProvider.MAX_AGE;
import static com.idea.messenger.ParseMessageProvider.MESSAGE;
import static com.idea.messenger.ParseMessageProvider.MESSAGE_CLASS_NAME;
import static com.idea.messenger.ParseMessageProvider.RETRIEVED;
import static com.idea.messenger.ParseMessageProvider.TARGET;

/**
 * @author Null-Pointer on 8/29/2015.
 */
class ParseDispatcher extends AbstractMessageDispatcher {

    private static final String TAG = ParseDispatcher.class.getSimpleName();
    private static final Dispatcher<Message> INSTANCE = new ParseDispatcher();

    private ParseDispatcher() {
    }

    static synchronized Dispatcher<Message> getInstance() {
        return INSTANCE;
    }

    @Override
    public void dispatchToGroup(final Message message, List<String> members) {
        finallyDispatch(message, members, true);
    }

    @Override
    public void dispatchToUser(final Message message) {
        finallyDispatch(message, message.getTo());
    }

    private void finallyDispatch(Message message, Object target) {
        PLog.d(TAG, "dispatching message: " + message.getMessageBody()
                + " from " + message.getFrom()
                + " to " + message.getTo());
        finallyDispatch(message, target, false);
    }

    private void finallyDispatch(Message message, Object target, boolean isGroupMessage) {
        ParseObject parseMessage = new ParseObject(MESSAGE_CLASS_NAME);
        parseMessage.put(MESSAGE, MessageJsonAdapter.INSTANCE.toJson(message).toString());
        parseMessage.put(TARGET, target);
        parseMessage.put(RETRIEVED, Collections.emptyList());
        parseMessage.put(EXPIRES, MAX_AGE);
        parseMessage.put(IS_GROUP_MESSAGE, isGroupMessage);
        try {
            parseMessage.save();
            onSent(message.getId());
            Map<String, String> params = new HashMap<>(3);
            params.put(ParseMessageProvider.TO,message.getTo());
            params.put(ParseMessageProvider.IS_GROUP_MESSAGE,Boolean.toString(isGroupMessage));
            ParseCloud.callFunctionInBackground("pushToSyncMessage", params);
        } catch (ParseException e) {
            onFailed(message.getId(), prepareReport(e));
        }
    }

    private String prepareReport(ParseException e) {
        if (e.getCode() == ParseException.CONNECTION_FAILED) {
            return Config.getApplicationContext().getString(R.string.unable_to_connect);
        }
        return "Sorry an unknown error occurred";
    }
}
