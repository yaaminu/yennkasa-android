package com.pairapp.data;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.text.format.DateUtils;

import com.pairapp.Errors.PairappException;
import com.pairapp.data.util.MessageUtils;
import com.pairapp.util.Config;
import com.pairapp.util.FileUtils;
import com.pairapp.util.GenericUtils;
import com.pairapp.util.PLog;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.realm.Realm;
import io.realm.RealmConfiguration;
import io.realm.RealmObject;
import io.realm.annotations.Index;
import io.realm.annotations.PrimaryKey;
import io.realm.annotations.RealmClass;

/**
 * this class represents a particular message sent by a given {@link User}.
 * it is normally used in conjunction with {@link Conversation}
 * the message may be attached to {@link Realm} or not.
 * <p/>
 * one can detach the message from realm by using its {@link #copy} method
 * and using the returned message.
 *
 * @author Null-Pointer on 5/26/2015.
 */
@RealmClass
public class Message extends RealmObject {

    public static final int STATE_PENDING = 0x3e9,
            STATE_SENT = 0x3ea,
            STATE_RECEIVED = 0x3eb,
            STATE_SEEN = 0x3ec,
            STATE_SEND_FAILED = 0x3ed;

    /***********************************
     * the order of these fields i.e. TYPE_* is relevant to some components for sorting purposes
     * make sure you don't change the order
     */
    public static final int TYPE_TEXT_MESSAGE = 0x3ee, //don't touch me!
            TYPE_BIN_MESSAGE = 0x3ef,  //don't touch me!
            TYPE_PICTURE_MESSAGE = 0x3f0,  //don't touch me!
            TYPE_VIDEO_MESSAGE = 0x3f1, //don't touch me!
            TYPE_CALL = 0x3f2, //don't touch me!
            TYPE_TYPING_MESSAGE = 0x3f3, //don't touch me!
            TYPE_DATE_MESSAGE = 0x3f4, //don't touch me!
            TYPE_LOG_MESSAGE = 0x3f5,//don't touch me!,
            TYPE_STICKER = 0x3f6;//don't touch me!

    public static final String FIELD_ID = "id",
            FIELD_FROM = "from",
            FIELD_TO = "to",
            FIELD_TYPE = "type",
            FIELD_STATE = "state",
            FIELD_DATE_COMPOSED = "dateComposed",
            FIELD_MESSAGE_BODY = "messageBody",
            FIELD_CALL_BODY = "callBody";
    public static final String MSG_STS_STATUS = "status";
    public static final String MSG_STS_MESSAGE_ID = "messageId";
    private static final String TAG = Message.class.getSimpleName();
    private final static Object idLock = new Object();
    @PrimaryKey
    private String id;
    @Index
    private String from; //sender's id
    @Index
    private String to; //recipient's id
    private String messageBody;
    private long dateComposed;
    private int state;
    private int type;

    private CallBody callBody;
    private String attachmentSize;

    /**
     * you are strongly advised to use the factory {@link Message#makeNew}
     * and its siblings instead
     */
    public Message() {
    } //required no-arg c'tor;

    /**
     * @return a new id that is likely unique.there is no guarantee that this will be unique
     */
    private static String generateIdPossiblyUnique(Realm realm, String mainUserId, String to) {
        //before changing how we generatePublicPrivateKeyPair ids make sure all components relying on this behaviour are updated
        //eg: MessageActivity (the name might have changed)
        if (to == null) {
            throw new IllegalArgumentException("to == null");
        }
        String id;
        synchronized (idLock) {
            long count = realm.where(Message.class).equalTo(FIELD_TO, to).count() + 1;
            id = count + "@" + mainUserId + "@" + to + "@" + System.currentTimeMillis();
        }
        PLog.i(TAG, "generated message id: " + id);
        return id;
    }

    /**
     * this is the best way of acquiring a realm for working with a {@link Message}
     *
     * @param context the current context
     * @return a {@link Realm that is guaranteed to work with messages all the time}
     */
    @SuppressWarnings("UnusedParameters")
    public static Realm REALM(Context context) {
        return REALM();
    }

    public static Realm REALM() {
        return Realm.getInstance(config/*, UserManager.getKey()*/);
    }

    // FIXME: 1/14/2016 add key
    private static final RealmConfiguration config;

    static {
        File file = Config.getApplicationContext().getDir("data", Context.MODE_PRIVATE);
        config = new RealmConfiguration.Builder().directory(file)
                .name("messagestore.realm")
                .schemaVersion(0)
                .deleteRealmIfMigrationNeeded().build();
    }

    public static boolean isTextMessage(Message message) {
        return message.getType() == TYPE_TEXT_MESSAGE;
    }

    public static boolean isBinMessage(Message message) {
        return message.getType() == TYPE_BIN_MESSAGE;
    }

    public static boolean isPictureMessage(Message message) {
        return message.getType() == TYPE_PICTURE_MESSAGE;
    }

    public static boolean isVideoMessage(Message message) {
        return message.getType() == TYPE_VIDEO_MESSAGE;
    }

