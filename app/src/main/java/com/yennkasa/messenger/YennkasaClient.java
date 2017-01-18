package com.yennkasa.messenger;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.util.Pair;

import com.yennkasa.BuildConfig;
import com.yennkasa.Errors.YennkasaException;
import com.yennkasa.call.CallController;
import com.yennkasa.call.CallManager;
import com.yennkasa.data.Message;
import com.yennkasa.data.User;
import com.yennkasa.data.UserManager;
import com.yennkasa.data.util.MessageUtils;
import com.yennkasa.net.ParseClient;
import com.yennkasa.net.ParseFileClient;
import com.yennkasa.net.sockets.MessageParser;
import com.yennkasa.net.sockets.SenderImpl;
import com.yennkasa.security.MessageEncryptor;
import com.yennkasa.ui.BaseCallActivity;
import com.yennkasa.util.Config;
import com.yennkasa.util.Event;
import com.yennkasa.util.EventBus;
import com.yennkasa.util.FileUtils;
import com.yennkasa.util.LiveCenter;
import com.yennkasa.util.PLog;
import com.yennkasa.util.TaskManager;
import com.yennkasa.util.ThreadUtils;

import org.json.JSONObject;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import io.realm.Realm;
import vc908.stickerfactory.StickersManager;

import static com.yennkasa.call.CallController.CALL_PUSH_PAYLOAD;
import static com.yennkasa.call.CallController.ON_CALL_CONNECTING;
import static com.yennkasa.call.CallController.ON_CALL_ESTABLISHED;
import static com.yennkasa.call.CallController.ON_CALL_MUTED;
import static com.yennkasa.call.CallController.ON_CALL_PROGRESSING;
import static com.yennkasa.call.CallController.ON_CAL_ENDED;
import static com.yennkasa.call.CallController.ON_CAL_ERROR;
import static com.yennkasa.call.CallController.ON_IN_COMING_CALL;
import static com.yennkasa.call.CallController.ON_LOUD_SPEAKER;
import static com.yennkasa.call.CallController.VIDEO_CALL_LOCAL_VIEW;
import static com.yennkasa.call.CallController.VIDEO_CALL_REMOTE_VIEW;
import static com.yennkasa.call.CallController.VIDEO_CALL_VIEW;
import static com.yennkasa.messenger.MessengerBus.ANSWER_CALL;
import static com.yennkasa.messenger.MessengerBus.CANCEL_MESSAGE_DISPATCH;
import static com.yennkasa.messenger.MessengerBus.CLEAR_NEW_MESSAGE_NOTIFICATION;
import static com.yennkasa.messenger.MessengerBus.EDIT_SENT_MESSAGE;
import static com.yennkasa.messenger.MessengerBus.ENABLE_SPEAKER;
import static com.yennkasa.messenger.MessengerBus.GET_STATUS_MANAGER;
import static com.yennkasa.messenger.MessengerBus.HANG_UP_CALL;
import static com.yennkasa.messenger.MessengerBus.MESSAGE_EDIT_RESULTS;
import static com.yennkasa.messenger.MessengerBus.MESSAGE_PUSH_INCOMING;
import static com.yennkasa.messenger.MessengerBus.MESSAGE_RECEIVED;
import static com.yennkasa.messenger.MessengerBus.MESSAGE_REVERT_RESULTS;
import static com.yennkasa.messenger.MessengerBus.MESSAGE_SEEN;
import static com.yennkasa.messenger.MessengerBus.MUTE_CALL;
import static com.yennkasa.messenger.MessengerBus.NOT_TYPING;
import static com.yennkasa.messenger.MessengerBus.OFFLINE;
import static com.yennkasa.messenger.MessengerBus.ONLINE;
import static com.yennkasa.messenger.MessengerBus.ON_CALL_PUSH_PAYLOAD_RECEIVED;
import static com.yennkasa.messenger.MessengerBus.ON_MESSAGE_DELIVERED;
import static com.yennkasa.messenger.MessengerBus.ON_MESSAGE_SEEN;
import static com.yennkasa.messenger.MessengerBus.REVERT_SENDING;
import static com.yennkasa.messenger.MessengerBus.SEND_MESSAGE;
import static com.yennkasa.messenger.MessengerBus.START_MONITORING_USER;
import static com.yennkasa.messenger.MessengerBus.STOP_MONITORING_USER;
import static com.yennkasa.messenger.MessengerBus.SWITCH_CAMERA;
import static com.yennkasa.messenger.MessengerBus.TYPING;
import static com.yennkasa.messenger.MessengerBus.VIDEO_CALL_USER;
import static com.yennkasa.messenger.MessengerBus.VOICE_CALL_USER;


