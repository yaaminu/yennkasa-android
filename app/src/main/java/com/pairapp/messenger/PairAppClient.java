package com.pairapp.messenger;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.util.Log;

import com.pairapp.BuildConfig;
import com.pairapp.Errors.PairappException;
import com.pairapp.data.Message;
import com.pairapp.data.StatusManager;
import com.pairapp.data.UserManager;
import com.pairapp.data.util.MessageUtils;
import com.pairapp.net.ParseClient;
import com.pairapp.net.ParseFileClient;
import com.pairapp.net.sockets.Sendable;
import com.pairapp.net.sockets.SenderImpl;
import com.pairapp.util.Config;
import com.pairapp.util.EventBus;
import com.pairapp.util.LiveCenter;
import com.pairapp.util.PLog;
import com.pairapp.util.TaskManager;
import com.pairapp.util.ThreadUtils;

import org.json.JSONObject;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import io.realm.Realm;
import io.realm.RealmResults;

import static com.pairapp.messenger.MessengerBus.CANCEL_MESSAGE_DISPATCH;
import static com.pairapp.messenger.MessengerBus.DE_REGISTER_NOTIFIER;
import static com.pairapp.messenger.MessengerBus.MESSAGE_RECEIVED;
import static com.pairapp.messenger.MessengerBus.MESSAGE_SEEN;
import static com.pairapp.messenger.MessengerBus.NOT_TYPING;
import static com.pairapp.messenger.MessengerBus.OFFLINE;
import static com.pairapp.messenger.MessengerBus.ONLINE;
import static com.pairapp.messenger.MessengerBus.ON_MESSAGE_DELIVERED;
import static com.pairapp.messenger.MessengerBus.ON_MESSAGE_SEEN;
import static com.pairapp.messenger.MessengerBus.REGISTER_NOTIFIER;
import static com.pairapp.messenger.MessengerBus.SEND_MESSAGE;
import static com.pairapp.messenger.MessengerBus.START_MONITORING_USER;
import static com.pairapp.messenger.MessengerBus.STOP_MONITORING_USER;
import static com.pairapp.messenger.MessengerBus.TYPING;


public class PairAppClient extends Service {
    public static final String TAG = PairAppClient.class.getSimpleName();
    public static final String ACTION_SEND_ALL_UNSENT = "send unsent messages";
    public static final String ACTION = "action";
    static final String VERSION = "version";
    static final int notId = 10983;
    public static final String READ_RECEIPT_DELIVERY_REPORT_COLLAPSE_KEY = "readReceiptDeliveryReport";
    public static final int WAIT_MILLIS_DELIVERY_REPORT = 0;
    private static AtomicBoolean isClientStarted = new AtomicBoolean(false);
    private static Set<Activity> backStack = new HashSet<>(6);
    private WorkerThread WORKER_THREAD;
    private WebSocketDispatcher webSocketDispatcher;
    private MessagePacker messagePacker;
    private final Map<String, Future<?>> disPatchingThreads = new ConcurrentHashMap<>();
    private Dispatcher.DispatcherMonitor monitor;
    private StatusManager statusManager;
    private PairAppClientEventsListener eventsListener = new PairAppClientEventsListener(new PairAppClientInterface());
    @SuppressWarnings("FieldCanBeLocal")
    private IncomingMessageProcessor incomingMessageProcessor; //field is required to outlive its scope
    private SenderImpl sender;


