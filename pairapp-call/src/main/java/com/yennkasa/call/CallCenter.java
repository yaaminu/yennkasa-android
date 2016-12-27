package com.yennkasa.call;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.util.Pair;
import android.view.View;

import com.yennkasa.data.BuildConfig;
import com.yennkasa.data.CallBody;
import com.yennkasa.data.Conversation;
import com.yennkasa.data.Message;
import com.yennkasa.data.User;
import com.yennkasa.data.UserManager;
import com.yennkasa.util.Config;
import com.yennkasa.util.Event;
import com.yennkasa.util.EventBus;
import com.yennkasa.util.GenericUtils;
import com.yennkasa.util.PLog;
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

import static com.yennkasa.call.CallController.CALL_TYPE_CONFERENCE_VOICE;
import static com.yennkasa.call.CallController.CALL_TYPE_VIDEO;
import static com.yennkasa.call.CallController.CALL_TYPE_VOICE;
import static com.yennkasa.call.CallController.ON_CALL_ESTABLISHED;
import static com.yennkasa.call.CallController.ON_CALL_PROGRESSING;
import static com.yennkasa.call.CallController.ON_CAL_ENDED;
import static com.yennkasa.call.CallController.ON_CAL_ERROR;

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
    private final String currentUserId;
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

    CallCenter(@NonNull EventBus bus, @NonNull SinchClient client, @NonNull AudioPlayer player, @NonNull String currentUserId) {
        GenericUtils.ensureNotNull(bus, client, player);
        GenericUtils.ensureNotEmpty(currentUserId);
        this.broadCastBus = bus;
        this.player = player;
        this.clientWeakReference = new WeakReference<>(client);
        this.currentUserId = currentUserId;
    }

    @Override
    public synchronized void onIncomingCall(final CallClient callClient, final Call call) {
        String remoteUserId = call.getRemoteUserId();
        if (isCallOngoing()) {
            call.hangup();
            PLog.d(TAG, "call with id %s from %s rejected because user is already on phone with %s", call.getCallId(), remoteUserId, "" + currentPeer);
            // TODO: 7/24/2016 broadcast an event that we rejected a call because the user is busy. to emulate "number busy" semantics
            return;
        }
        if (UserManager.getInstance().isBlocked(remoteUserId)) {
            PLog.d(TAG, "blocked user calling. not forwarding call");
            call.hangup();
        } else {
            isCallOngoing = true;
            currentCallId = call.getCallId();
            player.playRingtone();
            currentPeer = remoteUserId;
            call.addCallListener(this);
            broadCastBus.postSticky(Event.createSticky(CallController.ON_IN_COMING_CALL, null, CallData.from(call, getCallType(call), System.currentTimeMillis())));
        }
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
        boolean isVideoCall = getCallType(call) == CALL_TYPE_VIDEO;
        SinchClient sinchClient = clientWeakReference.get();
        //we'd rather crash. we will rather crash than check for nullablity
        if (isVideoCall) {
            sinchClient.getAudioController().enableSpeaker();
        } else {
            sinchClient.getAudioController().disableSpeaker();
        }
        broadCastBus.postSticky(Event.createSticky(ON_CALL_ESTABLISHED, null,
                CallData.from(call, getCallType(call), currentCallStart, false, isVideoCall)));
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
        Realm realm = Message.REALM(), userRealm = User.Realm(Config.getApplicationContext());
        Conversation conversation = Conversation.newConversation(realm, currentUserId, call.getRemoteUserId());
        try {
            CallBody callBody = new CallBody(call.getCallId(),
                    currentCallStart == 0 ? 0 : (int) (System.currentTimeMillis() - currentCallStart),
                    ourCallTypeToCallBodyCallType(callType));
            boolean isOutGoing = call.getDirection() == CallDirection.OUTGOING;
            realm.beginTransaction();
            Message lastMessage = Message.makeNewCallMessageAndPersist(realm, currentUserId, call.getRemoteUserId(), System.currentTimeMillis(), callBody, isOutGoing);
            if (!isOutGoing && call.getDetails().getEndCause() != CallEndCause.DENIED) {
                lastMessage.setState(Message.STATE_RECEIVED);
            }
            conversation.setLastMessage(lastMessage);
            String summary = Message.getCallSummary(Config.getApplicationContext(), userRealm, lastMessage);
            conversation.setSummary(summary);
            realm.commitTransaction();
        } finally {
            currentCallStart = 0;
            realm.close();
            userRealm.close();
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

            Pair<View, View> viewViewPair = Pair.create(controller.getLocalView(), controller.getRemoteView());
            remoteVideoTrackEvent = Event.create(CallController.VIDEO_CALL_VIEW, null, viewViewPair);
            broadCastBus.post(remoteVideoTrackEvent);

//            localVideoTrackEvent = Event.create(CallController.VIDEO_CALL_LOCAL_VIEW, null, controller.getLocalView());
//            broadCastBus.post(localVideoTrackEvent);
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
        remoteVideoTrackEvent = null;
        localVideoTrackEvent = null;
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
        if (headers == null || headers.isEmpty()) {
            return call.getDetails().isVideoOffered() ? CALL_TYPE_VIDEO : CALL_TYPE_VOICE;
        }
        String callType = headers.get(CALL_TYPE);
        if (HEADER_VOICE_CALL.equals(callType)) {
            return CALL_TYPE_VOICE;
        } else if (HEADER_VIDEO_CALL.equals(callType)) {
            return CALL_TYPE_VIDEO;
        } else if (HEADER_CONFERENCE_VOICE_CALL.equals(headers.get(CALL_TYPE))) {
            return CALL_TYPE_CONFERENCE_VOICE;
        } else {
            return call.getDetails().isVideoOffered() ? CALL_TYPE_VIDEO : CALL_TYPE_VOICE;
        }
    }

    @Override
    public void onShouldSendPushNotification(Call call, List<PushPair> list) {
        boolean allFailed = true;
        for (PushPair pushPair : list) {
            Event event = Event.create(CallController.CALL_PUSH_PAYLOAD, null, new Pair<>(call.getRemoteUserId(), pushPair.getPushPayload()));
            if (broadCastBus.post(event)) {
                allFailed = false;
            }
        }
        if (allFailed) {
            if (BuildConfig.DEBUG) {
                throw new IllegalStateException();
            }
            PLog.f(TAG, "failed to route call since no handler is available");
            call.hangup();
        }
    }

    @Nullable
    public synchronized String getCurrentPeer() {
        return currentPeer;
    }

    synchronized void setCurrentPeer(@Nullable String currentPeer) {
        this.currentPeer = currentPeer;
    }

    synchronized void setCallOngoing(String currentCallId) {
        if (isCallOngoing) {
            throw new IllegalStateException();
        }
        this.currentCallId = currentCallId;
        this.isCallOngoing = true;
    }

    public synchronized boolean isCallOngoing() {
        return isCallOngoing;
    }
}
