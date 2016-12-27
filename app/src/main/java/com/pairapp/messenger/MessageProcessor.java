package com.pairapp.messenger;

import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.util.Pair;

import com.pairapp.R;
import com.pairapp.call.BuildConfig;
import com.pairapp.data.Conversation;
import com.pairapp.data.Message;
import com.pairapp.data.User;
import com.pairapp.data.UserManager;
import com.pairapp.data.util.MessageUtils;
import com.pairapp.util.ConnectionUtils;
import com.pairapp.util.Event;
import com.pairapp.util.GenericUtils;
import com.pairapp.util.LiveCenter;
import com.pairapp.util.PLog;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;
import java.util.concurrent.Semaphore;

import io.realm.Realm;
import io.realm.exceptions.RealmPrimaryKeyConstraintException;

import static com.pairapp.messenger.MessengerBus.MESSAGE_RECEIVED;
import static com.pairapp.messenger.MessengerBus.PAIRAPP_CLIENT_POSTABLE_BUS;
import static com.pairapp.messenger.MessengerBus.get;

public class MessageProcessor extends IntentService {
    public static final String SUCCEEDED = "succeeded";
    static final String TAG = MessageProcessor.class.getSimpleName();
    static final String MESSAGE = "message";
    static final String UNKNOWN = "unknown";
    static final String MESSAGE_STATUS = "messageStatus";
    static final String TIMESTAMP = "timestamp";
    public static final String EDIT = "message.edit.sending";
    public static final String REVERT_RESULTS = "message.revert.sending.results";
    public static final String EDIT_RESULTS = "message.edit.sending.results";
    public static final String REVER_OR_EDIT = "rever.or.edit";
    public static String REVERT = "message.revert.sending";
    final Semaphore processLock = new Semaphore(1, true);

    public MessageProcessor() {
        super(TAG);
    }


    @Override
    protected void onHandleIntent(Intent intent) {
        Bundle bundle = intent.getExtras();
        final String data = bundle.getString(MESSAGE);
        assert data != null;
        final long timestamp = intent.getLongExtra(TIMESTAMP, 0L);


        PowerManager manager = ((PowerManager) getSystemService(POWER_SERVICE));
        final PowerManager.WakeLock wakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);

