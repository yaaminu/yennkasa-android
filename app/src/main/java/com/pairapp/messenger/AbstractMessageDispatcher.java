package com.pairapp.messenger;

import android.app.AlarmManager;
import android.content.SharedPreferences;
import android.util.Log;

import com.pairapp.Errors.PairappException;
import com.pairapp.data.ContactsManager;
import com.pairapp.data.Message;
import com.pairapp.data.User;
import com.pairapp.data.UserManager;
import com.pairapp.data.util.MessageUtils;
import com.pairapp.net.FileApi;
import com.pairapp.net.FileClientException;
import com.pairapp.net.SmartFileMessageClient;
import com.pairapp.util.Config;
import com.pairapp.util.ConnectionUtils;
import com.pairapp.util.PLog;
import com.pairapp.util.ThreadUtils;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

import io.realm.Realm;

/**
 * @author Null-Pointer on 8/29/2015.
 */
abstract class AbstractMessageDispatcher implements Dispatcher<Message> {
    public static final String TAG = AbstractMessageDispatcher.class.getSimpleName();
    public static final String UPLOAD_CACHE = "uploadCache" + TAG;

    private static final ContactsManager.Filter<User> USER_FILTER = new ContactsManager.Filter<User>() {
        @Override
        public boolean accept(User user) {
            return !UserManager.getInstance().isCurrentUser(user.getUserId());
        }
    };
    protected static final String ERR_USER_OFFLINE = "user offline";

    private class ProgressListenerImpl implements FileApi.ProgressListener {

        private final Message message;

        ProgressListenerImpl(Message message) {
            this.message = message;
        }

        @Override
        public void onProgress(long expected, long processed) {
            final int progress = (int) ((100 * processed) / expected);
            PLog.d(TAG, "progress : %s", progress + "%");
            synchronized (monitors) {
                for (DispatcherMonitor monitor : monitors) {
                    monitor.onProgress(this.message.getId(), progress, 100);
                }
            }
        }
    }

    public static final String KEY = "key";
    public static final String PASSWORD = "password";
    private final Set<DispatcherMonitor> monitors = new HashSet<>();
    private final FileApi file_service;
    private Timer timer = new Timer(TAG, false);

    AbstractMessageDispatcher(Map<String, String> credentials, DispatcherMonitor monitor) {
        if (credentials == null || monitor == null) {
            throw new IllegalArgumentException("invalid args");
        }
        this.file_service = //MessageFileClient.createInstance(Config.getMessageApiEndpoint(), credentials);//Config.getme
                new SmartFileMessageClient(credentials.get(KEY), credentials.get(PASSWORD), UserManager.getMainUserId());
        synchronized (monitors) {
            monitors.add(monitor);
        }
    }

    private void uploadFileAndProceed(final Message message) throws DispatchCancelledException {
        String messageBody = message.getMessageBody();

        if (messageBody.startsWith("http") || messageBody.startsWith("ftp")) { //we assume the file is uploaded
            proceedToSend(message);
        } else {
            final File actualFile = new File(messageBody);
            if (!actualFile.exists()) {
                onFailed(message.getId(), MessageUtils.ERROR_FILE_DOES_NOT_EXIST);
                return;
            }

            final String isAlreadyUploaded = actualFile.lastModified() + actualFile.getAbsolutePath();
            final SharedPreferences preferences = Config.getPreferences(UPLOAD_CACHE);
            String uri = preferences.getString(isAlreadyUploaded, null);
            if (uri != null) {
                PLog.d(TAG, "not uploading file at path: %s, because it's already uploaded");
                message.setMessageBody(uri);
                proceedToSend(message);
            } else {
                final ProgressListenerImpl listener = new ProgressListenerImpl(message);
                if (checkIfCancelled()) {
                    PLog.d(TAG, "message with id: %s cancelled", message.getId());
                    throw new DispatchCancelledException();
                }
                file_service.saveFileToBackend(actualFile, new FileApi.FileSaveCallback() {
                    @Override
                    public void done(FileClientException e, String locationUrl) {
                        if (e == null) {
                            //cache the uri... all these bullshit will be a thing of the past once we add job manager
                            preferences.edit().putString(isAlreadyUploaded, locationUrl).apply();
                            if (checkIfCancelled()) {
                                PLog.d(TAG, "message with id: %s cancelled", message.getId());
                                onFailed(message.getId(), MessageUtils.ERROR_CANCELLED);
                                return;
                            }
                            message.setMessageBody(locationUrl); //do not persist this change.
                            proceedToSend(message);
                        } else {
                            if (checkIfCancelled()) {
                                PLog.d(TAG, "message with id: %s cancelled", message.getId());
                                onFailed(message.getId(), MessageUtils.ERROR_CANCELLED);
                            } else {
                                onFailed(message.getId(), MessageUtils.ERROR_FILE_UPLOAD_FAILED);
                            }
                        }
                    }
                }, listener);
            }
        }
    }

