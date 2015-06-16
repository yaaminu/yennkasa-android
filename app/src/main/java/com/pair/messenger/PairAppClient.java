package com.pair.messenger;

import android.app.IntentService;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

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
    private boolean bound = false;
    private volatile int boundClients = 0;
    public final PairAppClientInterface INSTANCE = new PairAppClientInterface();
    private Dispatcher<Message> DISPATCHER_INSTANCE;

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
        Log.i(TAG, "starting pairapp client service");
        super.onStartCommand(intent, flags, startId);
        if (intent != null) {
            if (intent.getStringExtra(ACTION).equals(ACTION_SEND_ALL_UNSENT)) {
                attemptToSendAllUnsentMessages(); //async
            }
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "binding to : " + TAG);
        bound = true;
        boundClients++;
        if (intent != null && intent.getStringExtra(ACTION).equals(ACTION_SEND_ALL_UNSENT)) {
            attemptToSendAllUnsentMessages(); //async
        }
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
        Log.i(TAG, intent.getComponent().getClassName() + " re-binding");
        boundClients++;
        bound = true;
        super.onRebind(intent);
    }

    @SuppressWarnings("unused")
    public class PairAppClientInterface extends Binder {
        public Dispatcher<Message> getMessageDispatcher() {
            if (DISPATCHER_INSTANCE == null) {
                DISPATCHER_INSTANCE = MessageDispatcher.getInstance(MessageJsonAdapter.INSTANCE, MONITOR, 10);
            }
            return DISPATCHER_INSTANCE;
        }

        public Dispatcher getEventDispatcher() {
            throw new UnsupportedOperationException("not yet implemented");
        }

        public Dispatcher getCallDispatcher() {
            throw new UnsupportedOperationException("not yet implemented");
        }
    }

    private final Dispatcher.DispatcherMonitor MONITOR = new Dispatcher.DispatcherMonitor() {
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
                Log.i(TAG, "stopping self no job and no bound client");
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
        if (DISPATCHER_INSTANCE == null) {
            DISPATCHER_INSTANCE = MessageDispatcher.getInstance(MessageJsonAdapter.INSTANCE, MONITOR, 10);

        }
        new Thread() {
            @Override
            public void run() {
                Looper.prepare(); //ensure our query is updated while we run
                Realm realm = Realm.getInstance(PairAppClient.this);
                RealmResults<Message> messages = realm.where(Message.class).notEqualTo("type", Message.TYPE_DATE_MESSAGE).equalTo("state", Message.STATE_PENDING).findAll();
                if (messages.size() < 1) {
                    Log.d(TAG, "all messages sent");
                }else {
                    // ideally copied version of the messages should be passed to dispatcher
                    // but since we know dispatcher never uses message on a different thread we can
                    // confidently pass them
                    for (Message message : messages) {
                        DISPATCHER_INSTANCE.dispatch(message);
                    }
                }
                realm.close();
            }
        }.start();

    }
}