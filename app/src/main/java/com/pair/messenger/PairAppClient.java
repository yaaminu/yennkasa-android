package com.pair.messenger;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import com.pair.Config;
import com.pair.data.Message;
import com.pair.data.UserManager;
import com.pair.pairapp.BuildConfig;
import com.pair.util.L;
import com.pair.util.UiHelpers;
import com.parse.ParseAnalytics;
import com.sinch.android.rtc.ClientRegistration;
import com.sinch.android.rtc.ErrorType;
import com.sinch.android.rtc.PushPair;
import com.sinch.android.rtc.SinchClient;
import com.sinch.android.rtc.SinchClientListener;
import com.sinch.android.rtc.SinchError;
import com.sinch.android.rtc.messaging.MessageClient;
import com.sinch.android.rtc.messaging.MessageClientListener;
import com.sinch.android.rtc.messaging.MessageDeliveryInfo;
import com.sinch.android.rtc.messaging.MessageFailureInfo;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import io.realm.Realm;
import io.realm.RealmResults;


public class PairAppClient extends Service {
    // FIXME: 6/16/2015 improve how we stop background task
    public static final String TAG = PairAppClient.class.getSimpleName();
    public static final String ACTION_SEND_ALL_UNSENT = "send unsent messages";
    public static final String ACTION = "action";

    private final PairAppClientInterface INTERFACE = new PairAppClientInterface();
    private Dispatcher<Message> PARSE_MESSAGE_DISPATCHER, SINCH_MESSAGE_DISPATCHER;

    private SinchClient CLIENT;

    private WorkerThread WORKER_THREAD;
    private ExecutorService WORKER;

    private static AtomicBoolean isClientStarted = new AtomicBoolean(false);

    public static void start(Context context) {
        if (!UserManager.getInstance().isUserVerified()) {
            L.w(TAG, " pariapp client wont start when a user is not logged in");
            return;
        }
        if (!isClientStarted.get()) {
            Intent pairAppClient = new Intent(context, PairAppClient.class);
            pairAppClient.putExtra(PairAppClient.ACTION, PairAppClient.ACTION_SEND_ALL_UNSENT);
            context.startService(pairAppClient);
        } else {
            Log.d(TAG, "already running");
        }

    }


