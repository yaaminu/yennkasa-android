package com.pairapp.call;

import android.app.Application;

import com.pairapp.util.EventBus;
import com.pairapp.util.PLog;
import com.sinch.android.rtc.Sinch;
import com.sinch.android.rtc.SinchClient;
import com.sinch.android.rtc.calling.Call;

/**
 * @author aminu on 7/14/2016.
 */
public class CallManager implements CallController {

    static final String TAG = CallManager.class.getSimpleName();
    private final Application application;
    private final String environment;
    private static final String APP_KEY = "8a46c54f-0f44-481a-8727-63aa0561e6a7";
    private static final String APP_SECRET = "uORBRxz9m06k993JP85kIw==";
    private final String currUserId;
    private final EventBus bus;
    private SinchClient client;

    private CallManager(Application application, String currUserId, EventBus bus, boolean debug) {
        this.application = application;
        this.currUserId = currUserId;
        this.bus = bus;
        this.environment = debug ? "sandbox.sinch.com" : "app.sinch.com";
        initialiseClient();
    }

    private void initialiseClient() {
        client = Sinch.getSinchClientBuilder()
                .context(application)
                .environmentHost(environment)
                .applicationKey(APP_KEY)
                .applicationSecret(APP_SECRET)
                .userId(currUserId)
                .callerIdentifier(currUserId)
                .build();
    }

    public static CallController create(Application context,
                                        String currUserId,
                                        EventBus bus,
                                        boolean debug) {
        return new CallManager(context, currUserId, bus, debug);
    }

    @Override
    public void setup() {
        client.setSupportCalling(true);
        client.setSupportManagedPush(false);
        client.setSupportPushNotifications(true);
        client.setSupportActiveConnectionInBackground(true); // FIXME: 7/14/2016 change to false
        client.addSinchClientListener(new ClientListener());
        client.start();
        client.startListeningOnActiveConnection();
        client.getCallClient().addCallClientListener(new CallCenter(bus));
        client.getCallClient().setRespectNativeCalls(false); // TODO: 7/15/2016 let users change this in settings
    }

    @Override
    public CallData callUser(String callRecipient, @CallType int callType) {
        if (!client.isStarted()) {
            throw new IllegalStateException("not started");
        }
        switch (callType) {
            case CALL_TYPE_VOICE:
                Call call = client.getCallClient().callUser(callRecipient);
                return CallData.from(call, callType);
            case CALL_TYPE_VIDEO:
            case CALL_TYPE_CONFERENCE_VOICE:
            default:
                throw new UnsupportedOperationException();
        }
    }

    @Override
    public void answer(CallData data) {
        if (client.isStarted()) {
            Call call = client.getCallClient().getCall(data.getCallId());
            if (call != null) {
                call.answer();
            } else {
                PLog.f(TAG, "call not found to answer");
            }
        } else {
            PLog.w(TAG, "client not started");
        }
    }

    @Override
    public void hangUp(CallData data) {
        if (client.isStarted()) {
            Call call = client.getCallClient().getCall(data.getCallId());
            if (call != null) {
                call.hangup();
            } else {
                PLog.f(TAG, "call not found to hang up");
            }
        } else {
            PLog.w(TAG, "client not started");
        }
    }

    @Override
    public void shutDown() {
        if (client.isStarted())
            client.terminate();
    }

    public interface Sender {
        void send(byte[] payload);
    }

}
