package com.idea.messenger;

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;

import com.idea.Errors.PairappException;
import com.idea.data.Message;
import com.idea.data.UserManager;
import com.idea.net.ParseClient;
import com.idea.util.Config;
import com.idea.util.L;
import com.idea.util.LiveCenter;
import com.idea.util.PLog;
import com.idea.util.TaskManager;
import com.idea.util.ThreadUtils;

import org.json.JSONObject;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicBoolean;

import io.realm.Realm;
import io.realm.RealmResults;


public class PairAppClient extends Service {
    // FIXME: 6/16/2015 improve how we stop background task
    public static final String TAG = PairAppClient.class.getSimpleName();
    public static final String ACTION_SEND_ALL_UNSENT = "send unsent messages";
    public static final String ACTION = "action";
    private static Dispatcher<Message> SOCKETSIO_DISPATCHER;
    private static AtomicBoolean isClientStarted = new AtomicBoolean(false);
    private static MessagesProvider messageProvider = new ParseMessageProvider();
    private static Stack<Activity> backStack = new Stack<>();
    private final PairAppClientInterface INTERFACE = new PairAppClientInterface();
    private Dispatcher<Message> PARSE_MESSAGE_DISPATCHER;
    private WorkerThread WORKER_THREAD;
    private static final Map<String, String> credentials;

    static {
        Map<String, String> userCredentials = UserManager.getInstance().getUserCredentials();
        credentials = new HashMap<>(userCredentials.size() + 3);
        credentials.putAll(userCredentials);
        //////////////////////////////////////////////////////////////////////////////
        credentials.put(AbstractMessageDispatcher.KEY, "doTbKQlpZyNZohX7KPYGNQXIghATCx");
        credentials.put(AbstractMessageDispatcher.PASSWORD, "Dq8FLrF7HjeiyJBFGv9acNvOLV1Jqm");
        /////////////////////////////////////////////////////////////////////////////////////
    }

    public static void startIfRequired(Context context) {
        if (!UserManager.getInstance().isUserVerified()) {
            L.w(TAG, " pariapp client wont start when a user is not logged in");
            return;
        }
        if (!isClientStarted.get()) {
            Intent pairAppClient = new Intent(context, PairAppClient.class);
            pairAppClient.putExtra(PairAppClient.ACTION, PairAppClient.ACTION_SEND_ALL_UNSENT);
            context.startService(pairAppClient);
        } else {
            PLog.d(TAG, "already running");
        }

    }

    public static MessagesProvider getMessageProvider() {
        return messageProvider;
    }

    public static void markUserAsOffline(Activity activity) {
        ThreadUtils.ensureMain();
        ensureUserLoggedIn();
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
        ensureUserLoggedIn();
        if (activity == null) {
            throw new IllegalArgumentException();
        }
        if (backStack.isEmpty()) {
            LiveCenter.start();
            MessageCenter.startListeningForSocketMessages();
        }
        backStack.add(activity);
    }

    public static void notifyMessageSeen(Message message) {
        ensureUserLoggedIn();
        MessageCenter.notifyMessageSeen(message);
    }

