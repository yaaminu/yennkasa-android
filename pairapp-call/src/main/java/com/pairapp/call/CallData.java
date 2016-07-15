package com.pairapp.call;

import android.os.Parcel;
import android.os.Parcelable;

import com.sinch.android.rtc.calling.Call;
import com.sinch.android.rtc.calling.CallDetails;
import com.sinch.android.rtc.calling.CallDirection;

/**
 * @author aminu on 7/14/2016.
 */
public class CallData implements Parcelable {
    private final String peer;
    private final long callDate;
    private final boolean isOutGoing;
    private final int callDuration;
    private final int callType;
    private final String callId;

    CallData(String peer, String callId, long whenCalled, int duration, int callType, boolean isOutGoing) {
        this.peer = peer;
        this.callId = callId;
        this.callDate = whenCalled;
        this.callDuration = duration;
        this.isOutGoing = isOutGoing;
        this.callType = callType;
    }

    protected CallData(Parcel in) {
        peer = in.readString();
        callDate = in.readLong();
        isOutGoing = in.readByte() != 0;
        callDuration = in.readInt();
        callType = in.readInt();
        callId = in.readString();
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
        CallDetails details = call.getDetails();
        return new CallData(call.getRemoteUserId(), call.getCallId(),
                details.getStartedTime(), (int) (details.getEndedTime() - details.getEstablishedTime()),
                callType, call.getDirection() == CallDirection.OUTGOING
        );
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

    public int getCallDuration() {
        return callDuration;
    }

    @CallController.CallType
    public int getCallType() {
        return callType;
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
        dest.writeInt(callDuration);
        dest.writeInt(callType);
        dest.writeString(callId);
    }
}