    @Override
    public void onCreate() {
        super.onCreate();
        if (!isClientStarted.get()) {
            bootClient();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!UserManager.getInstance().isUserVerified()) {
            L.w(TAG, " pariapp client wont start when a user is not logged in");
            return START_NOT_STICKY;
        }

        Log.i(TAG, "starting pairapp client");
        super.onStartCommand(intent, flags, startId);
        if (intent != null && isClientStarted.get()) {
            if (intent.getStringExtra(ACTION).equals(ACTION_SEND_ALL_UNSENT)) {
                attemptToSendAllUnsentMessages();
            }
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (!isClientStarted.get()) {
            bootClient();
        }
        if (intent != null && intent.getStringExtra(ACTION).equals(ACTION_SEND_ALL_UNSENT)) {
            attemptToSendAllUnsentMessages();
        }
        return INTERFACE;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return true; //support re-binding default implementation returns false
    }


    @Override
    public void onDestroy() {
        shutDown();
        super.onDestroy();
    }

    private synchronized void bootClient() {
        if (!isClientStarted.getAndSet(true))
            try {
                CLIENT = SinchUtils.makeSinchClient(this, UserManager.getMainUserId());
                CLIENT.setSupportMessaging(true);
                CLIENT.startListeningOnActiveConnection();
                CLIENT.addSinchClientListener(sinchClientListener);
                if (BuildConfig.DEBUG) {
                    CLIENT.checkManifest();
                }
                MessageClient client = CLIENT.getMessageClient();
                client.addMessageClientListener(messageClientListener);
                SINCH_MESSAGE_DISPATCHER = SinchDispatcher.getInstance(client);
                CLIENT.start();
            } catch (SinchUtils.SinchNotFoundException e) {
                ParseAnalytics.trackEventInBackground("noSinch");
                Log.wtf(TAG, "user's device does not have complete  support, call features will not be available");
                //fallback to push
            }

        PARSE_MESSAGE_DISPATCHER = ParseDispatcher.getInstance();
        WORKER_THREAD = new WorkerThread();
        WORKER_THREAD.start();
        WORKER = Executors.newSingleThreadExecutor();
        isClientStarted.set(true);
    }

    private synchronized void shutDown() {
        if (isClientStarted.getAndSet(false)) {
            if (PARSE_MESSAGE_DISPATCHER != null) {
                PARSE_MESSAGE_DISPATCHER.close();
            }
            if (CLIENT != null) { //sinch client may not be available e.g if device arch is x86 or mips
                SINCH_MESSAGE_DISPATCHER.close();
                CLIENT.terminateGracefully();
            }
            WORKER_THREAD.shutDown(); //the order is important
            WORKER.shutdownNow();
            Log.i(TAG, TAG + ": bye");
            return;
        }
        L.w(TAG, "shutting down pairapp client when it is already shut down");
    }


    private void attemptToSendAllUnsentMessages() {
        if (!isClientStarted.get()) {
            return;
        }
        Runnable task = new Runnable() {
            @Override
            public void run() {
                Realm realm = Message.REALM(Config.getApplicationContext());
                RealmResults<Message> messages = realm.where(Message.class).notEqualTo(Message.FIELD_TYPE, Message.TYPE_DATE_MESSAGE)
                        .or().equalTo(Message.FIELD_TYPE, Message.TYPE_TYPING_MESSAGE)
                        .equalTo(Message.FIELD_STATE, Message.STATE_PENDING).findAll();
                if (messages.isEmpty()) {
                    Log.d(TAG, "all messages sent");
                } else {
                    final List<Message> copy = Message.copy(messages);
                    WORKER_THREAD.sendMessages(copy);
                }
                realm.close();
            }
        };
        try {
            WORKER.submit(task);
        } catch (Exception e) { //quick fix for a mysterious crash
            Log.e(TAG, "" + e.getMessage());
        }
    }


    private final SinchClientListener sinchClientListener = new SinchClientListener() {
        @Override
        public void onClientStarted(SinchClient sinchClient) {
            Log.i(TAG, "call client started, ready  to recieve calls");
        }

        @Override
        public void onClientStopped(SinchClient sinchClient) {
            //what caused the client to stop?
            if (isClientStarted.get()) {
                //something is wrong
                Log.wtf(TAG, "Client stopped surprisingly");
            }
        }

        @Override
        public void onClientFailed(SinchClient sinchClient, SinchError sinchError) {

            if (sinchError.getErrorType() == ErrorType.NETWORK) {
                bootClient(); //try again // TODO: 9/2/2015 do this exponentially
            } else {
                // TODO: 8/29/2015 more error handling
                Log.e(TAG, "Client failed to start with reason: " + sinchError.getErrorType());
                if (isClientStarted.get()) {

//                    UiHelpers.showPlainOlDialog(PairAppClient.this, "we are having trouble setting things up, " +
//                            "ensure your are connected to the internet and that your date is correct");
                }
            }
        }

        @Override
        public void onRegistrationCredentialsRequired(SinchClient sinchClient, ClientRegistration clientRegistration) {

        }

        @Override
        public void onLogMessage(int i, String s, String s1) {
            SinchUtils.logMessage(i, s, s1);
        }
    };


    private final MessageClientListener messageClientListener = new MessageClientListener() {
        @Override
        public void onIncomingMessage(MessageClient messageClient, com.sinch.android.rtc.messaging.Message message) {
            Bundle bundle = new Bundle();
            bundle.putString("message", message.getTextBody());
            Intent intent = new Intent(PairAppClient.this, MessageProcessor.class);
            intent.putExtras(bundle);
            startService(intent);
        }

        @Override
        public void onMessageSent(MessageClient messageClient, com.sinch.android.rtc.messaging.Message message, String s) {

        }

        @Override
        public void onMessageFailed(MessageClient messageClient, com.sinch.android.rtc.messaging.Message message, MessageFailureInfo messageFailureInfo) {

        }

        @Override
        public void onMessageDelivered(MessageClient messageClient, MessageDeliveryInfo messageDeliveryInfo) {

        }

        @Override
        public void onShouldSendPushData(MessageClient messageClient, com.sinch.android.rtc.messaging.Message message, List<PushPair> list) {

        }
    };

    public class PairAppClientInterface extends Binder {
        public void sendMessage(Message message) {
            if (!isClientStarted.get()) {
                bootClient();
            }
            if (WORKER_THREAD.isAlive()) //worker thread will send all pending messages immediately it come alive
                WORKER_THREAD.sendMessage(Message.copy(message)); //detach from realm
        }


        public void sendMessages(Collection<Message> tobeSent) {
            if (!isClientStarted.get()) {
                bootClient();
            }
            if (WORKER_THREAD.isAlive()) //worker thread will send all pending messages immediately it come alive
                WORKER_THREAD.sendMessages(Message.copy(tobeSent)); //detach from realm
        }

        public void disPatchEvent(String eventName) {
            oops();
        }

        public void disPatchEvent(String eventName, String details) {
            oops();
        }

        public void callUser(String userId) {
            oops();
        }

        public void registerUINotifier(Notifier notifier) {
            if (!isClientStarted.get()) {
                bootClient();
            }
            NotificationManager.INSTANCE.registerUI_Notifier(notifier);
        }

        public void unRegisterUINotifier(Notifier notifier) {
            NotificationManager.INSTANCE.unRegisterUI_Notifier(notifier);
        }

        private void oops() {
            throw new UnsupportedOperationException("not yet implemented");
        }
    }


    private final class WorkerThread extends Thread {
        Handler handler;

        public WorkerThread() {
        }

        @Override
        public void run() {
            Looper.prepare();
            handler = new MessageHandler();
            attemptToSendAllUnsentMessages();
            Looper.loop();
        }

        public void sendMessage(Message message) {
            if (checkStarted()) {
                android.os.Message msg = android.os.Message.obtain();
                msg.obj = message;
                msg.what = MessageHandler.SEND_MESSAGE;
                handler.sendMessage(msg);
            }
        }

        public void sendMessages(Collection<Message> messages) {
            if (checkStarted()) {
                android.os.Message msg = android.os.Message.obtain();
                msg.obj = messages;
                msg.what = MessageHandler.SEND_BATCH;
                handler.sendMessage(msg);
            }
        }

        public synchronized void shutDown() {
            android.os.Message message = android.os.Message.obtain();
            message.what = MessageHandler.SHUT_DOWN;
            handler.sendMessage(message);
        }

        private boolean checkStarted() {
            if (handler == null) {
                if (BuildConfig.DEBUG) {
                    throw new IllegalStateException("thread yet to run");
                }
                Log.w(TAG, "sending message when worker is yet to start");
                return false;
            }
            return true;
        }
    }

    private class MessageHandler extends Handler {
        public static final int SEND_MESSAGE = 0x0, SEND_BATCH = 0x01, SHUT_DOWN = 0x2;

        public MessageHandler() {
        }

        @Override
        public void handleMessage(android.os.Message msg) {
            switch (msg.what) {
                case SEND_MESSAGE:
                    sendMessage((Message) msg.obj);
                    break;
                case SEND_BATCH:
                    //noinspection unchecked
                    sendMessages((Collection<Message>) msg.obj);
                    break;
                case SHUT_DOWN:
                    //noinspection ConstantConditions
                    Looper.myLooper().quit();
                    break;
                default:
                    throw new AssertionError("unknown signal");
            }
        }

        private void sendMessage(Message message) {
            if (SINCH_MESSAGE_DISPATCHER != null && UserManager.getInstance().supportsCalling(message.getTo())) {
                SINCH_MESSAGE_DISPATCHER.dispatch(message);
            } else {
                PARSE_MESSAGE_DISPATCHER.dispatch(message);
            }
        }

        private void sendMessages(Collection<Message> messages) {
            PARSE_MESSAGE_DISPATCHER.dispatch(messages);
        }
    }

}
