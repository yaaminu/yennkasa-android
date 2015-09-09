package com.pair.messenger;

import com.pair.data.Message;
import com.pair.data.MessageJsonAdapter;
import com.parse.ParseException;
import com.parse.ParseObject;

import java.util.Collections;
import java.util.List;

import static com.pair.messenger.ParseMessageProvider.EXPIRES;
import static com.pair.messenger.ParseMessageProvider.IS_GROUP_MESSAGE;
import static com.pair.messenger.ParseMessageProvider.MAX_AGE;
import static com.pair.messenger.ParseMessageProvider.MESSAGE;
import static com.pair.messenger.ParseMessageProvider.MESSAGE_CLASS_NAME;
import static com.pair.messenger.ParseMessageProvider.RETRIEVED;
import static com.pair.messenger.ParseMessageProvider.TARGET;

/**
 * @author Null-Pointer on 8/29/2015.
 */
class ParseDispatcher extends AbstractMessageDispatcher {

    private static final String TAG = ParseDispatcher.class.getSimpleName();
    private static final Dispatcher<Message> INSTANCE = new ParseDispatcher();

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
        parseMessage.put(EXPIRES, MAX_AGE);
        parseMessage.put(IS_GROUP_MESSAGE, isGroupMessage);

        try {
            parseMessage.save();
            onSent(message.getId());
        } catch (ParseException e) {
            onFailed(message.getId(), prepareReport(e));
        }
    }

    private String prepareReport(ParseException e) {
        return e.getMessage();
    }
}
