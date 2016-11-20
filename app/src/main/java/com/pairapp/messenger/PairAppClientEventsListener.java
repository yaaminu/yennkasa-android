package com.pairapp.messenger;

import android.app.Activity;
import android.support.v4.util.Pair;

import com.pairapp.call.CallController;
import com.pairapp.call.CallData;
import com.pairapp.data.Message;
import com.pairapp.util.Event;
import com.pairapp.util.EventBus;
import com.pairapp.util.PLog;

import static com.pairapp.call.CallController.CALL_PUSH_PAYLOAD;
import static com.pairapp.call.CallController.ON_CALL_ESTABLISHED;
import static com.pairapp.call.CallController.ON_CALL_PROGRESSING;
import static com.pairapp.call.CallController.ON_CAL_ENDED;
import static com.pairapp.call.CallController.ON_IN_COMING_CALL;
import static com.pairapp.messenger.MessengerBus.ANSWER_CALL;
import static com.pairapp.messenger.MessengerBus.EDIT_SENT_MESSAGE;
import static com.pairapp.messenger.MessengerBus.MESSAGE_PUSH_INCOMING;
import static com.pairapp.messenger.MessengerBus.ON_CALL_PUSH_PAYLOAD_RECEIVED;
import static com.pairapp.messenger.MessengerBus.REVERT_SENDING;
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
            switch (tag) {
                case ONLINE:
                    pairAppClientInterface.markUserAsOnline(((Activity) event.getData()));
                    break;
                case OFFLINE:
                    pairAppClientInterface.markUserAsOffline(((Activity) event.getData()));
                    break;
                case TYPING:
                    pairAppClientInterface.notifyPeerTyping((((String) event.getData())));
                    break;
                case NOT_TYPING:
                    pairAppClientInterface.notifyPeerNotTyping(((String) event.getData()));
                    break;
                case START_MONITORING_USER:
                    pairAppClientInterface.startMonitoringUser(((String) event.getData()));
                    break;
                case STOP_MONITORING_USER:
                    pairAppClientInterface.stopMonitoringUser(((String) event.getData()));
                    break;
                case MESSAGE_SEEN:
                    pairAppClientInterface.markMessageSeen(((String) event.getData()));
                    break;
                case MESSAGE_RECEIVED:
                    pairAppClientInterface.markMessageDelivered(((String) event.getData()));
                    break;
                case ON_MESSAGE_DELIVERED:
                    pairAppClientInterface.onMessageDelivered(((String) event.getData()));
                    break;
                case ON_MESSAGE_SEEN:
                    pairAppClientInterface.onMessageSeen(((String) event.getData()));
                    break;
                case SEND_MESSAGE:
                    pairAppClientInterface.sendMessage(((Message) event.getData()));
                    break;
                case CANCEL_MESSAGE_DISPATCH:
                    pairAppClientInterface.cancelDisPatch(((Message) event.getData()));
                    break;
                case GET_STATUS_MANAGER:
                    pairAppClientInterface.getStatusManager();
                    break;
                case ON_CALL_PROGRESSING:
                    assert event.getData() != null;
                    pairAppClientInterface.onCallProgressing(((CallData) event.getData()));
                    break;
                case ON_CALL_ESTABLISHED:
                    assert event.getData() != null;
                    pairAppClientInterface.onCallEstablished(((CallData) event.getData()));
                    break;
                case ON_CAL_ENDED:
                    assert event.getData() != null;
                    pairAppClientInterface.onCallEnded(((CallData) event.getData()));
                    break;
                case ON_IN_COMING_CALL:
                    assert event.getData() != null;
                    pairAppClientInterface.onInComingCall(((CallData) event.getData()));
                    break;
                case VOICE_CALL_USER:
                    pairAppClientInterface.callUser((String) event.getData(), CallController.CALL_TYPE_VOICE);
                    break;
                case MessengerBus.VIDEO_CALL_USER:
                    pairAppClientInterface.callUser((String) event.getData(), CallController.CALL_TYPE_VIDEO);
                    break;
                case ANSWER_CALL:
                    pairAppClientInterface.answerCall(((CallData) event.getData()));
                    break;
                case HANG_UP_CALL:
                    pairAppClientInterface.hangUpCall((CallData) event.getData());
                    break;
                case CallController.ON_CAL_ERROR:
                    pairAppClientInterface.handleCallControllerError(event.getError());
                    break;
                case ENABLE_SPEAKER:
                    pairAppClientInterface.enableLoudSpeaker(((CallData) event.getData()));
                    break;
                case MUTE_CALL:
                    pairAppClientInterface.muteCall(((CallData) event.getData()));
                    break;
                case CallController.ON_CALL_MUTED:
                    pairAppClientInterface.onCallMuted(((CallData) event.getData()));
                    break;
                case CallController.ON_LOUD_SPEAKER:
                    pairAppClientInterface.onLoudSpeaker(((CallData) event.getData()));
                    break;
                case CallController.VIDEO_CALL_LOCAL_VIEW:
                    get(PAIRAPP_CLIENT_LISTENABLE_BUS).postSticky(Event.createSticky(MessengerBus.ON_ADD_VIDEO_CALL_LOCAL_VIEW, null, event.getData()));
                    break;
                case CallController.VIDEO_CALL_REMOTE_VIEW:
                    get(PAIRAPP_CLIENT_LISTENABLE_BUS).postSticky(Event.createSticky(MessengerBus.ON_ADD_VIDEO_CALL_REMOTE_VIEW, null, event.getData()));
                    break;
                case CLEAR_NEW_MESSAGE_NOTIFICATION:
                    pairAppClientInterface.clearNotifications(((long) event.getData()));
                    break;
                case MESSAGE_PUSH_INCOMING:
                    pairAppClientInterface.onIncomingPushMessage(((String) event.getData()));
                    break;
                case CALL_PUSH_PAYLOAD:
                    //noinspection unchecked
                    pairAppClientInterface.onRouteCallViaPush(((Pair<String, String>) event.getData()));
                    break;
                case ON_CALL_PUSH_PAYLOAD_RECEIVED:
                    pairAppClientInterface.onInComingCallPushPayload((String) event.getData());
                    break;
                case REVERT_SENDING:
                    pairAppClientInterface.revertSending((String) event.getData());
                    break;
                case EDIT_SENT_MESSAGE:
                    pairAppClientInterface.editSentMessage((String) event.getData());
                    break;
                case MessengerBus.MESSAGE_EDIT_RESULTS:
                    //noinspection unchecked
                    Pair<String, String> editResults = (Pair<String, String>) event.getData();
                    assert editResults != null;
                    //noinspection ThrowableResultOfMethodCallIgnored
                    pairAppClientInterface.notifyRevertResults(editResults.first, editResults.second, event.getError() != null);
                    break;
                case MessengerBus.MESSAGE_REVERT_RESULTS:
                    //noinspection unchecked
                    Pair<String, String> revertResults = (Pair<String, String>) event.getData();
                    assert revertResults != null;
                    //noinspection ThrowableResultOfMethodCallIgnored
                    pairAppClientInterface.notifyEditSentMessageResults(revertResults.first, revertResults.second, event.getError() != null);
                    break;
                default:
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
