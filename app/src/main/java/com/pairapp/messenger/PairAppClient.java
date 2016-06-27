package com.pairapp.messenger;

import android.app.Activity;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.util.Pair;
import android.util.Log;

import com.pairapp.BuildConfig;
import com.pairapp.Errors.PairappException;
import com.pairapp.R;
import com.pairapp.data.Message;
import com.pairapp.data.UserManager;
import com.pairapp.data.util.MessageUtils;
import com.pairapp.net.ParseClient;
import com.pairapp.net.ParseFileClient;
import com.pairapp.net.sockets.PairappSocket;
import com.pairapp.ui.ChatActivity;
import com.pairapp.util.Config;
import com.pairapp.util.LiveCenter;
import com.pairapp.util.PLog;
import com.pairapp.util.TaskManager;
import com.pairapp.util.ThreadUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

import io.realm.Realm;
import io.realm.RealmResults;


public class PairAppClient extends Service {
    // FIXME: 6/16/2015 improve how we stop background task
    public static final String TAG = PairAppClient.class.getSimpleName();
    public static final String ACTION_SEND_ALL_UNSENT = "send unsent messages";
    public static final String ACTION = "action";
    public static final String UPDATE_KEY = PairAppClient.class.getName() + "updateKey";
    static final String VERSION = "version";
    public static final int not_id = 10987;
    static final int notId = 10983;
    public static final String ACTION_SEND_MESSAGE = "sendMessage", ACTION_CANCEL_DISPATCH = "cancelMessage";
    public static final String EXTRA_MESSAGE = "message";
    private static AtomicBoolean isClientStarted = new AtomicBoolean(false);
    private static Stack<Activity> backStack = new Stack<>();
    private PairAppClientInterface INTERFACE;
    private WorkerThread WORKER_THREAD;
    private WebSocketDispatcher webSocketDispatcher;

    private final Map<String, Future<?>> disPatchingThreads = new ConcurrentHashMap<>();

