package com.pairapp.ui;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.util.Pair;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;

import com.pairapp.BuildConfig;
import com.pairapp.Errors.ErrorCenter;
import com.pairapp.Errors.PairappException;
import com.pairapp.R;
import com.pairapp.data.Conversation;
import com.pairapp.data.Message;
import com.pairapp.data.User;
import com.pairapp.data.UserManager;
import com.pairapp.data.util.MessageUtils;
import com.pairapp.messenger.MessengerBus;
import com.pairapp.util.Event;
import com.pairapp.util.GenericUtils;
import com.pairapp.util.LiveCenter;
import com.pairapp.util.PLog;
import com.pairapp.util.TaskManager;
import com.pairapp.util.ThreadUtils;
import com.pairapp.util.UiHelpers;
import com.pairapp.util.ViewUtils;
import com.rockerhieu.emojicon.EmojiconGridFragment;
import com.rockerhieu.emojicon.EmojiconsFragment;
import com.rockerhieu.emojicon.emoji.Emojicon;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import butterknife.OnClick;
import io.realm.Realm;

import static com.pairapp.messenger.MessengerBus.MESSAGE_SEEN;
import static com.pairapp.messenger.MessengerBus.SEND_MESSAGE;

/**
 * @author by Null-Pointer on 9/19/2015.
 */
public abstract class MessageActivity extends PairAppActivity implements LiveCenter.ProgressListener, TextWatcher, EmojiconsFragment.OnEmojiconBackspaceClickedListener, EmojiconGridFragment.OnEmojiconClickedListener {

    private static final String TAG = MessageActivity.class.getSimpleName();
    public static final String EMOJI_FRAGMENT = "emojiFragment";
    private boolean isTypingEmoji = false;
    private ImageButton emoji, camera, mic;
    private EditText messageEt;

    protected final void resendMessage(final String msgId) {
        TaskManager.executeNow(new Runnable() {
            @Override
            public void run() {
                resendFailedMessage(msgId);
            }
        }, false);
    }

