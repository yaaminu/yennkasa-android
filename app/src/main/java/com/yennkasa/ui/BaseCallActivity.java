package com.yennkasa.ui;

import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.rey.material.widget.SnackBar;
import com.yennkasa.BuildConfig;
import com.yennkasa.R;
import com.yennkasa.call.CallData;
import com.yennkasa.data.Message;
import com.yennkasa.data.User;
import com.yennkasa.data.UserManager;
import com.yennkasa.messenger.MessengerBus;
import com.yennkasa.messenger.YennkasaClient;
import com.yennkasa.util.Event;
import com.yennkasa.util.PLog;
import com.yennkasa.util.ViewUtils;

import java.util.concurrent.TimeUnit;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import rx.Observable;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;

import static android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD;
import static android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
import static android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
import static android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;
import static com.yennkasa.messenger.MessengerBus.ANSWER_CALL;

/**
 * @author aminu on 7/23/2016.
 */
public abstract class BaseCallActivity extends PairAppActivity {
    private static final int REQUEST_CODE = 1001;

    public static final String EXTRA_CALL_DATA = "callData";

    @SuppressWarnings("NullableProblems") //will always be initialised in onCreate.
    @NonNull
    private CallData callData;

    @Bind(R.id.iv_user_avatar)
    ImageView userAvatar;

    @Nullable
    @Bind(R.id.tv_user_name)
    TextView tvUserName;

    @Bind(R.id.tv_call_state)
    TextView tvCallState;

    @Bind(R.id.bt_decline_call)
    View declineCall;

    @Bind(R.id.bt_end_call)
    View endCall;

    @Bind(R.id.bt_answer_call)
    View answerCall;

    @Bind(R.id.bt_speaker)
    ImageButton enableSpeaker;

    @Bind(R.id.bt_mute)
    ImageButton mute;