    private MediaPlayer mediaPlayer;
    private final Object playerLock = new Object();


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
            // FIXME: 6/21/2016 implement this
            PLog.d(TAG, "marking user as offline");
            LiveCenter.stop();
        }
    }

    public static void markUserAsOnline(Activity activity) {
        ThreadUtils.ensureMain();
        ensureUserLoggedIn();
        if (activity == null) {
            throw new IllegalArgumentException();
        }
        if (backStack.isEmpty()) {
            TaskManager.executeNow(new Runnable() {
                @Override
                public void run() {
                    // TODO: 6/21/2016 implement this
                    Log.d(TAG, "marking user as online");
                    LiveCenter.start();
                }
            }, true);
        }
        backStack.add(activity);
    }

    private static void ensureUserLoggedIn() {
        if (!UserManager.getInstance().isUserVerified()) {
            throw new IllegalStateException("no user logged in");
        }
    }

    private static final Semaphore updateLock = new Semaphore(1, true);

    @Override
    public void onCreate() {
        super.onCreate();
        PLog.i(TAG, "starting pairapp client");
        if (WORKER_THREAD != null && WORKER_THREAD.isAlive()) {
            WORKER_THREAD.quit();
        }

        WORKER_THREAD = new WorkerThread();
        WORKER_THREAD.start();
        if (!updateLock.tryAcquire()) {
            PLog.d(TAG, "update lock held, deferring update check from prefs");
            return;
        }
        try {
            final SharedPreferences applicationWidePrefs = Config.getApplicationWidePrefs();
            String updateJson = applicationWidePrefs.getString(UPDATE_KEY, null);
            if (updateJson != null) {
                try {
                    final JSONObject object = new JSONObject(updateJson);
                    int versionCode = object.getInt("versionCode");
                    if (versionCode > BuildConfig.VERSION_CODE) {
                        TaskManager.execute(new Runnable() {
                            public void run() {
                                try {
                                    notifyUpdateAvailable(object);
                                } catch (JSONException e) {
                                    PLog.d(TAG, "error while trying to deserialize update data");
                                    applicationWidePrefs.edit().remove(UPDATE_KEY).apply();
                                }
                            }
                        }, false);
                    } else {
                        applicationWidePrefs.edit().remove(UPDATE_KEY).apply();
                    }
                } catch (JSONException e) {
                    PLog.d(TAG, "error while trying to deserialize update data");
                    applicationWidePrefs.edit().remove(UPDATE_KEY).apply();
                }
            }
        } finally {
            updateLock.release();
        }
    }

    static void notifyUpdateAvailable(JSONObject data1) throws JSONException {
        ThreadUtils.ensureNotMain();
        try {
            updateLock.acquire();
            final String version = data1.getString(PairAppClient.VERSION);
            final int versionCode = data1.getInt("versionCode");
            if (versionCode > BuildConfig.VERSION_CODE) {
                PLog.i(TAG, "update available, latest version: %s", version);
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(data1.getString("link")));
                final Context applicationContext = Config.getApplicationContext();
                Notification notification = new NotificationCompat.Builder(applicationContext)
                        .setContentTitle(applicationContext.getString(R.string.update_available))
                        .setContentIntent(PendingIntent.getActivity(applicationContext, 1003, intent, PendingIntent.FLAG_UPDATE_CURRENT))
                        .setSubText(applicationContext.getString(R.string.download_update))
                        .setSmallIcon(R.drawable.ic_stat_icon).build();
                NotificationManagerCompat manager = NotificationManagerCompat.from(applicationContext);// getSystemService(NOTIFICATION_SERVICE));
                manager.notify("update" + TAG, PairAppClient.notId, notification);
                SharedPreferences preferences = Config.getApplicationWidePrefs();
                final String savedUpdate = preferences.getString(PairAppClient.UPDATE_KEY, null);
                if (savedUpdate != null) {
                    JSONObject object = new JSONObject(savedUpdate);
                    if (object.optInt("versionCode", versionCode) >= versionCode) {
                        PLog.d(TAG, "push for update arrived late");
                        return;
                    }
                }
                preferences.edit().putString(PairAppClient.UPDATE_KEY, data1.toString()).apply();
            } else {
                PLog.d(TAG, "client up to date");
            }
        } catch (InterruptedException e) {
            PLog.d(TAG, "itterupted while wating to acquire update lock");
        } finally {
            updateLock.release();
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

        if (intent != null) {
            String action = intent.getStringExtra(ACTION);
            if (action == null) {
                action = intent.getAction();
            }

            if (action != null && isClientStarted.get()) {
                if (ACTION_SEND_MESSAGE.equals(action)) {
                    String messageJson = intent.getStringExtra(EXTRA_MESSAGE);
                    final Message message = Message.fromJSON(messageJson);
                    doSendMessage(message);
                } else if (ACTION_CANCEL_DISPATCH.equals(action)) {
                    String messageJson = intent.getStringExtra(EXTRA_MESSAGE);
                    final Message message = Message.fromJSON(messageJson);
                    INTERFACE.cancelDisPatch(message);
                }
            }
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (UserManager.getInstance().isUserVerified()) {
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
        WORKER_THREAD.attemptShutDown();
        synchronized (playerLock) {
            if (mediaPlayer != null) {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
                mediaPlayer = null;
            }
        }
        super.onDestroy();
    }

    private PairappSocket pairappSocket;

    private WebSocketDispatcher.MessageCodec messageCodec = new WebSocketDispatcher.MessageCodec() {
        @Override
        public byte[] encode(Message message) {

            byte[] msgBuffer = Message.toJSON(message).getBytes();
            ByteBuffer byteBuffer = ByteBuffer.allocate(msgBuffer.length +
                    (Message.isGroupMessage(message) ? message.getTo().getBytes().length : 10));
            byteBuffer.order(ByteOrder.BIG_ENDIAN);
            byteBuffer.put((byte) 4);
            if (Message.isGroupMessage(message)) {
                byteBuffer.put(message.getTo().getBytes());
            } else {
                byteBuffer.putDouble(Double.parseDouble(message.getTo()));
            }
            byteBuffer.put((byte) '-');
            byteBuffer.put(msgBuffer);
            return byteBuffer.array();
        }

        @Override
        public Message decode(byte[] bytes) {
            int i = 0;
            while (bytes[i++] != '-') ;
            byte[] sliced = new byte[bytes.length - i];
            System.arraycopy(bytes, i, sliced, 0, sliced.length);
            return Message.fromJSON(new String(sliced));
        }
    };
    private final PairappSocket.MessageParser parser = new PairappSocket.MessageParser() {
        @Override
        public void feed(byte[] bytes) {
            Message message = Message.fromJSON(new String(bytes));
            Intent intent = new Intent(PairAppClient.this, MessageProcessor.class);
            intent.putExtra(MessageProcessor.MESSAGE, Message.toJSON(message));
            startService(intent);
        }
    };

    private synchronized void bootClient() {
        ThreadUtils.ensureNotMain();
        if (!isClientStarted.get()) {
            INTERFACE = new PairAppClientInterface();
            String authToken = UserManager.getInstance().getCurrentUserAuthToken();
            Map<String, String> opts = new HashMap<>(1);
            opts.put("Authorization", authToken);
            pairappSocket = PairappSocket.create(opts, parser);
            pairappSocket.init();
            webSocketDispatcher = WebSocketDispatcher.create(new ParseFileClient(), monitor, pairappSocket, messageCodec);
            isClientStarted.set(true);
        }
    }


    private synchronized void shutDown() {
        ThreadUtils.ensureNotMain();
        if (isClientStarted.get()) {
            // FIXME: 6/21/2016 disconnect from server
            webSocketDispatcher.close();
            pairappSocket.disConnectBlocking();
            PLog.i(TAG, TAG + ": bye");
            INTERFACE = null;
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

    private final Dispatcher.DispatcherMonitor monitor = new Dispatcher.DispatcherMonitor() {
        final Map<String, Pair<String, Integer>> progressMap = new ConcurrentHashMap<>();

        @Override
        public void onDispatchFailed(String messageId, String reason) {
            PLog.d(TAG, "message with id : %s dispatch failed with reason: " + reason, messageId);
            LiveCenter.releaseProgressTag(messageId);
            progressMap.remove(messageId);
            // FIXME: 6/21/2016 leak detected. not freeing futures stored in dispatchinthreads
            cancelNotification(messageId);
        }

        @Override
        public void onDispatchSucceeded(String messageId) {
            // FIXME: 6/21/2016 leak detected. not freeing futures stored in dispatchinthreads
            PLog.d(TAG, "message with id : %s dispatched successfully", messageId);
            LiveCenter.releaseProgressTag(messageId);
            progressMap.remove(messageId);
            cancelNotification(messageId);
            Realm realm = Message.REALM(PairAppClient.this);
            Message message = realm.where(Message.class).equalTo(Message.FIELD_ID, messageId).findFirst();
            if (message != null
                    && Config.getCurrentActivePeer().equals(message.getTo())
                    && UserManager.getInstance().getBoolPref(UserManager.IN_APP_NOTIFICATIONS, true)) {
                playSound();
            }
            realm.close();
        }

        private void cancelNotification(String messageId) {
            NotificationManagerCompat manager = NotificationManagerCompat.from(PairAppClient.this);// get
            manager.cancel(messageId, not_id);
        }

        @Override
        public void onProgress(String id, int progress, int max) {
            LiveCenter.updateProgress(id, progress);
            Pair<String, Integer> previousProgress = progressMap.get(id);
            if (previousProgress != null && previousProgress.first != null && previousProgress.second != null) {
                if (previousProgress.second >= progress) {
                    PLog.d(TAG, "late progress report");
                    return;
                }
            } else {
                Realm messageRealm = Message.REALM(PairAppClient.this);
                Message message = messageRealm.where(Message.class).equalTo(Message.FIELD_ID, id).findFirst();
                if (message == null) {
                    return;
                }
                previousProgress = new Pair<>(message.getTo(), progress);
                progressMap.put(id, previousProgress);
                messageRealm.close();
            }

            PairAppClient self = PairAppClient.this;
            Intent intent = new Intent(self, ChatActivity.class);
            intent.putExtra(ChatActivity.EXTRA_PEER_ID, previousProgress.first);
            Notification notification = new NotificationCompat.Builder(self)
                    .setContentTitle(getString(R.string.upload_progress))
                    .setProgress(100, 1, true)
                    .setContentIntent(PendingIntent.getActivity(self, 1003, intent, PendingIntent.FLAG_UPDATE_CURRENT))
                    .setContentText(getString(R.string.uploading))
                    .setSmallIcon(R.drawable.ic_stat_icon).build();
            NotificationManagerCompat manager = NotificationManagerCompat.from(self);// getSystemService(NOTIFICATION_SERVICE));
            manager.notify(id, not_id, notification);
        }

        @Override
        public void onAllDispatched() {

        }
    };

    AtomicBoolean shouldPlaySound = new AtomicBoolean(true);

    private void playSound() {
        if (shouldPlaySound.getAndSet(false)) { //// FIXME: 6/21/2016 why main thread?
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    shouldPlaySound.set(true);
                }
            }, 1500);
            synchronized (playerLock) {
                try {
                    if (mediaPlayer == null) {
                        mediaPlayer = new MediaPlayer();
                    } else if (mediaPlayer.isPlaying()) {
                        mediaPlayer.stop();
                    }
                    mediaPlayer.reset();
                    AssetFileDescriptor fd = getResources().openRawResourceFd(R.raw.beep);
                    mediaPlayer.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
                    mediaPlayer.prepare();
                    fd.close();
                    mediaPlayer.setLooping(false);
                    mediaPlayer.setVolume(1f, 1f);
                    mediaPlayer.start();
                } catch (IOException e) {
                    PLog.d(TAG, "failed to play sound");
                }
            }
        } else {
            PLog.d(TAG, "not playing sound, played one not too lon ago");
        }
    }

    private void sendMessageInternal(Message message) {
        if (!Message.isTextMessage(message)) { // FIXME: 6/21/2016 why not check for all kinds of messages?
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

    public static void notifyMessageSeen(final Message message) {
        final Message tmp = Message.copy(message);
        TaskManager.executeNow(new Runnable() {
            @Override
            public void run() {
                if (tmp.getState() != Message.STATE_SEEN && !Message.isGroupMessage(tmp)) {
                    if (!isClientStarted.get()) {
                        PLog.d(TAG, "client not stared cann't send seen status");
                    }
                    if (Message.isGroupMessage(tmp)) {
                        PLog.d(TAG, "message status of group message are not sent");
                        return;
                    }
                    if (tmp.getState() == Message.STATE_SEEN) {
                        PLog.d(TAG, "message is seen");
                        if (BuildConfig.DEBUG) {
                            throw new RuntimeException();
                        }
                        return;
                    }
                    MessageCenter.notifyMessageSeen(tmp);
                    Realm realm = Message.REALM(Config.getApplicationContext());
                    Message realmMessage = realm.where(Message.class).equalTo(Message.FIELD_ID, tmp.getId()).findFirst();
                    if (realmMessage != null) {
                        realm.beginTransaction();
                        realmMessage.setState(Message.STATE_SEEN);
                        realm.commitTransaction();
                    }
                    realm.close();
                }
            }
        }, false);

    }

    public class PairAppClientInterface extends Binder {
        public void sendMessage(Message message) {
            WORKER_THREAD.sendMessage(Message.copy(message)); //detach the message from realm
        }

        @SuppressWarnings("unused")
        public void sendMessages(Collection<Message> tobeSent) {
            WORKER_THREAD.sendMessages(Message.copy(tobeSent)); //detach from realm
        }

        public void cancelDisPatch(Message message) {
            if (!Message.isTextMessage(message)) {
                if (!Message.isOutGoing(message)) {
                    throw new IllegalArgumentException("only outgoing messages may be cancelled!");
                }
                doCancelDispatch(Message.copy(message));
            } else {
                oops();
            }
        }

        public void registerUINotifier(final Notifier notifier) {
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

        public void sendMessages(Collection<Message> messages) {
            if (checkStarted()) {
                android.os.Message msg = android.os.Message.obtain();
                msg.obj = messages;
                msg.what = MessageHandler.SEND_BATCH;
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
                //notice that we dont rely on the future to be null before we cancel notifications
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

}
