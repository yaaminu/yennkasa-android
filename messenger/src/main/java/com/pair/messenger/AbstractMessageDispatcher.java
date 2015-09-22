package com.pair.messenger;

import android.util.Log;

import com.pair.Errors.PairappException;
import com.pair.data.ContactsManager;
import com.pair.data.Message;
import com.pair.data.User;
import com.pair.data.UserManager;
import com.pair.data.net.FileApi;
import com.pair.data.util.MessageUtils;
import com.pair.parse_client.ParseClient;
import com.pair.util.Config;
import com.pair.util.ConnectionUtils;
import com.pair.util.L;
import com.pair.util.ThreadUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import io.realm.Realm;

/**
 * @author Null-Pointer on 8/29/2015.
 */
abstract class AbstractMessageDispatcher implements Dispatcher<Message> {
    public static final String TAG = AbstractMessageDispatcher.class.getSimpleName();
    protected static final String ERR_USER_OFFLINE = "user offline";
    private final List<DispatcherMonitor> monitors = new ArrayList<>();
    private final FileApi file_service;

    AbstractMessageDispatcher() {
        this.file_service = ParseClient.getInstance();
    }

    private void uploadFileAndProceed(final Message message, FileApi.ProgressListener listener) {
        String messageBody = message.getMessageBody();

        if (messageBody.startsWith("http") || messageBody.startsWith("ftp")) { //we assume the file is uploaded
            proceedToSend(message);
        } else {
            final File actualFile = new File(messageBody);
            if (!actualFile.exists()) {
                onFailed(message.getId(), MessageUtils.ERROR_FILE_DOES_NOT_EXIST);
                return;
            }

            file_service.saveFileToBackend(actualFile, new FileApi.FileSaveCallback() {
                @Override
                public void done(Exception e, String locationUrl) {
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
                        //give the user manager a hint to sync the members.
                        UserManager.getInstance().refreshGroup(message.getTo());
//                        onFailed(message.getId(), MessageUtils.ERROR_MEMBERS_NOT_SYNCED);
//                        return;
                    }
                    dispatchToGroup(message, members);
                } else {
                    onFailed(message.getId(), MessageUtils.ERROR_RECIPIENT_NOT_FOUND);
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
            if (realmMessage != null) {
                if (realmMessage.getState() == Message.STATE_PENDING) {
                    realm.beginTransaction();
                    updateMessageStatus(realmMessage, Message.STATE_PENDING);
                    realm.commitTransaction();
                }
            }
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
            if (message != null) {
                int state = message.getState();
                if (state == Message.STATE_PENDING || state == Message.STATE_SEND_FAILED) {
                    realm.beginTransaction();
                    updateMessageStatus(message, Message.STATE_SENT);
                    realm.commitTransaction();
                }
            }
        } finally {
            realm.close();
        }
        synchronized (monitors) {
            for (DispatcherMonitor monitor : monitors) {
                monitor.onDispatchSucceed(messageId);
            }
        }
    }

    protected void onDelivered(String ourMessageId) {
        Realm realm = Message.REALM(Config.getApplicationContext());
        try {
            Message message = realm.where(Message.class).equalTo(Message.FIELD_ID, ourMessageId).findFirst();
            if (message != null) {
                realm.beginTransaction();
                updateMessageStatus(message, Message.STATE_RECEIVED);
                realm.commitTransaction();
            }
        } finally {
            realm.close();
        }
    }

    private void updateMessageStatus(Message message, int state) {
        if (message.isValid()) {
            message.setState(state);
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
            onFailed(message.getId(), MessageUtils.ERROR_NOT_CONNECTED);
            return;
        }
        //this pattern is not strict it only checks if it starts with http or ftp
        //even fttp will pass this test but am making a stupid assumption that we
        // will not receive such an input.
//        Pattern httpOrFtpPattern = Pattern.compile("^([hf]t{1,2}p)");
        try {
            MessageUtils.validate(message); //might throw
            //is the message a binary message?
            if (!Message.isTextMessage(message)) {
                //upload the file first before continuing
                uploadFileAndProceed(message, listener);
            } else {
                proceedToSend(message);
            }
        } catch (PairappException e) {
            onFailed(message.getId(), e.getMessage());
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
}
