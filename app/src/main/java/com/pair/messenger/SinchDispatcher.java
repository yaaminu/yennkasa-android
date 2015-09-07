package com.pair.messenger;

/**
 * @author Null-Pointer on 8/27/2015.
 */
abstract class SinchDispatcher extends AbstractMessageDispatcher {

    private static final String TAG = SinchDispatcher.class.getSimpleName();
    private static final String MESSAGE_ID_MAPPER = "sichMessageIdToOurMessageIdMapper";

//    private MessageClient messageClient;
//    private static SinchDispatcher INSTANCE;
//
//    synchronized static Dispatcher<Message> newInstance(MessageClient messageClient) {
//        if (INSTANCE == null) {
//            INSTANCE = new SinchDispatcher(messageClient);
//        }
//        return INSTANCE;
//    }
//
//    private void shutDown() {
//        if (INSTANCE.messageClient != null) {
//            INSTANCE.messageClient.removeMessageClientListener(this);
//            INSTANCE.messageClient = null;
//        }
//        INSTANCE = null;
//    }
//
//    private SinchDispatcher(MessageClient client) {
//        if (client == null) {
//            throw new IllegalArgumentException("listener or client may not be null");
//        }
//        this.messageClient = client;
//        this.messageClient.addMessageClientListener(this);
//    }
//
//
//    @Override
//    protected void dispatchToGroup(Message message, List<String> members) {
//        //sinch does not allow sending message to more than 10 recipients so we do it in batches
//        final int size = members.size();
//        final int chuckSize = 10;
//        int cursor = 0;
//        final String textBody = MessageJsonAdapter.INSTANCE.toJson(message).toString();
//        WritableMessage writableMessage;
//        do {
//            if (size - cursor > chuckSize) {
//                writableMessage = new WritableMessage(members.subList(cursor, cursor + chuckSize), textBody);
//            } else {
//                writableMessage = new WritableMessage(members.subList(cursor, size), textBody);
//            }
//            messageClient.send(writableMessage);
//            cursor += chuckSize;
//        } while (cursor < size);
//
//    }
//
//    @Override
//    protected void dispatchToUser(Message message) {
//        WritableMessage writableMessage = new WritableMessage(message.getTo(), MessageJsonAdapter.INSTANCE.toJson(message).toString());
//        messageClient.send(writableMessage);
//    }
//
//    @Override
//    public void close() {
//        synchronized (SinchDispatcher.class) {
//            shutDown();
//        }
//    }
//
//    @Override
//    public void onIncomingMessage(MessageClient messageClient, com.sinch.android.rtc.messaging.Message message) {
//        // WE DON'T HANDLE INCOMING MESSAGES
//    }
//
//    @Override
//    public void onMessageSent(MessageClient messageClient, final com.sinch.android.rtc.messaging.Message message, String s) {
//        if (ThreadUtils.isMainThread()) {
//            WORKER.submit(new Runnable() {
//                @Override
//                public void run() {
//                    onSent(message);
//                }
//            });
//        } else {
//            onSent(message);
//        }
//    }
//
//    private void onSent(com.sinch.android.rtc.messaging.Message message) {
//
//        String messageId = MessageJsonAdapter.INSTANCE.fromJson(message.getTextBody()).getId();
//        //persist the id of this sinch message and the messageId it maps see the how we report delivery report
//        SharedPreferences sinchMessageIdToOurMessageIdMapper =
//                Config.getApplicationContext().getSharedPreferences(MESSAGE_ID_MAPPER, Context.MODE_PRIVATE);
//        sinchMessageIdToOurMessageIdMapper.edit().putString(message.getMessageId(), messageId).apply();
//        onSent(MessageJsonAdapter.INSTANCE.fromJson(message.getTextBody()).getId());
//    }
//
//    @Override
//    public void onMessageFailed(final MessageClient messageClient, final com.sinch.android.rtc.messaging.Message message, final MessageFailureInfo messageFailureInfo) {
//        // TODO: 8/27/2015 retry?
//        if (ThreadUtils.isMainThread()) {
//            WORKER.submit(new Runnable() {
//                @Override
//                public void run() {
//                    onFailed(messageClient, message, messageFailureInfo);
//                }
//            });
//        } else {
//            onFailed(messageClient, message, messageFailureInfo);
//        }
//    }
//
//    private void onFailed(MessageClient messageClient, com.sinch.android.rtc.messaging.Message message, MessageFailureInfo messageFailureInfo) {
//        final ErrorType errorType = messageFailureInfo.getSinchError().getErrorType();
//        final Message failedMessage = MessageJsonAdapter.INSTANCE.fromJson(message.getTextBody());
//        if (errorType == ErrorType.NETWORK) {
//            messageClient.send(new WritableMessage(message));
//            onFailed(failedMessage, MessageUtils.ERROR_NOT_CONNECTED);
//        } else {
//            onFailed(failedMessage, MessageUtils.ERROR_UNKNOWN);
//        }
//    }
//
//    @Override
//    public void onMessageDelivered(final MessageClient messageClient, final MessageDeliveryInfo messageDeliveryInfo) {
//        if (ThreadUtils.isMainThread()) {
//            WORKER.submit(new Runnable() {
//                @Override
//                public void run() {
//                    onDelivered(messageDeliveryInfo);
//                }
//            });
//        } else {
//            onDelivered(messageDeliveryInfo);
//        }
//    }
//
//    private void onDelivered(MessageDeliveryInfo messageDeliveryInfo) {
//        String ourMessageId = Config.getApplicationContext()
//                .getSharedPreferences(MESSAGE_ID_MAPPER, Context.MODE_PRIVATE).getString(messageDeliveryInfo.getMessageId(), null);
//        if (ourMessageId != null) {
//            onDelivered(ourMessageId);
//            return;
//        }
//        L.w(TAG, "message id mapper tampered with. delivery report failed");
//    }
//
//
//    @Override
//    public void onShouldSendPushData(MessageClient messageClient, com.sinch.android.rtc.messaging.Message message, List<PushPair> list) {
//        // TODO: 8/27/2015 nothing
//    }
}
