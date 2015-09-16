package com.pair.messenger;

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import com.pair.data.Message;
import com.pair.data.UserManager;
import com.pair.util.Config;
import com.pair.util.L;
import com.pair.util.LiveCenter;
import com.pair.util.ThreadUtils;

import java.util.Collection;
import java.util.List;
import java.util.Stack;
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
    private Dispatcher<Message> PARSE_MESSAGE_DISPATCHER;
    private static Dispatcher<Message> SOCKETSIO_DISPATCHER;
    private WorkerThread WORKER_THREAD;
    private ExecutorService WORKER;

    private static AtomicBoolean isClientStarted = new AtomicBoolean(false);
    private static MessagesProvider messageProvider = new ParseMessageProvider();

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

    public static MessagesProvider getMessageProvider() {
        return messageProvider;
    }


    @Override
    public void onCreate() {
        super.onCreate();
        if (WORKER_THREAD == null) {
            WORKER_THREAD = new WorkerThread();
            WORKER_THREAD.start();
        }
        if (WORKER == null) {
            WORKER = Executors.newSingleThreadExecutor();
        }

        if (!isClientStarted.get()) {
            WORKER.execute(new Runnable() {
                @Override
                public void run() {
                    if (!isClientStarted.get())
                        bootClient();
                }
            });
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        if (!UserManager.getInstance().isUserVerified()) {
            L.w(TAG, " pariapp client wont start when a user is not logged in");
            stopSelf();
            return START_NOT_STICKY;
        }

        Log.i(TAG, "starting pairapp client");
        if (intent != null && isClientStarted.get()) {
            if (intent.getStringExtra(ACTION).equals(ACTION_SEND_ALL_UNSENT)) {
                attemptToSendAllUnsentMessages();
            }
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (UserManager.getInstance().isUserVerified()) {
            if (!isClientStarted.get()) {
                bootClient();
            }
            if (intent != null && intent.getStringExtra(ACTION).equals(ACTION_SEND_ALL_UNSENT)) {
                attemptToSendAllUnsentMessages();
            }
            return INTERFACE;
        }
        throw new IllegalStateException("user must be logged in");
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return true; //support re-binding default implementation returns false
    }


    @Override
    public void onDestroy() {
        shutDown();
        //order is important
        WORKER_THREAD.shutDown();
        WORKER.shutdownNow();
        super.onDestroy();
    }

    private synchronized void bootClient() {
        if (!isClientStarted.get()) {
            PARSE_MESSAGE_DISPATCHER = ParseDispatcher.getInstance();
            isClientStarted.set(true);
        }
    }


    private synchronized void shutDown() {
        if (isClientStarted.get()) {
            PARSE_MESSAGE_DISPATCHER.close();

            if (SOCKETSIO_DISPATCHER != null) {
                SOCKETSIO_DISPATCHER.close();
            }
            MessageCenter.stopListeningForSocketMessages();
            isClientStarted.set(false);
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
                synchronized (PairAppClient.this) {
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
            }
        };
        WORKER.submit(task);
    }


    private static Stack<Activity> backStack = new Stack<>();

    public static void markUserAsOffline(Activity activity) {
        ThreadUtils.ensureMain();
        if (activity == null) {
            throw new IllegalArgumentException();
        }
        if (backStack.size() > 0) { //avoid empty stack exceptions
            backStack.pop();
        }

        if (backStack.isEmpty()) {
            LiveCenter.stop();
            MessageCenter.stopListeningForSocketMessages();
            if (SOCKETSIO_DISPATCHER != null) {
                SOCKETSIO_DISPATCHER.close();
                SOCKETSIO_DISPATCHER = null;
            }
        }
    }

    public static void markUserAsOnline(Activity activity) {
        ThreadUtils.ensureMain();
        if (activity == null) {
            throw new IllegalArgumentException();
        }
        if (backStack.isEmpty()) {
            LiveCenter.start();
            MessageCenter.startListeningForSocketMessages();
        }
        backStack.add(activity);
    }

    public class PairAppClientInterface extends Binder {
        public void sendMessage(Message message) {
            if (!isClientStarted.get()) {
                bootClient();
            }
            if (WORKER_THREAD.isAlive()) //worker thread will send all pending messages immediately it comes alive
                WORKER_THREAD.sendMessage(message.isValid() ? Message.copy(message) : message); //detach the message from realm
        }

        public void sendMessages(Collection<Message> tobeSent) {
            if (!isClientStarted.get()) {
                bootClient();
            }
            if (WORKER_THREAD.isAlive()) //worker thread will send all pending messages immediately it come alive
                WORKER_THREAD.sendMessages(Message.copy(tobeSent)); //detach from realm
        }

        @SuppressWarnings("unused")
        public void disPatchEvent(String eventName) {
            oops();
        }

        @SuppressWarnings("unused")
        public void disPatchEvent(String eventName, String details) {
            oops();
        }

        @SuppressWarnings("unused")
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


    private final class WorkerThread extends HandlerThread {
        Handler handler;

        public WorkerThread() {
            super(TAG, NORM_PRIORITY);
        }

        @Override
        protected void onLooperPrepared() {
            handler = new MessageHandler(getLooper());
            attemptToSendAllUnsentMessages();
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

        public MessageHandler(Looper looper) {
            super(looper);
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
            if (LiveCenter.isOnline(message.getTo()) && !UserManager.getInstance().isGroup(message.getTo())) {
                if (SOCKETSIO_DISPATCHER == null) {
                    SOCKETSIO_DISPATCHER = SocketsIODispatcher.newInstance();
                }
                SOCKETSIO_DISPATCHER.dispatch(message);
            } else {
                PARSE_MESSAGE_DISPATCHER.dispatch(message);
            }
        }

        private void sendMessages(Collection<Message> messages) {
            for (Message message : messages) {
                sendMessage(message);
            }
        }
    }

}
