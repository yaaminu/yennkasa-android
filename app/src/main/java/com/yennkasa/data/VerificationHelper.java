package com.yennkasa.data;

import com.sinch.verification.Config;
import com.sinch.verification.InitiationResult;
import com.sinch.verification.SinchVerification;
import com.sinch.verification.Verification;
import com.sinch.verification.VerificationListener;
import com.yennkasa.call.CallManager;
import com.yennkasa.util.Event;
import com.yennkasa.util.EventBus;
import com.yennkasa.util.PLog;

import static android.content.ContentValues.TAG;

/**
 * Created by yaaminu on 1/11/17.
 */

public class VerificationHelper implements VerificationListener {

    public static final String VERIFICATION_SUCCESS = "verification.success";
    public static final String VERIFICATION_FAILED = "verification.failed";
    public static final String VERIFICATION_INITIATED = "verification.initiated";
    private final String userId;
    private Verification sinchVerification;

    public VerificationHelper(String userId) {
        Config config = SinchVerification.config()
                .applicationKey(CallManager.APPLICATION_KEY)
                .context(com.yennkasa.util.Config.getApplicationContext())
                .build();
        this.userId = userId;
        sinchVerification = SinchVerification.createSmsVerification(config, "+" + userId, this);
    }

    public void sendVerificationToken() {
        sinchVerification.initiate();
    }

    public void verify(String code) {
        sinchVerification.verify(code);
    }

    @Override
    public void onInitiated(InitiationResult initiationResult) {
        PLog.d(TAG, "verification started");
        EventBus.getDefault().post(Event.create(VERIFICATION_INITIATED, null, this.userId));
    }

    @Override
    public void onInitiationFailed(Exception e) {
        PLog.d(TAG, e.getMessage(), e);
        EventBus.getDefault().postSticky(Event.createSticky(VERIFICATION_FAILED, e, null));
    }

    @Override
    public void onVerified() {
        EventBus.getDefault().postSticky(Event.createSticky(VERIFICATION_SUCCESS, null, userId));
    }

    @Override
    public void onVerificationFailed(Exception e) {
        EventBus.getDefault().postSticky(Event.createSticky(VERIFICATION_FAILED, e, null));
    }
}
