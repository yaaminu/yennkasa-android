package com.yennkasa.messenger;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;

import com.yennkasa.R;
import com.yennkasa.util.Event;
import com.yennkasa.util.EventBus;
import com.yennkasa.util.PLog;

import static com.yennkasa.net.ParseClient.VERIFICATION_CODE_RECEIVED;

public class SmsReciever extends BroadcastReceiver {
    private static final String TAG = "SmsReciever";

    public SmsReciever() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        PLog.d(TAG, "sms recieved");
        Bundle bundle = intent.getExtras();
        if (bundle != null) {
            Object[] pdus = (Object[]) bundle.get("pdus");
            if (pdus != null) {
                SmsMessage[] messges = new SmsMessage[pdus.length];
                for (int i = 0; i < pdus.length; i++) {
                    messges[i] = SmsMessage.createFromPdu((byte[]) pdus[i]);
                }
                for (SmsMessage messge : messges) {
                    PLog.d(TAG, "from: %s, \nbody: %s", messge.getOriginatingAddress(), messge.getMessageBody());
                    if (messge.getMessageBody().startsWith(context.getString(R.string.verification_code))) {
                        EventBus.getDefault().postSticky(Event.createSticky(VERIFICATION_CODE_RECEIVED, null,
                                retrieveTokenFromMessage(messge.getMessageBody())));
                    }
                }
            }
        }
    }

    private String retrieveTokenFromMessage(String messageBody) {
        return messageBody.split(":")[1].trim();
    }
}
