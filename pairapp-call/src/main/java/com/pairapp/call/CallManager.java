package com.pairapp.call;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.StringRes;
import android.support.v4.app.NotificationManagerCompat;
import android.widget.Toast;

import com.pairapp.util.ConnectionUtils;
import com.pairapp.util.Event;
import com.pairapp.util.EventBus;
import com.pairapp.util.PLog;
import com.sinch.android.rtc.PushPair;
import com.sinch.android.rtc.Sinch;
import com.sinch.android.rtc.SinchClient;
import com.sinch.android.rtc.calling.Call;
import com.sinch.android.rtc.calling.CallListener;

import java.util.List;

/**
 * @author aminu on 7/14/2016.
 */
public class CallManager implements CallController {

    static final String TAG = CallManager.class.getSimpleName();
    public static final String ERR_CALL_ALREADY_ONGOING = "err_call_alread_ongoing";
    private final Application application;
    private final String environment;
    private static final String APP_KEY = "8a46c54f-0f44-481a-8727-63aa0561e6a7";
    private static final String APP_SECRET = "uORBRxz9m06k993JP85kIw==";
    private final String currUserId;
    private final EventBus bus;
    private SinchClient client;
    private final CallCenter callCenter;

    private CallManager(Application application, String currUserId, EventBus bus, boolean debug) {
        this.application = application;
        this.currUserId = currUserId;
        this.bus = bus;
        this.environment = debug ? "sandbox.sinch.com" : "app.sinch.com";
        callCenter = new CallCenter(bus, new AudioPlayer(application));
        initialiseClient();
    }

    public static CallController create(Application context,
                                        String currUserId,
                                        EventBus bus,
                                        boolean debug) {
        return new CallManager(context, currUserId, bus, debug);
    }

    private synchronized void initialiseClient() {
        client = Sinch.getSinchClientBuilder()
                .context(application)
                .environmentHost(environment)
                .applicationKey(APP_KEY)
                .applicationSecret(APP_SECRET)
                .userId(currUserId)
                .callerIdentifier(currUserId)
                .build();
    }

    @Override
    public synchronized void setup() {
        client.setSupportCalling(true);
        client.setSupportManagedPush(false);
        client.setSupportPushNotifications(true);
        client.setSupportActiveConnectionInBackground(true); // FIXME: 7/14/2016 change to false
        client.addSinchClientListener(new ClientListener(this));
        client.startListeningOnActiveConnection();
        client.getCallClient().addCallClientListener(callCenter);
        client.getCallClient().setRespectNativeCalls(false); // TODO: 7/15/2016 let users change this in settings
        client.start();
    }

    @Override
    public synchronized CallData callUser(String callRecipient, @CallType int callType) {
        if (!ConnectionUtils.isConnected() || !client.isStarted()) {
            notifyClientNotStarted(callRecipient);
            return null;
        } else {
            switch (callType) {
                case CALL_TYPE_VOICE:
                    if (callCenter.isCallOngoing.get()) {
                        bus.post(Event.create(ON_CAL_ERROR, new Exception(ERR_CALL_ALREADY_ONGOING, null), callRecipient));
                        return null;
                    } else {
                        callCenter.isCallOngoing.set(true);
                        Call call = client.getCallClient().callUser(callRecipient);
                        call.addCallListener(callCenter);
                        call.addCallListener(internalCallListener);
                        return CallData.from(call, callType, System.currentTimeMillis());
                    }
                case CALL_TYPE_VIDEO:
                case CALL_TYPE_CONFERENCE_VOICE:
                default:
                    throw new UnsupportedOperationException();
            }
        }
    }

    @Override
    public synchronized void answer(CallData data) {
        if (client.isStarted()) {
            Call call = client.getCallClient().getCall(data.getCallId());
            if (call != null) {
                call.answer();
            } else {
                PLog.f(TAG, "call not found to answer");
                bus.post(Event.create(ON_CAL_ERROR, new Exception(ERR_CALL_NOT_FOUND, null), data.getPeer()));
            }
        } else {
            notifyClientNotStarted(data.getPeer());
        }
    }


    @Override
    public synchronized void hangUp(CallData data) {
        if (client.isStarted()) {
            final Call call = client.getCallClient().getCall(data.getCallId());
            if (call != null) {
                call.hangup();
            } else {
                PLog.f(TAG, "call not found to hang up");
                bus.post(Event.create(ON_CAL_ERROR, new Exception(ERR_CALL_NOT_FOUND, null), data.getPeer()));
            }
        } else {
            notifyClientNotStarted(data.getPeer());
        }
    }

    private void notifyClientNotStarted(String peer) {
        bus.post(Event.create(ON_CAL_ERROR, new Exception(ERR_NOT_CONNECTED, null), peer));
        PLog.w(TAG, "client not started");
    }

    @Override
    public synchronized void enableSpeaker(CallData data) {
        if (client.isStarted()) {
            if (data.isLoudSpeaker()) {
                client.getAudioController().disableSpeaker();
                showToast(R.string.loud_speaker_off);
            } else {
                client.getAudioController().enableSpeaker();
                showToast(R.string.loud_speaker_on);
            }
            Call call = client.getCallClient().getCall(data.getCallId());
            bus.post(Event.create(ON_LOUD_SPEAKER, null, CallData.from(call, CALL_TYPE_VOICE,
                    data.getEstablishedTime(),
                    data.isMuted(),
                    !data.isLoudSpeaker()))); //negate the current status of the loudspeaker
        } else {
            notifyClientNotStarted(data.getPeer());
        }
    }

    private void showToast(final @StringRes int res) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(application, res, Toast.LENGTH_SHORT).
                        show();
            }
        });
    }

    @Override
    public synchronized void muteCall(CallData data) {
        if (client.isStarted()) {
            if (data.isMuted()) {
                client.getAudioController().unmute();
                showToast(R.string.call_unmuted);
            } else {
                client.getAudioController().mute();
                showToast(R.string.call_muted);
            }
            Call call = client.getCallClient().getCall(data.getCallId());
            bus.post(Event.create(ON_CALL_MUTED, null, CallData.from(call, CALL_TYPE_VOICE,
                    data.getEstablishedTime(),
                    !data.isMuted(),
                    data.isLoudSpeaker()))); //negate the current status of the loudspeaker
        } else {
            notifyClientNotStarted(data.getPeer());
        }
    }

    @Override
    public synchronized void shutDown() {
        if (client.isStarted()) {
            client.terminate();
        }
    }

    private final CallListener internalCallListener = new CallListener() {
        @Override
        public void onCallProgressing(Call call) {

        }

        @Override
        public void onCallEstablished(Call call) {

        }

        @Override
        public void onCallEnded(Call call) {
            client.getAudioController().disableSpeaker();
            client.getAudioController().unmute();
            NotificationManagerCompat.from(application)
                    .cancel(call.getRemoteUserId(), CALL_NOTIFICATION_ID);
            call.removeCallListener(internalCallListener);
        }

        @Override
        public void onShouldSendPushNotification(Call call, List<PushPair> list) {

        }
    };
}
