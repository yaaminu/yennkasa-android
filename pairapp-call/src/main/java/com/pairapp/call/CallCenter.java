package com.pairapp.call;

import com.pairapp.util.Event;
import com.pairapp.util.EventBus;
import com.sinch.android.rtc.PushPair;
import com.sinch.android.rtc.calling.Call;
import com.sinch.android.rtc.calling.CallClient;
import com.sinch.android.rtc.calling.CallClientListener;
import com.sinch.android.rtc.calling.CallListener;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

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
        broadCastBus.postSticky(Event.createSticky(ON_CALL_ESTABLISHED, null, CallData.from(call, CALL_TYPE_VOICE, System.currentTimeMillis())));
    }

    @Override
    public void onCallEnded(Call call) {
        // TODO: 7/15/2016 play a simple tone to signify call ended
        // TODO: 7/15/2016 log the call
        isCallOngoing.set(false);
        player.stopRingtone();
        player.stopProgressTone();
        call.removeCallListener(this);
        broadCastBus.postSticky(Event.createSticky(ON_CAL_ENDED, null, CallData.from(call, CALL_TYPE_VOICE, System.currentTimeMillis())));
    }

    @Override
    public void onShouldSendPushNotification(Call call, List<PushPair> list) {
        throw new UnsupportedOperationException();
    }
}