    @SuppressWarnings("NullableProblems") //will always be initialised in onCreate.
    @NonNull
    private User peer;


    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); //getIntent will be the new intent see super implementation
        handleIntent();
    }

    void handleIntent() {
        callData = getIntent().getParcelableExtra(EXTRA_CALL_DATA);
        //noinspection ConstantConditions
        assert callData != null;
        String peerId = callData.getPeer();
        peer = UserManager.getInstance().fetchUserIfRequired(peerId);
    }

    private void refreshDisplay() {
        switch (callData.getCallState()) {
            case CallData.INITIATING:
                if (callData.isOutGoing()) {
                    tvCallState.setText(R.string.dialing);
                    ViewUtils.hideViews(declineCall, answerCall);
                    ViewUtils.showViews(endCall, mute, enableSpeaker);
                } else {
                    tvCallState.setText(getString(R.string.incoming_call, peer.getName()));
                    ViewUtils.showViews(declineCall, answerCall);
                    ViewUtils.hideViews(endCall, mute, enableSpeaker);
                }
                break;
            case CallData.PROGRESSING:
            case CallData.TRANSFERRING:
                tvCallState.setText(R.string.call_progressing);
                if (callData.isOutGoing()) {
                    ViewUtils.hideViews(declineCall, answerCall);
                    ViewUtils.showViews(endCall, mute, enableSpeaker);
                } else {
                    ViewUtils.showViews(declineCall, answerCall);
                    ViewUtils.hideViews(endCall, mute, enableSpeaker);
                    if (answeringCall) {
                        tvCallState.setText(R.string.connecting);
                    }
                }
                break;
            case CallData.CONNECTING_CALL:
                tvCallState.setText(R.string.connecting);
                ViewUtils.hideViews(declineCall, answerCall);
                ViewUtils.showViews(endCall, mute, enableSpeaker);
                break;
            case CallData.ESTABLISHED:
                startTimer();
                ViewUtils.hideViews(declineCall, answerCall);
                ViewUtils.showViews(endCall, mute, enableSpeaker);
                break;
            case CallData.ENDED:
                tvCallState.setText(R.string.call_ended);
                new Handler().postDelayed(new Runnable() {

                    @Override
                    public void run() {
                        finish();
                    }
                }, 2000);
                ViewUtils.hideViews(endCall, declineCall, answerCall, mute, enableSpeaker);
                break;
            default:
                throw new AssertionError();
        }

        mute.setBackgroundResource(callData.isMuted() ? R.drawable.call_button_selected_background : R.drawable.round_button);
        enableSpeaker.setBackgroundResource(callData.isLoudSpeaker() ? R.drawable.call_button_selected_background : R.drawable.round_button);
    }

    @NonNull
    private final Subscriber<Long> subscriber = new Subscriber<Long>() {
        @Override
        public void onCompleted() {

        }

        @Override
        public void onError(Throwable e) {
            PLog.e(TAG, e.getMessage());
        }

        @Override
        public void onNext(Long o) {
            if (callData.getCallState() == CallData.ESTABLISHED) {
                long duration = System.currentTimeMillis() - callData.getEstablishedTime();
                tvCallState.setText(Message.formatTimespan(duration));
            }
        }
    };

    private void startTimer() {
        Observable.interval(0, 1000, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(subscriber);
    }


    @LayoutRes
    protected abstract int getLayout();

    protected abstract Intent getNotificationIntent();

    protected abstract String getNotificationTitle();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(FLAG_DISMISS_KEYGUARD |
                FLAG_SHOW_WHEN_LOCKED | FLAG_TURN_SCREEN_ON | FLAG_KEEP_SCREEN_ON);
        super.setContentView(getLayout());
        ButterKnife.bind(this);
        handleIntent();
        registerForEvent(MessengerBus.ON_CALL_EVENT);
    }

    @Override
    protected void onResume() {
        super.onResume();
        populateUserData();
        refreshDisplay();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        unRegister(MessengerBus.ON_CALL_EVENT);
        if (!subscriber.isUnsubscribed()) {
            subscriber.unsubscribe();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        NotificationManagerCompat.from(this)
                .cancel(getPeer().getUserId(), MessengerBus.CALL_NOTIFICATION_ID);
    }

    boolean delibratelyEndingCall = false;

    @Override
    protected void onStop() {
        super.onStop();
        if (getCallData().getCallState() != CallData.ENDED && !delibratelyEndingCall) {
            Intent intent = new Intent(this, YennkasaClient.class);
            intent.setAction(MessengerBus.HANG_UP_CALL);
            intent.putExtra(EXTRA_CALL_DATA, callData);
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                    .setSmallIcon(R.drawable.ic_stat_icon)
                    .setTicker(getNotificationTitle())
                    .setContentTitle(getString(R.string.yennkasa_call))
                    .setContentText(getNotificationTitle())
                    .setOngoing(true)
                    .setContentIntent(PendingIntent.getActivity(this,
                            REQUEST_CODE, getNotificationIntent(), PendingIntent.FLAG_UPDATE_CURRENT))
                    .addAction(R.drawable.ic_call_end_black_24dp, getString(R.string.end_call), PendingIntent.getService(this, 1001, intent, PendingIntent.FLAG_UPDATE_CURRENT));
            NotificationManagerCompat.from(this)
                    .notify(getPeer().getUserId(), MessengerBus.CALL_NOTIFICATION_ID, builder.build());
        }
    }

    private void populateUserData() {
        if (tvUserName != null) {
            tvUserName.setText(peer.getName());
        }
        Resources resources = getResources();
        ImageLoader.load(this, peer.getDP())
                .error(User.isGroup(peer) ? R.drawable.group_avatar : R.drawable.user_avartar)
                .placeholder(User.isGroup(peer) ? R.drawable.group_avatar : R.drawable.user_avartar)
                .resize((int) resources.getDimension(R.dimen.thumbnail_width), (int) resources.getDimension(R.dimen.thumbnail_height))
                .onlyScaleDown().into(userAvatar);
    }

    @NonNull
    @Override
    protected SnackBar getSnackBar() {
        return ButterKnife.findById(this, R.id.notification_bar);
    }

    @Override
    protected void handleEvent(Event event) {
        Object tag = event.getTag();
        if (tag.equals(MessengerBus.ON_CALL_EVENT)) {
            //noinspection ThrowableResultOfMethodCallIgnored
            Exception error = event.getError();
            if (error != null) {
                new AlertDialog.Builder(this)
                        .setMessage(error.getMessage())
                        .setPositiveButton(getString(android.R.string.ok), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                finish();
                            }
                        }).setCancelable(false)
                        .create().show();

            } else {
                CallData data = ((CallData) event.getData());
                assert data != null;
                //since call events are sticky, it's possible that a user leave the
                //call screen while a call is ongoing. if the call ends and the user  never
                //comes back to the call screen, an old event might get delivered to us so lets screen them out
                if (data.getEstablishedTime() < callData.getEstablishedTime()) { //an  old call event, not using
                    PLog.d(TAG, "can't replace an old callData with a newer callData");
                } else {
                    callData = data;
                    if (callData.getPeer().equals(peer.getUserId())) {
                        refreshDisplay();
                    } else {
                        if (BuildConfig.DEBUG) {
                            throw new IllegalStateException();
                        }
                        PLog.w(TAG, "unknown call from user with id %s", callData.getPeer());
                    }
                }
            }
        }
    }

    boolean answeringCall;

    @OnClick(R.id.bt_answer_call)
    public void answerCall(View v) {
        postEvent(Event.create(ANSWER_CALL, null, callData));
        answeringCall = true;
        refreshDisplay();
    }

    @OnClick(R.id.bt_end_call)
    public void endCall(View v) {
        delibratelyEndingCall = true;
        postEvent(Event.create(MessengerBus.HANG_UP_CALL, null, callData));
        finish();
    }

    @OnClick(R.id.bt_decline_call)
    public void declineCall(View v) {
        delibratelyEndingCall = true;
        postEvent(Event.create(MessengerBus.HANG_UP_CALL, null, callData));
        finish();
    }

    @OnClick(R.id.bt_mute)
    public void muteCall(View view) {
        if (callData.getCallState() == CallData.CONNECTING_CALL) {
            return;
        }
        postEvent(Event.create(MessengerBus.MUTE_CALL, null, callData));
    }

    @OnClick(R.id.bt_speaker)
    public void speaker(View view) {
        if (callData.getCallState() == CallData.CONNECTING_CALL) {
            return;
        }
        postEvent(Event.create(MessengerBus.ENABLE_SPEAKER, null, callData));
    }

    @NonNull
    protected CallData getCallData() {
        return callData;
    }

    @NonNull
    protected User getPeer() {
        return peer;
    }

    @Override
    public void setContentView(@LayoutRes int layoutResID) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setContentView(View view) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setContentView(View view, ViewGroup.LayoutParams params) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void onBackPressed() {
        //do nothing!!
    }
}
