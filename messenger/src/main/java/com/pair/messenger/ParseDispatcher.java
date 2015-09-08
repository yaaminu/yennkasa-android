package com.pair.messenger;

import android.app.AlarmManager;

import com.pair.data.Message;
import com.pair.data.MessageJsonAdapter;
import com.parse.ParseException;
import com.parse.ParseObject;

import java.util.Collections;
import java.util.List;

/**
 * @author Null-Pointer on 8/29/2015.
 */
class ParseDispatcher extends AbstractMessageDispatcher {

    private static final String TAG = ParseDispatcher.class.getSimpleName();
    static final String MESSAGE_CLASS_NAME = "message";
    static final String TARGET = "target";
    static final String GROUP_MESSAGE = "groupMessage";

    private static final Dispatcher<Message> INSTANCE = new ParseDispatcher();
    static final String RETRIEVED = "retrieved";
    static final String MESSAGE = "message";
    static final String EXPIRES = "expires";

    static synchronized Dispatcher<Message> getInstance() {
        return INSTANCE;
    }

    private ParseDispatcher() {
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
        finallyDispatch(message, target, false);
    }

    private void finallyDispatch(Message message, Object target, boolean isGroupMessage) {
        ParseObject parseMessage = new ParseObject(MESSAGE_CLASS_NAME);
        parseMessage.put(MESSAGE, MessageJsonAdapter.INSTANCE.toJson(message).toString());
        parseMessage.put(TARGET, target);
        parseMessage.put(RETRIEVED, Collections.emptyList());
        parseMessage.put(EXPIRES, AlarmManager.INTERVAL_DAY * 10);
        parseMessage.put(GROUP_MESSAGE, isGroupMessage);
        try {
            parseMessage.save();
            onSent(message.getId());
        } catch (ParseException e) {
            onFailed(message, prepareReport(e));
        }
    }

    private String prepareReport(ParseException e) {
        return e.getMessage();
    }
}
