package com.idea.messenger;

import com.idea.data.Message;
import com.idea.data.MessageJsonAdapter;
import com.idea.pairapp.R;
import com.idea.util.Config;
import com.idea.util.PLog;
import com.parse.ParseCloud;
import com.parse.ParseException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.idea.messenger.ParseMessageProvider.FROM;
import static com.idea.messenger.ParseMessageProvider.IS_GROUP_MESSAGE;
import static com.idea.messenger.ParseMessageProvider.MESSAGE;
import static com.idea.messenger.ParseMessageProvider.TO;

/**
 * @author Null-Pointer on 8/29/2015.
 */
class ParseDispatcher extends AbstractMessageDispatcher {

    private static final String TAG = ParseDispatcher.class.getSimpleName();
    private static  Dispatcher<Message> INSTANCE;

    private ParseDispatcher(Map<String, String> credentials) {
        super(credentials);
    }

    static synchronized Dispatcher<Message> getInstance(Map<String, String> credentials) {
        if(INSTANCE == null){
            INSTANCE = new ParseDispatcher(credentials);
        }
        return INSTANCE;
    }

    @Override
    public void dispatchToGroup(final Message message, List<String> members) {
        finallyDispatch(message, members, true);
    }

    @Override
    public void dispatchToUser(final Message message) {
        List<String> target = new ArrayList<>(1);
        target.add(message.getTo());
        finallyDispatch(message,target);
    }

    private void finallyDispatch(Message message, List<String> target) {
        PLog.d(TAG, "dispatching message: " + message.getMessageBody()
                + " from " + message.getFrom()
                + " to " + message.getTo());
        finallyDispatch(message, target, false);
    }

    private void finallyDispatch(Message message, Object target, boolean isGroupMessage) {
        String messageJson = MessageJsonAdapter.INSTANCE.toJson(message).toString();
        try {
            Map<String, Object> params = new HashMap<>(3);
            params.put(TO, message.getTo());
            params.put(IS_GROUP_MESSAGE, isGroupMessage);
            params.put(FROM,message.getFrom());
            params.put(MESSAGE,messageJson);
            ParseCloud.callFunction("pushToSyncMessages", params);
            onSent(message.getId());
        } catch (ParseException e) {
            onFailed(message.getId(), prepareReport(e));
        }
    }

    private String prepareReport(ParseException e) {
        if (e.getCode() == ParseException.CONNECTION_FAILED) {
            return Config.getApplicationContext().getString(R.string.st_unable_to_connect);
        }
        return "Sorry an unknown error occurred";
    }
}
