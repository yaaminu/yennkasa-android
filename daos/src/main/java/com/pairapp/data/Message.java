package com.pairapp.data;

import android.app.Application;
import android.content.Context;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import com.pairapp.Errors.ErrorCenter;
import com.pairapp.Errors.PairappException;
import com.pairapp.data.util.MessageUtils;
import com.pairapp.util.Config;
import com.pairapp.util.FileUtils;
import com.pairapp.util.PLog;
import com.pairapp.util.ThreadUtils;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import io.realm.Realm;
import io.realm.RealmConfiguration;
import io.realm.RealmObject;
import io.realm.annotations.Index;
import io.realm.annotations.PrimaryKey;
import io.realm.annotations.RealmClass;
import io.realm.exceptions.RealmException;

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
@SuppressWarnings("unused")
@RealmClass
public class Message extends RealmObject {

    public static final int NEVER_DELETE = 0, AFTER_FIVE_DAYS = 1, AFTER_TEN_DAYS = 2, AFTER_30_DAYS = 3;

    public static final int STATE_PENDING = 0x3e9,
            STATE_SENT = 0x3ea, //if you change this remember to update the socket io server too
            STATE_RECEIVED = 0x3eb,
            STATE_SEEN = 0x3ec,
            STATE_SEND_FAILED = 0x3ed; //and this one too

    /***********************************
     * the order of these fields i.e. TYPE_* is relevant to some components for sorting purposes
     * make sure you don't change the order
     */
    public static final int TYPE_TEXT_MESSAGE = 0x3ee, //don't touch me!
            TYPE_BIN_MESSAGE = 0x3ef,  //don't touch me!
            TYPE_PICTURE_MESSAGE = 0x3f0,  //don't touch me!
            TYPE_VIDEO_MESSAGE = 0x3f1, //don't touch me!
            TYPE_DATE_MESSAGE = 0x3f2, //don't touch me!
            TYPE_TYPING_MESSAGE = 0x3f3; //don't touch me!
    public static final String FIELD_ID = "id",
            FIELD_FROM = "from",
            FIELD_TO = "to",
            FIELD_TYPE = "type",
            FIELD_STATE = "state",
            FIELD_DATE_COMPOSED = "dateComposed",
            FIELD_MESSAGE_BODY = "messageBody";
    public static final String EVENT_MSG_STATUS = "msgStatus";
    public static final String MSG_STS_STATUS = "status";
    public static final String MSG_STS_MESSAGE_ID = "messageId";
    public static final String EVENT_MESSAGE = "message";
    private static final String TAG = Message.class.getSimpleName();
    private final static Object idLock = new Object();
    public static final String SENDABLE_MESSAGE = "sendableMessage";
    @PrimaryKey
    private String id;
    @Index
    private String from; //sender's id
    @Index
    private String to; //recipient's id
    private String messageBody;
    private Date dateComposed;
    private int state;
    private int type;

//    /**
//     * @param mesdsage the message to be used as the basis of the new clone
//     * @see {@link #copy}
//     * @deprecated use {@link #copy}instead
//     */
//    @Deprecated
//    public Message(Message message) {
//        this.from = message.getFrom();
//        this.to = message.getTo();
//        this.id = message.getId();
//        this.type = message.getType();
//        this.dateComposed = message.getDateComposed();
//        this.messageBody = message.getMessageBody();
//        this.state = message.getState();
//    }

    /**
     * you are strongly advised to use the factory {@link Message#makeNew}
     * and its siblings instead
     */
    public Message() {
    } //required no-arg c'tor;

    /**
     * @return a new id that is likely unique.there is no guarantee that this will be unique
     */
    public static String generateIdPossiblyUnique(String to) {
        //before changing how we generate ids make sure all components relying on this behaviour are updated
        //eg: MessageActivity (the name might have changed)
        if (to == null) {
            throw new IllegalArgumentException("to == null");
        }
        Application appContext = Config.getApplication();
        Realm realm = REALM(appContext);
        String id;
        synchronized (idLock) {
            long count = realm.where(Message.class).equalTo(FIELD_ID, to).count() + 1;
            id = count + "@" + UserManager.getMainUserId() + "@" + to + "@" + System.currentTimeMillis();
        }
        PLog.i(TAG, "generated message id: " + id);
        realm.close();
        return id;
    }

    /**
     * this is the best way of acquiring a realm for working with a {@link Message}
     *
     * @param context the current context
     * @return a {@link Realm that is guaranteed to work with messages all the time}
     */
    public static Realm REALM(Context context) {
        File dataFile = context.getDir("messages_crypt", Context.MODE_PRIVATE);
        try {
            return Realm.getInstance(config/*, UserManager.getKey()*/);
        } catch (RealmException e) {
            ErrorCenter.reportError("realmSecureError", Config.getApplicationContext().getString(R.string.encryptionNotAvailable), null);
            return Realm.getInstance(config);
        }
    }

    // FIXME: 1/14/2016 add key
    private static final RealmConfiguration config;

