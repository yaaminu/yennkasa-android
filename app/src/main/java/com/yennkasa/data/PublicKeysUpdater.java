package com.yennkasa.data;

import android.app.AlarmManager;
import android.app.IntentService;
import android.content.Intent;

import com.yennkasa.messenger.MessengerBus;
import com.yennkasa.messenger.YennkasaClient;
import com.yennkasa.util.Event;
import com.yennkasa.util.EventBus;

import java.util.List;
import java.util.concurrent.Semaphore;

import io.realm.Realm;
import io.realm.RealmResults;
import rx.Observable;
import rx.Observer;
import rx.functions.Func1;

import static com.yennkasa.data.Message.STATE_PENDING;
import static com.yennkasa.data.Message.STATE_SENT;
import static com.yennkasa.data.Message.TYPE_BIN_MESSAGE;
import static com.yennkasa.data.Message.TYPE_PICTURE_MESSAGE;
import static com.yennkasa.data.Message.TYPE_STICKER;
import static com.yennkasa.data.Message.TYPE_TEXT_MESSAGE;
import static com.yennkasa.data.Message.TYPE_VIDEO_MESSAGE;

public class PublicKeysUpdater extends IntentService {

    private static final Semaphore SEMAPHORE = new Semaphore(1, true);
    public static final String TAG = "PublicKeysUpdater";
    private long oldEnough;

    public PublicKeysUpdater() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        SEMAPHORE.acquireUninterruptibly();
        oldEnough = System.currentTimeMillis() - AlarmManager.INTERVAL_HOUR * 4;
        Realm realm = Message.REALM(this);
        try {
            List<Message> messages = realm.where(Message.class).in(Message.FIELD_STATE,
                    new Integer[]{STATE_PENDING, STATE_SENT})
                    .in(Message.FIELD_TYPE, new Integer[]{TYPE_BIN_MESSAGE, TYPE_STICKER, TYPE_VIDEO_MESSAGE, TYPE_TEXT_MESSAGE,
                            TYPE_PICTURE_MESSAGE})
                    .lessThanOrEqualTo(Message.FIELD_DATE_COMPOSED, oldEnough)
                    .distinct(Message.FIELD_TO);

            Observable.from(messages)
                    .map(new Func1<Message, String>() {
                        @Override
                        public String call(Message message) {
                            return message.getTo();
                        }
                    }).subscribe(new Observer<String>() {
                @Override
                public void onCompleted() {
                    SEMAPHORE.release();
                }

                @Override
                public void onError(Throwable e) {
                    SEMAPHORE.release();
                }

                @Override
                public void onNext(String userId) {
                    if (refreshPublicKey(userId)) {
                        sendAllMessagesToThisUserAgain(userId);
                    }
                }
            });
        } finally {
            realm.close();
        }
    }

    private void sendAllMessagesToThisUserAgain(String userId) {
        Realm realm = Message.REALM(this);
        try {
            RealmResults<Message> messages = realm.where(Message.class).equalTo(Message.FIELD_TO, userId)
                    .in(Message.FIELD_STATE, new Integer[]{STATE_PENDING, STATE_SENT})
                    .in(Message.FIELD_TYPE, new Integer[]{TYPE_BIN_MESSAGE, TYPE_STICKER, TYPE_VIDEO_MESSAGE, TYPE_TEXT_MESSAGE,
                            TYPE_PICTURE_MESSAGE})
                    .lessThanOrEqualTo(Message.FIELD_DATE_COMPOSED, oldEnough)
                    .findAllSorted(Message.FIELD_DATE_COMPOSED);
            for (Message message : messages) {
                EventBus eventBus = MessengerBus.get(MessengerBus.PAIRAPP_CLIENT_POSTABLE_BUS);
                boolean posted = eventBus
                        .post(Event.create(MessengerBus.SEND_MESSAGE, null, realm.copyFromRealm(message)));
                while (!posted) {
                    YennkasaClient.startIfRequired(this);
                    eventBus
                            .post(Event.create(MessengerBus.SEND_MESSAGE, null, realm.copyFromRealm(message)));
                }
            }
        } finally {
            realm.close();
        }
    }

    private boolean refreshPublicKey(String userId) {
        UserManager instance = UserManager.getInstance();
        return instance.publicKeyForUser(userId, true) != null;
    }
}
