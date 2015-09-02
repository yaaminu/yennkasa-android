package com.pair.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.util.Pair;
import android.support.v7.app.ActionBarActivity;
import android.text.TextUtils;
import android.view.View;

import com.pair.data.Message;
import com.pair.messenger.Notifier;
import com.pair.messenger.PairAppClient;
import com.pair.pairapp.R;
import com.pair.util.ScreenUtility;
import com.pair.util.UiHelpers;
import com.rey.material.widget.SnackBar;

import java.util.ArrayList;
import java.util.List;

import io.realm.Realm;
import io.realm.RealmChangeListener;

/**
 * @author Null-Pointer on 8/12/2015.
 */
public abstract class PairAppBaseActivity extends ActionBarActivity implements Notifier, RealmChangeListener {
    static private volatile List<Pair<String, String>> recentChatList = new ArrayList<>();
    private static volatile long unReadMessages = 0;
    static private volatile Message latestMessage;
    protected PairAppClient.PairAppClientInterface pairAppClientInterface;
    protected boolean bound = false;

    private float dpWidth, dpHeight, pixelsHeight, pixelsWidth;


    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            pairAppClientInterface = ((PairAppClient.PairAppClientInterface) service);
            bound = true;
            onBind();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            onUnbind();
            bound = false;
            pairAppClientInterface = null; //free memory
        }
    };
    private Realm realm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setUpScreenDimensions();
        realm = Message.REALM(this);
    }


    @Override
    protected void onStart() {
        super.onStart();
        doBind();
    }

    @Override
    protected void onResume() {
        super.onResume();
        realm.addChangeListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        realm.removeChangeListener(this);
    }

    @Override
    protected void onStop() {
        if (bound) {
            bound = false;
            unbindService(connection);
            onUnbind();
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        realm.close();
        super.onDestroy();
    }

    protected void doBind() {
        Intent intent = new Intent(this, PairAppClient.class);
        intent.putExtra(PairAppClient.ACTION, PairAppClient.ACTION_SEND_ALL_UNSENT);
        bindService(intent, connection, BIND_AUTO_CREATE);
    }

    @NonNull
    private String formatNotificationMessage(Message message, String sender) {
        unReadMessages++;
        String text;
        final int recentCount = recentChatList.size();
        switch (recentCount) {
            case 0:
                return getString(R.string.new_message);
            case 1:
                String messageBody = Message.isTextMessage(message) ? message.getMessageBody() : Message.typeToString(this, message.getType());
                text = sender + ":\n" + messageBody;
                break;
            case 2:
                text = unReadMessages + " " + getString(R.string.new_message_from) + " " + recentChatList.get(0).second + getString(R.string.and) + recentChatList.get(1).second;
                break;
            case 3:
                text = unReadMessages + "  " + getString(R.string.new_message_from) + " " + recentChatList.get(0).second + ", " + recentChatList.get(1).second + getString(R.string.and) + recentChatList.get(2).second;
                break;
            default:
                text = "" + recentCount + " " + getString(R.string.new_message_from) + " " + recentChatList.get(0).second + getString(R.string.and) + (recentCount - 1) + getString(R.string.others);
                break; //redundant
        }
        return text;
    }

    @Override
    public void notifyUser(Context context, final Message message, final String sender) {
        latestMessage = message;
        final Pair<String, String> tuple = new Pair<>(message.getFrom(), sender);
        if (recentChatList.contains(tuple)) {
            recentChatList.remove(tuple);
        }

        recentChatList.add(tuple);
        // TODO: 8/17/2015 vibrate or play short tone
        if (snackBar.getState() != SnackBar.STATE_SHOWN) {
            snackBar.postDelayed(new Runnable() {
                @Override
                public void run() {
                    doNotifyUser(message, sender);

                }
            }, 4000);
        }
    }

    private void doNotifyUser(Message message, String sender) {
        snackBar.setTag(R.id.latest_message, message.getFrom());
        snackBar.applyStyle(R.style.Material_Widget_SnackBar_Mobile_MultiLine);
        final String text;// = String.format(getString(R.string.message_from_v2), sender) + message.getMessageBody();

        text = formatNotificationMessage(message, sender);

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
        snackBar.removeOnDismiss(true).show(this);
    }

    private View.OnClickListener listener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            PairAppBaseActivity self = PairAppBaseActivity.this;
            if (recentChatList.size() == 1) {
                recentChatList.clear();
                unReadMessages = 0;
                UiHelpers.enterChatRoom(self, v.getTag(R.id.latest_message).toString());
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
    protected SnackBar snackBar;

    protected void setUpScreenDimensions() {
        ScreenUtility utility = new ScreenUtility(this);
        this.dpHeight = utility.getDpHeight();
        this.dpWidth = utility.getDpWidth();
        this.pixelsHeight = utility.getPixelsHeight();
        this.pixelsWidth = utility.getPixelsWidth();
    }

    protected float SCREEN_WIDTH_PIXELS() {
        return pixelsWidth;
    }

    protected float SCREEN_WIDTH_DP() {
        return dpWidth;
    }

    protected float SCREEN_HEIGHT_PIXELS() {

        return pixelsHeight;
    }

    protected float SCREEN_HEIGHT_DP() {
        return dpHeight;
    }

    @Override
    public location where() {
        return location.FORE_GROUND;
    }

    @Override
    public void onChange() {
        long newMessageCount = realm.where(Message.class).equalTo(Message.FIELD_STATE, Message.STATE_RECEIVED).count();
        if (newMessageCount > 0 && newMessageCount >= unReadMessages) {
            unReadMessages = newMessageCount;
        } else {
            clearRecentChat();
        }
    }

    protected abstract void onBind();

    protected abstract void onUnbind();

    public void clearRecentChat() {
        recentChatList.clear();
        unReadMessages = 0;
    }
}
