package com.pairapp.call;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.util.Pair;
import android.widget.Toast;

import com.pairapp.util.ConnectionUtils;
import com.pairapp.util.Event;
import com.pairapp.util.EventBus;
import com.pairapp.util.PLog;
import com.sinch.android.rtc.Sinch;
import com.sinch.android.rtc.SinchClient;
import com.sinch.android.rtc.calling.Call;
import com.sinch.android.rtc.calling.CallState;

import java.util.HashMap;
import java.util.Map;

/**
 * @author aminu on 7/14/2016.
 */
public class CallManager implements CallController {

    static final String TAG = CallManager.class.getSimpleName();
    public static final String APPLICATION_KEY = "8a46c54f-0f44-481a-8727-63aa0561e6a7";

    @NonNull
    private final Application application;
    @NonNull
    private final EventBus bus;
    @NonNull
    private final SinchClient client;
    @NonNull
    private final CallCenter callCenter;
    private final RegistrationTokenSource source;

    private CallManager(@NonNull Application application, @NonNull String currUserId, @NonNull EventBus bus, @NonNull RegistrationTokenSource source, boolean debug) {
        this.application = application;
        this.bus = bus;

        this.source = source;
        client = Sinch.getSinchClientBuilder()
                .context(application)
                .environmentHost(debug ? "sandbox.sinch.com" : "app.sinch.com")
                .userId(currUserId)
                .applicationKey(APPLICATION_KEY)
                .callerIdentifier(currUserId)
                .build();
        callCenter = new CallCenter(bus, client, new AudioPlayer(application), currUserId);
    }

    public interface RegistrationTokenSource {
        Pair<String, Long> getSinchRegistrationToken();
    }

    @NonNull
    public static CallController create(@NonNull Application context,
                                        @NonNull String currUserId,
                                        @NonNull EventBus bus,
                                        @NonNull RegistrationTokenSource source,
                                        boolean debug) {
        return new CallManager(context, currUserId, bus, source, debug);
    }


    @Override
    public synchronized void setup() {
        client.setSupportCalling(true);
        client.setSupportPushNotifications(true);
        client.registerPushNotificationData(client.getLocalUserId().getBytes());
        client.setSupportActiveConnectionInBackground(false);
        client.setSupportManagedPush(false);
        client.addSinchClientListener(new ClientListener(source));
        client.getCallClient().addCallClientListener(callCenter);
        client.getCallClient().setRespectNativeCalls(false); // TODO: 7/15/2016 let users change this in settings
        //noinspection ConstantConditions
        client.start();
    }

    @Nullable
    @Override
    public synchronized CallData callUser(@NonNull String callRecipient, @CallType int callType) {
        if (!ConnectionUtils.isConnected() || !client.isStarted()) {
            notifyClientNotStarted(callRecipient);
            return null;
        } else {
            if (callCenter.isCallOngoing()) {
                if (!callRecipient.equals(callCenter.getCurrentPeer())) {
                    bus.post(Event.create(ON_CAL_ERROR, new Exception(ERR_CALL_ALREADY_ONGOING, null), callRecipient));
                }
                return null;
            }
            final Call call;
            Map<String, String> headers = new HashMap<>(1);
            switch (callType) {
                case CALL_TYPE_VOICE:
                    headers.put(CallCenter.CALL_TYPE, CallCenter.HEADER_VOICE_CALL);
                    call = client.getCallClient().callUser(callRecipient);
                    break;
                case CALL_TYPE_VIDEO:
                    headers.put(CallCenter.CALL_TYPE, CallCenter.HEADER_VIDEO_CALL);
                    call = client.getCallClient().callUserVideo(callRecipient, headers);
                    break;
                case CALL_TYPE_CONFERENCE_VOICE:
                default:
                    throw new UnsupportedOperationException();
            }

            callCenter.setCurrentPeer(callRecipient);
            callCenter.setCallOngoing(call.getCallId());
            call.addCallListener(callCenter);
            return CallData.from(call, callType, System.currentTimeMillis());
        }
    }


    @Override
    public synchronized void answer(@NonNull CallData data) {
        if (client.isStarted()) {
            Call call = client.getCallClient().getCall(data.getCallId());
            if (call != null) {
                if (CallState.ENDED.equals(call.getState())) {
                    forceEndCallIfNotEnded(call);
                } else {
                    call.answer();
                }
            } else {
                PLog.f(TAG, "call not found to answer");
                bus.post(Event.create(ON_CAL_ERROR, new Exception(ERR_CALL_NOT_FOUND, null), data.getPeer()));
            }
        } else {
            notifyClientNotStarted(data.getPeer());
        }
    }

    private void forceEndCallIfNotEnded(final Call call) {
        //force hangup the call if the callCenter#onCallEnded is not invoked
        //by the sinch client. this is just a quick fix for a problem we facing
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                if (callCenter.isCallOngoing()) {
                    callCenter.onCallEnded(call);
                }
            }
        }, 250);
    }

    @Override
    public synchronized void hangUp(@NonNull CallData data) {
        if (client.isStarted()) {
            final Call call = client.getCallClient().getCall(data.getCallId());
            if (call != null) {
                call.hangup();
                forceEndCallIfNotEnded(call);
            } else {
                PLog.f(TAG, "call not found to hang up");
                bus.post(Event.create(ON_CAL_ERROR, new Exception(ERR_CALL_NOT_FOUND, null), data.getPeer()));
            }
        } else {
            notifyClientNotStarted(data.getPeer());
        }
    }

    private void notifyClientNotStarted(@NonNull String peer) {
        bus.post(Event.create(ON_CAL_ERROR, new Exception(ERR_NOT_CONNECTED, null), peer));
        PLog.w(TAG, "client not started");
    }

    @Override
    public synchronized void enableSpeaker(@NonNull CallData data) {
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
    public synchronized void muteCall(@NonNull CallData data) {
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

    @Override
    public void handleCallPushPayload(String payload) {
        if (client.isStarted()) {
            client.relayRemotePushNotificationPayload(payload);
        } else {
            if (BuildConfig.DEBUG) {
                throw new IllegalStateException();
            }
            PLog.f(TAG, "missed incoming call push");
        }
    }
}
