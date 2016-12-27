package com.yennkasa.ui;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.util.Pair;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;

import com.yennkasa.BuildConfig;
import com.yennkasa.Errors.ErrorCenter;
import com.yennkasa.Errors.YennkasaException;
import com.yennkasa.R;
import com.yennkasa.data.Conversation;
import com.yennkasa.data.Message;
import com.yennkasa.data.User;
import com.yennkasa.data.UserManager;
import com.yennkasa.data.util.MessageUtils;
import com.yennkasa.messenger.MessengerBus;
import com.yennkasa.util.Config;
import com.yennkasa.util.Event;
import com.yennkasa.util.EventBus;
import com.yennkasa.util.GenericUtils;
import com.yennkasa.util.LiveCenter;
import com.yennkasa.util.PLog;
import com.yennkasa.util.TaskManager;
import com.yennkasa.util.ThreadUtils;
import com.yennkasa.util.UiHelpers;
import com.yennkasa.util.ViewUtils;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import butterknife.OnClick;
import io.realm.Realm;
import vc908.stickerfactory.ui.OnEmojiBackspaceClickListener;
import vc908.stickerfactory.ui.OnStickerSelectedListener;
import vc908.stickerfactory.ui.fragment.StickersFragment;

import static com.yennkasa.messenger.MessengerBus.MESSAGE_SEEN;
import static com.yennkasa.messenger.MessengerBus.SEND_MESSAGE;

/**
 * @author by Null-Pointer on 9/19/2015.
 */
public abstract class MessageActivity extends PairAppActivity implements
        LiveCenter.ProgressListener, TextWatcher, OnStickerSelectedListener, OnEmojiBackspaceClickListener {

    private static final String TAG = MessageActivity.class.getSimpleName();
    public static final String EMOJI_FRAGMENT = "emojiFragment";
    public static final String ON_MESSAGE_QUEUED = "onMessageQueued";
    private boolean isTypingEmoji = false;
    private ImageButton emoji, camera, mic;
    private EditText messageEt;
    FrameLayout stickersContainer;

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
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EventBus.getDefault().register(this, ON_MESSAGE_QUEUED);
    }

    @Override
    protected void onDestroy() {
        EventBus.getDefault().unregister(ON_MESSAGE_QUEUED, this);
        super.onDestroy();
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        stickersContainer = ((FrameLayout) findViewById(R.id.emoji_pannel_slot));
        messageEt = (EditText) findViewById(R.id.et_message);
        emoji = ((ImageButton) findViewById(R.id.ib_attach_emoji));
        camera = ((ImageButton) findViewById(R.id.ib_attach_capture_photo));
        mic = ((ImageButton) findViewById(R.id.ib_attach_record_audio));
        messageEt.addTextChangedListener(this);
        StickersFragment emojiFragment = new StickersFragment();
        emojiFragment.setOnStickerSelectedListener(this);
        emojiFragment.setOnEmojiBackspaceClickListener(this);
        getSupportFragmentManager().beginTransaction().replace(R.id.emoji_pannel_slot, emojiFragment,
                EMOJI_FRAGMENT).commit();
        messageEt.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    ViewUtils.hideViews(stickersContainer);
                    emoji.setImageResource(R.drawable.sp_ic_stickers);
                    isTypingEmoji = false;
                }
            }
        });
        messageEt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ViewUtils.hideViews(stickersContainer);
                emoji.setImageResource(R.drawable.sp_ic_stickers);
                isTypingEmoji = false;
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

    private static void doSendMessage(Realm realm, final Message message) {
        postEvent(Event.createSticky(SEND_MESSAGE, null, Message.copy(message)));
        EventBus.getDefault().post(Event.create(ON_MESSAGE_QUEUED, null, realm.copyFromRealm(message)));
    }

    protected static void sendMessage(final String messageBody, final Set<String> recipientIds, final int type, final MessageActivity.SendCallback callback) {
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
                } catch (final YennkasaException e) {
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

    private static void sendMessage(String msgBody, String to, int msgType, boolean active) {
        ThreadUtils.ensureNotMain();
        Realm realm = Conversation.Realm();
        try {
            Message message = createMessage(msgBody, to, msgType, active);
            doSendMessage(realm, message);
        } catch (YennkasaException e) {
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
            }
        } finally {
            realm.close();
        }
        // TODO: 12/6/16 notify user of failed attempt to edit message
    }

    private static void resendFailedMessage(final String msgId) {
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
                        doSendMessage(realm, newMessage);
                    } catch (YennkasaException e) {
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


    private static Message createMessage(String messageBody, String recipient, int type, boolean active) throws YennkasaException {
        Realm userRealm = User.Realm(Config.getApplicationContext()),
                realm = Conversation.Realm();
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
                summary = MessageUtils.typeToString(Config.getApplicationContext(), message);
            }
            currConversation.setSummary(summary);
            realm.commitTransaction();
            return message;
        } catch (YennkasaException e) { //caught for the for the purpose of cleanup
            realm.cancelTransaction();
            throw new YennkasaException(e.getMessage(), "");
        } finally {
            userRealm.close();
            realm.close();
        }
    }

    private static boolean trySetupNewSession(Conversation conversation, Realm realm, Realm userRealm) {
        //set up session
        return Conversation.newSession(realm, UserManager.getMainUserId(userRealm), conversation);
    }

    protected void reportProgress(String messageId, int progress) {
    }

    protected void onCancelledOrDone(String messageId) {
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
        final ImageButton button = ((ImageButton) view);
        final InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (isTypingEmoji) {
            ViewUtils.hideViews(stickersContainer);
            //show keyboard
            manager.showSoftInput(messageEt, 0);

            //set the icon to emoji
            button.setImageResource(R.drawable.sp_ic_stickers);
            isTypingEmoji = false;
        } else {
            //hide keyboard
            manager.hideSoftInputFromWindow(messageEt.getWindowToken(), 0);

            stickersContainer.getHandler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    //show emoji fragment
                    ViewUtils.showViews(stickersContainer);
                    //set the icon to keyboard
                    button.setImageResource(R.drawable.sp_ic_keyboard);
                    isTypingEmoji = !isTypingEmoji;
                }
            }, 200);
        }
    }

    @Override
    public void onBackPressed() {
        if (isTypingEmoji) {
            ViewUtils.hideViews(stickersContainer);
            //set the icon to emoji
            emoji.setImageResource(R.drawable.sp_ic_stickers);
            isTypingEmoji = false;
        } else {
            super.onBackPressed();
        }
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

    @Override
    public final void onEmojiBackspaceClicked() {
        KeyEvent event = new KeyEvent(0, 0, 0, KeyEvent.KEYCODE_DEL, 0, 0, 0, 0, KeyEvent.KEYCODE_ENDCALL);
        messageEt.dispatchKeyEvent(event);
    }

    @Override
    public final void onStickerSelected(final String stickerCode) {
        onSendSticker(stickerCode);
    }

    @Override
    public final void onEmojiSelected(String emoji) {
        messageEt.append(emoji);
    }

    protected abstract void onSendSticker(String stickerCode);

    @Override
    protected void handleEvent(Event event) {
        if (event.getTag().equals(ON_MESSAGE_QUEUED)) {
            assert event.getData() != null;
            onMessageQueued(((Message) event.getData()).getId());
        }
    }

    protected void onMessageQueued(String messageId) {
    }
}
