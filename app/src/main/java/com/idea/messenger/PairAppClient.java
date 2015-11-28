package com.idea.messenger;

import android.app.Activity;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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

import com.idea.Errors.PairappException;
import com.idea.data.Message;
import com.idea.data.UserManager;
import com.idea.net.ParseClient;
import com.idea.pairapp.BuildConfig;
import com.idea.pairapp.R;
import com.idea.ui.ChatActivity;
import com.idea.util.Config;
import com.idea.util.L;
import com.idea.util.LiveCenter;
import com.idea.util.PLog;
import com.idea.util.TaskManager;
import com.idea.util.ThreadUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;
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
    private static Dispatcher<Message> SOCKETSIO_DISPATCHER;
    private static AtomicBoolean isClientStarted = new AtomicBoolean(false);
    private static MessagesProvider messageProvider = new ParseMessageProvider();
    private static Stack<Activity> backStack = new Stack<>();
    private PairAppClientInterface INTERFACE;// = new PairAppClientInterface();
    private Dispatcher<Message> PARSE_MESSAGE_DISPATCHER;
    private WorkerThread WORKER_THREAD;
    private static Map<String, String> credentials;


    public static void startIfRequired(Context context) {
        if (!UserManager.getInstance().isUserVerified()) {
            L.w(TAG, " pariapp client wont start when a user is not logged in");
            return;
        }
        if (!isClientStarted.get()) {
            credentials = new HashMap<>();
            if (UserManager.getInstance().isUserVerified()) {
                Map<String, String> userCredentials = UserManager.getInstance().getUserCredentials();
                credentials.putAll(userCredentials);
                //////////////////////////////////////////////////////////////////////////////
                credentials.put(AbstractMessageDispatcher.KEY, "doTbKQlpZyNZohX7KPYGNQXIghATCx");
                credentials.put(AbstractMessageDispatcher.PASSWORD, "Dq8FLrF7HjeiyJBFGv9acNvOLV1Jqm");
                /////////////////////////////////////////////////////////////////////////////////////
            }
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

            TaskManager.executeNow(new Runnable() {
                @Override
                public void run() {
                    LiveCenter.stop();
                    MessageCenter.stopListeningForSocketMessages();
                    if (SOCKETSIO_DISPATCHER != null) {
                        SOCKETSIO_DISPATCHER.close();
                        SOCKETSIO_DISPATCHER = null;
                    }
                    NotificationManager.INSTANCE.reNotifyForReceivedMessages();
                }
            }, false);
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
                    NotificationManager.INSTANCE.clearAllMessageNotifications();
                    LiveCenter.start();
                    MessageCenter.startListeningForSocketMessages();
                }
            }, true);
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

    private static final Semaphore updateLock = new Semaphore(1, true);

    @Override
    public void onCreate() {
        super.onCreate();
        if (WORKER_THREAD == null || !WORKER_THREAD.isAlive()) {
            WORKER_THREAD = new WorkerThread();
            WORKER_THREAD.start();
        }
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

        PLog.i(TAG, "starting pairapp client");
//        if (intent != null && isClientStarted.get()) {
//            if (intent.getStringExtra(ACTION).equals(ACTION_SEND_ALL_UNSENT)) {
//                attemptToSendAllUnsentMessages();
//            }
//        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (UserManager.getInstance().isUserVerified()) {
//            if (intent != null && intent.getStringExtra(ACTION).equals(ACTION_SEND_ALL_UNSENT)) {
//                attemptToSendAllUnsentMessages();
//            }
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
        WORKER_THREAD.shutDown();
        super.onDestroy();
    }

    private synchronized void bootClient() {
        if (!isClientStarted.get()) {
            INTERFACE = new PairAppClientInterface();
            PARSE_MESSAGE_DISPATCHER = ParseDispatcher.getInstance(credentials,monitor);
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
            INTERFACE = null;
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
        TaskManager.execute(task, true);
    }

    private final Dispatcher.DispatcherMonitor monitor = new Dispatcher.DispatcherMonitor() {
        final Map<String, Pair<String, Integer>> progressMap = new ConcurrentHashMap<>();

        @Override
        public void onDispatchFailed(String reason, String messageId) {
            PLog.d(TAG, "message with id : %s dispatch failed with reason: " + reason, messageId);
            LiveCenter.releaseProgressTag(messageId);
            progressMap.remove(messageId);
            cancelNotification(messageId);
        }

        @Override
        public void onDispatchSucceed(String messageId) {
            PLog.d(TAG, "message with id : %s dispatched successfully", messageId);
            LiveCenter.releaseProgressTag(messageId);
            progressMap.remove(messageId);
            cancelNotification(messageId);
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
                    .setContentText(getString(R.string.loading))
                    .setSmallIcon(R.drawable.ic_stat_icon).build();
            NotificationManagerCompat manager = NotificationManagerCompat.from(self);// getSystemService(NOTIFICATION_SERVICE));
            manager.notify(id, not_id, notification);
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
                SOCKETSIO_DISPATCHER = SocketsIODispatcher.getInstance(credentials,monitor);
            }
            SOCKETSIO_DISPATCHER.dispatch(message);
        } else {
            PARSE_MESSAGE_DISPATCHER.dispatch(message);
        }
    }


    public static void sendFeedBack(final JSONObject report, final List<String> attachments) {
        TaskManager.execute(new Runnable() {
            @Override
            public void run() {
                ParseClient.sendFeedBack(report, attachments);
            }
        }, true);
    }

    public class PairAppClientInterface extends Binder {
        public void sendMessage(Message message) {
            WORKER_THREAD.sendMessage(message.isValid() ? Message.copy(message) : message); //detach the message from realm
        }

        @SuppressWarnings("unused")
        public void sendMessages(Collection<Message> tobeSent) {
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

        public void downloadAttachment(Message message) {
            if (message == null) {
                throw new IllegalArgumentException("message is null");
            }
            Worker.download(PairAppClient.this, message);
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
            bootClient();
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
                    shutDown();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        //noinspection ConstantConditions
                        Looper.myLooper().quitSafely();
                    } else {
                        //noinspection ConstantConditions
                        Looper.myLooper().quit();
                    }
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
            TaskManager.executeNow(sendTask, true);
        }

        private void doSendMessages(Collection<Message> messages) {
            for (Message message : messages) {
                doSendMessage(message);
            }
        }
    }

}
