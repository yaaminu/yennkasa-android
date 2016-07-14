package com.pairapp.messenger;

import android.content.Context;
import android.content.Intent;

import com.pairapp.util.Config;
import com.pairapp.util.Event;
import com.pairapp.util.EventBus;
import com.pairapp.util.PLog;

import rx.Observer;

/**
 * @author aminu on 6/28/2016.
 */
public class IncomingMessageProcessor implements Observer<MessagePacker.DataEvent> {
    public static final String TAG = IncomingMessageProcessor.class.getSimpleName();
    private final StatusManager manager;
    private final EventBus eventBus;

    public IncomingMessageProcessor(StatusManager manager, EventBus eventBus) {
        this.manager = manager;
        this.eventBus = eventBus;
    }

    @Override
    public void onCompleted() {
        PLog.d(TAG, "shutting down");
    }

    @Override
    public void onError(Throwable e) {
        PLog.e(TAG, e.getMessage(), e);
    }

    @Override
    public void onNext(MessagePacker.DataEvent data) {
        PLog.d(TAG, "opcode: %s, data: %s, cursor: %s", data.getOpCode(), data.getData(), data.getCursorPos());
        if (data.getOpCode() == MessagePacker.READABLE_MESSAGE) {
            Context context = Config.getApplicationContext();
            Intent intent = new Intent(context, MessageProcessor.class);
            intent.putExtra(MessageProcessor.MESSAGE, data.getData());
            intent.putExtra(MessageProcessor.CURSOR, data.getCursorPos());
            context.startService(intent);
        } else if (data.getOpCode() == MessagePacker.ONLINE) {
            manager.handleStatusAnnouncement(data.getData(), true);
        } else if (data.getOpCode() == MessagePacker.OFFLINE) {
            manager.handleStatusAnnouncement(data.getData(), false);
        } else if (data.getOpCode() == MessagePacker.TYPING) {
            manager.handleTypingAnnouncement(data.getData(), true);
        } else if (data.getOpCode() == MessagePacker.NOT_TYPING) {
            manager.handleTypingAnnouncement(data.getData(), false);
        } else if (data.getOpCode() == MessagePacker.MESSAGE_STATUS_SEEN) {
            eventBus.post(Event.create(MessengerBus.ON_MESSAGE_SEEN, null, data.getData()));
        } else if (data.getOpCode() == MessagePacker.MESSAGE_STATUS_DELIVERED) {
            eventBus.post(Event.create(MessengerBus.ON_MESSAGE_DELIVERED, null, data.getData()));
        } else {
            PLog.d(TAG, "can't handle this message type");
        }
    }
}
