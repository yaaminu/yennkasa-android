package com.idea.ui;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v4.util.Pair;
import android.view.View;

import com.idea.Errors.ErrorCenter;
import com.idea.Errors.PairappException;
import com.idea.data.Conversation;
import com.idea.data.Message;
import com.idea.data.util.MessageUtils;
import com.idea.pairapp.BuildConfig;
import com.idea.pairapp.R;
import com.idea.util.LiveCenter;
import com.idea.util.PLog;
import com.idea.util.TaskManager;
import com.idea.util.UiHelpers;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.realm.Realm;

/**
 * @author by Null-Pointer on 9/19/2015.
 */
public abstract class MessageActivity extends PairAppActivity implements LiveCenter.ProgressListener {

    public static final String CONVERSATION_ACTIVE = "active";
    private static final String TAG = MessageActivity.class.getSimpleName();
    private Worker worker;

    protected final void resendMessage(String messageId) {
        android.os.Message msg = android.os.Message.obtain();
        msg.what = Worker.RESEND_MESSAGE;
        msg.obj = messageId;
        worker.sendMessage(msg);
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
        if (bound) {
            pairAppClientInterface.sendMessage(message);
        } else {
            bind(); //after binding all waiting messages will be  smartly sent.
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sendStopSignal();
        worker = new Worker(this);
        worker.start();
    }

    @Override
    protected void onDestroy() {
        sendStopSignal();
        super.onDestroy();
    }

    private void sendStopSignal() {
        if (worker != null && worker.isAlive()) {
            android.os.Message message = android.os.Message.obtain();
            message.what = Worker.STOP;
            worker.sendMessage(message);
        }
    }

    protected final void sendMessage(final String messageBody, final Set<String> recipientIds, final int type, final MessageActivity.SendCallback callback) {
        AsyncTask<Void, Void, Void> sendMessageTask = new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                for (String recipientId : recipientIds) {
                    sendMessage(messageBody, recipientId, type);
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                callback.onSendComplete(null);
            }
        };

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            sendMessageTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        } else {
            sendMessageTask.execute();
        }
    }

    protected final void sendMessage(String messageBody, String recipient, int type) {
        sendMessage(messageBody, recipient, type, false);
    }

    protected final void sendMessage(final int requestCode, final Intent data, final String recipient) {

        final ProgressDialog dialog = new ProgressDialog(this);
        dialog.setCancelable(false);
        dialog.setMessage(getString(R.string.st_please_wait));
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                dialog.show();

                final Runnable runnable = new Runnable() {
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
                };
                TaskManager.executeNow(runnable, false);
            }
        });
    }

    protected final void sendMessage(String messageBody, String recipient, int type, boolean active) {
        android.os.Message message = android.os.Message.obtain();
        message.what = Worker.SEND_MESSAGE;
        Bundle bundle = new Bundle(4);
        bundle.putString(Message.FIELD_MESSAGE_BODY, messageBody);
        bundle.putString(Message.FIELD_TO, recipient);
        bundle.putInt(Message.FIELD_TYPE, type);
        bundle.putBoolean(CONVERSATION_ACTIVE, active);
        message.setData(bundle);
        worker.sendMessage(message);
    }

    protected final void onMessageSeen(Message message) {
        if (message.getState() != Message.STATE_SEEN) {
            pairAppClientInterface.notifyMessageSeen(message);
        }
    }

    private final class Worker extends HandlerThread implements Handler.Callback {
        public static final String TAG = "dispatchThread";
        @SuppressWarnings("unused")
        final static int START = 0x1,
                STOP = 0x2, SEND_MESSAGE = 0x3, SEND_MESSAGE_TO_MANY = 0x4, RESEND_MESSAGE = 0x5, MARK_AS_SEEN = 0x6;
        private final Context context;
        private final Set<android.os.Message> waitingMessages = new HashSet<>();
        private Handler handler;
        private Realm realm;

        public Worker(Context context) {
            super("messageHandlerThread", android.os.Process.THREAD_PRIORITY_DEFAULT);
            this.context = context;
        }

        @Override
        public boolean handleMessage(final android.os.Message msg) {
            switch (msg.what) {
                case STOP:
                    doStop();
                    break;
                case SEND_MESSAGE:
                    try {
                        Message message = createMessage(msg.getData());
                        final String messageId = message.getId();
                        final boolean notTextMessage = message.getType() != Message.TYPE_TEXT_MESSAGE;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                onMessageQueued(messageId);
                                if (notTextMessage) {
                                    progresses.put(messageId, 0);
                                    reportProgress(messageId, 0);
                                }
                            }
                        });
                        doSendMessage(message);
                    } catch (PairappException e) {
                        // TODO: 9/20/2015 handle error well
                        ErrorCenter.reportError(TAG, e.getMessage());
                    }
                    break;
                case SEND_MESSAGE_TO_MANY:
                    throw new UnsupportedOperationException();
                case RESEND_MESSAGE:
                    final String msgId = ((String) msg.obj);
                    Message message = realm.where(Message.class).equalTo(Message.FIELD_ID, msgId).findFirst();
                    if (message != null) {
                        if (message.getState() == Message.STATE_SEND_FAILED) {
                            realm.beginTransaction();
                            message.setState(Message.STATE_PENDING);
                            realm.commitTransaction();
                            final boolean notTextMessage = message.getType() != Message.TYPE_TEXT_MESSAGE;
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    onMessageQueued(msgId);
                                    if (notTextMessage) {
                                        progresses.put(msgId, 0);
                                        reportProgress(msgId, 0);
                                    }
                                }
                            });
                            doSendMessage(message);
                            break;
                        }
                        if (BuildConfig.DEBUG) {
                            throw new IllegalArgumentException("attempted to resend a non-failed message");
                        }
                        PLog.w(MessageActivity.TAG, "message cannot be resent because its dispatch has not failed");
                        break;
                    }
                    PLog.w(MessageActivity.TAG, "failed to resend message, reason: message deleted");
                    break;
                default:
                    throw new AssertionError();
            }
            return true;
        }

        void sendMessage(android.os.Message message) {
            if (handler == null) {
                //queue the message
                synchronized (waitingMessages) {
                    waitingMessages.add(message);
                }
            } else {
                handler.sendMessage(message);
            }
        }

        @Override
        protected void onLooperPrepared() {
            handler = new Handler(getLooper(), this);
            realm = Message.REALM(context);
            synchronized (waitingMessages) {
                for (android.os.Message waitingMessage : waitingMessages) {
                    handler.sendMessage(waitingMessage);
                }
                waitingMessages.clear();
            }
        }

        void doStop() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                quitSafely();
            } else {
                quit();
            }
            realm.close();
        }

        private Message createMessage(Bundle bundle) throws PairappException {
            return createMessage(bundle.getString(Message.FIELD_MESSAGE_BODY),
                    bundle.getString(Message.FIELD_TO), bundle.getInt(Message.FIELD_TYPE),
                    bundle.getBoolean(CONVERSATION_ACTIVE, false));
        }

        private Message createMessage(String messageBody, String recipient, int type, boolean active) throws PairappException {
            Conversation currConversation = realm.where(Conversation.class).equalTo(Conversation.FIELD_PEER_ID, recipient).findFirst();
            if (currConversation == null) {
                currConversation = Conversation.newConversation(realm, recipient, active);
            }
            realm.beginTransaction();
            boolean newDay = trySetupNewSession(currConversation);
            try {
                Message message = Message.makeNew(realm, messageBody, recipient, type);
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
            }
        }

        private boolean trySetupNewSession(Conversation conversation) {
            //set up session
            return Conversation.newSession(realm, conversation);
        }

//        @Override
//        public void uncaughtException(Thread thread, Throwable ex) {
//            PLog.w(TAG, " uncaught exception : " + ex);
//            throw new RuntimeException(ex);
////            if(ex instanceof RuntimeException){
////                throw new RuntimeException(ex);
////            }else{
////                if(BuildConfig.DEBUG){
////                    throw new RuntimeException(ex.getCause());
////                }
////                Log.d(TAG,"caught a checked exception " + ex);
////            }
//        }
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

    public void attachCamera(View view) {
        UiHelpers.takePhoto();
    }

    public void attachVoiceNote(View view) {
    }

    protected interface SendCallback {
        void onSendComplete(Exception e);
    }
}
