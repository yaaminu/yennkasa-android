package com.yennkasa.ui;

import android.content.Intent;
import android.support.annotation.LayoutRes;

import com.yennkasa.R;

public class VoiceCallActivity extends BaseCallActivity {


    @Override
    @LayoutRes
    protected int getLayout() {
        return R.layout.activity_call;
    }

    @Override
    protected Intent getNotificationIntent() {
        Intent intent = new Intent(this, VoiceCallActivity.class);
        intent.putExtra(EXTRA_CALL_DATA, getCallData());
        return intent;
    }

    @Override
    protected String getNotificationTitle() {
        return getString(R.string.ongoing_voice_call_notice, getPeer().getName());
    }
}

