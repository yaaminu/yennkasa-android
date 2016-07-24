package com.pairapp.data;

import io.realm.RealmObject;

/**
 * @author aminu on 7/20/2016.
 */
public class CallBody extends RealmObject {

    public static final String FIELD_CALL_ID = "callId", FIELD_CALL_DURATION = "callDuration", FIELD_CALL_TYPE = "callType";
    public static final int CALL_TYPE_VOICE = 1, CALL_TYPE_VIDEO = 2, CALL_TYPE_CONFERENCE = 3;
    private String callId;
    private int callDuration;
    private int callType;

    public CallBody() {
    }

    public CallBody(String callId, int callDuration, int callType) {
        this.callId = callId;
        this.callDuration = callDuration;
        this.callType = callType;
    }

    public int getCallType() {
        return callType;
    }

    public int getCallDuration() {
        return callDuration;
    }

    public String getCallId() {
        return callId;
    }
}
