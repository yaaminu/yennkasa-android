package com.pair.messenger;

import android.app.IntentService;
import android.content.Intent;

import com.pair.adapter.MessageJsonAdapter;
import com.pair.data.Message;

import io.realm.Realm;
import io.realm.RealmResults;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p/>
 * TODO: Customize class - update intent actions, extra parameters and static
 * helper methods.
 */
public class MessageService extends IntentService {
    public static final String TAG = MessageService.class.getSimpleName();
    public static final String ACTION_SEND_ALL_UNSENT = "send unsent messages";

    /**
     * Starts this service to perform action Foo with the given parameters. If
     * the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    public MessageService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent.getAction().equals(ACTION_SEND_ALL_UNSENT)) {
            attemptToSendAllUnsentMessages();
        }
    }

    private void attemptToSendAllUnsentMessages() {
        //TODO make sure this does not conflict with message dispatcher's backoff mechanism
        Realm realm = Realm.getInstance(this);
        RealmResults<Message> messages = realm.where(Message.class).equalTo("state", Message.STATE_PENDING).findAll();
        MessageDispatcher dispatcher = MessageDispatcher.getInstance(MessageJsonAdapter.INSTANCE, null, 10);
        for (Message message : messages) {
            dispatcher.dispatch(message);
        }
        realm.close();
    }
}