    private void proceedToSend(final Message message) {
        PLog.d(TAG, "dispatching message: " + message.getMessageBody()
                + " from " + message.getFrom()
                + " to " + message.getTo());
        //is this message to a group?
        if (Message.isGroupMessage(message)) {
            final Realm realm = User.Realm(Config.getApplicationContext());
            try {
                User user = realm.where(User.class).equalTo(User.FIELD_ID, message.getTo()).findFirst();
                if (user != null && user.getMembers().size() > 1) {
                    dispatchToGroup(message, User.aggregateUserIds(user.getMembers(), USER_FILTER));
                } else {
                    //give the user manager a hint to sync the members.
                    UserManager.getInstance().fetchUserIfRequired(realm, message.getTo(), true);
                    //we are going to run some minutes later hoping that the members are 'sync'ed'.
                    timer.schedule(new SendLaterTimerTask(message), AlarmManager.INTERVAL_FIFTEEN_MINUTES / 3);
                }
            } finally {
                realm.close();
            }
        } else { //to a single user
            dispatchToUser(message);
        }
    }

    /**
     * @param message the message whose dispatch failed
     * @param reason  reason the dispatch failed
     * @deprecated use {@link #onFailed(String, String)}rather
     */
    @SuppressWarnings("unused")
    @Deprecated
    protected final void onFailed(Message message, String reason) {
        onFailed(message.getId(), reason);
    }

    /**
     * reports a failed dispatch
     *
     * @param messageId the id of the message whose dispatch failed
     * @param reason    reason the dispatch failed
     */
    protected final void onFailed(String messageId, String reason) {
        Realm realm = Message.REALM(Config.getApplicationContext());
        try {
            Message realmMessage = realm.where(Message.class).equalTo(Message.FIELD_ID, messageId).findFirst();
            realm.beginTransaction();
            if (realmMessage != null && realmMessage.isValid()) {
                if (realmMessage.getState() == Message.STATE_PENDING) {
                    realmMessage.setState(Message.STATE_SEND_FAILED);
                }
                File file = new File(realmMessage.getMessageBody());
                if (file.exists()) {
                    SharedPreferences prefs = Config.getPreferences(UPLOAD_CACHE);
                    prefs.edit().remove(file.lastModified() + file.getAbsolutePath()).apply();
                }
            }
            realm.commitTransaction();
        } finally {
            realm.close();
        }
        synchronized (monitors) {
            for (DispatcherMonitor monitor : monitors) {
                monitor.onDispatchFailed(messageId,reason);
            }
        }
    }

    protected final void onSent(String messageId) {
        Realm realm = Message.REALM(Config.getApplicationContext());
        try {
            Message message = realm.where(Message.class).equalTo(Message.FIELD_ID, messageId).findFirst();
            realm.beginTransaction();
            if (message != null && message.isValid() && !Message.isGroupMessage(message)) {
                int state = message.getState();
                if (state == Message.STATE_PENDING || state == Message.STATE_SEND_FAILED) {
                    message.setState(Message.STATE_SENT);
                }
            }
            realm.commitTransaction();
        } finally {
            realm.close();
        }
        synchronized (monitors) {
            for (DispatcherMonitor monitor : monitors) {
                monitor.onDispatchSucceeded(messageId);
            }
        }
    }

    protected final void onDelivered(String ourMessageId) {
        Realm realm = Message.REALM(Config.getApplicationContext());
        try {
            Message message = realm.where(Message.class).equalTo(Message.FIELD_ID, ourMessageId).findFirst();
            realm.beginTransaction();
            if (message != null && message.isValid() && !Message.isGroupMessage(message)) {
                if (message.getState() != Message.STATE_SEEN) {
                    message.setState(Message.STATE_RECEIVED);
                }
            }
            realm.commitTransaction();
        } finally {
            realm.close();
        }
    }


