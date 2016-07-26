package com.pairapp.call;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.pairapp.data.CallBody;
import com.pairapp.data.Conversation;
import com.pairapp.data.Message;
import com.pairapp.util.Config;
import com.pairapp.util.Event;
import com.pairapp.util.EventBus;
import com.pairapp.util.PLog;
import com.sinch.android.rtc.PushPair;
import com.sinch.android.rtc.SinchClient;
import com.sinch.android.rtc.calling.Call;
import com.sinch.android.rtc.calling.CallClient;
import com.sinch.android.rtc.calling.CallClientListener;
import com.sinch.android.rtc.calling.CallDirection;
import com.sinch.android.rtc.calling.CallEndCause;
import com.sinch.android.rtc.video.VideoCallListener;
import com.sinch.android.rtc.video.VideoController;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;

import io.realm.Realm;

import static com.pairapp.call.CallController.CALL_TYPE_CONFERENCE_VOICE;
import static com.pairapp.call.CallController.CALL_TYPE_VIDEO;
import static com.pairapp.call.CallController.CALL_TYPE_VOICE;
import static com.pairapp.call.CallController.ON_CALL_ESTABLISHED;
import static com.pairapp.call.CallController.ON_CALL_PROGRESSING;
import static com.pairapp.call.CallController.ON_CAL_ENDED;
import static com.pairapp.call.CallController.ON_CAL_ERROR;

/**
 * @author aminu on 7/14/2016.
 */
class CallCenter implements CallClientListener, VideoCallListener {
    private static final String TAG = "callCenter";
    static final String CALL_TYPE = "ct";
    static final String HEADER_VIDEO_CALL = "vi";
    private static final String HEADER_CONFERENCE_VOICE_CALL = "cvo";
    public static final String HEADER_VOICE_CALL = "vo";
    private final EventBus broadCastBus;
    private boolean isCallOngoing = false;
    private final AudioPlayer player;
    private long currentCallStart = 0;
    private final WeakReference<SinchClient> clientWeakReference;
    @Nullable
    private String currentPeer;
    @Nullable
    private String currentCallId;

    @Nullable
    private Event remoteVideoTrackEvent, localVideoTrackEvent;

    CallCenter(@NonNull EventBus bus, @NonNull SinchClient client, @NonNull AudioPlayer player) {
        this.broadCastBus = bus;
        this.player = player;
        this.clientWeakReference = new WeakReference<>(client);
    }

    @Override
    public synchronized void onIncomingCall(CallClient callClient, Call call) {
        if (isCallOngoing()) {
            PLog.d(TAG, "call with id %s from %s rejected because user is already on phone with %s", call.getCallId(), call.getRemoteUserId(), "" + currentPeer);
            // TODO: 7/24/2016 broadcast an event that we rejected a call because the user is busy. to emulate "number busy" semantics
            return;
        }
        isCallOngoing = true;
        currentCallId = call.getCallId();
        player.playRingtone();
        currentPeer = call.getRemoteUserId();
        call.addCallListener(this);
        broadCastBus.postSticky(Event.createSticky(CallController.ON_IN_COMING_CALL, null, CallData.from(call, getCallType(call), System.currentTimeMillis())));
    }

    @Override
    public synchronized void onCallProgressing(Call call) {
        // TODO: 7/15/2016 set the audio stream to call
        isCallOngoing = true;
        currentCallId = call.getCallId();
        player.playProgressTone();
        broadCastBus.postSticky(Event.createSticky(ON_CALL_PROGRESSING, null, CallData.from(call, getCallType(call), System.currentTimeMillis())));
    }

    @Override
    public synchronized void onCallEstablished(Call call) {
        isCallOngoing = true;
        currentCallId = call.getCallId();
        player.stopProgressTone();
        player.stopRingtone();
        currentCallStart = System.currentTimeMillis();
        broadCastBus.postSticky(Event.createSticky(ON_CALL_ESTABLISHED, null, CallData.from(call, getCallType(call), currentCallStart)));
    }

