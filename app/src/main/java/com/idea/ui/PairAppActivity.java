package com.idea.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Vibrator;
import android.text.Spanned;
import android.text.TextUtils;
import android.view.View;

import com.idea.PairApp;
import com.idea.data.Message;
import com.idea.data.User;
import com.idea.data.UserManager;
import com.idea.messenger.Notifier;
import com.idea.messenger.PairAppClient;
import com.idea.pairapp.BuildConfig;
import com.idea.pairapp.R;
import com.idea.util.Config;
import com.idea.util.LiveCenter;
import com.idea.util.MediaUtils;
import com.idea.util.PLog;
import com.idea.util.TaskManager;
import com.idea.util.UiHelpers;
import com.rey.material.widget.SnackBar;

import java.util.ArrayList;
import java.util.List;

import io.realm.Realm;

/**
 * @author Null-Pointer on 8/12/2015.
 */
public abstract class PairAppActivity extends PairAppBaseActivity implements Notifier, NoticeFragment.NoticeFragmentCallback {
    public static final int DELAY_MILLIS = 2000;
    public static final String TAG = PairAppActivity.class.getSimpleName();
    static private volatile Message latestMessage;
    protected PairAppClient.PairAppClientInterface pairAppClientInterface;
    protected boolean bound = false;


    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            pairAppClientInterface = ((PairAppClient.PairAppClientInterface) service);
            bound = true;
            pairAppClientInterface.registerUINotifier(PairAppActivity.this);
            onBind();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bound = false;
            pairAppClientInterface.registerUINotifier(PairAppActivity.this);
            pairAppClientInterface = null; //free memory
            onUnbind();
        }
    };
    private Realm messageRealm;
    private View.OnClickListener listener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            snackBar.dismiss();
            PairAppActivity self = PairAppActivity.this;

            final int totalUnreadMessages = LiveCenter.getTotalUnreadMessages();
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
            //noinspection deprecation
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (isUserVerified()) {
            bind();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isUserVerified()) {
            Config.appOpen(true);
            if (bound) {
                pairAppClientInterface.registerUINotifier(this);
            }
            if (snackBar == null) {
                snackBar = getSnackBar();
                if (snackBar == null) {
                    throw new IllegalStateException("snack bar cannot be null");
                }
                snackBar.applyStyle(R.style.Material_Widget_SnackBar_Mobile_MultiLine);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isUserVerified()) {
            Config.appOpen(false);
            if (bound) {
                pairAppClientInterface.unRegisterUINotifier(this);
            }
        }
    }

    @Override
    protected void onStop() {
        if (isUserVerified()) {
            if (bound) {
                unbindService(connection);
                onUnbind();
            }
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (isUserVerified()) {
            messageRealm.close();
        }
        super.onDestroy();
    }

    protected final void bind() {
        Intent intent = new Intent(this, PairAppClient.class);
        intent.putExtra(PairAppClient.ACTION, PairAppClient.ACTION_SEND_ALL_UNSENT);
        bindService(intent, connection, BIND_AUTO_CREATE);
    }

    private String formatNotificationMessage(Message message, String sender) {
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
        final int recentCount = recentChatList.size(), unReadMessages = LiveCenter.getTotalUnreadMessages();
        if (unReadMessages < 1) {
            return null;
        }
        switch (recentCount) {
            case 0:
                if (BuildConfig.DEBUG) throw new AssertionError();
                return getString(R.string.new_message);
            case 1:
                if (unReadMessages == 1) {
                    String messageBody = Message.isTextMessage(message) ? message.getMessageBody() : PairApp.typeToString(this, message);
                    text = (Message.isGroupMessage(message) ? sender + "@" + recentChatList.get(0) : sender) + ":  " + messageBody;
                } else {
                    text = unReadMessages + " " + getString(R.string.new_message_from) + " " + (Message.isGroupMessage(message) ? sender + "@" + recentChatList.get(0) : sender);
                }
                break;
            case 2:
                text = unReadMessages + " " + getString(R.string.new_message_from) + " " + recentChatList.get(0) + getString(R.string.and) + recentChatList.get(1);
                break;
            case 3:
                text = unReadMessages + "  " + getString(R.string.new_message_from) + " " + recentChatList.get(0) + ", " + recentChatList.get(1) + getString(R.string.and) + recentChatList.get(2);
                break;
            default:
                text = "" + recentCount + " " + getString(R.string.new_message_from) + " " + recentChatList.get(0) + getString(R.string.and) + (recentCount - 1) + getString(R.string.others);
                break; //redundant but safe
        }
        return text;
    }

    @Override
    public void notifyUser(Context context, final Message message, final String sender) {
        if (userManager.getBoolPref(UserManager.IN_APP_NOTIFICATIONS, true)) {
            latestMessage = message;
            // TODO: 8/17/2015 vibrate or play short tone
            if (snackBar.getState() != SnackBar.STATE_SHOWN) { //we only notify when there is no ongoing notification
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
        // TODO: 11/7/2015 play tone
        playNewMessageTone(this);
        vibrateIfAllowed(this);
        snackBar.removeOnDismiss(true).show(this);
    }

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

    protected void onBind() {
    }

    protected void onUnbind() {
    }

    protected final void clearRecentChat(final String peerId) {
        if (peerId == null) {
            throw new IllegalArgumentException("peer id is null!");
        }
        final Runnable invalidateTask = new Runnable() {
            @Override
            public void run() {
                LiveCenter.invalidateNewMessageCount(peerId);
            }
        };
        TaskManager.executeNow(invalidateTask, true);
    }

    protected abstract SnackBar getSnackBar();


    private class notifyTask extends AsyncTask<Object, Void, String> {
        @Override
        protected String doInBackground(Object... params) {
            return formatNotificationMessage((Message) params[0], (String) params[1]);
        }

        @Override
        protected void onPostExecute(String s) {
            if (s != null) {
                doNotify(s);
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

    public static final int VIBRATION_DURATION = 1000;

    private static void vibrateIfAllowed(Context context) {
        if (UserManager.getInstance().getBoolPref(UserManager.VIBRATE, false)) {
            PLog.v(TAG, "vibrating....");
            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                if (vibrator.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        AudioAttributes audioAttributes = new AudioAttributes.Builder().setFlags(AudioAttributes.USAGE_NOTIFICATION).build();
                        vibrator.vibrate(VIBRATION_DURATION, audioAttributes);
                    } else {
                        vibrator.vibrate(VIBRATION_DURATION);
                    }
                }
            } else {
                vibrator.vibrate(VIBRATION_DURATION);
            }
        }
    }

    @Override
    public void clearNotifications() {
        throw new UnsupportedOperationException();
    }
}
