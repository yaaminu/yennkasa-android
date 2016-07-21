package com.pairapp.call;

import com.pairapp.data.CallBody;
import com.pairapp.data.Conversation;
import com.pairapp.data.Message;
import com.pairapp.util.Event;
import com.pairapp.util.EventBus;
import com.sinch.android.rtc.PushPair;
import com.sinch.android.rtc.calling.Call;
import com.sinch.android.rtc.calling.CallClient;
import com.sinch.android.rtc.calling.CallClientListener;
import com.sinch.android.rtc.calling.CallDirection;
import com.sinch.android.rtc.calling.CallListener;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.realm.Realm;

import static com.pairapp.call.CallController.CALL_TYPE_VOICE;
import static com.pairapp.call.CallController.ON_CALL_ESTABLISHED;
import static com.pairapp.call.CallController.ON_CALL_PROGRESSING;
import static com.pairapp.call.CallController.ON_CAL_ENDED;

/**
 * @author aminu on 7/14/2016.
 */
class CallCenter implements CallClientListener, CallListener {

    private final EventBus broadCastBus;
    final AtomicBoolean isCallOngoing = new AtomicBoolean(false);
    private final AudioPlayer player;
    private long currentCallStart = 0;

    CallCenter(EventBus bus, AudioPlayer player) {
        this.broadCastBus = bus;
        this.player = player;
    }

    @Override
    public void onIncomingCall(CallClient callClient, Call call) {
        // TODO: 7/15/2016 play ring tone
        player.playRingtone();
        isCallOngoing.set(true);
        call.addCallListener(this);
        broadCastBus.postSticky(Event.createSticky(CallController.ON_IN_COMING_CALL, null, CallData.from(call, CallController.CALL_TYPE_VOICE, System.currentTimeMillis())));
    }

    @Override
    public void onCallProgressing(Call call) {
        // TODO: 7/15/2016 set the audio stream to call
        isCallOngoing.set(true);
        player.playProgressTone();
        broadCastBus.postSticky(Event.createSticky(ON_CALL_PROGRESSING, null, CallData.from(call, CALL_TYPE_VOICE, System.currentTimeMillis())));
    }

    @Override
    public void onCallEstablished(Call call) {
        isCallOngoing.set(true);
        player.stopProgressTone();
        player.stopRingtone();
        currentCallStart = System.currentTimeMillis();
        broadCastBus.postSticky(Event.createSticky(ON_CALL_ESTABLISHED, null, CallData.from(call, CALL_TYPE_VOICE, currentCallStart)));
    }

    @Override
    public void onCallEnded(Call call) {
        // TODO: 7/15/2016 play a simple tone to signify call ended
        isCallOngoing.set(false);
        player.stopRingtone();
        player.stopProgressTone();
        call.removeCallListener(this);
        broadCastBus.postSticky(Event.createSticky(ON_CAL_ENDED, null, CallData.from(call, CALL_TYPE_VOICE, System.currentTimeMillis())));
        Realm realm = Message.REALM();
        Conversation conversation = Conversation.newConversation(realm, call.getRemoteUserId());
        try {
            CallBody callBody = new CallBody(call.getCallId(), currentCallStart == 0 ? 0 : (int) (System.currentTimeMillis() - currentCallStart), CallBody.CALL_TYPE_VOICE);
            boolean isOutGoing = call.getDirection() == CallDirection.OUTGOING;
            Message lastMessage = Message.makeNewCallMessageAndPersist(realm, call.getRemoteUserId(), System.currentTimeMillis(), callBody, isOutGoing);
            realm.beginTransaction();
            conversation.setLastMessage(lastMessage);
            String summary = Message.getCallSummary(lastMessage);
            conversation.setSummary(summary);
            realm.commitTransaction();
        } finally {
            currentCallStart = 0;
            realm.close();
        }
    }

    @Override
    public void onShouldSendPushNotification(Call call, List<PushPair> list) {
        throw new UnsupportedOperationException();
    }
}
