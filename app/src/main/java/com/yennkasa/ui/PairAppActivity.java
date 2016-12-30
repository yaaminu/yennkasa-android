
package com.yennkasa.ui;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.util.Pair;
import android.text.Spanned;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import com.rey.material.widget.SnackBar;
import com.yennkasa.BuildConfig;
import com.yennkasa.R;
import com.yennkasa.data.Message;
import com.yennkasa.data.User;
import com.yennkasa.data.UserManager;
import com.yennkasa.data.util.MessageUtils;
import com.yennkasa.messenger.MessengerBus;
import com.yennkasa.util.Config;
import com.yennkasa.util.Event;
import com.yennkasa.util.EventBus;
import com.yennkasa.util.LiveCenter;
import com.yennkasa.util.MediaUtils;
import com.yennkasa.util.PLog;
import com.yennkasa.util.TaskManager;
import com.yennkasa.util.ThreadUtils;
import com.yennkasa.util.UiHelpers;
import com.yennkasa.util.ViewUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

import io.realm.Realm;

import static com.yennkasa.messenger.MessengerBus.CONNECTED;
import static com.yennkasa.messenger.MessengerBus.PAIRAPP_CLIENT_LISTENABLE_BUS;
import static com.yennkasa.messenger.MessengerBus.PAIRAPP_CLIENT_POSTABLE_BUS;
import static com.yennkasa.messenger.MessengerBus.get;

/**
 * @author Null-Pointer on 8/12/2015.
 */
public abstract class PairAppActivity extends PairAppBaseActivity implements NoticeFragment.NoticeFragmentCallback, EventBus.EventsListener {
    public static final int DELAY_MILLIS = 2000;
    public static final String TAG = PairAppActivity.class.getSimpleName();
    static private volatile Message latestMessage;
    private volatile int totalUnreadMessages = 0;
    private Realm messageRealm;
    private View.OnClickListener listener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            snackBar.dismiss();
            PairAppActivity self = PairAppActivity.this;

