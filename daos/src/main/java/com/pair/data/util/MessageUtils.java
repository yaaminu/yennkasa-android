package com.pair.data.util;

import android.content.Context;
import android.util.Log;

import com.pair.Errors.PairappException;
import com.pair.data.Message;
import com.pair.data.R;
import com.pair.util.Config;

import org.apache.commons.io.FileUtils;

import java.io.File;

/**
 * @author Null-Pointer on 8/27/2015.
 */
public class MessageUtils {
    private static final String TAG = MessageUtils.class.getSimpleName();

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

    public static boolean validate(Message message) throws PairappException {

        if (message == null) {
            throw new IllegalArgumentException("null message!"); //we wont report this
        }

        if (message.getType() == Message.TYPE_DATE_MESSAGE) {
            final String msg = "attempted to send a date message,but will not be sent";
            Log.w(TAG, msg);
            throw new PairappException(msg, ERROR_IS_DATE_MESSAGE);
        }
        if (message.getType() == Message.TYPE_TYPING_MESSAGE) {
            final String msg = "attempted to send a typing message,but will not be sent";
            Log.w(TAG, msg);
            throw new PairappException(msg, ERROR_IS_TYPING_MESSAGE);
        }

        if (message.getType() != Message.TYPE_TEXT_MESSAGE) { //is it a binary message?
            if (message.getMessageBody().startsWith("file://") && !new File(message.getMessageBody()).exists()) {
                String msg = "error: " + message.getMessageBody() + " is not a valid file path";
                Log.w(TAG, msg);
                throw new PairappException(msg, ERROR_ATTACHMENT_TOO_LARGE);

            } else { //file exists lets check the size
                if (new File(message.getMessageBody()).length() > FileUtils.ONE_MB * 8) {//larger
                    final String msg = "error: " + message.getMessageBody() + " is too large. max allowed size is  8MB";
                    Log.w(TAG, msg);
                    throw new PairappException(msg, ERROR_ATTACHMENT_TOO_LARGE);
                }
            }
        } else { //text message
            if (message.getMessageBody().getBytes().length > FileUtils.ONE_KB * 3) { //3KB so that we can accommodate other fields which stays pretty constant
                final String msg = "error " + message.getMessageBody() + " is too large. max allowed size is than 4KB";
                Log.w(TAG, msg);
                throw new PairappException(msg, ERROR_MESSAGE_BODY_TOO_LARGE);
            }
        }
        if ((message.getState() != Message.STATE_PENDING) && (message.getState() != Message.STATE_SEND_FAILED)) {
            final String msg = "attempted to send a sent message, but will not be sent";
            Log.w(TAG, msg);
            throw new PairappException(msg, ERROR_MESSAGE_ALREADY_SENT);
        }
        return true;
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
}
