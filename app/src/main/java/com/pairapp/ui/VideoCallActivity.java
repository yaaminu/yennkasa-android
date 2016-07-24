package com.pairapp.ui;

import android.content.Intent;
import android.support.annotation.LayoutRes;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;

import com.pairapp.R;
import com.pairapp.messenger.MessengerBus;
import com.pairapp.util.Event;
import com.pairapp.util.EventBus;
import com.pairapp.util.PLog;
import com.pairapp.util.ViewUtils;

import butterknife.Bind;

import static com.pairapp.messenger.MessengerBus.ON_ADD_VIDEO_CALL_LOCAL_VIEW;
import static com.pairapp.messenger.MessengerBus.ON_ADD_VIDEO_CALL_REMOTE_VIEW;
import static com.pairapp.messenger.MessengerBus.PAIRAPP_CLIENT_LISTENABLE_BUS;

public class VideoCallActivity extends BaseCallActivity {

    @Bind(R.id.remote_view)
    FrameLayout remoteView;

    @Bind(R.id.local_view)
    FrameLayout localView;

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
            View view = (View) event.getData();
            assert view != null;
            ViewParent parent = view.getParent();
            if (parent != null) {
                ((ViewGroup) parent).removeView(view);
            }
            if (ON_ADD_VIDEO_CALL_LOCAL_VIEW.equals(event.getTag())) {
                PLog.d(TAG, "adding local view");

                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
                params.gravity = Gravity.CENTER;
                localView.addView(view, 0, params);
                ViewUtils.hideViews(userAvatar);
                ViewUtils.showViews(((ViewGroup) localView.getParent()));
                localView.invalidate();
            } else if (ON_ADD_VIDEO_CALL_REMOTE_VIEW.equals(event.getTag())) {
                PLog.d(TAG, "adding remote view");

                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
                params.gravity = Gravity.CENTER;
                remoteView.addView(view, 0, params);
                ViewUtils.hideViews(userAvatar);
                ViewUtils.showViews(remoteView);
                remoteView.invalidate();
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
