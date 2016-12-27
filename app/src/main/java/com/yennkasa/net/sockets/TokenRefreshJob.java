package com.yennkasa.net.sockets;

import com.yennkasa.util.Event;
import com.yennkasa.util.EventBus;
import com.yennkasa.util.GenericUtils;
import com.yennkasa.util.PLog;
import com.yennkasa.util.Task;
import com.path.android.jobqueue.Params;
import com.path.android.jobqueue.RetryConstraint;

import org.json.JSONObject;

/**
 * @author by aminu on 11/13/2016.
 */
class TokenRefreshJob extends Task {
    public static final String TOKEN_NEW_REFRESH = "token.new.refresh";
    private SenderImpl.Authenticator authenticator;

    private TokenRefreshJob(SenderImpl.Authenticator authenticator, Params params) {
        super(params);
        this.authenticator = authenticator;
    }

    public static Task create(SenderImpl.Authenticator authenticator) {
        Params params = new Params(1000);
        params.setPersistent(false);
        params.addTags("tokenRefresh");
        params.setRequiresNetwork(true);
        params.setGroupId("tokenGroup");
        return new TokenRefreshJob(authenticator, params);
    }

    @Override
    protected JSONObject toJSON() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected Task fromJSON(JSONObject jsonObject) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void onAdded() {
        PLog.d(SenderImpl.TAG, "added");
    }

    @Override
    public void onRun() throws Throwable {
        String authToken = authenticator.requestNewToken();
        GenericUtils.ensureNotNull(authToken);
        EventBus.getDefault().post(Event.create(TOKEN_NEW_REFRESH, null, authToken));
    }

    @Override
    protected RetryConstraint shouldReRunOnThrowable(Throwable throwable, int runCount, int maxRunCount) {
        return RetryConstraint.createExponentialBackoff(runCount, 100);
    }

    @Override
    protected void onCancel() {
        PLog.d(SenderImpl.TAG, "cancelled");
    }

    @Override
    protected int getRetryLimit() {
        return 10000;
    }
}
