package com.pairapp.messenger;

import android.util.Log;

import com.pairapp.Errors.PairappException;
import com.pairapp.data.Message;
import com.pairapp.data.User;
import com.pairapp.data.util.MessageUtils;
import com.pairapp.net.FileApi;
import com.pairapp.net.FileClientException;
import com.pairapp.util.Config;
import com.pairapp.util.ConnectionUtils;
import com.pairapp.util.FileUtils;
import com.pairapp.util.GenericUtils;
import com.pairapp.util.PLog;
import com.pairapp.util.ThreadUtils;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.realm.Realm;

/**
 * @author Null-Pointer on 8/29/2015.
 */
@SuppressWarnings("ALL")
abstract class AbstractMessageDispatcher implements Dispatcher<Message> {
    public static final String TAG = AbstractMessageDispatcher.class.getSimpleName();

    private class ProgressListenerImpl implements FileApi.ProgressListener {

        private final Message message;

        ProgressListenerImpl(Message message) {
            this.message = message;
        }

        @Override
        public void onProgress(long expected, long processed) {
            ThreadUtils.ensureNotMain();
            final int progress = (int) ((100 * processed) / expected);
            PLog.d(TAG, "progress : %s", progress + "%");
            synchronized (monitors) {
                for (DispatcherMonitor monitor : monitors) {
                    monitor.onProgress(this.message.getId(), progress, 100);
                }
            }
        }
    }

    private final Set<DispatcherMonitor> monitors = new HashSet<>();
    private final FileApi file_service;


    AbstractMessageDispatcher(FileApi fileApi, DispatcherMonitor monitor) {
        GenericUtils.ensureNotNull(monitor, "monitor == null");
        this.file_service = fileApi;
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

            final ProgressListenerImpl listener = new ProgressListenerImpl(message);
            if (checkIfCancelled()) {
                PLog.d(TAG, "message with id: %s cancelled", message.getId());
                throw new DispatchCancelledException();
            }
            try {
                File tmpFile = new File(Config.getTempDir(),
                        FileUtils.hashFile(actualFile));
                org.apache.commons.io.FileUtils.copyFile(actualFile, tmpFile);
                file_service.saveFileToBackend(actualFile, new FileApi.FileSaveCallback() {
                    @Override
                    public void done(FileClientException e, String locationUrl) {
                        if (e == null) {
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
            } catch (IOException e) {
                onFailed(message.getId(), MessageUtils.ERROR_FILE_UPLOAD_FAILED);
            }
        }
    }
//    }

    private void proceedToSend(final Message message) {
        PLog.d(TAG, "dispatching message: " + message.getMessageBody()
                + " from " + message.getFrom()
                + " to " + message.getTo());
        //is this message to a group?
        Realm userRealm = User.Realm(Config.getApplicationContext());
        try {
            if (Message.isGroupMessage(userRealm, message)) {
                dispatchToGroup(message);
            } else { //to a single user
                dispatchToUser(message);
            }
        } finally {
            userRealm.close();
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
                monitor.onDispatchFailed(messageId, reason);
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
                monitor.onDispatchSucceeded(messageId);
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
        if (!ConnectionUtils.isConnectedOrConnecting() && message.hasAttachment()) {
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
            if (message.hasAttachment()) {
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
        if (t != null && t.isAlive() && message.hasAttachment()) {
            t.interrupt();
            return true;
        }
        return false;
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

    protected abstract void dispatchToGroup(Message message);

    protected abstract void dispatchToUser(Message message);
}
