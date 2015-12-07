package com.idea.messenger;

import android.content.SharedPreferences;

import com.idea.data.Message;
import com.idea.data.util.MessageUtils;
import com.idea.util.Config;
import com.idea.util.PLog;
import com.idea.util.TaskManager;
import com.idea.util.ThreadUtils;
import com.sinch.android.rtc.ErrorType;
import com.sinch.android.rtc.PushPair;
import com.sinch.android.rtc.messaging.MessageClient;
import com.sinch.android.rtc.messaging.MessageClientListener;
import com.sinch.android.rtc.messaging.MessageDeliveryInfo;
import com.sinch.android.rtc.messaging.MessageFailureInfo;
import com.sinch.android.rtc.messaging.WritableMessage;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Null-Pointer on 8/27/2015.
 */
class SinchDispatcher extends AbstractMessageDispatcher implements MessageClientListener {

    private static final String TAG = SinchDispatcher.class.getSimpleName();
    private static final String MESSAGE_ID_MAPPER = "sichMessageIdToOurMessageIdMapper";

    private MessageClient messageClient;
    private static SinchDispatcher INSTANCE;

    synchronized static Dispatcher<Message> createInstance(Map<String, String> credentials, DispatcherMonitor monitor, MessageClient messageClient) {
        INSTANCE = new SinchDispatcher(credentials, monitor, messageClient);
        return INSTANCE;
    }

    private void shutDown() {
        if (INSTANCE.messageClient != null) {
            INSTANCE.messageClient.removeMessageClientListener(this);
            INSTANCE.messageClient = null;
        }
        INSTANCE = null;
    }

    private SinchDispatcher(Map<String, String> credentials, DispatcherMonitor monitor, MessageClient client) {
        super(credentials, monitor);
        if (client == null) {
            throw new IllegalArgumentException("listener or client may not be null");
        }
        this.messageClient = client;
        this.messageClient.addMessageClientListener(this);
    }


    @Override
    protected void dispatchToGroup(Message message, List<String> members) {
        //sinch does not allow sending message to more than 10 recipients so we do it in batches
        final int size = members.size();
        final int chuckSize = 10;
        int cursor = 0;
        final String textBody = Message.toJSON(message);
        WritableMessage writableMessage;
        do {
            if (size - cursor > chuckSize) {
                writableMessage = new WritableMessage(members.subList(cursor, cursor + chuckSize), textBody);
            } else {
                writableMessage = new WritableMessage(members.subList(cursor, size), textBody);
            }
            writableMessage.addHeader(Message.SENDABLE_MESSAGE, "1");
            messageClient.send(writableMessage);
            cursor += chuckSize;
        } while (cursor < size);

    }

    @Override
    protected void dispatchToUser(Message message) {
        WritableMessage writableMessage = new WritableMessage(message.getTo(), Message.toJSON(message));
        writableMessage.addHeader(Message.SENDABLE_MESSAGE, "1");
        messageClient.send(writableMessage);
    }


    @Override
    public boolean isClosed() {
        return isClosed.get();
    }

    private final AtomicBoolean isClosed = new AtomicBoolean(false);

    @Override
    protected boolean doClose() {
        if (!isClosed.getAndSet(true)) {
            synchronized (SinchDispatcher.class) {
                shutDown();
            }
            return true;
        }
        return false;
    }


    @Override
    public void onIncomingMessage(MessageClient messageClient, com.sinch.android.rtc.messaging.Message message) {
        // WE DON'T HANDLE INCOMING MESSAGES
    }

    @Override
    public void onMessageSent(MessageClient messageClient, final com.sinch.android.rtc.messaging.Message message, String s) {
        if (ThreadUtils.isMainThread()) {
            TaskManager.execute(new Runnable() {
                @Override
                public void run() {
                    onSent(message);
                }
            }, false);
        } else {
            onSent(message);
        }
    }

    private void onSent(com.sinch.android.rtc.messaging.Message message) {
        if (message.getHeaders().containsKey(Message.SENDABLE_MESSAGE)) {
            String messageId = Message.fromJSON(message.getTextBody()).getId();
            //persist the id of this sinch message and the messageId it maps see the how we report delivery report
            SharedPreferences sinchMessageIdToOurMessageIdMapper =
                    Config.getPreferences(MESSAGE_ID_MAPPER);
            sinchMessageIdToOurMessageIdMapper.edit().putString(message.getMessageId(), messageId).apply();
            onSent(messageId);
        }
    }

    @Override
    public void onMessageFailed(final MessageClient messageClient, final com.sinch.android.rtc.messaging.Message message, final MessageFailureInfo messageFailureInfo) {
        // TODO: 8/27/2015 retry?
        if (ThreadUtils.isMainThread()) {
            TaskManager.execute(new Runnable() {
                @Override
                public void run() {
                    onFailed(message, messageFailureInfo);
                }
            }, false);
        } else {
            onFailed(message, messageFailureInfo);
        }
    }

    private void onFailed(com.sinch.android.rtc.messaging.Message message, MessageFailureInfo messageFailureInfo) {
        if (message.getHeaders().containsKey(Message.SENDABLE_MESSAGE)) {
            final ErrorType errorType = messageFailureInfo.getSinchError().getErrorType();
            final Message failedMessage = Message.fromJSON(message.getTextBody());
            if (errorType == ErrorType.NETWORK) {
                onFailed(failedMessage.getId(), MessageUtils.ERROR_NOT_CONNECTED);
            } else {
                onFailed(failedMessage.getId(), MessageUtils.ERROR_UNKNOWN);
            }
        }
    }

    @Override
    public void onMessageDelivered(final MessageClient messageClient, final MessageDeliveryInfo messageDeliveryInfo) {
        if (ThreadUtils.isMainThread()) {
            TaskManager.execute(new Runnable() {
                @Override
                public void run() {
                    onDelivered(messageDeliveryInfo);
                }
            }, false);
        } else {
            onDelivered(messageDeliveryInfo);
        }
    }

    private void onDelivered(MessageDeliveryInfo messageDeliveryInfo) {
        String ourMessageId = Config.getPreferences(MESSAGE_ID_MAPPER).getString(messageDeliveryInfo.getMessageId(), null);
        if (ourMessageId != null) {
            onDelivered(ourMessageId);
            return;
        }
        PLog.w(TAG, "message id could not be retrieved. delivery report failed");
    }


    @Override
    public void onShouldSendPushData(MessageClient messageClient, com.sinch.android.rtc.messaging.Message message, List<PushPair> list) {
        // TODO: 8/27/2015 nothing
    }
}