            if (totalUnreadMessages < 1) {
                PLog.w(TAG, "snackbar visible yet no unread message");
                return;
            }
            if (totalUnreadMessages == 1) {
                UiHelpers.enterChatRoom(self,
                        Message.isGroupMessage(userRealm, latestMessage)
                                ? latestMessage.getTo() : latestMessage.getFrom(), true);
            } else {
                if (self instanceof MainActivity) {
                    ((MainActivity) self).setPagePosition(MainActivity.CONVERSATION_TAB);
                } else {
                    Intent intent = new Intent(self, MainActivity.class);
                    intent.putExtra(MainActivity.DEFAULT_FRAGMENT, MainActivity.CONVERSATION_TAB);
                    startActivity(intent);
                    finish(); //better use flags instead
                }
            }
        }
    };
    private SnackBar snackBar;

    @NonNull
    private View notificationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (isUserVerified()) {
            messageRealm = Message.REALM(this);
        }
    }


    protected int getSnackBarStyle() {
        if (isUserVerified()) {
            return R.style.snackbar_black;
        }
        throw new IllegalStateException("no user");
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        notificationView = findViewById(R.id.inline_notification_text_parent);
        if (hideConnectionView()) {
            ViewUtils.hideViews(notificationView);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isUserVerified()) {
            Config.appOpen(true);
            registerForEvent(MessengerBus.SOCKET_CONNECTION, MessengerBus.UI_ON_NEW_MESSAGE_RECEIVED);
            if (snackBar == null) {
                snackBar = getSnackBar();
                snackBar.applyStyle(getSnackBarStyle());
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isUserVerified()) {
            Config.appOpen(false);
            unRegister(MessengerBus.SOCKET_CONNECTION, MessengerBus.UI_ON_NEW_MESSAGE_RECEIVED);
        }
    }

    @Override
    protected void onDestroy() {
        if (messageRealm != null) {
            messageRealm.close();
        }
        super.onDestroy();
    }


    private Pair<String, String> formatNotificationMessage(Message message, String sender) {
        String text;
        List<String> recentChatList = new ArrayList<>(LiveCenter.getAllPeersWithUnreadMessages());
        Realm backgroundUserRealm = User.Realm(this);
        try {

            for (int i = 0; i < recentChatList.size(); i++) {
                if (i > 3) {
                    break;
                }
                User user = userManager.fetchUserIfRequired(backgroundUserRealm, recentChatList.get(i), true, true);
                recentChatList.set(i, user.getName());
            }
            final int recentCount = recentChatList.size();
            totalUnreadMessages = LiveCenter.getTotalUnreadMessages();
            if (totalUnreadMessages < 1) {
                return null;
            }
            String peerId = Message.isGroupMessage(backgroundUserRealm, message) ? message.getTo() : message.getFrom();
            switch (recentCount) {
                case 0:
                    if (BuildConfig.DEBUG) throw new AssertionError();
                    return new Pair<>(peerId, getString(R.string.new_message));
                case 1:
                    if (totalUnreadMessages == 1) {
                        String messageBody = Message.isTextMessage(message) ? message.getMessageBody() : MessageUtils.typeToString(this, message);
                        text = sender + ":  " + messageBody;
                    } else {
                        text = totalUnreadMessages + " " + getString(R.string.new_message_from) + " " + sender;
                    }
                    break;
                case 2:
                    text = totalUnreadMessages + " " + getString(R.string.new_message_from) + " " + recentChatList.get(0) + getString(R.string.and) + recentChatList.get(1);
                    break;
                case 3:
                    text = totalUnreadMessages + "  " + getString(R.string.new_message_from) + " " + recentChatList.get(0) + ", " + recentChatList.get(1) + getString(R.string.and) + recentChatList.get(2);
                    break;
                default:
                    text = "" + recentCount + " " + getString(R.string.new_message_from) + " " + recentChatList.get(0) + getString(R.string.and) + (recentCount - 1) + getString(R.string.others);
                    break; //redundant but safe
            }
            return new Pair<>(peerId, text);
        } finally {
            backgroundUserRealm.close();
        }
    }

    protected void notifyUser(Context context, final Message message, final String sender) {
        if (userManager.getBoolPref(UserManager.IN_APP_NOTIFICATIONS, true)) {
            latestMessage = message;
            int state = snackBar.getState();
            if (state != SnackBar.STATE_SHOWN && state != SnackBar.STATE_SHOWING) { //we only notify when there is no ongoing notification
                snackBar.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        doNotifyUser(message, sender);
                    }
                }, DELAY_MILLIS);
            }
        }
    }

    private void doNotifyUser(Message message, String sender) {
        snackBar.setTag(R.id.latest_message, message.getFrom());
        new notifyTask().execute(message, sender);
    }

    private void doNotify(String text) {
        int state = snackBar.getState();
        if (state == SnackBar.STATE_SHOWING || state == SnackBar.STATE_SHOWN) {
            return;
        }
        snackBar.text(text)
                .ellipsize(TextUtils.TruncateAt.END)
                .maxLines(2)
                .actionText(R.string.close)
                .actionClickListener(new SnackBar.OnActionClickListener() {
                    @Override
                    public void onActionClick(SnackBar sb, int actionId) {
                        if (sb.getState() == SnackBar.STATE_SHOWN) {
                            sb.dismiss();
                        }
                    }
                })
                .duration(6000) //6 secs
                .setOnClickListener(listener);

        if (shouldPlayTone.getAndSet(false)) {
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!shouldPlayTone.get()) //hopefully we might avoid race conditions
                        shouldPlayTone.set(true);
                }
            }, 3000);
            TaskManager.executeNow(new Runnable() {
                public void run() {
                    playNewMessageTone(PairAppActivity.this);
                    vibrateIfAllowed(PairAppActivity.this);
                }
            }, false);

        }
        snackBar.removeOnDismiss(true).show(this);
    }

    private final AtomicBoolean shouldPlayTone = new AtomicBoolean(true);

    @Override
    public CharSequence getActionText() {
        return null;
    }

    @Override
    public Spanned getNoticeText() {
        return null;
    }

    @Override
    public void onAction() {

    }

    protected final void clearRecentChat(final String peerId) {
        if (peerId == null) {
            throw new IllegalArgumentException("peer id is null!");
        }
        LiveCenter.invalidateNewMessageCount(peerId);
    }


    @NonNull
    protected abstract SnackBar getSnackBar();

    private class notifyTask extends AsyncTask<Object, Void, Pair<String, String>> {
        @Override
        protected Pair<String, String> doInBackground(Object... params) {
            return formatNotificationMessage((Message) params[0], (String) params[1]);
        }

        @Override
        protected void onPostExecute(Pair<String, String> s) {
            if (s != null) {
                doNotify(s.second);
            }
        }
    }

    private static void playNewMessageTone(Context context) {
        String uriString = UserManager.getInstance().getStringPref(UserManager.NEW_MESSAGE_TONE, UserManager.SILENT);
        if (uriString.equals(UserManager.SILENT)) {
            PLog.d(TAG, "silent, aborting ringtone playing");
        }
        Uri uri;
        if (TextUtils.isEmpty(uriString) || uriString.equals(UserManager.DEFAULT)) {
            uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            if (uri == null) {
                PLog.e(TAG, " unable to play default notification tone");
            }
        } else {
            uri = Uri.parse(uriString);
        }
        PLog.d(TAG, "Retrieved ringtone %s", uri + "");
        MediaUtils.playTone(context, uri);
    }

    public static final int VIBRATION_DURATION = 150;

    private static void vibrateIfAllowed(Context context) {
        AudioManager manager = ((AudioManager) context.getSystemService(AUDIO_SERVICE));
        int ringerMode = manager.getRingerMode();
        if (ringerMode == AudioManager.RINGER_MODE_VIBRATE || ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            if (UserManager.getInstance().getBoolPref(UserManager.VIBRATE, false)) {
                PLog.v(TAG, "vibrating....");
                Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                    if (vibrator.hasVibrator()) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            AudioAttributes audioAttributes = new AudioAttributes.Builder().setFlags(AudioAttributes.USAGE_NOTIFICATION).build();
                            doVibrate(vibrator, audioAttributes);//.vibrate(VIBRATION_DURATION, audioAttributes);
                        } else {
                            doVibrate(vibrator, null);
                        }
                    }
                } else {
                    doVibrate(vibrator, null);
                }
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private static void doVibrate(final Vibrator vibrator, final AudioAttributes attributes) {
        final Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (attributes == null) {
                    vibrator.vibrate(VIBRATION_DURATION);
                } else {
                    vibrator.vibrate(VIBRATION_DURATION, attributes);
                }
                timer.cancel();
            }
        }, 500);
        if (attributes == null) {
            vibrator.vibrate(VIBRATION_DURATION);
        } else {
            vibrator.vibrate(VIBRATION_DURATION, attributes);
        }
    }


    protected static void postEvent(Event event) {
        EventBus bus = get(PAIRAPP_CLIENT_POSTABLE_BUS);
        if (event.isSticky()) {
            bus.postSticky(event);
        } else {
            bus.post(event);
        }
    }

    protected void registerForEvent(Object tag, Object... tags) {
        registerForEvent(get(PAIRAPP_CLIENT_LISTENABLE_BUS), tag, tags);
    }

    protected void registerForEvent(EventBus bus, Object tag, Object... tags) {
        bus.register(this, tag, tags);
    }

    protected void unRegister(Object tag, Object... otherTags) {
        unRegister(get(PAIRAPP_CLIENT_LISTENABLE_BUS), tag, otherTags);
    }

    protected void unRegister(EventBus bus, Object tag, Object... otherTags) {
        bus.unregister(tag, this);
        for (Object otherTag : otherTags) {
            bus.unregister(otherTag, this);
        }
    }

    @Override
    public final void onEvent(EventBus yourBus, Event event) {
        if (event.getTag().equals(MessengerBus.UI_ON_NEW_MESSAGE_RECEIVED)) {
            try {
                //noinspection unchecked
                Pair<Message, String> data = ((Pair) event.getData());
                assert data != null;
                notifyUser(this, data.first, data.second);
            } finally {
                event.recycle();
            }
        } else if (event.getTag().equals(MessengerBus.SOCKET_CONNECTION)) {
            //noinspection ConstantConditions
            handleConnectionEvent(((Integer) event.getData()));
        } else {
            try {
                handleEvent(event);
            } finally {
                if (event.isSticky()) {
                    yourBus.removeStickyEvent(event);
                } else {
                    event.recycle();
                }
            }
        }
    }

    private static int currentStatus = MessengerBus.CONNECTED;

    private void handleConnectionEvent(int status) {
        ThreadUtils.ensureMain();
        if (currentStatus == status && status == CONNECTED)
            return; //hide showing connected notification in more than one activity
        currentStatus = status;
        if (hideConnectionView()) {
            return;
        }
        switch (status) {
            case MessengerBus.DISCONNECTED:
                ((TextView) notificationView.findViewById(R.id.inline_notification_text)).setText(getString(R.string.disconnected));
                notificationView.setBackgroundColor(getResources().getColor(R.color.red));
                break;
            case MessengerBus.CONNECTING:
                ((TextView) notificationView.findViewById(R.id.inline_notification_text)).setText(getString(R.string.connecting));
                notificationView.setBackgroundColor(getResources().getColor(R.color.orange));
                break;
            case MessengerBus.CONNECTED:
                ((TextView) notificationView.findViewById(R.id.inline_notification_text)).setText(getString(R.string.connected));
                notificationView.setBackgroundColor(getResources().getColor(R.color.colorPrimaryDark));
                handler.removeCallbacks(hideNotificationViewRunnable);
                handler.postDelayed(hideNotificationViewRunnable, 1500);
                break;
            default:
                throw new AssertionError();
        }
        ViewUtils.showViews(notificationView);
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hideNotificationViewRunnable = new
            Runnable() {
                @Override
                public void run() {
                    if (currentStatus == MessengerBus.CONNECTED) {
                        ViewUtils.hideViews(notificationView);
                    }
                }
            };

    protected void handleEvent(Event event) {

    }

    protected boolean hideConnectionView() {
        return true;
    }

    @Override
    public final int threadMode() {
        return EventBus.MAIN;
    }


    @Override
    public final boolean sticky() {
        return true;
    }
}