    private static void ensureUserLoggedIn() {
        if (!UserManager.getInstance().isUserVerified()) {
            throw new IllegalStateException("no user logged in");
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (WORKER_THREAD == null || !WORKER_THREAD.isAlive()) {
            WORKER_THREAD = new WorkerThread();
            WORKER_THREAD.start();
        }

        if (!isClientStarted.get()) {
            TaskManager.execute(new Runnable() {
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
            PLog.f(TAG, " pariapp client wont start when a user is not logged in");
            stopSelf();
            return START_NOT_STICKY;
        }

        PLog.i(TAG, "starting pairapp client");
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
        super.onDestroy();
    }

    private synchronized void bootClient() {
        if (!isClientStarted.get()) {
            PARSE_MESSAGE_DISPATCHER = ParseDispatcher.getInstance(credentials);
            monitorDispatcher(PARSE_MESSAGE_DISPATCHER);
            isClientStarted.set(true);
        }
    }

    private synchronized void shutDown() {
        if (isClientStarted.get()) {
            if (PARSE_MESSAGE_DISPATCHER != null) {
                PARSE_MESSAGE_DISPATCHER.close();
            }

            if (SOCKETSIO_DISPATCHER != null) {
                SOCKETSIO_DISPATCHER.close();
            }
            MessageCenter.stopListeningForSocketMessages();
            isClientStarted.set(false);
            PLog.i(TAG, TAG + ": bye");
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
                    RealmResults<Message> messages = realm.where(Message.class).equalTo(Message.FIELD_STATE, Message.STATE_PENDING).findAll();
                    final List<Message> copy = Message.copy(messages);
                    realm.close();

                    if (copy.isEmpty()) {
                        PLog.d(TAG, "all messages sent");
                    } else {
                        for (Message message : copy) {
                            sendMessageInternal(message);
                        }
                    }
                }
            }
        };
        TaskManager.execute(task);
    }

    private final Dispatcher.DispatcherMonitor monitor = new Dispatcher.DispatcherMonitor() {
        @Override
        public void onDispatchFailed(String reason, String objectIdentifier) {
            PLog.d(TAG, "message with id : %s dispatch failed with reason: " + reason, objectIdentifier);
            LiveCenter.releaseProgressTag(objectIdentifier);
        }

        @Override
        public void onDispatchSucceed(String objectIdentifier) {
            PLog.d(TAG, "message with id : %s dispatched successfully", objectIdentifier);
            LiveCenter.releaseProgressTag(objectIdentifier);
        }

        @Override
        public void onProgress(String id, int progress, int max) {
            LiveCenter.updateProgress(id, progress);
        }

        @Override
        public void onAllDispatched() {

        }
    };

    private void sendMessageInternal(Message message) {
        if (!Message.isTextMessage(message)) {
            try {
                LiveCenter.acquireProgressTag(message.getId());
            } catch (PairappException e) {
                throw new RuntimeException(e.getCause());
            }
        }
        if (LiveCenter.isOnline(message.getTo()) && !UserManager.getInstance().isGroup(message.getTo())) {
            if (SOCKETSIO_DISPATCHER == null) {
                SOCKETSIO_DISPATCHER = SocketsIODispatcher.newInstance(credentials);
                monitorDispatcher(SOCKETSIO_DISPATCHER);
            }
            SOCKETSIO_DISPATCHER.dispatch(message);
        } else {
            PARSE_MESSAGE_DISPATCHER.dispatch(message);
        }
    }

    private void monitorDispatcher(Dispatcher dispatcher) {
        dispatcher.addMonitor(monitor);
    }

    public static void sendFeedBack(final JSONObject report) {
        if (report == null || !report.keys().hasNext()) {
            throw new IllegalArgumentException("empty report");
        }

        TaskManager.execute(new Runnable() {
            @Override
            public void run() {
                ParseClient.getInstance().sendFeedBack(report);
            }
        });
    }

    public static void download(Message message) {
        MessageProcessor.download(message);
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

        public void registerUINotifier(final Notifier notifier) {
            if (!isClientStarted.get()) {
                TaskManager.execute(new Runnable() {
                    @Override
                    public void run() {
                        bootClient();
                        NotificationManager.INSTANCE.registerUI_Notifier(notifier);
                    }
                });
            } else {
                NotificationManager.INSTANCE.registerUI_Notifier(notifier);
            }
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
                PLog.w(TAG, "sending message when worker is yet to start");
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
                    doSendMessage((Message) msg.obj);
                    break;
                case SEND_BATCH:
                    //noinspection unchecked
                    doSendMessages((Collection<Message>) msg.obj);
                    break;
                case SHUT_DOWN:
                    //noinspection ConstantConditions
                    Looper.myLooper().quit();
                    break;
                default:
                    throw new AssertionError("unknown signal");
            }
        }

        private void doSendMessage(final Message message) {
            final Runnable sendTask = new Runnable() {
                @Override
                public void run() {
                    sendMessageInternal(message);
                }
            };
            if (!TaskManager.executeNow(sendTask)) {
                TaskManager.execute(sendTask);
            }
        }

        private void doSendMessages(Collection<Message> messages) {
            for (Message message : messages) {
                doSendMessage(message);
            }
        }
    }


}
