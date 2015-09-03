package com.pair.messenger;

import android.util.Log;

import com.pair.Config;
import com.pair.Exceptions.PairappException;
import com.pair.data.ContactsManager;
import com.pair.data.Message;
import com.pair.data.User;
import com.pair.data.UserManager;
import com.pair.net.FileApi;
import com.pair.parse_client.ParseClient;
import com.pair.util.ConnectionUtils;
import com.pair.util.L;
import com.pair.util.ThreadUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.realm.Realm;

/**
 * @author Null-Pointer on 8/29/2015.
 */
abstract class AbstractMessageDispatcher implements Dispatcher<Message> {
    public static final String TAG = AbstractMessageDispatcher.class.getSimpleName();

    private final List<DispatcherMonitor> monitors = new ArrayList<>();
    private final FileApi file_service;

    AbstractMessageDispatcher() {
        this.file_service = ParseClient.getInstance();
    }

    private void uploadFileAndProceed(final Message messageFile, FileApi.ProgressListener listener) {
        final File actualFile = new File(messageFile.getMessageBody());
        //if file does not exist, we error will be thrown...
        try {
            file_service.saveFileToBackend(actualFile, new FileApi.FileSaveCallback() {
                @Override
                public void done(Exception e, String locationUrl) {
                    if (e == null) {
                        messageFile.setMessageBody(locationUrl); //do not persist this change.
                        proceedToSend(messageFile);
                    } else {
                        onFailed(messageFile, MessagingUtils.ERROR_FILE_UPLOAD_FAILED);
                    }
                }
            }, listener);
        } catch (IOException e) {
            L.e(TAG, e.getMessage());
            onFailed(messageFile, MessagingUtils.ERROR_FILE_UPLOAD_FAILED);
        }
    }

    private void proceedToSend(Message message) {
        //is this message to a group?
        if (UserManager.getInstance().isGroup(message.getTo())) {
            Realm realm = User.Realm(Config.getApplicationContext());
            try {
                User user = realm.where(User.class).equalTo(User.FIELD_ID, message.getTo()).findFirst();
                if (user != null) {
                    List<String> members = User.aggregateUserIds(user.getMembers(), new ContactsManager.Filter<User>() {
                        @Override
                        public boolean accept(User user) {
                            return !UserManager.getInstance().isCurrentUser(user.getUserId());
                        }
                    });
                    if (members.size() < 2) {
                        //let the user manager sync the members, flag the message as unsent so that
                        //it will be retried as soon as possible
                        UserManager.getInstance().refreshGroup(message.getTo());
                        onFailed(message, MessagingUtils.ERROR_MEMBERS_NOT_SYNCED);
                        return;
                    }
                    dispatchToGroup(message, members);
                } else {
                    onFailed(message, MessagingUtils.ERROR_RECIPIENT_NOT_FOUND);
                }
            } finally {
                realm.close();
            }
        } else { //to a single user
            dispatchToUser(message);
        }
    }


    protected final void onFailed(Message message, String reason) {
        Realm realm = Message.REALM(Config.getApplicationContext());
        Message realmMessage = realm.where(Message.class).equalTo(Message.FIELD_ID, message.getId()).findFirst();
        if (realmMessage != null) {
            realm.beginTransaction();
            realmMessage.setState(Message.STATE_SEND_FAILED);
            realm.commitTransaction();
        }
        realm.close();
        synchronized (monitors) {
            for (DispatcherMonitor monitor : monitors) {
                monitor.onSendFailed(reason, message.getId());
            }
        }
    }

    protected final void onSent(String messageId) {
        Realm realm = Message.REALM(Config.getApplicationContext());
        try {
            Message message = realm.where(Message.class).equalTo(Message.FIELD_ID, messageId).findFirst();
            if (message != null) {
                realm.beginTransaction();
                message.setState(Message.STATE_SENT);
                realm.commitTransaction();
            }
        } finally {
            realm.close();
        }
        synchronized (monitors) {
            for (DispatcherMonitor monitor : monitors) {
                monitor.onSendSucceeded(messageId);
            }
        }
    }

    protected void onDelivered(String ourMessageId) {
        Realm realm = Message.REALM(Config.getApplicationContext());
        try {
            Message message = realm.where(Message.class).equalTo(Message.FIELD_ID, ourMessageId).findFirst();
            if (message != null) {
                realm.beginTransaction();
                message.setState(Message.STATE_RECEIVED);
                realm.commitTransaction();
            }
        } finally {
            realm.close();
        }
    }

    protected final void onAllSent() {
        synchronized (monitors) {
            for (DispatcherMonitor monitor : monitors) {
                monitor.onAllDispatched();
            }
        }
    }

    @Override
    public final void dispatch(Message message) {
        ThreadUtils.ensureNotMain();
        dispatch(message, DUMMY_LISTENER);
    }

    @Override
    public final void dispatch(Collection<Message> messages) {
        ThreadUtils.ensureNotMain();
        dispatch(messages, DUMMY_LISTENER);
    }

    @Override
    public final void dispatch(Message message, FileApi.ProgressListener listener) {
        ThreadUtils.ensureNotMain();
        if (!ConnectionUtils.isConnectedOrConnecting()) {
            Log.w(TAG, "no internet connection, message can not be sent now");
            onFailed(message, MessagingUtils.ERROR_NOT_CONNECTED);
            return;
        }
        try {
            MessagingUtils.validate(message); //might throw
            //is the message a binary message?
            if (!Message.isTextMessage(message)) {
                //upload the file first before continuing
                uploadFileAndProceed(message, listener);
            } else {
                proceedToSend(message);
            }
        } catch (PairappException e) {
            onFailed(message, e.getMessage());
        }
    }

    @Override
    public final void dispatch(Collection<Message> messages, FileApi.ProgressListener listener) {
        ThreadUtils.ensureNotMain();
        for (Message message : messages) {
            dispatch(message, listener);
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
        this.monitors.clear();
    }

    protected abstract void dispatchToGroup(Message message, List<String> members);

    protected abstract void dispatchToUser(Message message);


    protected static final FileApi.ProgressListener DUMMY_LISTENER = new FileApi.ProgressListener() {
        @Override
        public void onProgress(int percentComplete) {
            //do nothing
            L.d(TAG, "dummy progress listener: " + percentComplete);
        }
    };

    protected static final ExecutorService WORKER = Executors.newCachedThreadPool();
}
