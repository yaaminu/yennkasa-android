package com.pairapp.net.sockets;

import com.pairapp.data.Message;
import com.pairapp.util.GenericUtils;
import com.pairapp.util.PLog;

import java.util.List;

import io.realm.Realm;
import io.realm.RealmResults;

import static com.pairapp.net.sockets.Sendable.FIELD_INDEX;
import static com.pairapp.net.sockets.Sendable.FIELD_PROCESSING;
import static com.pairapp.net.sockets.Sendable.FIELD_START_PROCESSING_AT;
import static com.pairapp.net.sockets.Sendable.FIELD_SURVIVES_RESTART;
import static com.pairapp.net.sockets.Sendable.FIELD_VALID_UNTIL;
import static com.pairapp.net.sockets.Sendable.INVALID_INDEX;

/**
 * @author aminu on 7/10/2016.
 */
class MessageQueueItemDataSource implements QueueDataSource {

    public static final String TAG = MessageQueueItemDataSource.class.getSimpleName();
    private final RealmProvider realmProvider;

    private QueueItemCleanedListener hooks;
    private volatile boolean initialised;

    MessageQueueItemDataSource(RealmProvider realmProvider) {
        this.realmProvider = realmProvider;
    }

    @Override
    public Sendable nextItem() {
        ensureStateValid();
        Realm realm = getRealm();
        try {
            long now = System.currentTimeMillis();
            RealmResults<Sendable> items = realm.where(Sendable.class).equalTo(FIELD_PROCESSING, false)
                    .greaterThanOrEqualTo(FIELD_VALID_UNTIL, now) //filter out all expired jobs
                    .lessThanOrEqualTo(FIELD_START_PROCESSING_AT, now) //filter out jobs that are not due
                    .findAllSorted(FIELD_INDEX);
            if (items.isEmpty()) { //when ever we have nothing, lets take advantage of that and do cleanup
                removeExpiredItems(realm);
                return null;
            }
            Sendable ret = items.first();
            realm.beginTransaction();
            ret.setProcessing(true);
            realm.commitTransaction();
            return realm.copyFromRealm(ret);
        } finally {
            realm.close();
        }
    }

    @Override
    public void registerCallback(QueueItemCleanedListener queueItemCleanedListener) {
        GenericUtils.ensureNotNull(queueItemCleanedListener);
        this.hooks = queueItemCleanedListener;
    }

    @Override
    public boolean clearQueue() {
        ensureStateValid();

        Realm realm = getRealm();
        try {
            realm.beginTransaction();
            realm.delete(Sendable.class);
            realm.commitTransaction();
        } finally {
            realm.close();
        }
        return true;
    }

    @Override
    public boolean removeByCollpaseKey(String collapseKey) {
        ensureStateValid();
        GenericUtils.ensureNotNull(collapseKey);
        Realm realm = getRealm();
        try {
            realm.beginTransaction();
            Sendable ret = realm.where(Sendable.class)
                    .equalTo(Sendable.FIELD_COLLAPSE_KEY, collapseKey)
                    .equalTo(FIELD_PROCESSING, false)
                    .findFirst();
            if (ret != null) {
                ret.deleteFromRealm();
            }
            realm.commitTransaction();
            return ret != null;
        } finally {
            realm.close();
        }
    }

    @Override
    public boolean removeItem(Sendable item) {
        ensureStateValid();
        GenericUtils.ensureNotNull(item);
        Realm realm = getRealm();
        try {
            realm.beginTransaction();
            Sendable ret = realm.where(Sendable.class)
                    .equalTo(FIELD_INDEX, item.getIndex())
                    .findFirst();
            if (ret != null) {
                ret.deleteFromRealm();
            }
            realm.commitTransaction();
            return ret != null;
        } finally {
            realm.close();
        }
    }

    @Override
    public void addItem(Sendable item) {
        ensureStateValid();
        Realm realm = getRealm();
        try {
            realm.beginTransaction();
            if (item.getIndex() == INVALID_INDEX) {
                synchronized (MessageQueueItemDataSource.class) { //generatePublicPrivateKeyPair the id
                    Number highestIndex = realm.where(Sendable.class).max(FIELD_INDEX);
                    if (highestIndex != null) {
                        item.setIndex(highestIndex.longValue() + 1);
                    }
                }
            }
            item.setProcessing(false);
            realm.copyToRealmOrUpdate(item);
            realm.commitTransaction();
        } finally {
            realm.close();
        }
    }

    @Override
    public List<Sendable> pending() {
        ensureStateValid();
        Realm realm = getRealm();
        try {
            return realm.copyFromRealm(realm.
                    where(Sendable.class)
                    .equalTo(FIELD_PROCESSING, false).findAll());
        } finally {
            realm.close();
        }
    }

    @Override
    public List<Sendable> processing() {
        ensureStateValid();
        Realm realm = getRealm();
        try {
            return realm.copyFromRealm(realm
                    .where(Sendable.class)
                    .equalTo(FIELD_PROCESSING, true).findAll());
        } finally {
            realm.close();
        }
    }

    @Override
    public synchronized void init() {
        if (initialised) {
            PLog.w(TAG, "queue already initialised");
            return;
        }
        initialised = true;
        Realm realm = getRealm();
        try {
            resetProcessingItems(realm);
            removeExpiredItems(realm);
        } finally {
            realm.close();
        }
    }

    private void resetProcessingItems(Realm realm) {
        ensureStateValid();
        //restart all jobs
        realm.beginTransaction();
        RealmResults<Sendable> results = realm.where(Sendable.class)
                .equalTo(FIELD_PROCESSING, true)
                .findAll();
        for (Sendable result : results) {
            result.setProcessing(false);
        }
        realm.commitTransaction();
    }

    private void removeExpiredItems(Realm realm) {
        ensureStateValid();
        //remove all expired jobs
        realm.beginTransaction();
        RealmResults<Sendable> results = realm.where(Sendable.class)
                .equalTo(FIELD_PROCESSING, false)
                .lessThan(FIELD_VALID_UNTIL, System.currentTimeMillis())
                .equalTo(FIELD_SURVIVES_RESTART, false)
                .findAll();
        for (Sendable result : results) {
            if (result.exceededRetries() || !result.surviveRestarts()) {
                //we cannot mutate the iterable while we are walking over its content
                //so force this to expire and delete in the next transaction. see below for how this is used.
                result.setValidUntil(System.currentTimeMillis() - 10000); //10 seconds older is enough
            }
        }
        realm.commitTransaction();
        realm.beginTransaction();
        results = realm.where(Sendable.class).lessThan(FIELD_VALID_UNTIL, System.currentTimeMillis()).findAll();
        List<Sendable> copied = realm.copyFromRealm(results); //defensive copy!!!
        results.deleteAllFromRealm();
        realm.commitTransaction();
        hooks.onExpiredItemsRemoved(copied);
    }

    private void ensureStateValid() {
        if (!initialised && hooks != null) {
            throw new IllegalStateException("underlying realm is closed or in transaction!!!");
        }
    }

    Realm getRealm() {
        return realmProvider.getRealm();
    }

    interface RealmProvider {
        Realm getRealm();
    }
}
