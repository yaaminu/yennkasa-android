package com.idea.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.text.Spanned;
import android.text.TextUtils;
import android.view.View;

import com.idea.PairApp;
import com.idea.util.LiveCenter;
import com.idea.data.Message;
import com.idea.data.UserManager;
import com.idea.messenger.Notifier;
import com.idea.messenger.PairAppClient;
import com.idea.pairapp.BuildConfig;
import com.idea.pairapp.R;
import com.idea.util.Config;
import com.idea.util.PLog;
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
            PairAppActivity self = PairAppActivity.this;

            final int totalUnreadMessages = LiveCenter.getTotalUnreadMessages();
            if (totalUnreadMessages < 1) {
                PLog.w(TAG, "snackbar visible yet no unread message");
                return;
            }
            if (totalUnreadMessages == 1) {
                UiHelpers.enterChatRoom(self, Message.isGroupMessage(latestMessage) ? latestMessage.getTo() : latestMessage.getFrom());
            } else {
                if (getClass().equals(MainActivity.class)) {
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
    private SoundPool pool;
    private int streamId;
//    private String latestActivePeer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (isUserVerified()) {
            messageRealm = Message.REALM(this);
            //noinspection deprecation
            pool = new SoundPool(1, AudioManager.STREAM_NOTIFICATION, 0);
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
            if(snackBar == null) {
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
            if (streamId != 0) {
                pool.pause(streamId);
            }
            pool.release();
            messageRealm.close();
        }
        super.onDestroy();
    }

    protected final void bind() {
        Intent intent = new Intent(this, PairAppClient.class);
        intent.putExtra(PairAppClient.ACTION, PairAppClient.ACTION_SEND_ALL_UNSENT);
        bindService(intent, connection, BIND_AUTO_CREATE);
    }

    @NonNull
    private String formatNotificationMessage(Message message, String sender) {
        String text;
        List<String> recentChatList = new ArrayList<>(LiveCenter.getAllPeersWithUnreadMessages());
        final int recentCount = recentChatList.size(), unReadMessages = LiveCenter.getTotalUnreadMessages();
        switch (recentCount) {
            case 0:
                if(BuildConfig.DEBUG) throw new AssertionError();
                return getString(R.string.new_message);
            case 1:
                if(unReadMessages == 1) {
                    String messageBody = Message.isTextMessage(message) ? message.getMessageBody() : PairApp.typeToString(this, message.getType());
                    text = sender + ":  " + messageBody;
                }else {
                    text = unReadMessages + " " + getString(R.string.new_message_from) + " " + sender;
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
        int id = pool.load(this, R.raw.sound_a, 1);
        streamId = pool.play(id, 1, 1, 0, 1, 1);
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

    protected final void clearRecentChat(String peerId) {
        if (peerId == null) {
            throw new IllegalArgumentException("peer id is null!");
        }
        LiveCenter.invalidateNewMessageCount(peerId);
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
}