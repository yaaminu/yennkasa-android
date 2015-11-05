package com.idea.messenger;

import android.app.AlarmManager;
import android.util.Log;

import com.idea.Errors.PairappException;
import com.idea.data.ContactsManager;
import com.idea.data.Message;
import com.idea.data.User;
import com.idea.data.UserManager;
import com.idea.data.util.MessageUtils;
import com.idea.net.FileApi;
import com.idea.net.FileClientException;
import com.idea.net.SmartFileMessageClient;
import com.idea.util.Config;
import com.idea.util.ConnectionUtils;
import com.idea.util.PLog;
import com.idea.util.ThreadUtils;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import io.realm.Realm;

/**
 * @author Null-Pointer on 8/29/2015.
 */
abstract class AbstractMessageDispatcher implements Dispatcher<Message> {
    public static final String TAG = AbstractMessageDispatcher.class.getSimpleName();

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
            //do nothing
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

    AbstractMessageDispatcher(Map<String, String> credentials) {
        this.file_service = //MessageFileClient.createInstance(Config.getMessageApiEndpoint(), credentials);//Config.getme
                new SmartFileMessageClient(credentials.get(KEY), credentials.get(PASSWORD), UserManager.getMainUserId());
    }

    private void uploadFileAndProceed(final Message message) {
        String messageBody = message.getMessageBody();

        if (messageBody.startsWith("http") || messageBody.startsWith("ftp")) { //we assume the file is uploaded
            proceedToSend(message);
        } else {
            final File actualFile = new File(messageBody);
            if (!actualFile.exists()) {
                onFailed(message.getId(), MessageUtils.ERROR_FILE_DOES_NOT_EXIST);
                return;
            }

            final ProgressListenerImpl listener = new ProgressListenerImpl(message);
            file_service.saveFileToBackend(actualFile, new FileApi.FileSaveCallback() {
                @Override
                public void done(FileClientException e, String locationUrl) {
                    if (e == null) {
                        message.setMessageBody(locationUrl); //do not persist this change.
                        proceedToSend(message);
                    } else {
                        onFailed(message.getId(), MessageUtils.ERROR_FILE_UPLOAD_FAILED);
                    }
                }
            }, listener);
        }
    }

    private void proceedToSend(final Message message) {
        //is this message to a group?
        if (UserManager.getInstance().isGroup(message.getTo())) {
            final Realm realm = User.Realm(Config.getApplicationContext());
            try {
                User user = realm.where(User.class).equalTo(User.FIELD_ID, message.getTo()).findFirst();
                if (user != null && user.getMembers().size() > 3) {
                    dispatchToGroup(message, User.aggregateUserIds(user.getMembers(), USER_FILTER));
                } else {
                    //give the user manager a hint to sync the members.
                    UserManager.getInstance().refreshUserDetails(message.getTo());
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
            }
            realm.commitTransaction();
        } finally {
            realm.close();
        }
        synchronized (monitors) {
            for (DispatcherMonitor monitor : monitors) {
                monitor.onDispatchFailed(reason, messageId);
            }
        }
    }

    protected final void onSent(String messageId) {
        Realm realm = Message.REALM(Config.getApplicationContext());
        try {
            Message message = realm.where(Message.class).equalTo(Message.FIELD_ID, messageId).findFirst();
            realm.beginTransaction();
            if (message != null && message.isValid()) {
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
                monitor.onDispatchSucceed(messageId);
            }
        }
    }

    protected final void onDelivered(String ourMessageId) {
        Realm realm = Message.REALM(Config.getApplicationContext());
        try {
            Message message = realm.where(Message.class).equalTo(Message.FIELD_ID, ourMessageId).findFirst();
            realm.beginTransaction();
            if (message != null && message.isValid()) {
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
        if (!ConnectionUtils.isConnectedOrConnecting()) {
            Log.w(TAG, "no internet connection, message can not be sent now");
            onFailed(message.getId(), MessageUtils.ERROR_NOT_CONNECTED);
            return;
        }
        try {
            MessageUtils.validate(message); //might throw
            //is the message a binary message?
            if (!Message.isTextMessage(message)) {
                //upload the file first before continuing
                uploadFileAndProceed(message);
            } else {
                proceedToSend(message);
            }
        } catch (PairappException e) {
            onFailed(message.getId(), e.getMessage());
        }
    }

    @Override
    public boolean cancelDispatchMayPossiblyFail(Message message) {
        //not implemented
        oops();
        return false;
    }

    private boolean oops() {
        throw new UnsupportedOperationException("unsupported");
    }

    @Override
    public final void addMonitor(DispatcherMonitor monitor) {
        if (monitor == null) {
            throw new IllegalArgumentException("monitor may not be null");
        }
        synchronized (monitors) {
            monitors.add(monitor);
        }
    }

    @Override
    public final void removeMonitor(DispatcherMonitor toBeRemoved) {
        if (toBeRemoved != null) {
            synchronized (monitors) {
                monitors.remove(toBeRemoved);
            }
        }
    }

    @Override
    public void close() {
        //subclasses should override this if the need to free any resource
        synchronized (monitors) {
            monitors.clear();
        }
    }

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
