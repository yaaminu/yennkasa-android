package com.pairapp.messenger;

import android.content.Context;
import android.content.Intent;

import com.pairapp.data.StatusManager;
import com.pairapp.util.Config;
import com.pairapp.util.PLog;

import rx.Observer;

/**
 * @author aminu on 6/28/2016.
 */
public class IncomingMessageProcessor implements Observer<MessagePacker.DataEvent> {
    public static final String TAG = IncomingMessageProcessor.class.getSimpleName();
    private final StatusManager manager;

    public IncomingMessageProcessor(StatusManager manager) {
        this.manager = manager;
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
            context.startService(intent);
        } else if (data.getOpCode() == MessagePacker.ONLINE) {
            manager.handleStatusAnnouncement(data.getData(), true);
        } else if (data.getOpCode() == MessagePacker.OFFLINE) {
            manager.handleStatusAnnouncement(data.getData(), false);
        } else if (data.getOpCode() == MessagePacker.TYPING) {
            manager.handleTypingAnnouncement(data.getData(), true);
        } else if (data.getOpCode() == MessagePacker.NOT_TYPING) {
            manager.handleStatusAnnouncement(data.getData(), false);
        } else if (data.getOpCode() == MessagePacker.MESSAGE_STATUS_SEEN) {

        } else if (data.getOpCode() == MessagePacker.MESSAGE_STATUS_DELIVERED) {

        } else { // FIXME: 7/3/2016 add more listeners for message seen,recieved etc
            PLog.d(TAG, "cont handle this message type");
        }
    }
}
