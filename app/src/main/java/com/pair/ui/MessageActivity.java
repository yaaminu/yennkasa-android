package com.pair.ui;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.pair.Errors.ErrorCenter;
import com.pair.Errors.PairappException;
import com.pair.data.Conversation;
import com.pair.data.Message;
import com.pair.data.util.MessageUtils;
import com.pair.pairapp.BuildConfig;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import io.realm.Realm;

/**
 * @author by Null-Pointer on 9/19/2015.
 */
public abstract class MessageActivity extends PairAppActivity {

    private static final String TAG = MessageActivity.class.getSimpleName();
    public static final String CONVERSATION_ACTIVE = "active";
    private Worker worker;

    //public for MessagesAdapter to access
    public void resendMessage(String messageId) {
        android.os.Message msg = android.os.Message.obtain();
        msg.what = Worker.RESEND_MESSAGE;
        msg.obj = messageId;
        worker.sendMessage(msg);
    }


    private void doSendMessage(Message message) {
        if (bound) {
            pairAppClientInterface.sendMessage(message);
        } else {
            bind(); //after binding dispatcher will smartly dispatch all unsent waitingMessages
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        worker = new Worker(this);
        worker.start();
    }

    @Override
    protected void onDestroy() {
        android.os.Message message = android.os.Message.obtain();
        message.what = Worker.STOP;
        worker.sendMessage(message);
        super.onDestroy();
    }

    protected void sendMessage(String messageBody, Set<String> recipientIds, int type) {
        for (String recipientId : recipientIds) {
            sendMessage(messageBody, recipientId, type);
        }
    }

    protected void sendMessage(String messageBody, String recipient, int type) {
        sendMessage(messageBody, recipient, type, false);
    }

    protected void sendMessage(String messageBody, String recipient, int type, boolean active) {
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

    private final class Worker extends HandlerThread implements Handler.Callback, Thread.UncaughtExceptionHandler {
        @SuppressWarnings("unused")
        final static int START = 0x1,
                STOP = 0x2, SEND_MESSAGE = 0x3, SEND_MESSAGE_TO_MANY = 0x4, RESEND_MESSAGE = 0x5;
        public static final String TAG = "dispatchThread";
        private final Context context;
        private Handler handler;
        private Realm realm;

        public Worker(Context context) {
            super("messageHandlerThread", android.os.Process.THREAD_PRIORITY_DEFAULT);
            this.context = context;
        }

        @Override
        public boolean handleMessage(android.os.Message msg) {
            switch (msg.what) {
                case STOP:
                    doStop();
                    break;
                case SEND_MESSAGE:
                    try {
                        Message message = createMessage(msg.getData());
                        doSendMessage(message);
                    } catch (PairappException e) {
                        // TODO: 9/20/2015 handle error well
                        ErrorCenter.reportError(TAG, e.getMessage());
                    }
                    break;
                case SEND_MESSAGE_TO_MANY:
                    throw new UnsupportedOperationException();
                case RESEND_MESSAGE:
                    String msgId = ((String) msg.obj);
                    Message message = realm.where(Message.class).equalTo(Message.FIELD_ID, msgId).findFirst();
                    if (message != null) {
                        if (message.getState() == Message.STATE_SEND_FAILED) {
                            realm.beginTransaction();
                            message.setState(Message.STATE_PENDING);
                            realm.commitTransaction();
                            doSendMessage(message);
                            break;
                        }
                        if (BuildConfig.DEBUG) {
                            throw new IllegalArgumentException("attempted to resend a non-failed message");
                        }
                        Log.w(MessageActivity.TAG, "message cannot be resent because its dispatch has not failed");
                        break;
                    }
                    Log.w(MessageActivity.TAG, "failed to resend message, reason: message deleted");
                    break;
                default:
                    throw new AssertionError();
            }
            return true;
        }

        private final Set<android.os.Message> waitingMessages = new HashSet<>();

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
            this.setUncaughtExceptionHandler(this);
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
                    //realm is so fast that it takes less than a millisecond to create an object. this has an undesirable effect of making our date waitingMessages
                    // and the first message of the day have the same date in milliseconds precision which in return can cause sorting problems.
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

        @Override
        public void uncaughtException(Thread thread, Throwable ex) {
            Log.w(TAG, " uncaught exception : " + ex);
            throw new RuntimeException(ex);
//            if(ex instanceof RuntimeException){
//                throw new RuntimeException(ex);
//            }else{
//                if(BuildConfig.DEBUG){
//                    throw new RuntimeException(ex.getCause());
//                }
//                Log.d(TAG,"caught a checked exception " + ex);
//            }
        }
    }
}
