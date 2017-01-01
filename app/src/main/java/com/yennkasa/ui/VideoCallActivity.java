package com.yennkasa.ui;

import android.content.Intent;
import android.support.annotation.LayoutRes;
import android.support.v4.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.yennkasa.R;
import com.yennkasa.messenger.MessengerBus;
import com.yennkasa.util.Event;
import com.yennkasa.util.EventBus;
import com.yennkasa.util.PLog;
import com.yennkasa.util.ViewUtils;

import butterknife.Bind;
import butterknife.OnClick;

import static com.yennkasa.messenger.MessengerBus.ON_ADD_VIDEO_CALL_LOCAL_VIEW;
import static com.yennkasa.messenger.MessengerBus.ON_ADD_VIDEO_CALL_REMOTE_VIEW;
import static com.yennkasa.messenger.MessengerBus.PAIRAPP_CLIENT_LISTENABLE_BUS;

public class VideoCallActivity extends BaseCallActivity {

    @Bind(R.id.remoteVideo)
    LinearLayout remoteVideo;
    @Bind(R.id.localVideo)
    RelativeLayout localVideo;

    boolean cameraFacesFront = true;

    @OnClick(R.id.switch_camera)
    void switchCamera(View view) {
        postEvent(Event.create(MessengerBus.SWITCH_CAMERA, null, getCallData()));
        if (cameraFacesFront) {
            ((ImageButton) view).setImageResource(R.drawable.ic_camera_rear_white_24dp);
        } else {
            ((ImageButton) view).setImageResource(R.drawable.ic_camera_front_white_24dp);
        }
        view.invalidate();
    }

    private static final String TAG = "VideoCallActivity";


    @Override
    protected void onStart() {
        super.onStart();
        MessengerBus.get(PAIRAPP_CLIENT_LISTENABLE_BUS).register(eventsListener,
                ON_ADD_VIDEO_CALL_LOCAL_VIEW, ON_ADD_VIDEO_CALL_REMOTE_VIEW);
    }

    @Override
    protected void onStop() {
        EventBus eventBus = MessengerBus.get(PAIRAPP_CLIENT_LISTENABLE_BUS);
        eventBus.unregister(ON_ADD_VIDEO_CALL_LOCAL_VIEW, eventsListener);
        eventBus.unregister(ON_ADD_VIDEO_CALL_REMOTE_VIEW, eventsListener);
        super.onStop();
    }

    @Override
    protected Intent getNotificationIntent() {
        Intent intent = new Intent(this, VideoCallActivity.class);
        intent.putExtra(EXTRA_CALL_DATA, getCallData());
        return intent;
    }

    @Override
    protected String getNotificationTitle() {
        return getString(R.string.ongoing_video_call_notice, getPeer().getName());
    }

    @Override
    @LayoutRes
    protected int getLayout() {
        return R.layout.activity_video_call;
    }


    EventBus.EventsListener eventsListener = new EventBus.EventsListener() {
        @Override
        public int threadMode() {
            return EventBus.MAIN;
        }

        @Override
        public void onEvent(EventBus yourBus, Event event) {
            PLog.d(TAG, "received event  %s", event);
            if (ON_ADD_VIDEO_CALL_REMOTE_VIEW.equals(event.getTag())) {
                //noinspection unchecked
                Pair<View, View> views = (Pair<View, View>) event.getData();
                assert views != null;
                ViewParent parent = views.first.getParent();
                if (parent != null) {
                    ((ViewGroup) parent).removeView(views.first);
                }
                parent = views.second.getParent();
                if (parent != null) {
                    ((ViewGroup) parent).removeView(views.second);
                }
                PLog.d(TAG, "adding local view");
                ViewUtils.hideViews(userAvatar);
                localVideo.addView(views.first);

                PLog.d(TAG, "adding remote view");
                remoteVideo.addView(views.second);
                ViewUtils.hideViews(userAvatar);
            } else {
                throw new AssertionError();
            }
        }

        @Override
        public boolean sticky() {
            return true;
        }
    };
}
