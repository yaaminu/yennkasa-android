package com.pair.messenger;

import android.app.IntentService;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import com.pair.adapter.BaseJsonAdapter;
import com.pair.adapter.MessageJsonAdapter;
import com.pair.data.Message;
import com.pair.net.Dispatcher;

import io.realm.Realm;
import io.realm.RealmResults;


/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p/>
 * TODO: Customize class - update intent actions and extra parameters.
 */
public class PairAppClient extends Service {
    public static final String TAG = PairAppClient.class.getSimpleName();
    public static final String ACTION_SEND_ALL_UNSENT = "send unsent messages";
    public static final String ACTION = "action";
    public final PairAppClientInterface INSTANCE = new PairAppClientInterface();


    public static void start(Context context) {
        Intent pairAppClient = new Intent(context, PairAppClient.class);
        pairAppClient.putExtra(PairAppClient.ACTION, PairAppClient.ACTION_SEND_ALL_UNSENT);
        context.startService(pairAppClient);
    }
    public PairAppClient() {
        super();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "starting service");
        super.onStartCommand(intent, flags, startId);
        if (intent != null) {
            if (intent.getStringExtra(ACTION).equals(ACTION_SEND_ALL_UNSENT)) {
                attemptToSendAllUnsentMessages();
            }
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, intent.getComponent().getClassName() + " binding");
        bound = true;
        boundClients++;
        return INSTANCE;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(TAG, intent.getComponent().getClassName() + " unbinding");
        boundClients--;
        if (boundClients < 1) {
            bound = false;
            stopSelf();
        }
        return true; //support re-binding
    }

    @Override
    public void onRebind(Intent intent) {
        boundClients++;
        bound = false;
        super.onRebind(intent);
    }

    @SuppressWarnings("unused")
    public class PairAppClientInterface extends Binder {
        public Dispatcher<Message> getMessageDispatcher(BaseJsonAdapter<Message> adapter, int retryTimes) {
            return MessageDispatcher.getInstance(adapter, monitor, retryTimes);
        }

        public Dispatcher getEventDispatcher() {
            throw new UnsupportedOperationException("not yet implemented");
        }

        public Dispatcher getCallDispacher() {
            throw new UnsupportedOperationException("not yet implemented");
        }
    }

    private boolean bound = false;
    private volatile int boundClients = 0;

    private Dispatcher.DispatcherMonitor monitor = new Dispatcher.DispatcherMonitor() {
        @Override
        public void onSendFailed(String reason, String messageId) {
            //TODO handle this callback
        }

        @Override
        public void onSendSucceeded(final String messageId) {
            Realm realm = Realm.getInstance(PairAppClient.this);
            realm.beginTransaction();
            Message message = realm.where(Message.class).equalTo("id", messageId).findFirst();
            if (message != null) {
                message.setState(Message.STATE_SENT);
            }
            realm.commitTransaction();
            realm.close();
        }

        @Override
        public void onAllDispatched() {
            if (!bound) { //in the future this service will run infinitely
                Log.i(TAG, "stopping self no job and now client bound");
                stopSelf();
            }
        }
    };

    @Override
    public void onDestroy() {
        Log.i(TAG, TAG + ": bye");
        super.onDestroy();
    }

    private void attemptToSendAllUnsentMessages() {
        //TODO make sure this does not conflict with message dispatcher's backoff mechanism
        Realm realm = Realm.getInstance(this);
        RealmResults<Message> messages = realm.where(Message.class).equalTo("state", Message.STATE_PENDING).findAll();
        MessageDispatcher dispatcher = MessageDispatcher.getInstance(MessageJsonAdapter.INSTANCE, monitor, 10);
        for (Message message : messages) {
            dispatcher.dispatch(message);
        }
        realm.close();
    }
}