        try {
            processLock.acquireUninterruptibly();
            wakeLock.acquire();
            PLog.d(TAG, "payload: %s, timestamp: %s", data, new Date(timestamp));
            handleMessage(data, timestamp);
        } finally {
            wakeLock.release();
            processLock.release();
        }
    }

    private void handleMessage(String data, long timestamp) {
        Realm realm = Message.REALM(this), userRealm = User.Realm(this);
        try {
            final JSONObject jsonObject = new JSONObject(data);
            String type = getType(jsonObject);
            //noinspection IfCanBeSwitch
            if (type.equals(MESSAGE)) {
                Message message = Message.fromJSON(data);
                doProcessMessage(realm, userRealm, message, timestamp);
            } else if (type.equals(MESSAGE_STATUS)) {
                int state = jsonObject.optInt(Message.MSG_STS_STATUS, Message.STATE_SEEN);
                String messageId = jsonObject.getString(Message.MSG_STS_MESSAGE_ID);
                Message message = realm.where(Message.class).equalTo(Message.FIELD_ID, messageId).findFirst();
                if (message != null && message.isValid()) {
                    realm.beginTransaction();
                    if (state == Message.STATE_RECEIVED && message.getState() != Message.STATE_SEEN) {
                        message.setState(Message.STATE_RECEIVED);
                    } else if (state == Message.STATE_SEEN) {
                        message.setState(Message.STATE_SEEN);
                    }
                    realm.commitTransaction();
                } else {
                    PLog.d(TAG, "message not available for update");
                }
            } else if (EDIT.equals(type)) {
                handleEditMessage(realm, jsonObject, UserManager.getMainUserId(userRealm));
            } else if (REVERT.equals(type)) {
                handleRevertingMessage(realm, jsonObject);
            } else if (REVERT_RESULTS.equals(type)) {
                Message message = realm.where(Message.class).equalTo(Message.FIELD_ID, jsonObject.getString(Message.FIELD_ID)).findFirst();
                if (message != null) {
                    realm.beginTransaction();
                    message.setState(Message.STATE_SEND_FAILED);
                    realm.commitTransaction();
                    NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                            .setContentTitle(GenericUtils.getString(R.string.message_unsent_success))
                            .setContentText(getString(R.string.message_reverted_description, UserManager.getInstance().getName(userRealm, message.getTo())))
                            .setAutoCancel(true)
                            .setContentIntent(PendingIntent.getActivity(this, 1000, new Intent(), PendingIntent.FLAG_NO_CREATE))
                            .setSmallIcon(R.drawable.ic_stat_icon);

                    NotificationManagerCompat manager = NotificationManagerCompat.from(this);// getSystemService(NOTIFICATION_SERVICE));
                    manager.notify(REVER_OR_EDIT, 100000111, builder.build());
                }
            } else if (EDIT_RESULTS.equals(type)) {
                Message message = realm.where(Message.class).equalTo(Message.FIELD_ID, jsonObject.getString(Message.FIELD_ID)).findFirst();
                if (message != null) {
                    realm.beginTransaction();
                    message.setState(Message.STATE_RECEIVED);
                    realm.commitTransaction();
                    NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                            .setContentTitle(getString(R.string.sent_message_edited))
                            .setContentText(getString(R.string.message_edited_description, UserManager.getInstance().getName(userRealm, message.getTo())))
                            .setAutoCancel(true)
                            .setContentIntent(PendingIntent.getActivity(this, 1000, new Intent(), PendingIntent.FLAG_NO_CREATE))
                            .setSmallIcon(R.drawable.ic_stat_icon);

                    NotificationManagerCompat manager = NotificationManagerCompat.from(this);// getSystemService(NOTIFICATION_SERVICE));
                    manager.notify(REVER_OR_EDIT, 100000111, builder.build());
                }
            } else {
                throw new JSONException("unknown message %s " + data);
            }
        } catch (JSONException e) {
            if (BuildConfig.DEBUG) {
                throw new RuntimeException(e);
            }
            PLog.d(TAG, e.getMessage(), e);
        } finally {
            realm.close();
            userRealm.close();
        }
    }

    private void handleEditMessage(Realm realm, JSONObject jsonObject, String currentUserId) throws JSONException {
        boolean succeded = false;
        String messageId = jsonObject.getString(Message.FIELD_ID);
        Message message = realm.where(Message.class).equalTo(Message.FIELD_ID, messageId).findFirst();
        if (message != null) {
            realm.beginTransaction();
            if (message.getState() != Message.STATE_SEEN) {
                String messageBody = jsonObject.getString(Message.FIELD_MESSAGE_BODY);
                GenericUtils.ensureNotEmpty(messageBody);
                message.setMessageBody(messageBody);
                succeded = true;
            }
            realm.commitTransaction();
        }
        NotificationManager.INSTANCE.reNotifyForReceivedMessages(this, currentUserId);
        postEvent(Event.create(MessengerBus.MESSAGE_EDIT_RESULTS, succeded ?
                new Exception("failed") : null, Pair.create(messageId, jsonObject.getString(Message.FIELD_FROM))));
    }

    private void handleRevertingMessage(Realm realm, JSONObject jsonObject) throws JSONException {
        boolean succeded = false;
        String messageId = jsonObject.getString(Message.FIELD_ID);
        Message message = realm.where(Message.class).equalTo(Message.FIELD_ID, messageId).findFirst();
        if (message != null && message.isValid()) {
            realm.beginTransaction();
            if (message.getState() != Message.STATE_SEEN) {
                message.deleteFromRealm();
                succeded = true;
            }
            realm.commitTransaction();
        }

        postEvent(Event.create(MessengerBus.MESSAGE_REVERT_RESULTS, succeded ?
                new Exception("failed") : null, Pair.create(messageId, jsonObject.getString(Message.FIELD_FROM))));
    }

    private String getType(JSONObject data) {
        if (data.has(REVERT)) {
            return REVERT;
        }
        if (data.has(EDIT)) {
            return EDIT;
        }
        if (data.has(REVERT_RESULTS)) {
            return REVERT_RESULTS;
        }
        if (data.has(EDIT_RESULTS)) {
            return EDIT_RESULTS;
        }
        if (data.has(Message.FIELD_MESSAGE_BODY) && data.has(Message.FIELD_ID)) {
            return MESSAGE;
        }
        if ((data.length() == 1 && data.has(Message.FIELD_ID)) || data.has(MESSAGE_STATUS)) {
            return MESSAGE_STATUS;
        }

        return UNKNOWN;
    }

    private void doProcessMessage(Realm realm, Realm userRealm, Message message, long timestamp) {
        String currentUserId = UserManager.getMainUserId(userRealm);
        if (message.getFrom().equals(currentUserId)) {
            //how did this happen?
            return;
        }
        if (!MessageUtils.isSendableMessage(message)) {
            PLog.d(TAG, "unsupported message type %s", Message.toJSON(message));
            return;
        }

        String peerId;
        //for messages sent to groups, the group is always the recipient
        //and the members the senders
        if (Message.isGroupMessage(userRealm, message)) {
            peerId = message.getTo();
        } else {
            peerId = message.getFrom();
        }
        UserManager userManager = UserManager.getInstance();
        if (userManager.isBlocked(peerId)) {
            PLog.d(TAG, "message from a blocked user, dropping message");
            PLog.d(TAG, "%s is blocked", peerId);
            return;
        }
        userManager.fetchUserIfRequired(peerId);
        //all other operations are deferred till we set up the conversation
        Conversation conversation = realm.where(Conversation.class).equalTo(Conversation.FIELD_PEER_ID, peerId).findFirst();

        //WATCH OUT! don't touch this block!
        //////////////////////////////////////////////////////////////////////////////////////////
        //ensure the conversation and session is set up
        // before persisting the message
        if (conversation == null) { //create a new one
            conversation = Conversation.newConversation(realm, currentUserId, peerId);
            realm.beginTransaction();
        } else {
            realm.beginTransaction();
            Conversation.newSession(realm, currentUserId, conversation);
        }
        ///////////////////////////////////////////////////////////////////////////////////////////
        Message newestMessage = realm.where(Message.class)
                .equalTo(Message.FIELD_TO, peerId)
                .greaterThan(Message.FIELD_DATE_COMPOSED, timestamp).findFirst();

        //only use the server date for the message if there is no message on our side newer that it.
        message.setDateComposed(newestMessage == null ? new Date(Math.min(System.currentTimeMillis(), timestamp)) : new Date());

        message.setState(Message.STATE_RECEIVED);
        if (message.hasAttachment()) {
            String size = Uri.parse(message.getMessageBody()).getQueryParameter("size");
            message.setAttachmentSize(size);
        }
        //if user has invalid date settings
        conversation.setLastActiveTime(new Date(Math.max(timestamp, System.currentTimeMillis())));
        try {
            message = realm.copyToRealm(message);
            conversation.setLastMessage(message);
        } catch (RealmPrimaryKeyConstraintException primaryKey) {
            //lets eat up this exception
            realm.cancelTransaction();
            PLog.d(TAG, primaryKey.getMessage());
            PLog.d(TAG, "failed to process message");
            return;
        }
        conversation.setSummary(""); //ui elements must detect this
        message = Message.copy(message);
        if (!conversation.isActive()) { //hopefully we might be able to void race conditions
            LiveCenter.incrementUnreadMessageForPeer(conversation.getPeerId());
        }
        realm.commitTransaction();
        NotificationManager.INSTANCE.onNewMessage(this, message);
        postEvent(Event.create(MESSAGE_RECEIVED, null, message.getId()));
        if (message.hasAttachment() && Worker.getCurrentActiveDownloads() < Worker.MAX_PARRALLEL_DOWNLOAD) {
            if ((ConnectionUtils.isWifiConnected()
                    && userManager.getBoolPref(UserManager.AUTO_DOWNLOAD_MESSAGE_WIFI, false))
                    || (ConnectionUtils.isMobileConnected()
                    && userManager.getBoolPref(UserManager.AUTO_DOWNLOAD_MESSAGE_MOBILE, false))) {
                Worker.download(this, message, true);
            }
        }
    }

    static void postEvent(Event event) {
        if (event.isSticky()) {
            get(PAIRAPP_CLIENT_POSTABLE_BUS).postSticky(event);
        } else {
            get(PAIRAPP_CLIENT_POSTABLE_BUS).post(event);
        }
    }
}