    public static void startIfRequired(Context context) {
        if (!UserManager.getInstance().isUserVerified()) {
            PLog.w(TAG, " pariapp client wont start when a user is not logged in");
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


    public static void downloadAttachment(Message message) {
        if (message == null) {
            throw new IllegalArgumentException("message is null");
        }
        Worker.download(Config.getApplicationContext(), message);
    }

    public static void cancelDownload(Message message) {
        if (message == null) {
            throw new IllegalArgumentException("message is null");
        }
        Worker.cancelDownload(Config.getApplicationContext(), message);
    }

    private static void ensureUserLoggedIn() {
        if (!UserManager.getInstance().isUserVerified()) {
            throw new IllegalStateException("no user logged in");
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        PLog.i(TAG, "starting pairapp client");
        if (WORKER_THREAD != null && WORKER_THREAD.isAlive()) {
            WORKER_THREAD.quit();
        }
        new UpdateHelper().checkUpdates();
        WORKER_THREAD = new WorkerThread();
        WORKER_THREAD.start();

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        if (!UserManager.getInstance().isUserVerified()) {
            PLog.f(TAG, " pariapp client wont start when a user is not logged in");
            stopSelf();
            return START_NOT_STICKY;
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        WORKER_THREAD.attemptShutDown();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private synchronized void bootClient() {
        ThreadUtils.ensureNotMain();
        if (!isClientStarted.get()) {
            EventBus.resetBus(ListneableBusClazz.class);
            EventBus.resetBus(PostableBusClazz.class);

            monitor = new DispatcherMonitorImpl(this, disPatchingThreads);
            messagePacker = MessagePacker.create(UserManager.getMainUserId());

            String authToken = UserManager.getInstance().getCurrentUserAuthToken();
            Map<String, String> opts = new HashMap<>(1);
            opts.put("Authorization", authToken);
            opts.put("cursor", MessageProcessor.getCursor() + "");
            sender = new SenderImpl(opts, new MessageParserImpl(messagePacker));
            sender.start();
            statusManager = StatusManager.create(sender, messagePacker, listenableBus());
            webSocketDispatcher = WebSocketDispatcher.create(new ParseFileClient(), monitor, sender,
                    new MessageEncoderImpl(messagePacker));
            incomingMessageProcessor = new IncomingMessageProcessor(statusManager, postableBus());
            messagePacker.observe().subscribe(incomingMessageProcessor);
            postableBus().register(eventsListener, OFFLINE, ONLINE, NOT_TYPING, TYPING,
                    STOP_MONITORING_USER, START_MONITORING_USER,
                    MESSAGE_RECEIVED, MESSAGE_SEEN,
                    ON_MESSAGE_DELIVERED, ON_MESSAGE_SEEN,
                    SEND_MESSAGE, CANCEL_MESSAGE_DISPATCH,
                    REGISTER_NOTIFIER, DE_REGISTER_NOTIFIER);
            isClientStarted.set(true);
        }
    }


    private synchronized void shutDown() {
        ThreadUtils.ensureNotMain();
        if (isClientStarted.get()) {
            sender.shutdownSafely();
            EventBus.resetBus(ListneableBusClazz.class);
            EventBus.resetBus(PostableBusClazz.class);
            messagePacker.close();
            webSocketDispatcher.unRegisterMonitor(monitor);
            webSocketDispatcher.close();
            PLog.i(TAG, TAG + ": bye");
            isClientStarted.set(false);
            return;
        }
        PLog.w(TAG, "shutting down pairapp client when it is already shut down");
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
                            if (Message.isOutGoing(message)) //new incoming messages that have not been  reported still have their state set to pending
                                sendMessageInternal(message);
                        }
                    }
                }
            }
        };
        TaskManager.execute(task, true);
    }

    private void sendMessageInternal(Message message) {
        Realm realm = Message.REALM(this);
        try {
            String messageId = message.getId();
            Message tmp = realm.where(Message.class).equalTo(Message.FIELD_ID, message.getId()).findFirst();
            if (tmp == null || tmp.getState() != Message.STATE_PENDING) {
                PLog.d(TAG, "failed to send message, message either canceled or deleted by user");
                monitor.onDispatchFailed(messageId, MessageUtils.ERROR_CANCELLED);
                return;
            }
        } finally {
            realm.close();
        }
        if (!Message.isTextMessage(message)) {
            try {
                LiveCenter.acquireProgressTag(message.getId());
            } catch (PairappException e) {
                throw new RuntimeException(e.getCause());
            }
        }
        webSocketDispatcher.dispatch(message);
    }


