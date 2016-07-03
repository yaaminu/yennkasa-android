package com.pairapp.messenger;

import android.app.Activity;

import com.pairapp.util.Event;
import com.pairapp.util.EventBus;
import com.pairapp.util.PLog;

import static com.pairapp.messenger.MessengerBus.NOT_TYPING;
import static com.pairapp.messenger.MessengerBus.OFFLINE;
import static com.pairapp.messenger.MessengerBus.ONLINE;
import static com.pairapp.messenger.MessengerBus.START_MONITORING_USER;
import static com.pairapp.messenger.MessengerBus.STOP_MONITORING_USER;
import static com.pairapp.messenger.MessengerBus.TYPING;

/**
 * @author aminu on 7/2/2016.
 */
class PairAppClientEventsListener implements EventBus.EventsListener {

    public static final String TAG = PairAppClientEventsListener.class.getSimpleName();
    private final PairAppClient.PairAppClientInterface pairAppClientInterface;

    public PairAppClientEventsListener(PairAppClient.PairAppClientInterface pairAppClientInterface) {
        this.pairAppClientInterface = pairAppClientInterface;
    }

    @Override
    public int threadMode() {
        return EventBus.BACKGROUND;
    }

    @Override
    public void onEvent(EventBus yourBus, Event event) {
        try {
            PLog.d(TAG, "event with tag: %s received", event.getTag());
            String tag = ((String) event.getTag());
            if (tag.equals(ONLINE)) {
                pairAppClientInterface.markUserAsOnline(((Activity) event.getData()));
            } else if (tag.equals(OFFLINE)) {
                pairAppClientInterface.markUserAsOffline(((Activity) event.getData()));
            } else if (tag.equals(TYPING)) {
                pairAppClientInterface.notifyPeerTyping((((String) event.getData())));
            } else if (tag.equals(NOT_TYPING)) {
                pairAppClientInterface.notifyPeerNotTyping(((String) event.getData()));
            } else if (tag.equals(START_MONITORING_USER)) {
                pairAppClientInterface.startMonitoringUser(((String) event.getData()));
            } else if (tag.equals(STOP_MONITORING_USER)) {
                pairAppClientInterface.stopMonitoringUser(((String) event.getData()));
            }
        } finally {
            if (event.isSticky()) {
                yourBus.removeStickyEvent(event);
            } else {
                event.recycle();
            }
        }
    }

    @Override
    public boolean sticky() {
        return true;
    }
}