    public static boolean isDateMessage(Message message) {
        return message.getType() == TYPE_DATE_MESSAGE;
    }

    public static boolean isTypingMessage(Message message) {
        return message.getType() == TYPE_TYPING_MESSAGE;
    }

    public static boolean isIncoming(Realm userRealm, Message message) {
        return !isOutGoing(userRealm, message);
    }

    public static boolean isOutGoing(Realm userRealm, Message message) {
        return UserManager.getInstance().isCurrentUser(userRealm, message.getFrom());
    }

    /**
     * creates a new message. callers must ensure they are in
     * realm transaction.
     *
     * @param theRealm the realm to use in creating the message
     * @param callBody the body of the call. the message type will be set
     *                 automatically to {@link #TYPE_CALL}
     * @param peer     the other party in the call
     * @return the newly createdMessage
     * @throws io.realm.exceptions.RealmException if you are not in a transaction
     * @see {@link Message#makeNew(Realm, String, String, String, int)}
     */
    @NonNull
    public static Message makeNewCallMessageAndPersist(Realm theRealm, String mainUserId, String peer, long callDate, CallBody callBody, boolean isOutGoing) {
        Message message = theRealm.createObject(Message.class, generateIdPossiblyUnique(theRealm, mainUserId, peer));
//        message.setId();
        message.setDateComposed(new Date(callDate));
        message.setCallBody(theRealm.copyToRealm(callBody));
        if (isOutGoing) {
            message.setFrom(mainUserId);
            message.setTo(peer);
        } else {
            message.setFrom(peer);
            message.setTo(mainUserId);
        }
        message.setState(Message.STATE_SEEN);
        message.setType(TYPE_CALL);
        return message;
    }

    /**
     * creates a new {@link Message}. callers must ensure they are in
     * {@link Realm} transaction. by calling {@link Realm#beginTransaction()}
     *
     * @param theRealm the realm to use in creating the message
     * @param body     the body of the message. the message body
     *                 may be a path to a file if the message is a
     *                 binary message
     * @param to       the recipient of the {@code message}
     * @param type     the type of the message
     * @return the newly createdMessage
     * @throws com.pairapp.Errors.PairappException if the message is invalid. this could be because
     *                                             a binary message is too huge, etc
     * @throws io.realm.exceptions.RealmException  if you are not in a transaction
     * @see {@link Message#makeNewCallMessageAndPersist(Realm, String, String, long, CallBody, boolean)}
     * @see {@link MessageUtils#validate(Message)}
     */
    public static Message makeNew(Realm theRealm, String mainUserId, String body, String to, int type) throws PairappException {
        if (type == TYPE_BIN_MESSAGE || type == TYPE_PICTURE_MESSAGE || type == TYPE_VIDEO_MESSAGE) {
            File file = new File(body);
            if (!file.exists()) {
                throw new PairappException("file does not exists", MessageUtils.ERROR_FILE_DOES_NOT_EXIST);
            }
            if (file.length() > FileUtils.ONE_MB * 16) {
                throw new PairappException("file is too large", MessageUtils.ERROR_ATTACHMENT_TOO_LARGE);
            }
        }
        Message message = theRealm.createObject(Message.class, generateIdPossiblyUnique(theRealm, mainUserId, to));
//        message.setId();
        message.setDateComposed(new Date());
        message.setMessageBody(body);
        message.setFrom(mainUserId);
        message.setState(Message.STATE_PENDING);
        message.setType(type);
        message.setTo(to);
        return message;
    }


    /**
     * copies the message. This effectively detaches the message from {@link Realm} so
     * that you can pass it around even between threads with no problem.
     * this method will only copy object that are attached to realm if you want to copy
     * the message irrespective of whether it's attached to realm or not, use the
     * other overload {@link Message#copy(Message, boolean)}
     *
     * @param message the message to be copied
     * @return a clone of the message passed
     * @throws IllegalArgumentException if the message is null
     * @see {@link #Message#copy(Collection) }
     */
    public static Message copy(Message message) {
        if (message == null) {
            throw new IllegalArgumentException("message is null");
        }
        return copy(message, false);
    }

    @NonNull
    private static Message internalCopy(Message message) {
        Message clone = new Message();
        clone.setId(message.getId());
        clone.setFrom(message.getFrom());
        clone.setTo(message.getTo());
        clone.setDateComposed(message.getDateComposed());
        clone.setType(message.getType());
        clone.setMessageBody(message.getMessageBody());
        clone.setState(message.getState());
        if (clone.getCallBody() != null) {
            CallBody callBody = new CallBody(clone.getCallBody().getCallId(),
                    clone.getCallBody().getCallDuration(),
                    clone.getCallBody().getCallType());
            clone.setCallBody(callBody);
        }
        return clone;
    }

    /**
     * copies a message irrespective of whether it's attached to realm or not
     *
     * @param message   the message to be copied, may not be null
     * @param forceCopy indicate whether to copy the message irrespective of whether its attached to realm or not
     * @return the copied message
     * @see {@link #copy(Message)}
     */
    public static Message copy(Message message, boolean forceCopy) {
        if (!message.isValid() && !forceCopy) {
            return message;
        }
        return internalCopy(message);
    }