    public static void sendFeedBack(final JSONObject report, final List<String> attachments) {
        TaskManager.execute(new Runnable() {
            @Override
            public void run() {
                ParseClient.sendFeedBack(report, attachments);
            }
        }, true);
    }

    class PairAppClientInterface {
        public void sendMessage(Message message) {
            WORKER_THREAD.sendMessage(Message.copy(message)); //detach the message from realm
        }

        public void cancelDisPatch(Message message) {
            if (!Message.isOutGoing(message)) {
                throw new IllegalArgumentException("only outgoing messages may be cancelled!");
            }
            doCancelDispatch(Message.copy(message));
        }

        public void registerUINotifier(final Notifier notifier) {
            NotificationManager.INSTANCE.registerUI_Notifier(notifier);
        }

        public void unRegisterUINotifier(Notifier notifier) {
            NotificationManager.INSTANCE.unRegisterUI_Notifier(notifier);
        }

        public void markUserAsOffline(Activity activity) {
            ThreadUtils.ensureMain();
            ensureUserLoggedIn();
            if (activity == null) {
                throw new IllegalArgumentException();
            }
            backStack.remove(activity);
            if (backStack.isEmpty()) {
                PLog.d(TAG, "marking user as offline");
                statusManager.announceStatusChange(false);
            }
        }

        public void markUserAsOnline(Activity activity) {
            ThreadUtils.ensureMain();
            ensureUserLoggedIn();
            if (activity == null) {
                throw new IllegalArgumentException();
            }
            if (backStack.isEmpty()) {
                TaskManager.executeNow(new Runnable() {
                    @Override
                    public void run() {
                        Log.d(TAG, "marking user as online");
                        statusManager.announceStatusChange(true);
                    }
                }, true);
            }
            backStack.add(activity);
        }

        public void notifyPeerTyping(final String user) {
            statusManager.announceStartTyping(user);
        }

        public void notifyPeerNotTyping(final String user) {
            statusManager.announceStopTyping(user);
        }

        public void startMonitoringUser(final String user) {
            statusManager.startMonitoringUser(user);
        }

        public void stopMonitoringUser(String user) {
            statusManager.stopMonitoringUser(user);
        }


        private Sendable createReadReceiptSendable(String recipient, byte[] data, long startProcessingAt) {
            return new Sendable.Builder()
                    .collapseKey(recipient + READ_RECEIPT_DELIVERY_REPORT_COLLAPSE_KEY)
                    .data(sender.bytesToString(data))
                    .startProcessingAt(startProcessingAt)
                    .validUntil(System.currentTimeMillis() + AlarmManager.INTERVAL_DAY * 30) //30 days
                    .surviveRestarts(true)
                    .maxRetries(Sendable.RETRY_FOREVER)
                    .build();
        }

        public void markMessageSeen(final String msgId) {
            Realm realm = Message.REALM(PairAppClient.this);
            try {
                Message message = Message.markMessageSeen(realm, msgId);
                if (message != null) {
                    sender.sendMessage(createReadReceiptSendable(message.getFrom(),
                            messagePacker.createMsgStatusMessage(message.getFrom(), msgId, false), System.currentTimeMillis()));
                }
            } finally {
                realm.close();
            }
        }


        public void markMessageDelivered(final String msgId) {
            Realm realm = Message.REALM(PairAppClient.this);
            try {
                Message message = Message.markMessageDelivered(realm, msgId);
                if (message != null) {
                    //we don't want to send the delivered when the user is in the chat room.
                    //that's too wasteful so wait for few seconds and if user does not scroll to it
                    //then continue
                    if (message.getFrom().equals(Config.getCurrentActivePeer())) {
                        delayAndNotifyIfNotMarkedAsSeen(msgId);
                    } else {
                        notifySenderMessageDelivered(message);
                    }
                }
            } finally {
                realm.close();
            }
        }

