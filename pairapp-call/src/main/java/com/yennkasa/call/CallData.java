package com.yennkasa.call;

import android.os.Parcel;
import android.os.Parcelable;

import com.sinch.android.rtc.calling.Call;
import com.sinch.android.rtc.calling.CallDetails;
import com.sinch.android.rtc.calling.CallDirection;

import java.util.Map;

import static com.yennkasa.call.CallController.CALL_TYPE_CONFERENCE_VOICE;
import static com.yennkasa.call.CallController.CALL_TYPE_VIDEO;
import static com.yennkasa.call.CallController.CALL_TYPE_VOICE;

/**
 * @author aminu on 7/14/2016.
 */
public class CallData implements Parcelable {
    private static final String HEADER_CONFERENCE_VOICE_CALL = "cvo";
    private final String peer;
    private final long callDate;
    private final boolean isOutGoing;
    private final int callType;
    private final String callId;
    private int callState;

    public static final int INITIATING = 0,
            PROGRESSING = 1,
            ESTABLISHED = 2,
            ENDED = 3,
            TRANSFERRING = 4,
            CONNECTING_CALL = 5;
    private final long establishedTime;
    private final boolean loudSpeaker, muted;

    CallData(String peer, String callId, long whenCalled, long establishedTime,
             int callType, int callState, boolean isOutGoing, boolean muted, boolean loudSpeaker) {
        this.peer = peer;
        this.callId = callId;
        this.callDate = whenCalled;
        this.isOutGoing = isOutGoing;
        this.callType = callType;
        this.callState = callState;
        this.establishedTime = establishedTime;
        this.loudSpeaker = loudSpeaker;
        this.muted = muted;
    }

    protected CallData(Parcel in) {
        peer = in.readString();
        callDate = in.readLong();
        isOutGoing = in.readByte() != 0;
        callType = in.readInt();
        callId = in.readString();
        callState = in.readInt();
        establishedTime = in.readLong();
        loudSpeaker = in.readByte() != 0;
        muted = in.readByte() != 0;
    }

    public static final Creator<CallData> CREATOR = new Creator<CallData>() {
        @Override
        public CallData createFromParcel(Parcel in) {
            return new CallData(in);
        }

        @Override
        public CallData[] newArray(int size) {
            return new CallData[size];
        }
    };

    static CallData from(Call call, int callType) {
        return from(call, callType, System.currentTimeMillis());
    }

    static CallData from(Call call, int callType, long dateCallEstablished) {
        return from(call, callType, dateCallEstablished, false, false);
    }

    static CallData from(Call call, int callType, long dateCallEstablished, boolean muted, boolean isLoudSpeaker) {
        CallDetails details = call.getDetails();
        return new CallData(call.getRemoteUserId(), call.getCallId(),
                details.getStartedTime(), dateCallEstablished,
                callType, call.getState().ordinal(),
                call.getDirection() == CallDirection.OUTGOING,
                muted, isLoudSpeaker
        );
    }

    static int getCallType(Call call) {
        Map<String, String> headers = call.getHeaders();
        if (headers == null || headers.isEmpty()) {
            return call.getDetails().isVideoOffered() ? CALL_TYPE_VIDEO : CALL_TYPE_VOICE;
        }
        String callType = headers.get(CallCenter.CALL_TYPE);
        if (CallCenter.HEADER_VOICE_CALL.equals(callType)) {
            return CALL_TYPE_VOICE;
        } else if (CallCenter.HEADER_VIDEO_CALL.equals(callType)) {
            return CALL_TYPE_VIDEO;
        } else if (HEADER_CONFERENCE_VOICE_CALL.equals(headers.get(CallCenter.CALL_TYPE))) {
            return CALL_TYPE_CONFERENCE_VOICE;
        } else {
            return call.getDetails().isVideoOffered() ? CALL_TYPE_VIDEO : CALL_TYPE_VOICE;
        }
    }

    public String getPeer() {
        return peer;
    }

    String getCallId() {
        return callId;
    }

    public boolean isOutGoing() {
        return isOutGoing;
    }

    public long getCallDate() {
        return callDate;
    }

    public long getEstablishedTime() {
        return establishedTime;
    }

    public int getCallState() {
        return callState;
    }

    @CallController.CallType
    public int getCallType() {
        return callType;
    }


    public boolean isLoudSpeaker() {
        return loudSpeaker;
    }

    public boolean isMuted() {
        return muted;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(peer);
        dest.writeLong(callDate);
        dest.writeByte((byte) (isOutGoing ? 1 : 0));
        dest.writeInt(callType);
        dest.writeString(callId);
        dest.writeInt(callState);
        dest.writeLong(establishedTime);
        dest.writeByte((byte) (loudSpeaker ? 1 : 0));
        dest.writeByte((byte) (muted ? 1 : 0));
    }

    public static CallData connectionCall(Call call, int callType) {
        CallData callData = CallData.from(call, callType);
        callData.callState = CONNECTING_CALL;
        return callData;
    }
}
