package com.pairapp.messenger;

import android.content.Context;
import android.content.Intent;

import com.pairapp.util.Config;
import com.pairapp.util.PLog;

import rx.Observer;

/**
 * @author aminu on 6/28/2016.
 */
public class IncomingMessageProcessor implements Observer<MessagePacker.DataEvent> {
    public static final String TAG = IncomingMessageProcessor.class.getSimpleName();

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
        } else {
            PLog.d(TAG, "cont handle this message type");
        }
    }
}