    protected final void editMessage(final String msgId, final String newContent) {
        TaskManager.executeNow(new Runnable() {
            @Override
            public void run() {
                editSentMessage(msgId, newContent);
            }
        }, false);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        messageEt = (EditText) findViewById(R.id.et_message);
        emoji = ((ImageButton) findViewById(R.id.ib_attach_emoji));
        camera = ((ImageButton) findViewById(R.id.ib_attach_capture_photo));
        mic = ((ImageButton) findViewById(R.id.ib_attach_record_audio));
        messageEt.addTextChangedListener(this);
        messageEt.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    FragmentManager supportFragmentManager = getSupportFragmentManager();
                    Fragment f = supportFragmentManager.findFragmentByTag(EMOJI_FRAGMENT);
                    if (f != null) {
                        supportFragmentManager.beginTransaction().remove(f).commit();
                        emoji.setImageResource(R.drawable.ic_msg_panel_smiles);
                        isTypingEmoji = false;
                    }
                }
            }
        });
        messageEt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FragmentManager supportFragmentManager = getSupportFragmentManager();
                Fragment f = supportFragmentManager.findFragmentByTag(EMOJI_FRAGMENT);
                if (f != null) {
                    supportFragmentManager.beginTransaction().remove(f).commit();
                    emoji.setImageResource(R.drawable.ic_msg_panel_smiles);
                    isTypingEmoji = false;
                }
            }
        });
        messageEt.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                FragmentManager supportFragmentManager = getSupportFragmentManager();
                Fragment f = supportFragmentManager.findFragmentByTag(EMOJI_FRAGMENT);
                if (f != null && f.isInLayout() && isTypingEmoji) {
                    supportFragmentManager.beginTransaction().remove(f).commit();
                    emoji.setImageResource(R.drawable.ic_msg_panel_smiles);
                    isTypingEmoji = false;
                }
                return false;
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        TaskManager.executeNow(new Runnable() {
            @Override
            public void run() {
                LiveCenter.listenForAllProgress(MessageActivity.this);
            }
        }, false);
    }

    @Override
    protected void onPause() {
        TaskManager.executeNow(new Runnable() {
            @Override
            public void run() {
                LiveCenter.stopListeningForAllProgress(MessageActivity.this);
            }
        }, false);
        super.onPause();
    }

    private void doSendMessage(final Message message) {
        postEvent(Event.createSticky(SEND_MESSAGE, null, Message.copy(message)));
    }

    protected final void sendMessage(final String messageBody, final Set<String> recipientIds, final int type, final MessageActivity.SendCallback callback) {
        TaskManager.executeNow(new Runnable() {
            @Override
            public void run() {
                if (ThreadUtils.isMainThread()) {
                    callback.onSendComplete(null);
                } else {
                    for (String recipientId : recipientIds) {
                        sendMessage(messageBody, recipientId, type, false);
                    }
                    TaskManager.executeOnMainThread(this);
                }
            }
        }, false);
    }

    protected final void sendMessage(final String messageBody, final String recipient, final int type) {
        sendMessage(messageBody, recipient, type, false);
    }

    protected final void sendMessageActive(final String messageBody, final String recipient, final int type, final boolean isActive) {
        TaskManager.executeNow(new Runnable() {
            @Override
            public void run() {
                sendMessage(messageBody, recipient, type, isActive);
            }
        }, false);
    }

    protected final void sendMessage(final int requestCode, final Intent data, final String recipient) {
        final ProgressDialog dialog = new ProgressDialog(this);
        dialog.setCancelable(false);
        dialog.setMessage(getString(R.string.st_please_wait));
        dialog.show();

        TaskManager.executeNow(new Runnable() {
            @Override
            public void run() {
                try {
                    Pair<String, Integer> pathAndType = UiHelpers.completeAttachIntent(requestCode, data);
                    sendMessage(pathAndType.first, recipient, pathAndType.second);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            dialog.dismiss();
                        }
                    });
                } catch (final PairappException e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            dialog.dismiss();
                            UiHelpers.showErrorDialog(MessageActivity.this, e.getMessage());
                        }
                    });
                }
            }
        }, false);
    }

    private void sendMessage(String msgBody, String to, int msgType, boolean active) {
        ThreadUtils.ensureNotMain();
        Realm realm = Conversation.Realm(this);
        try {
            Message message = createMessage(msgBody, to, msgType, active);
            final String messageId = message.getId();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    onMessageQueued(messageId);
                }
            });
            doSendMessage(message);
        } catch (PairappException e) {
            ErrorCenter.reportError(TAG, e.getMessage());
        } finally {
            realm.close();
        }
    }

    protected final void onMessageSeen(final Message message) {
        final String messageId = message.getId();
        TaskManager.executeNow(new Runnable() {
            @Override
            public void run() {
                postEvent(Event.create(MESSAGE_SEEN, null, messageId));
            }
        }, false);
    }

    private void editSentMessage(final String msgId, String newContent) {
        ThreadUtils.ensureNotMain();
        GenericUtils.ensureNotEmpty(msgId, newContent);
        Realm realm = Message.REALM();
        try {
            final Message message = realm.where(Message.class).equalTo(Message.FIELD_ID, msgId).findFirst();
            if (message != null && !message.getMessageBody().equals(newContent)) {
                realm.beginTransaction();
                message.setMessageBody(newContent);
                realm.commitTransaction();
                postEvent(Event.create(MessengerBus.EDIT_SENT_MESSAGE, null, msgId));
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        onMessageQueued(msgId);
                    }
                });
            }
        } finally {
            realm.close();
        }
        // TODO: 12/6/16 notify user of failed attempt to edit message
    }

    private void resendFailedMessage(final String msgId) {
        ThreadUtils.ensureNotMain();
        Realm realm = Message.REALM();
        try {
            final Message message = realm.where(Message.class).equalTo(Message.FIELD_ID, msgId).findFirst();
            if (message != null) {
                if (message.getState() == Message.STATE_SEND_FAILED) {
                    try {
                        realm.beginTransaction();
                        Message newMessage = Message.makeNew(realm, message.getFrom(), message.getMessageBody(), message.getTo(), message.getType());
                        message.deleteFromRealm();
                        realm.commitTransaction();
                        doSendMessage(newMessage);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                onMessageQueued(msgId);
                            }
                        });
                    } catch (PairappException e) {
                        ErrorCenter.reportError(message.getId(), e.getMessage());
                    }
                    return;
                }
                if (BuildConfig.DEBUG) {
                    throw new IllegalArgumentException("attempted to resend a non-failed message");
                }
                PLog.w(MessageActivity.TAG, "message cannot be resent because it has not failed");
                return;
            }
            PLog.w(MessageActivity.TAG, "failed to resend message, reason: message deleted");
        } finally {
            realm.close();
        }
    }


    private Message createMessage(String messageBody, String recipient, int type, boolean active) throws PairappException {
        Realm userRealm = User.Realm(MessageActivity.this),
                realm = Conversation.Realm(this);
        try {
            Conversation currConversation = realm.where(Conversation.class).equalTo(Conversation.FIELD_PEER_ID, recipient).findFirst();
            if (currConversation == null) {
                currConversation = Conversation.newConversation(realm, UserManager.getMainUserId(userRealm), recipient, active);
            }
            realm.beginTransaction();
            boolean newDay = trySetupNewSession(currConversation, realm, userRealm);
            Message message = Message.makeNew(realm, UserManager.getMainUserId(userRealm), messageBody, recipient, type);
            if (newDay) {
                //realm dates are in seconds not milliseconds. this has an undesirable effect of making our date waitingMessages
                // and the first message of the day have the same date in seconds precision which in return can cause sorting problems.
                // to curb this we force this message to be newer than the date message. by first committing the transaction and also making the message
                // to be sent newer
                realm.commitTransaction();
                realm.beginTransaction();
                message.setDateComposed(new Date(System.currentTimeMillis() + 10)); //always force new waitingMessages to be newer
            }
            currConversation.setLastMessage(message);
            currConversation.setLastActiveTime(message.getDateComposed());
            String summary;
            if (type == Message.TYPE_TEXT_MESSAGE) {
                summary = message.getMessageBody();
            } else {
                summary = MessageUtils.getDescription(type);
            }
            currConversation.setSummary(summary);
            realm.commitTransaction();
            return message;
        } catch (PairappException e) { //caught for the for the purpose of cleanup
            realm.cancelTransaction();
            throw new PairappException(e.getMessage(), "");
        } finally {
            userRealm.close();
            realm.close();
        }
    }

    private boolean trySetupNewSession(Conversation conversation, Realm realm, Realm userRealm) {
        //set up session
        return Conversation.newSession(realm, UserManager.getMainUserId(userRealm), conversation);
    }

    protected void reportProgress(String messageId, int progress) {
    }

    protected void onCancelledOrDone(String messageId) {
    }

    protected void onMessageQueued(@SuppressWarnings("UnusedParameters") String messageId /* for sub classes*/) {

    }

    private static final Map<Object, Integer> progresses = new HashMap<>();

    @Override
    public final void onProgress(final Object tag, final int progress) {
        synchronized (progresses) {
            Integer stored = progresses.get(tag);
            if (stored == null) {
                stored = progress - 1;
            }
            if (stored >= progress) {
                PLog.d(TAG, "late progress report");
                return;
            }
            PLog.d(TAG, stored + "");
            progresses.put(tag, progress);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    reportProgress((String) tag, progress);
                }
            });
        }
    }

    protected final int getMessageProgress(Message message) {
        synchronized (progresses) {
            Integer integer = progresses.get(message.getId());
            return integer == null ? -1 : integer;
        }
    }

    @Override
    public final void doneOrCancelled(final Object tag) {
        synchronized (progresses) {
            progresses.remove(tag);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    onCancelledOrDone(((String) tag));
                }
            });
        }
    }

    @OnClick(R.id.ib_attach_capture_photo)
    public final void attachCamera(View view) {
        UiHelpers.takePhoto();
    }

    @OnClick(R.id.ib_attach_record_audio)
    public final void attachVoiceNote(View view) {
        UiHelpers.recordAudio();
    }

    @OnClick(R.id.ib_attach_emoji)
    public final void toggleEmoji(View view) {
        ImageButton button = ((ImageButton) view);
        Fragment emojiFragment = getSupportFragmentManager().findFragmentByTag(EMOJI_FRAGMENT);
        InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (isTypingEmoji) {
            //remove emojiFragment
            if (emojiFragment != null) {
                getSupportFragmentManager().beginTransaction().remove(emojiFragment).commit();
            }
            //show keyboard
            manager.showSoftInput(messageEt, 0);

            //set the icon to emoji
            button.setImageResource(R.drawable.ic_msg_panel_smiles);
        } else {
            //hide keyboard
            manager.hideSoftInputFromWindow(messageEt.getWindowToken(), 0);

            //show emoji fragment
            if (emojiFragment == null) {
                emojiFragment = EmojiconsFragment.newInstance(false);
            }
            getSupportFragmentManager().beginTransaction().replace(R.id.emoji_pannel_slot, emojiFragment,
                    EMOJI_FRAGMENT).commit();
            //set the icon to keyboard
            button.setImageResource(R.drawable.ic_msg_panel_kb);
        }
        isTypingEmoji = !isTypingEmoji;
    }

    @Override
    public void onBackPressed() {
        if (isTypingEmoji) {
            //remove emoji fragment
            Fragment emojiFragment = getSupportFragmentManager().findFragmentByTag(EMOJI_FRAGMENT);
            if (emojiFragment != null) {
                getSupportFragmentManager().beginTransaction().remove(emojiFragment).commit();
            }
            //set the icon to emoji
            emoji.setImageResource(R.drawable.ic_msg_panel_smiles);
            isTypingEmoji = false;
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onEmojiconClicked(Emojicon emojicon) {
        EmojiconsFragment.input(messageEt, emojicon);
    }

    @Override
    public void onEmojiconBackspaceClicked(View v) {
        EmojiconsFragment.backspace(messageEt);
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {

    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

    }

    @Override
    public void afterTextChanged(Editable s) {
        if (s.length() == 0) {
            //show camera and mic
            ViewUtils.showViews(mic, camera);
        } else {
            //hide camera and mic
            ViewUtils.hideViews(mic, camera);
        }
    }

    protected interface SendCallback {
        void onSendComplete(Exception e);
    }
}
