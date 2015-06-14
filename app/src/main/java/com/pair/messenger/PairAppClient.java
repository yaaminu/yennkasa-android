package com.pair.messenger;

import android.app.IntentService;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

import com.pair.adapter.BaseJsonAdapter;
import com.pair.data.Message;
import com.pair.net.Dispatcher;


/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p/>
 * TODO: Customize class - update intent actions and extra parameters.
 */
public class PairAppClient extends IntentService {
    public PairAppClient() {
        super("PairAppClient");
    }

    @Override
    protected void onHandleIntent(Intent intent) {

    }

    @Override
    public IBinder onBind(Intent intent) {
        return PairAppClientInterface.INSTANCE;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return true; //support re-binding
    }

    @Override
    public void onRebind(Intent intent) {
        super.onRebind(intent);
    }

    public static class PairAppClientInterface extends Binder {
        public static final PairAppClientInterface INSTANCE = new PairAppClientInterface();

        public Dispatcher<Message> getMessageDispatcher(BaseJsonAdapter<Message> adapter, int retryTimes) {
            return MessageDispatcher.getInstance(adapter, null, retryTimes);
        }

        public Dispatcher getEventDispatcher() {
            throw new UnsupportedOperationException("not yet implemented");
        }

        public Dispatcher getCallDispacher() {
            throw new UnsupportedOperationException("not yet implemented");
        }
    }
}