    @SuppressWarnings("unused")
    protected final void onAllSent() {
        synchronized (monitors) {
            for (DispatcherMonitor monitor : monitors) {
                monitor.onAllDispatched();
            }
        }
    }


    @Override
    public final void dispatch(Collection<Message> messages) {
        ThreadUtils.ensureNotMain();
        for (Message message : messages) {
            dispatch(message);
        }
    }

    @Override
    public final void dispatch(Message message) {
        ThreadUtils.ensureNotMain();
        if (!ConnectionUtils.isConnectedOrConnecting() && !Message.isTextMessage(message)) {
            Log.w(TAG, "no internet connection, message can not be sent now");
            onFailed(message.getId(), MessageUtils.ERROR_NOT_CONNECTED);
            return;
        }
        disPatchingThreads.put(message.getId(), Thread.currentThread());
        try {
            doDispatch(message);
        } catch (DispatchCancelledException e) {
            onFailed(message.getId(), MessageUtils.ERROR_CANCELLED);
        } finally {
            disPatchingThreads.remove(message.getId());
        }
    }

    private void doDispatch(Message message) throws DispatchCancelledException {
        if (checkIfCancelled()) {
            PLog.d(TAG, "message with id: %s cancelled", message.getId());
            throw new DispatchCancelledException();
        }
        try {
            MessageUtils.validate(message); //might throw
            //is the message a binary message?
            if (!Message.isTextMessage(message)) {
                //upload the file first before continuing
                if (checkIfCancelled()) {
                    PLog.d(TAG, "message with id: %s cancelled", message.getId());
                    throw new DispatchCancelledException();
                }
                uploadFileAndProceed(message);
            } else {
                proceedToSend(message);
            }
        } catch (PairappException e) {
            onFailed(message.getId(), e.getMessage());
        }
    }

    private static class DispatchCancelledException extends Exception {
        public DispatchCancelledException(String message) {
            super(message);
        }

        public DispatchCancelledException(String message, Throwable cause) {
            super(message, cause);
        }

        public DispatchCancelledException() {
            super();
        }
    }

    private final Map<String, Thread> disPatchingThreads = new ConcurrentHashMap<>();

    private static boolean checkIfCancelled() {
        return Thread.currentThread().isInterrupted();
    }

    @Override
    public final boolean cancelDispatchMayFail(Message message) {
        Thread t = disPatchingThreads.get(message.getId());
        if (t != null && t.isAlive() && !Message.isTextMessage(message)) {
            t.interrupt();
            return true;
        }
        return false;
    }

    private boolean oops() {
        throw new UnsupportedOperationException("unsupported");
    }

    @Override
    public final void registerMonitor(DispatcherMonitor monitor) {
        if (monitor == null) {
            throw new IllegalArgumentException("monitor may not be null");
        }
        synchronized (monitors) {
            monitors.add(monitor);
        }
    }

    @Override
    public final void unRegisterMonitor(DispatcherMonitor toBeRemoved) {
        if (toBeRemoved != null) {
            synchronized (monitors) {
                monitors.remove(toBeRemoved);
            }
        }
    }


    @Override
    public final void close() {
        if (doClose()) {
            synchronized (monitors) {
                monitors.clear();
            }
            disPatchingThreads.clear();
        }
    }

    //subclasses should override this to free any resource
    protected abstract boolean doClose();

    protected abstract void dispatchToGroup(Message message, List<String> members);

    protected abstract void dispatchToUser(Message message);

    private class SendLaterTimerTask extends TimerTask {
        final Message message;

        public SendLaterTimerTask(Message message) {
            this.message = message;
        }

        @Override
        public void run() {
            final Realm realm = User.Realm(Config.getApplicationContext());
            User user = realm.where(User.class).equalTo(User.FIELD_ID, message.getTo()).findFirst();
            if (user != null && user.getMembers().size() >= 2) {
                dispatchToGroup(message, User.aggregateUserIds(user.getMembers(), USER_FILTER));
            } else {
                onFailed(message.getId(), MessageUtils.ERROR_RECIPIENT_NOT_FOUND);
            }
            realm.close();
        }
    }
}
