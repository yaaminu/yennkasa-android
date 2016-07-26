package com.pairapp.call;

import android.support.annotation.IntDef;

/**
 * @author aminu on 7/14/2016.
 */
public interface CallController {

    String ON_CALL_PROGRESSING = "onCallProgressing";
    String ON_CALL_ESTABLISHED = "onCallEstablished";
    String ON_CAL_ENDED = "onCalEnded";
    String ON_IN_COMING_CALL = "onInComingCall";
    String ON_CAL_ERROR = "onCallError";
    String ERR_CALL_NOT_FOUND = "ENOTFOUND";
    String ERR_NOT_CONNECTED = "ECONNREFUSED";
    String ERR_CALL_ALREADY_ONGOING = "EBUSY";
    String ON_CALL_MUTED = "onCallMuted";
    String ON_LOUD_SPEAKER = "onLoudSpeaker";
    int CALL_NOTIFICATION_ID = 1001;
    int MISSED_CALL_NOTIFICATION_ID = 1002;
    String VIDEO_CALL_LOCAL_VIEW = "video call local view";
    String VIDEO_CALL_REMOTE_VIEW = "video call remote view";
    String ERR_VIDEO_LOAD_FAILED = "err_video_load_failed";

    void hangUp(CallData data);

    void answer(CallData data);

    void enableSpeaker(CallData data);

    void muteCall(CallData data);

    @IntDef({CALL_TYPE_VOICE, CALL_TYPE_VIDEO, CALL_TYPE_CONFERENCE_VOICE})
    @interface CallType {
    }


    int CALL_TYPE_VOICE = 1, CALL_TYPE_VIDEO = 2, CALL_TYPE_CONFERENCE_VOICE = 3;

    void setup();

    CallData callUser(String callRecipient, @CallType int callType);

    void shutDown();
}
