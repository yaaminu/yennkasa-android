package com.pairapp.ui;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.support.v4.util.Pair;
import android.text.Spanned;
import android.text.TextUtils;
import android.view.View;

import com.pairapp.BuildConfig;
import com.pairapp.Errors.ErrorCenter;
import com.pairapp.PairApp;
import com.pairapp.R;
import com.pairapp.data.Message;
import com.pairapp.data.User;
import com.pairapp.data.UserManager;
import com.pairapp.messenger.Notifier;
import com.pairapp.util.Config;
import com.pairapp.util.Event;
import com.pairapp.util.EventBus;
import com.pairapp.util.LiveCenter;
import com.pairapp.util.MediaUtils;
import com.pairapp.util.PLog;
import com.pairapp.util.TaskManager;
import com.pairapp.util.ThreadUtils;
import com.pairapp.util.UiHelpers;
import com.rey.material.widget.SnackBar;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

import io.realm.Realm;

import static com.pairapp.messenger.MessengerBus.DE_REGISTER_NOTIFIER;
import static com.pairapp.messenger.MessengerBus.PAIRAPP_CLIENT_LISTENABLE_BUS;
import static com.pairapp.messenger.MessengerBus.PAIRAPP_CLIENT_POSTABLE_BUS;
import static com.pairapp.messenger.MessengerBus.REGISTER_NOTIFIER;
import static com.pairapp.messenger.MessengerBus.get;

/**
 * @author Null-Pointer on 8/12/2015.
 */