    @Override
    public synchronized void onCallEnded(Call call) {
        // TODO: 7/15/2016 play a simple tone to signify call ended
        if (!call.getCallId().equals(currentCallId)) {
            PLog.d(TAG, "unknown call with id %s from %s reporting as ended", call.getCallId(), call.getRemoteUserId());
            return;
        }
        removeVideoTrackEventsIfPossible();
        currentPeer = null;
        currentCallId = null;
        isCallOngoing = false;
        player.stopRingtone();
        player.stopProgressTone();
        int callType = getCallType(call);
        Realm realm = Message.REALM();
        Conversation conversation = Conversation.newConversation(realm, call.getRemoteUserId());
        try {
            CallBody callBody = new CallBody(call.getCallId(),
                    currentCallStart == 0 ? 0 : (int) (System.currentTimeMillis() - currentCallStart),
                    ourCallTypeToCallBodyCallType(callType));
            boolean isOutGoing = call.getDirection() == CallDirection.OUTGOING;
            realm.beginTransaction();
            Message lastMessage = Message.makeNewCallMessageAndPersist(realm, call.getRemoteUserId(), System.currentTimeMillis(), callBody, isOutGoing);
            if (!isOutGoing && call.getDetails().getEndCause() != CallEndCause.DENIED) {
                lastMessage.setState(Message.STATE_RECEIVED);
            }
            conversation.setLastMessage(lastMessage);
            String summary = Message.getCallSummary(Config.getApplicationContext(), lastMessage);
            conversation.setSummary(summary);
            realm.commitTransaction();
        } finally {
            currentCallStart = 0;
            realm.close();
        }
        SinchClient client = clientWeakReference.get();
        if (client != null) {
            client.getAudioController().disableSpeaker();
            client.getAudioController().unmute();
        }

        broadCastBus.postSticky(Event.createSticky(ON_CAL_ENDED, null, CallData.from(call, callType, System.currentTimeMillis())));
        call.removeCallListener(this);
    }

    @Override
    public synchronized void onVideoTrackAdded(Call call) {
        SinchClient client = clientWeakReference.get();
        if (client != null) {
            VideoController controller = client.getVideoController();


            removeVideoTrackEventsIfPossible();

            remoteVideoTrackEvent = Event.create(CallController.VIDEO_CALL_REMOTE_VIEW, null, controller.getRemoteView());
            broadCastBus.post(remoteVideoTrackEvent);

            localVideoTrackEvent = Event.create(CallController.VIDEO_CALL_LOCAL_VIEW, null, controller.getLocalView());
            broadCastBus.post(localVideoTrackEvent);
        } else {
            broadCastBus.post(Event.create(ON_CAL_ERROR, new Exception(CallController.ERR_VIDEO_LOAD_FAILED), call.getRemoteUserId()));
        }
    }

    private void removeVideoTrackEventsIfPossible() {
        if (remoteVideoTrackEvent != null) {
            broadCastBus.removeStickyEvent(remoteVideoTrackEvent);
        }
        if (localVideoTrackEvent != null) {
            broadCastBus.removeStickyEvent(localVideoTrackEvent);
        }
    }

    private int ourCallTypeToCallBodyCallType(int callType) {
        int callBodyCallType;
        switch (callType) {
            case CALL_TYPE_CONFERENCE_VOICE:
                callBodyCallType = CallBody.CALL_TYPE_CONFERENCE;
                break;
            case CALL_TYPE_VIDEO:
                callBodyCallType = CallBody.CALL_TYPE_VIDEO;
                break;
            case CALL_TYPE_VOICE:
                callBodyCallType = CallBody.CALL_TYPE_VOICE;
                break;
            default:
                throw new AssertionError();
        }
        return callBodyCallType;
    }

    int getCallType(Call call) {
        Map<String, String> headers = call.getHeaders();
        if (headers == null) {
            return CALL_TYPE_VOICE;
        }
        String callType = headers.get(CALL_TYPE);
        if (HEADER_VOICE_CALL.equals(callType)) {
            return CALL_TYPE_VOICE;
        } else if (HEADER_VIDEO_CALL.equals(callType)) {
            return CALL_TYPE_VIDEO;
        } else if (HEADER_CONFERENCE_VOICE_CALL.equals(headers.get(CALL_TYPE))) {
            return CALL_TYPE_CONFERENCE_VOICE;
        } else {
            return CALL_TYPE_VOICE;
        }
    }

    @Override
    public void onShouldSendPushNotification(Call call, List<PushPair> list) {
        throw new UnsupportedOperationException();
    }

    @Nullable
    public synchronized String getCurrentPeer() {
        return currentPeer;
    }

    public synchronized void setCurrentPeer(@Nullable String currentPeer) {
        this.currentPeer = currentPeer;
    }

    public synchronized void setCallOngoing() {
        if (isCallOngoing) {
            throw new IllegalStateException();
        }
        this.isCallOngoing = true;
    }

    public synchronized boolean isCallOngoing() {
        return isCallOngoing;
    }
}
