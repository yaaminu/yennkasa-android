package com.pairapp.messenger;

import com.pairapp.BuildConfig;
import com.pairapp.data.Message;
import com.pairapp.util.Config;
import com.pairapp.util.PLog;
import com.pairapp.util.Task;
import com.parse.ParseException;
import com.path.android.jobqueue.Params;
import com.path.android.jobqueue.RetryConstraint;

import org.json.JSONException;
import org.json.JSONObject;

import io.realm.Realm;

/**
 * by Null-Pointer on 11/28/2015.
 */
final class NotifyMessageStatusJob extends Task {
    private static final String TAG = NotifyMessageStatusJob.class.getName();
    private static final String PRIORITY = "priority",
            MESSAGE = "message",
            MESSAGE_STATUS = "messageStatus";
    private static final int RETRY_LIMIT = 10;
    private final Message message;
    private final int messageStatus;

    private NotifyMessageStatusJob(Params params, int messageStatus, Message message) {
        super(params);
        this.messageStatus = messageStatus;
        this.message = message;
    }

    //required no-arg constructor
    @SuppressWarnings("unused")
    public NotifyMessageStatusJob() {
        this.message = null;
        this.messageStatus = -1;
    }

    @Override
    protected int getRetryLimit() {
        return RETRY_LIMIT;
    }

    @Override
    public void onAdded() {
        PLog.d(TAG, "yay! message status report added to job queue");
    }

    @Override
    protected RetryConstraint shouldReRunOnThrowable(Throwable throwable, int runCount, int maxRunCount) {
        if (throwable instanceof ParseException) {
            ParseException e = (ParseException) throwable;
            int code = e.getCode();
            if (code == ParseException.EXCEEDED_QUOTA) {
                // fail silently now
                return RetryConstraint.CANCEL;
            } else if (code == ParseException.REQUEST_LIMIT_EXCEEDED) {
                return RetryConstraint.createExponentialBackoff(runCount,/*one minute*/ 1000 * 60);
            }
        } else if (throwable instanceof RuntimeException) { //we wont eat up this error
            if (BuildConfig.DEBUG) {
                throw new RuntimeException(throwable);
            }
            PLog.d(TAG, "on run  raised a runtime exception", throwable);
            return RetryConstraint.CANCEL;
        } else {
            PLog.d(TAG, "onRun raised unknown checked exception!", throwable);
            if (BuildConfig.DEBUG) {
                throw new RuntimeException(throwable); //crash and burn
            }
        }
        return RetryConstraint.createExponentialBackoff(runCount, 1000);
    }

    @Override
    public void onRun() throws Throwable {
        if (message == null) {
            throw new IllegalStateException("invalid task, message is null");
        }

        if (messageStatus == -1) {
            throw new IllegalStateException("message status is invalid");
        }

        Realm realm = Message.REALM(Config.getApplicationContext());
        try {
            Message message = realm.where(Message.class).equalTo(Message.FIELD_ID, this.message.getId()).findFirst();
            if (message != null) {
                if (message.getState() == Message.STATE_SEEN && messageStatus == Message.STATE_RECEIVED) {
                    PLog.d(TAG, "late status report, messageState is seen, not reporting as received no more");
                    return;
                }
            }
//            MessageCenter.doNotify(this.message, this.messageStatus);
            message = realm.where(Message.class).equalTo(Message.FIELD_ID, this.message.getId()).findFirst();
            if (message != null) {
                realm.beginTransaction();
                if (messageStatus == Message.STATE_RECEIVED && message.getState() == Message.STATE_SEEN) {
                    //how did this happen
//                    if (BuildConfig.DEBUG) {
//                        throw new IllegalStateException("impossible!");
//                    }
                    PLog.w(TAG, "late message status report");
                } else {
                    message.setState(messageStatus);
                }
                realm.commitTransaction();
            } else {
                PLog.d(TAG, "message not found for marking as received or seen, was it deleted?");
            }
        } finally {
            realm.close();
        }
    }

    @Override
    protected void onCancel() {

    }

    static NotifyMessageStatusJob makeNew(int messageStatus, Message message) {
        return makeNew(1, messageStatus, message);
    }

    private static NotifyMessageStatusJob makeNew(int priority, int messageStatus, Message message) {
        if (messageStatus != Message.STATE_RECEIVED && messageStatus != Message.STATE_SEEN) {
            throw new IllegalArgumentException("invalid message status");
        }
        if (message == null) {
            throw new IllegalArgumentException("message == null");
        }
        if (message.getType() == Message.TYPE_DATE_MESSAGE || message.getType() == Message.TYPE_TYPING_MESSAGE) {
            throw new IllegalArgumentException("invalid message, type is " + message.getType());
        }
        //defensive copy
        message = Message.copy(message, true);
        Params params = new Params(priority);
        params.setPersistent(true);
        params.setGroupId("messageStatusGroup");
        params.setRequiresNetwork(true);
        params.addTags("messageStatus");
        return new NotifyMessageStatusJob(params, messageStatus, message);
    }

    @Override
    protected JSONObject toJSON() {
        JSONObject object = new JSONObject();
        try {
            object.put(PRIORITY, getPriority());
            object.put(MESSAGE, Message.toJSON(message));
            object.put(MESSAGE_STATUS, messageStatus);
            return object;
        } catch (JSONException e) {
            throw new RuntimeException(e.getCause());
        }
    }

    @Override
    protected Task fromJSON(JSONObject json) {
        try {
            Message message = Message.fromJSON(json.getString(MESSAGE));
            int messageStatus = json.getInt(MESSAGE_STATUS),
                    priority = json.getInt(PRIORITY);
            return makeNew(priority, messageStatus, message);
        } catch (JSONException e) {
            throw new RuntimeException(e.getCause());
        }
    }
}