    /**
     * copies the message. This effectively detaches the message from {@link Realm} so
     * that you can pass it around even between threads with no problem
     *
     * @param messages the message to be copied
     * @return a clone of the collection passed as list. if the messages passed is null
     * or empty an empty collection is returned
     * @see {@link #copy}
     */
    public static List<Message> copy(Collection<Message> messages) {
        return copy(messages, false);
    }

    public static List<Message> copy(Collection<Message> messages, boolean forceCopy) {
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }
        List<Message> copy = new ArrayList<>(messages.size());
        for (Message message : messages) {
            copy.add(copy(message, forceCopy));
        }
        return copy;
    }

    public static boolean isGroupMessage(Realm userRealm, Message message) {
        return UserManager.getInstance().isGroup(userRealm, message.getTo());
    }

    public static String formatTimespan(long timespan) {
        long totalSeconds = timespan / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        if (minutes >= 60) {
            return String.format(Locale.US, "%02d:%02d:%02d", (int) (minutes / 60), minutes % 60, seconds);
        } else {
            return String.format(Locale.US, "%02d:%02d", minutes, seconds);
        }
    }

    @NonNull
    public static String getCallSummary(Context context, Realm userRealm, Message message) {
        //noinspection ConstantConditions
        int callDuration = message.getCallBody().getCallDuration();
        return DateUtils.formatDateTime(context, message.getDateComposed().getTime(),
                DateUtils.FORMAT_SHOW_TIME).toLowerCase() + "   " +
                (Message.isOutGoing(userRealm, message) || callDuration > 0 ? "" + formatTimespan(callDuration) : GenericUtils.getString(R.string.missed_call));
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getMessageBody() {
        return messageBody;
    }

    public void setMessageBody(String messageBody) {
        this.messageBody = messageBody;
    }

    public Date getDateComposed() {
        return new Date(dateComposed);
    }

    public void setDateComposed(Date dateComposed) {
        this.dateComposed = dateComposed.getTime();
    }

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }

    public static String toJSON(Message message) {
        if (message == null) {
            throw new IllegalArgumentException("message == null");
        }
        return MessageJsonAdapter.toJson(message).toString();
    }

    public static Message fromJSON(String json) {
        if (TextUtils.isEmpty(json)) {
            throw new IllegalArgumentException("json == null || json is empty");
        }
        Realm userRealm = User.Realm(Config.getApplicationContext());
        try {
            return MessageJsonAdapter.fromJson(UserManager.getMainUserId(userRealm), json);
        } finally {
            userRealm.close();
        }
    }

    public static Message markMessageSeen(Realm realm, String msgId) {
        GenericUtils.ensureNotNull(realm, msgId);
        Message tmp = realm.where(Message.class).equalTo(Message.FIELD_ID, msgId).findFirst();
        if (tmp != null) {
            if (tmp.getState() != Message.STATE_SEND_FAILED && tmp.getState() != Message.STATE_SEEN) {
                realm.beginTransaction();
                if (tmp.isValid()) {
                    tmp.setState(Message.STATE_SEEN);
                }
                realm.commitTransaction();
            }
        }
        return tmp;
    }

    public static Message markMessageDelivered(Realm realm, String msgId) {
        GenericUtils.ensureNotNull(realm, msgId);
        Message tmp = realm.where(Message.class).equalTo(Message.FIELD_ID, msgId).findFirst();
        if (tmp != null) {
            if (tmp.getState() != STATE_SEND_FAILED && tmp.getState() != STATE_SEEN && tmp.getState() != STATE_RECEIVED) {
                realm.beginTransaction();
                if (tmp.isValid()) {
                    tmp.setState(STATE_RECEIVED);
                }
                realm.commitTransaction();
            }
        }
        return tmp;
    }

    @Nullable
    public CallBody getCallBody() {
        return callBody;
    }

    private void setCallBody(@NonNull CallBody callBody) {
        this.callBody = callBody;
    }

    public static boolean isCallMessage(Message message) {
        return message.getType() == TYPE_CALL;
    }


    public boolean hasAttachment() {
        return getType() == TYPE_PICTURE_MESSAGE || getType() == TYPE_VIDEO_MESSAGE || getType() == TYPE_BIN_MESSAGE;
    }

    public static boolean canRevert(Realm userRealm, Message msg) {
        return isOutGoing(userRealm, msg) && (msg.getState() ==
                STATE_PENDING || msg.getState() == Message.STATE_SENT || msg.getState() == Message.STATE_RECEIVED);
    }

    public static boolean canEdit(Realm userRealm, Message msg) {
        return Message.isTextMessage(msg) && canRevert(userRealm, msg);
    }

    @Nullable
    public String getAttachmentSize() {
        return attachmentSize;
    }

    public void setAttachmentSize(String attachmentSize) {
        this.attachmentSize = attachmentSize;
    }
}