    static {
        File file = Config.getApplicationContext().getDir("data", Context.MODE_PRIVATE);
        config = new RealmConfiguration.Builder(file)
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

    public static boolean isIncoming(Message message) {
        return !isOutGoing(message);
    }

    public static boolean isOutGoing(Message message) {
        return UserManager.getInstance().isCurrentUser(message.getFrom());
    }

    /**
     * creates a new message. callers must ensure they are in
     * realm transaction.
     *
     * @param theRealm the realm to use in creating the message
     * @param body     the body of the message. the message type will be set
     *                 automatically to {@link #TYPE_TEXT_MESSAGE}
     * @param to       the recipient of the {@link Message}
     * @return the newly createdMessage
     * @throws com.pairapp.Errors.PairappException if the message is invalid. this could be because
     *                                             a binary message is too huge, etc
     * @throws io.realm.exceptions.RealmException  if you are not in a transaction
     * @see {@link Message#makeNew(Realm, String, String, int)}
     */
    public static Message makeNew(Realm theRealm, String body, String to) throws PairappException {
        return makeNew(theRealm, body, to, TYPE_TEXT_MESSAGE);
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
     * @see {@link Message#makeNew(Realm, String, String)}
     * @see {@link MessageUtils#validate(Message)}
     */
    public static Message makeNew(Realm theRealm, String body, String to, int type) throws PairappException {
        if (type == TYPE_BIN_MESSAGE || type == TYPE_PICTURE_MESSAGE || type == TYPE_VIDEO_MESSAGE) {
            File file = new File(body);
            if (!file.exists()) {
                throw new PairappException("file does not exists", MessageUtils.ERROR_FILE_DOES_NOT_EXIST);
            }
            if (file.length() > FileUtils.ONE_MB * 16) {
                throw new PairappException("file is too large", MessageUtils.ERROR_ATTACHMENT_TOO_LARGE);
            }
        }
        Message message = theRealm.createObject(Message.class);
        message.setDateComposed(new Date());
        message.setMessageBody(body);
        message.setId(generateIdPossiblyUnique(to));
        message.setFrom(UserManager.getMainUserId());
        message.setState(Message.STATE_PENDING);
        message.setType(type);
        message.setTo(to);
        return message;
    }

    /**
     * a convenient method for creating a new message that needs not be attached
     * to a realm
     *
     * @param body the message's body, may be a path to a file if its a bin message
     * @param to   the recipient of the message
     * @param type the type of the message, one of {@link #TYPE_TEXT_MESSAGE,#TYPE_BIN_MESSAGE,#TYPE_PICTURE_MESSAGE,#TYPE_VIDEO_MESSAGE}
     * @return the message
     * @throws PairappException if the message is not a text and is too huge
     * @see {@link #makeNew(Realm, String, String)}
     * @see {@link #makeNew(Realm, String, String, int)}
     */
    public static Message makeNew(String body, String to, int type) throws PairappException {
        if (type == TYPE_BIN_MESSAGE || type == TYPE_PICTURE_MESSAGE || type == TYPE_VIDEO_MESSAGE) {
            File file = new File(body);
            if (!file.exists()) {
                throw new PairappException("file does not exists", MessageUtils.ERROR_FILE_DOES_NOT_EXIST);
            }
            if (file.length() > FileUtils.ONE_MB * 16) {
                throw new PairappException("file is too large", MessageUtils.ERROR_ATTACHMENT_TOO_LARGE);
            }
        }
        Message message = new Message();
        message.setDateComposed(new Date());
        message.setMessageBody(body);
        message.setId(generateIdPossiblyUnique(to));
        message.setFrom(UserManager.getMainUserId());
        message.setState(Message.STATE_PENDING);
        message.setType(type);
        message.setTo(to);
        return message;
    }

    public static String state(Context context, int status) {
        switch (status) {
            case Message.STATE_PENDING:
                return "pending";
            case Message.STATE_SEND_FAILED:
                return "failed";
            case Message.STATE_RECEIVED:
                return "delivered";
            case Message.STATE_SEEN:
                return "seen";
            case Message.STATE_SENT:
                return "sent";
            default:
                throw new AssertionError("new on unknown message status");
        }
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

    /**
     * make a typing message. this method may not be called from a thread other than the main thread.
     * clients must set the date the message was created
     *
     * @param peerId the peer ID in the conversation
     * @return a new typing message
     * @throws IllegalStateException if it is not called from the main thread
     */
    public static Message makeTypingMessage(String peerId) {
        ThreadUtils.ensureMain();
        Message typingMessage = new Message();
        typingMessage.setFrom(UserManager.getMainUserId());
        typingMessage.setType(TYPE_TYPING_MESSAGE);
        typingMessage.setTo(peerId);
        typingMessage.setId(peerId + "typing");
        return typingMessage;
    }

    public static boolean isGroupMessage(Message message) {
        return UserManager.getInstance().isGroup(message.getTo());
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
        return dateComposed;
    }

    public void setDateComposed(Date dateComposed) {
        this.dateComposed = dateComposed;
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
        return MessageJsonAdapter.INSTANCE.toJSON(message).toString();
    }

    public static JSONObject toJsonObject(Message message) {
        return MessageJsonAdapter.INSTANCE.toJSON(message);
    }

    public static Message fromJSON(String json) {
        if (TextUtils.isEmpty(json)) {
            throw new IllegalArgumentException("json == null || json is empty");
        }
        return MessageJsonAdapter.INSTANCE.fromJson(json);
    }
}