public abstract class PairAppActivity extends PairAppBaseActivity
        implements Notifier, NoticeFragment.NoticeFragmentCallback, EventBus.EventsListener {
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
                UiHelpers.enterChatRoom(self, Message.isGroupMessage(latestMessage) ? latestMessage.getTo() : latestMessage.getFrom());
            } else {
                if (self instanceof MainActivity) {
                    ((MainActivity) self).setPagePosition(MainActivity.MyFragmentStatePagerAdapter.POSITION_CONVERSATION_FRAGMENT);
                } else {
                    Intent intent = new Intent(self, MainActivity.class);
                    intent.putExtra(MainActivity.DEFAULT_FRAGMENT, MainActivity.MyFragmentStatePagerAdapter.POSITION_CONVERSATION_FRAGMENT);
                    startActivity(intent);
                    finish(); //better use flags instead
                }
            }
        }
    };
    private SnackBar snackBar;

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
    protected void onResume() {
        super.onResume();
        if (isUserVerified()) {
            Config.appOpen(true);
            postEvent(Event.createSticky(REGISTER_NOTIFIER, null, this));
            if (snackBar == null) {
                snackBar = getSnackBar();
                if (snackBar == null) {
                    throw new IllegalStateException("snack bar cannot be null");
                }
                snackBar.applyStyle(getSnackBarStyle());
                if (isStickyMessageShown && !TextUtils.isEmpty(stickYmessage)) {
                    notifySticky(stickYmessage);
                }
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isUserVerified()) {
            Config.appOpen(false);
            postEvent(Event.createSticky(DE_REGISTER_NOTIFIER, null, this));
        }
    }

    @Override
    protected void onDestroy() {
        if (isUserVerified()) {
            messageRealm.close();
        }
        super.onDestroy();
    }


    private Pair<String, String> formatNotificationMessage(Message message, String sender) {
        String text;
        List<String> recentChatList = new ArrayList<>(LiveCenter.getAllPeersWithUnreadMessages());
        Realm realm = User.Realm(this);

        for (int i = 0; i < recentChatList.size(); i++) {
            if (i > 3) {
                break;
            }
            User user = userManager.fetchUserIfRequired(realm, recentChatList.get(i));
            recentChatList.set(i, user.getName());
        }
        realm.close();
        final int recentCount = recentChatList.size();
        totalUnreadMessages = LiveCenter.getTotalUnreadMessages();
        if (totalUnreadMessages < 1) {
            return null;
        }
        String peerId = Message.isGroupMessage(message) ? message.getTo() : message.getFrom();
        switch (recentCount) {
            case 0:
                if (BuildConfig.DEBUG) throw new AssertionError();
                return new Pair<>(peerId, getString(R.string.new_message));
            case 1:
                if (totalUnreadMessages == 1) {
                    String messageBody = Message.isTextMessage(message) ? message.getMessageBody() : PairApp.typeToString(this, message);
                    text = (Message.isGroupMessage(message) ? sender + "@" + recentChatList.get(0) : sender) + ":  " + messageBody;
                } else {
                    text = totalUnreadMessages + " " + getString(R.string.new_message_from) + " " + (Message.isGroupMessage(message) ? sender + "@" + recentChatList.get(0) : sender);
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
    }

    @Override
    public void notifyUser(Context context, final Message message, final String sender) {
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

    private void doNotify(String userId, String text) {
        if (isStickyMessageShown) {
            PLog.d(TAG, "notification %s dropped as a sticky message is shown", text);
            return;
        }
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
        isStickyMessageShown = false;
        snackBar.removeOnDismiss(true).show(this);
    }

    private final AtomicBoolean shouldPlayTone = new AtomicBoolean(true);

    @Override
    public final location where() {
        return location.FORE_GROUND;
    }

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

    protected abstract SnackBar getSnackBar();

    private class notifyTask extends AsyncTask<Object, Void, Pair<String, String>> {
        @Override
        protected Pair<String, String> doInBackground(Object... params) {
            return formatNotificationMessage((Message) params[0], (String) params[1]);
        }

        @Override
        protected void onPostExecute(Pair<String, String> s) {
            if (s != null) {
                doNotify(s.first, s.second);
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

    @Override
    public void clearNotifications() {
        throw new UnsupportedOperationException();
    }

    private static boolean isStickyMessageShown = false;
    private static String stickYmessage = "";

    public void notifySticky(final String message) {
        stickYmessage = message;
        final Runnable runnable = new Runnable() {
            @Override
            public void run() {
                snackBar.dismiss();
                snackBar.text(message)
                        .ellipsize(TextUtils.TruncateAt.END)
                        .maxLines(2)
                        .actionText(R.string.close)
                        .actionClickListener(new SnackBar.OnActionClickListener() {
                            @Override
                            public void onActionClick(SnackBar sb, int actionId) {
                                if (sb.getState() == SnackBar.STATE_SHOWN) {
                                    stickYmessage = "";
                                    isStickyMessageShown = false;
                                    sb.dismiss();
                                }
                            }
                        })
                        .duration(0);
                snackBar.removeOnDismiss(true).show(PairAppActivity.this);
                isStickyMessageShown = true;
            }
        };
        if (!ThreadUtils.isMainThread()) {
            runOnUiThread(runnable);
        } else {
            runnable.run();
        }
    }

    @Override
    public void showError(ErrorCenter.Error error) {
        if (error.style == ErrorCenter.ReportStyle.STICKY) {
            notifySticky(error.message);
        } else {
            super.showError(error);
        }
    }

    @Override
    public void disMissError(String errorId) {
        if (isStickyMessageShown) {
            snackBar.dismiss();
            isStickyMessageShown = false;
        } else {
            super.disMissError(errorId);
        }
    }

    protected void postEvent(Event event) {
        EventBus bus = get(PAIRAPP_CLIENT_POSTABLE_BUS);
        if (event.isSticky()) {
            bus.postSticky(event);
        } else {
            bus.post(event);
        }
    }

    protected void register(String tag, String... tags) {
        get(PAIRAPP_CLIENT_LISTENABLE_BUS).register(this, tag, tags);
    }

    protected void unRegister(String tag, String... otherTags) {
        get(PAIRAPP_CLIENT_LISTENABLE_BUS).unregister(tag, this);
        for (String otherTag : otherTags) {
            get(PAIRAPP_CLIENT_LISTENABLE_BUS).unregister(otherTag, this);
        }
    }

    @Override
    public final void onEvent(EventBus yourBus, Event event) {
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

    protected void handleEvent(Event event) {

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