        private void delayAndNotifyIfNotMarkedAsSeen(final String msgId) {
            new Handler(WORKER_THREAD.getLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    Realm realm = Message.REALM(PairAppClient.this);
                    try {
                        Message message = realm.where(Message.class).equalTo(Message.FIELD_ID, msgId).findFirst();
                        if (message != null) {
                            if (message.getState() == Message.STATE_SEEN) return;
                            notifySenderMessageDelivered(message);
                        }
                    } finally {
                        realm.close();
                    }
                }
            }, 2000);
        }

        private void notifySenderMessageDelivered(Message message) {
            sender.sendMessage(createReadReceiptSendable(message.getFrom(),
                    messagePacker.createMsgStatusMessage(message.getFrom(), message.getId(), true), System.currentTimeMillis() + WAIT_MILLIS_DELIVERY_REPORT));
        }

        public void onMessageDelivered(String msgId) {
            Realm realm = Message.REALM(PairAppClient.this);
            try {
                Message.markMessageDelivered(realm, msgId);
            } finally {
                realm.close();
            }
        }

        public void onMessageSeen(String msgId) {
            Realm realm = Message.REALM(PairAppClient.this);
            try {
                Message.markMessageSeen(realm, msgId);
            } finally {
                realm.close();
            }
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
            bootClient();
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

        private void attemptShutDown() {
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
        private static final int SEND_MESSAGE = 0x0;
        private static final int SEND_BATCH = 0x01;
        private static final int SHUT_DOWN = 0x2;  /*PLAY_TONE = 0x5,*/
        private static final int CANCEL_DISPATCH = 0x6;

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
                    shutDown();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        //noinspection ConstantConditions
                        Looper.myLooper().quitSafely();
                    } else {
                        //noinspection ConstantConditions
                        Looper.myLooper().quit();
                    }
                    break;
                case CANCEL_DISPATCH:
                    Message message = (Message) msg.obj;
                    doCancelDispatch(message);
                    break;
                default:
                    throw new AssertionError("unknown signal");
            }
        }
    }

    private void doSendMessage(final Message message) {
        final Callable sendTask = new Callable<Void>() {
            @Override
            public Void call() {
                sendMessageInternal(message);
                return null;
            }
        };
        disPatchingThreads.put(message.getId(), TaskManager.executeNow(sendTask, true));
    }

    private void doSendMessages(Collection<Message> messages) {
        for (Message message : messages) {
            doSendMessage(message);
        }
    }

    private void doCancelDispatch(final Message message) {
        TaskManager.executeNow(new Runnable() {
            @Override
            public void run() {
                Future<?> future = disPatchingThreads.get(message.getId());
                if (future != null && !future.isDone()) {
                    future.cancel(true);
                }
                //we have to stop all notifications and progress report since the dispatcher will end up
                //cancelling the dispatch. we count on it!
                //notice that we don't rely on the future to be null before we cancel notifications
                //this is because we want to give user impression that the message has been cancelled.
                monitor.onDispatchFailed(message.getId(), MessageUtils.ERROR_CANCELLED);
                Realm realm = Message.REALM(PairAppClient.this);
                message.setState(Message.STATE_SEND_FAILED);
                Message liveMessage = realm.where(Message.class).equalTo(Message.FIELD_ID, message.getId()).findFirst();
                if (liveMessage != null && liveMessage.getState() == Message.STATE_PENDING) {
                    realm.beginTransaction();
                    liveMessage.setState(Message.STATE_SEND_FAILED);
                    realm.commitTransaction();
                }
                realm.close();
            }
        }, false);
    }

    @SuppressWarnings("SpellCheckingInspection")
    private static final EventBus postableBuz, listenableBus;


    static {
        postableBuz = EventBus.getBusOrCreate(PostableBusClazz.class);
        listenableBus = EventBus.getBusOrCreate(ListneableBusClazz.class);
    }

    @SuppressWarnings("SpellCheckingInspection")
    private static class ListneableBusClazz {

    }

    @SuppressWarnings("SpellCheckingInspection")
    private static class PostableBusClazz {

    }

    @SuppressWarnings("SpellCheckingInspection")
    static EventBus postableBus() {
        return postableBuz;
    }

    static EventBus listenableBus() {
        return listenableBus;
    }
}