public class YennkasaClient extends Service {
    public static final String TAG = YennkasaClient.class.getSimpleName();
    static final String VERSION = "version";
    static final int notId = 10983;
    private static AtomicBoolean isClientStarted = new AtomicBoolean(false);
    private WorkerThread WORKER_THREAD;
    private WebSocketDispatcher webSocketDispatcher;
    private MessagePacker messagePacker;
    private final Map<String, Future<?>> disPatchingThreads = new ConcurrentHashMap<>();
    private Dispatcher.DispatcherMonitor monitor;
    @SuppressWarnings("FieldCanBeLocal")
    private StatusManager statusManager;
    @SuppressWarnings("FieldCanBeLocal")
    private PairAppClientEventsListener eventsListener;
    @SuppressWarnings("FieldCanBeLocal")
    private IncomingMessageProcessor incomingMessageProcessor; //field is required to outlive its scope
    private SenderImpl sender;
    private CallController callController;
    private final EventBus callManagerBus = EventBus.getBusOrCreate(shadowClazz.class);
    private MessageParser messageParser;

    private static class shadowClazz {
    }

    public static void startIfRequired(Context context) {
        Realm realm = User.Realm(context);
        try {
            if (!UserManager.getInstance().isUserVerified(realm)) {
                PLog.w(TAG, " pariapp client wont start when a user is not logged in");
                return;
            }
            if (!isClientStarted.get()) {
                Intent pairAppClient = new Intent(context, YennkasaClient.class);
                context.startService(pairAppClient);
            }
        } finally {
            realm.close();
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

    static void ensureUserLoggedIn(Realm userRealm) {
        if (!UserManager.getInstance().isUserVerified(userRealm)) {
            throw new IllegalStateException("no user logged in");
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        PLog.i(TAG, "starting yennkasa client");
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
        Realm userRealm = User.Realm(this);
        try {
            if (!UserManager.getInstance().isUserVerified(userRealm)) {
                PLog.f(TAG, " pariapp client wont start when a user is not logged in");
                stopSelf();
                return START_NOT_STICKY;
            }
            if (intent != null && MessengerBus.HANG_UP_CALL.equals(intent.getAction())) { // TODO: 7/25/2016 find a better way to do this
                postableBus().post(Event.create(MessengerBus.HANG_UP_CALL, null, intent.getParcelableExtra(BaseCallActivity.EXTRA_CALL_DATA)));
            }
            return START_STICKY;
        } finally {
            userRealm.close();
        }
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

    @Override
    public void onTrimMemory(int level) {
        PrivatePublicKeySourceImpl.getInstance().onTrimMemory(level);
        super.onTrimMemory(level);
    }

    synchronized void bootClient() {
        ThreadUtils.ensureNotMain();
        if (!isClientStarted.get()) {
            StickersManager.initialize("fd82531004986458d9d4fd66eacf9e20", this, BuildConfig.DEBUG);
            Realm realm = User.Realm(this);
            try {
                StickersManager.setUser(FileUtils.sha1(UserManager.getMainUserId(realm)),
                        Collections.singletonMap("version", String.valueOf(BuildConfig.VERSION_NAME)));
            } finally {
                realm.close();
            }
            Realm userRealm = User.Realm(this);
            try {
                monitor = new DispatcherMonitorImpl(this, disPatchingThreads);
                String currentUserId = UserManager.getMainUserId(userRealm);
                MessageEncryptor encryptor = new MessageEncryptor(new PrivatePublicKeySourceImpl(), BuildConfig.DEBUG);
                messagePacker = MessagePacker.create(currentUserId, new ZlibCompressor(),
                        new CryptoImpl(encryptor));

                messageParser = new MessageParserImpl(messagePacker);
                sender = new SenderImpl(authenticator, messageParser, new Handler(Looper.myLooper()));
                statusManager = StatusManager.create(sender, messagePacker, listenableBus());
                webSocketDispatcher = WebSocketDispatcher.create(new ParseFileClient(), monitor, sender,
                        new MessageEncoderImpl(messagePacker), encryptor);

                sender.start(); //this must always come after initialising websocketdispatcher.

                incomingMessageProcessor = new IncomingMessageProcessor(statusManager, postableBus());
                messagePacker.observe().subscribe(incomingMessageProcessor);

                callController = CallManager.create(getApplication(),
                        currentUserId, callManagerBus,
                        registrationTokenSource,
                        BuildConfig.DEBUG);
                callController.setup();

                eventsListener = new PairAppClientEventsListener(new PairAppClientInterface(this, callController, sender, messagePacker,
                        statusManager, WORKER_THREAD.handler, messageParser));

                callManagerBus.register(eventsListener,
                        ON_CALL_CONNECTING,
                        ON_CAL_ERROR, ON_IN_COMING_CALL, CALL_PUSH_PAYLOAD,
                        ON_CAL_ENDED, ON_CALL_ESTABLISHED, VIDEO_CALL_LOCAL_VIEW, VIDEO_CALL_REMOTE_VIEW,
                        ON_CALL_PROGRESSING, ON_CALL_MUTED, ON_LOUD_SPEAKER, SWITCH_CAMERA, VIDEO_CALL_VIEW);

                EventBus.getDefault().register(UserManager.ACTION_SEND_MESSAGE, eventsListener);
                postableBus().register(eventsListener, OFFLINE, ONLINE, NOT_TYPING, TYPING,
                        STOP_MONITORING_USER, START_MONITORING_USER,
                        MESSAGE_RECEIVED, MESSAGE_SEEN,
                        ON_MESSAGE_DELIVERED, ON_MESSAGE_SEEN,
                        SEND_MESSAGE, CANCEL_MESSAGE_DISPATCH, GET_STATUS_MANAGER, CLEAR_NEW_MESSAGE_NOTIFICATION,
                        REVERT_SENDING, EDIT_SENT_MESSAGE, MESSAGE_REVERT_RESULTS, MESSAGE_EDIT_RESULTS,
                        VOICE_CALL_USER, VIDEO_CALL_USER, HANG_UP_CALL, ANSWER_CALL, ENABLE_SPEAKER, MUTE_CALL,
                        MESSAGE_PUSH_INCOMING, ON_CALL_PUSH_PAYLOAD_RECEIVED);
                isClientStarted.set(true);
                NotificationManager.INSTANCE.reNotifyForReceivedMessages(this, currentUserId);
            } finally {
                userRealm.close();
            }
        }
    }

    private static final SenderImpl.Authenticator authenticator = new SenderImpl.Authenticator() {
        @NonNull
        @Override
        public String getToken() {
            return UserManager.getInstance().getCurrentUserAuthToken();
        }

        @NonNull
        @Override
        public String requestNewToken() throws YennkasaException {
            Realm realm = User.Realm(Config.getApplicationContext());
            try {
                return UserManager.getInstance().getNewAuthTokenSync(realm);
            } finally {
                realm.close();
            }
        }
    };

    private synchronized void shutDown() {
        ThreadUtils.ensureNotMain();
        if (isClientStarted.get()) {
            sender.shutdownSafely();
            EventBus.resetBus(ListneableBusClazz.class);
            EventBus.resetBus(PostableBusClazz.class);
            EventBus.resetBus(shadowClazz.class);
            EventBus.getDefault().unregister(UserManager.ACTION_SEND_MESSAGE, eventsListener);
            messagePacker.close();
            webSocketDispatcher.unRegisterMonitor(monitor);
            webSocketDispatcher.close();
            callController.shutDown();
            isClientStarted.set(false);
            PLog.i(TAG, TAG + ": bye");
            return;
        }
        PLog.w(TAG, "shutting down yennkasa client when it is already shut down");
    }

    private void sendMessageInternal(Message message) {
        Realm realm = Message.REALM(this);
        try {
            String messageId = message.getId();
            Message tmp = realm.where(Message.class).equalTo(Message.FIELD_ID, message.getId()).findFirst();
            if (tmp == null) {
                PLog.d(TAG, "failed to send message, message either canceled or deleted by user");
                monitor.onDispatchFailed(messageId, MessageUtils.ERROR_CANCELLED);
                return;
            }
        } finally {
            realm.close();
        }
        if (message.hasAttachment()) {
            try {
                LiveCenter.acquireProgressTag(message.getId());
            } catch (YennkasaException e) {
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

    private final class WorkerThread extends HandlerThread {
        Handler handler;

        public WorkerThread() {
            super(TAG, NORM_PRIORITY);
        }

        @Override
        protected void onLooperPrepared() {
            handler = new MessageHandler(getLooper());
            bootClient();
        }


        private void attemptShutDown() {
            android.os.Message message = android.os.Message.obtain();
            message.what = MessageHandler.SHUT_DOWN;
            handler.sendMessage(message);
        }
    }

    class MessageHandler extends Handler {
        static final int SEND_MESSAGE = 0x0;
        static final int SEND_BATCH = 0x01;
        static final int SHUT_DOWN = 0x2;  /*PLAY_TONE = 0x5,*/
        static final int CANCEL_DISPATCH = 0x6;

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
        disPatchingThreads.put(message.getId(), TaskManager.execute(sendTask, true));
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
                Realm realm = Message.REALM(YennkasaClient.this);
                try {
                    message.setState(Message.STATE_SEND_FAILED);
                    Message liveMessage = realm.where(Message.class).equalTo(Message.FIELD_ID, message.getId()).findFirst();
                    if (liveMessage != null && liveMessage.getState() == Message.STATE_PENDING) {
                        realm.beginTransaction();
                        liveMessage.setState(Message.STATE_SEND_FAILED);
                        realm.commitTransaction();
                    }
                } finally {
                    realm.close();
                }
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

    static final CallManager.RegistrationTokenSource registrationTokenSource = new CallManager.RegistrationTokenSource() {
        @Override
        public Pair<String, Long> getSinchRegistrationToken() {
            return UserManager.getInstance().getSinchRegistrationToken();
        }
    };

    private static class CryptoImpl implements MessagePacker.Cryptor {

        private final MessageEncryptor encryptor;

        public CryptoImpl(MessageEncryptor encryptor) {
            this.encryptor = encryptor;
        }

        @NonNull
        @Override
        public byte[] encrypt(@NonNull String recipient, @NonNull byte[] input) throws MessageEncryptor.EncryptionException {
            return encryptor.encrypt(recipient, input);
        }

        @NonNull
        @Override
        public byte[] decrypt(@NonNull byte[] input) throws MessageEncryptor.EncryptionException {
            return encryptor.decrypt(input);
        }

    }

}
