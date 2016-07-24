package com.pairapp.messenger;

import android.app.Activity;

import com.pairapp.call.CallController;
import com.pairapp.call.CallData;
import com.pairapp.data.Message;
import com.pairapp.util.Event;
import com.pairapp.util.EventBus;
import com.pairapp.util.PLog;

import static com.pairapp.call.CallController.ON_CALL_ESTABLISHED;
import static com.pairapp.call.CallController.ON_CALL_PROGRESSING;
import static com.pairapp.call.CallController.ON_CAL_ENDED;
import static com.pairapp.call.CallController.ON_IN_COMING_CALL;
import static com.pairapp.messenger.MessengerBus.ANSWER_CALL;
import static com.pairapp.messenger.MessengerBus.VOICE_CALL_USER;
import static com.pairapp.messenger.MessengerBus.CANCEL_MESSAGE_DISPATCH;
import static com.pairapp.messenger.MessengerBus.CLEAR_NEW_MESSAGE_NOTIFICATION;
import static com.pairapp.messenger.MessengerBus.ENABLE_SPEAKER;
import static com.pairapp.messenger.MessengerBus.GET_STATUS_MANAGER;
import static com.pairapp.messenger.MessengerBus.HANG_UP_CALL;
import static com.pairapp.messenger.MessengerBus.MESSAGE_RECEIVED;
import static com.pairapp.messenger.MessengerBus.MESSAGE_SEEN;
import static com.pairapp.messenger.MessengerBus.MUTE_CALL;
import static com.pairapp.messenger.MessengerBus.NOT_TYPING;
import static com.pairapp.messenger.MessengerBus.OFFLINE;
import static com.pairapp.messenger.MessengerBus.ONLINE;
import static com.pairapp.messenger.MessengerBus.ON_MESSAGE_DELIVERED;
import static com.pairapp.messenger.MessengerBus.ON_MESSAGE_SEEN;
import static com.pairapp.messenger.MessengerBus.PAIRAPP_CLIENT_LISTENABLE_BUS;
import static com.pairapp.messenger.MessengerBus.SEND_MESSAGE;
import static com.pairapp.messenger.MessengerBus.START_MONITORING_USER;
import static com.pairapp.messenger.MessengerBus.STOP_MONITORING_USER;
import static com.pairapp.messenger.MessengerBus.TYPING;
import static com.pairapp.messenger.MessengerBus.get;

/**
 * @author aminu on 7/2/2016.
 */
class PairAppClientEventsListener implements EventBus.EventsListener {

    static final String TAG = PairAppClientEventsListener.class.getSimpleName();
    final PairAppClientInterface pairAppClientInterface;

    public PairAppClientEventsListener(PairAppClientInterface pairAppClientInterface) {
        this.pairAppClientInterface = pairAppClientInterface;
    }

    @Override
    public int threadMode() {
        return EventBus.BACKGROUND;
    }

    @Override
    public void onEvent(EventBus yourBus, Event event) {
        try {
            PLog.d(TAG, "event with tag: %s received", event);
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
            } else if (tag.equals(MESSAGE_SEEN)) {
                pairAppClientInterface.markMessageSeen(((String) event.getData()));
            } else if (tag.equals(MESSAGE_RECEIVED)) {
                pairAppClientInterface.markMessageDelivered(((String) event.getData()));
            } else if (tag.equals(ON_MESSAGE_DELIVERED)) {
                pairAppClientInterface.onMessageDelivered(((String) event.getData()));
            } else if (tag.equals(ON_MESSAGE_SEEN)) {
                pairAppClientInterface.onMessageSeen(((String) event.getData()));
            } else if (tag.equals(SEND_MESSAGE)) {
                pairAppClientInterface.sendMessage(((Message) event.getData()));
            } else if (tag.equals(CANCEL_MESSAGE_DISPATCH)) {
                pairAppClientInterface.cancelDisPatch(((Message) event.getData()));
            } else if (tag.equals(GET_STATUS_MANAGER)) {
                pairAppClientInterface.getStatusManager();
            } else if (tag.equals(ON_CALL_PROGRESSING)) {
                assert event.getData() != null;
                pairAppClientInterface.onCallProgressing(((CallData) event.getData()));
            } else if (tag.equals(ON_CALL_ESTABLISHED)) {
                assert event.getData() != null;
                pairAppClientInterface.onCallEstablished(((CallData) event.getData()));
            } else if (tag.equals(ON_CAL_ENDED)) {
                assert event.getData() != null;
                pairAppClientInterface.onCallEnded(((CallData) event.getData()));
            } else if (tag.equals(ON_IN_COMING_CALL)) {
                assert event.getData() != null;
                pairAppClientInterface.onInComingCall(((CallData) event.getData()));
            } else if (tag.equals(VOICE_CALL_USER)) {
                pairAppClientInterface.callUser((String) event.getData(), CallController.CALL_TYPE_VOICE);
            } else if (tag.equals(MessengerBus.VIDEO_CALL_USER)) {
                pairAppClientInterface.callUser((String) event.getData(), CallController.CALL_TYPE_VIDEO);
            } else if (tag.equals(ANSWER_CALL)) {
                pairAppClientInterface.answerCall(((CallData) event.getData()));
            } else if (tag.equals(HANG_UP_CALL)) {
                pairAppClientInterface.hangUpCall((CallData) event.getData());
            } else if (tag.equals(CallController.ON_CAL_ERROR)) {
                pairAppClientInterface.handleCallControllerError(event.getError());
            } else if (tag.equals(ENABLE_SPEAKER)) {
                pairAppClientInterface.enableLoudSpeaker(((CallData) event.getData()));
            } else if (tag.equals(MUTE_CALL)) {
                pairAppClientInterface.muteCall(((CallData) event.getData()));
            } else if (tag.equals(CallController.ON_CALL_MUTED)) {
                pairAppClientInterface.onCallMuted(((CallData) event.getData()));
            } else if (tag.equals(CallController.ON_LOUD_SPEAKER)) {
                pairAppClientInterface.onLoudSpeaker(((CallData) event.getData()));
            } else if (tag.equals(CallController.VIDEO_CALL_LOCAL_VIEW)) {
                get(PAIRAPP_CLIENT_LISTENABLE_BUS).postSticky(Event.createSticky(MessengerBus.ON_ADD_VIDEO_CALL_LOCAL_VIEW, null, event.getData()));
            } else if (tag.equals(CallController.VIDEO_CALL_REMOTE_VIEW)) {
                get(PAIRAPP_CLIENT_LISTENABLE_BUS).postSticky(Event.createSticky(MessengerBus.ON_ADD_VIDEO_CALL_REMOTE_VIEW, null, event.getData()));
            } else if (tag.equals(CLEAR_NEW_MESSAGE_NOTIFICATION)) {
                pairAppClientInterface.clearNotifications(((long) event.getData()));
            } else {
                throw new AssertionError();
            }
        } finally {
            if (!event.isRecycled()) {
                if (event.isSticky()) {
                    yourBus.removeStickyEvent(event);
                } else {
                    event.recycle();
                }
            }
        }
    }

    @Override
    public boolean sticky() {
        return true;
    }
}
