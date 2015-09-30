package com.pair.data.util;

import android.content.Context;

import com.pair.Errors.PairappException;
import com.pair.data.Message;
import com.pair.data.R;
import com.pair.util.Config;
import com.pair.util.PLog;
import com.pair.util.TaskManager;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;

import io.realm.Realm;

/**
 * @author Null-Pointer on 8/27/2015.
 */
public class MessageUtils {
    public static final String ERROR_FILE_UPLOAD_FAILED = "fileUploadFailed";
    public static final String ERROR_FILE_DOES_NOT_EXIST = "fileNotExist";
    public static final String ERROR_MEMBERS_NOT_SYNCED = "membersNotSynced";
    public static final String ERROR_INVALID_MESSAGE = "invalid_message";
    public static final String ERROR_NOT_CONNECTED = "notConnected";
    public static final String ERROR_UNKNOWN = "unknown";
    public static final String ERROR_RECIPIENT_NOT_FOUND = "recipient not found";
    public static final String ERROR_ATTACHMENT_TOO_LARGE = "fileTooLarge";
    public static final String ERROR_MESSAGE_BODY_TOO_LARGE = "message too large";
    public static final String ERROR_MESSAGE_ALREADY_SENT = "message sent";
    public static final String ERROR_IS_DATE_MESSAGE = "is date message";
    public static final String ERROR_IS_TYPING_MESSAGE = "is typing message";
    private static final String TAG = MessageUtils.class.getSimpleName();

    public static boolean validate(Message message) throws PairappException {

        if (message == null) {
            throw new IllegalArgumentException("null message!"); //we wont report this
        }

        if (message.getType() == Message.TYPE_DATE_MESSAGE) {
            final String msg = "attempted to send a date message,but will not be sent";
            PLog.w(TAG, msg);
            throw new PairappException(msg, ERROR_IS_DATE_MESSAGE);
        }
        if (message.getType() == Message.TYPE_TYPING_MESSAGE) {
            final String msg = "attempted to send a typing message,but will not be sent";
            PLog.w(TAG, msg);
            throw new PairappException(msg, ERROR_IS_TYPING_MESSAGE);
        }

        if (message.getType() != Message.TYPE_TEXT_MESSAGE) { //is it a binary message?
            if (message.getMessageBody().startsWith("file://") && !new File(message.getMessageBody()).exists()) {
                String msg = "error: " + message.getMessageBody() + " is not a valid file path";
                PLog.w(TAG, msg);
                throw new PairappException(msg, ERROR_ATTACHMENT_TOO_LARGE);

            } else { //file exists lets check the size
                if (new File(message.getMessageBody()).length() > FileUtils.ONE_MB * 8) {//larger
                    final String msg = "error: " + message.getMessageBody() + " is too large. max allowed size is  8MB";
                    PLog.w(TAG, msg);
                    throw new PairappException(msg, ERROR_ATTACHMENT_TOO_LARGE);
                }
            }
        } else { //text message
            if (message.getMessageBody().getBytes().length > FileUtils.ONE_KB * 3) { //3KB so that we can accommodate other fields which stays pretty constant
                final String msg = "error " + message.getMessageBody() + " is too large. max allowed size is than 4KB";
                PLog.w(TAG, msg);
                throw new PairappException(msg, ERROR_MESSAGE_BODY_TOO_LARGE);
            }
        }
        if ((message.getState() != Message.STATE_PENDING) && (message.getState() != Message.STATE_SEND_FAILED)) {
            final String msg = "attempted to send a sent message, but will not be sent";
            PLog.w(TAG, msg);
            throw new PairappException(msg, ERROR_MESSAGE_ALREADY_SENT);
        }
        return true;
    }

    public static void download(final Message realmMessage, final Callback callback) {
        final WeakReference<Callback> callbackWeakReference = new WeakReference<>(callback);

        final Message message = Message.copy(realmMessage); //detach from realm
        final String messageId = message.getId(),
                messageBody = message.getMessageBody();
        TaskManager.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
                        Realm realm = null;
                        final File finalFile;
                        String destination = messageBody.substring(messageBody.lastIndexOf('/'));

                        switch (message.getType()) {
                            case Message.TYPE_VIDEO_MESSAGE:
                                finalFile = new File(Config.getAppVidMediaBaseDir(), destination);
                                break;
                            case Message.TYPE_PICTURE_MESSAGE:
                                finalFile = new File(Config.getAppImgMediaBaseDir(), destination);
                                break;
                            case Message.TYPE_BIN_MESSAGE:
                                finalFile = new File(Config.getAppBinFilesBaseDir(), destination);
                                break;
                            default:
                                throw new AssertionError("should never happen");
                        }
                        try {
                            com.pair.util.FileUtils.save(finalFile, messageBody);
                            realm = Message.REALM(Config.getApplicationContext());
                            realm.beginTransaction();
                            Message toBeUpdated = realm.where(Message.class).equalTo(Message.FIELD_ID, messageId).findFirst();
                            toBeUpdated.setMessageBody(finalFile.getAbsolutePath());
                            realm.commitTransaction();
                            onComplete(null);
                        } catch (IOException e) {
                            PLog.e(TAG, e.getMessage(), e.getCause());
                            onComplete(e);
                        } finally {
                            if (realm != null) {
                                realm.close();
                            }
                        }
                    }

                    private void onComplete(final Exception error) {
                        if (callbackWeakReference.get() != null) {
                            TaskManager.executeOnMainThread(new Runnable() {
                                @Override
                                public void run() {
                                    callbackWeakReference.get().onDownloaded(error,message.getId());
                                }
                            });
                        }
                    }
                });
    }

    public static String getDescription(int type) {
        Context context = Config.getApplicationContext();
        switch (type) {
            case Message.TYPE_BIN_MESSAGE:
                return context.getString(R.string.File);
            case Message.TYPE_VIDEO_MESSAGE:
                return context.getString(R.string.Video);
            case Message.TYPE_PICTURE_MESSAGE:
                //fall through
            default:
                return context.getString(R.string.Image);
        }
    }

    public interface Callback {
        void onDownloaded(Exception e,String messageId);
    }
}
