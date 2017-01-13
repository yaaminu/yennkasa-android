package com.yennkasa.data;

import android.content.Context;
import android.support.annotation.NonNull;

import com.yennkasa.util.SimpleDateUtil;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;
import io.realm.annotations.RealmClass;

import static com.yennkasa.data.Message.TYPE_DATE_MESSAGE;

/**
 * @author by Null-Pointer on 5/30/2015.
 */
@SuppressWarnings("unused")
@RealmClass
public class Conversation extends RealmObject {


    public static final String FIELD_SUMMARY = "summary",
            FIELD_ACTIVE = "active",
            FIELD_LAST_MESSAGE = "lastMessage",
            FIELD_LAST_ACTIVE_TIME = "lastActiveTime",
            FIELD_PEER_ID = "peerId",
            FIELD_NOTIFICATION_SOUND = "notificationSoundMessage",
            FIELD_NOTIFICATION_SOUND_CALL = "notificationSoundCall",
            AUTO_DOWNLOAD_MOBILE = "autoDownloadMobile",
            AUTO_DOWNLOAD_WIFI = "autoDownloadWifi",
            CONVERSATION_LOCKED = "lockType";

    private static final int WIFI_IMG = 0x1, WIFI_VID = 0x2, WIFI_AUDIO = 0x4, WIFI_OTHER = 0x8;
    private static final int MOBILE_IMG = 0x1, MOBILE_VID = 0x2, MOBILE_AUDIO = 0x4, MOBILE_OTHER = 0x8;
    private static final int LOCK_TYPE_NONE = 0x2,
            LOCK_TYPE_FINGERPRINT = 0x1,
            LOCK_TYPE_PIN = 0x2,
            LOCK_TYPE_PATTERN = 0x4;

    @PrimaryKey
    private String peerId; //other peer in chat
    private String summary;
    private Date lastActiveTime;
    private Message lastMessage;
    private boolean active, hidden;
    private int autoDownloadWifi, autoDownloadMobile, lockType, textSize;
    private String notificationSoundCall, notificationSoundMessage;
    private String notificationSoundMessageTitle;
    private String notificationSoundCallTitle;
    private boolean mute;

    public Conversation() {
        this.autoDownloadWifi = WIFI_AUDIO | WIFI_VID | WIFI_AUDIO | WIFI_OTHER;
        this.autoDownloadMobile = 0; //all off.
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public int getAutoDownloadWifi() {
        return this.autoDownloadWifi;
    }

    public void setAutoDownloadWifi(int autoDownloadWifi) {
        this.autoDownloadWifi = autoDownloadWifi;
    }

    public int getAutoDownloadMobile() {
        return autoDownloadMobile;
    }

    public void setAutoDownloadMobile(int autoDownloadMobile) {
        this.autoDownloadMobile = autoDownloadMobile;
    }

    public void setNotificationSoundCall(String notificationSoundCall) {
        this.notificationSoundCall = notificationSoundCall;
    }

    public void setTextSize(int textSize) {
        this.textSize = textSize;
    }

    public int getTextSize() {
        return textSize == 0 ? 100 : textSize;
    }

    public void setMute(boolean mute) {
        this.mute = mute;
    }

    public boolean isMute() {
        return mute;
    }

    public String getNotificationSoundCall() {
        return notificationSoundCall;
    }

    public void setNotificationSoundMessage(String notificationSoundMessage) {
        this.notificationSoundMessage = notificationSoundMessage;
    }

    public String getNotificationSoundMessage() {
        return notificationSoundMessage;
    }

    public Message getLastMessage() {
        return lastMessage;
    }

    public void setLastMessage(Message lastMessage) {
        this.lastMessage = lastMessage;
    }

    public String getPeerId() {
        return peerId;
    }

    public void setPeerId(String peer) {
        this.peerId = peer;
    }

    public Date getLastActiveTime() {
        return lastActiveTime;
    }

    public void setLastActiveTime(Date lastActiveTime) {
        this.lastActiveTime = lastActiveTime;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public boolean isActive() {
        return this.active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @SuppressWarnings("ConstantConditions")
    public synchronized static boolean newSession(Realm realm, String currentUserId, Conversation conversation) {
        return newSession(realm, currentUserId, conversation, System.currentTimeMillis());
    }

    public synchronized static void newConversation(Context context, String currentUserId, String peerId) {
        newConversation(context, currentUserId, peerId, false);
    }

    public synchronized static void newConversation(Context context, String currentUserId, String peerId, boolean active) {
        Realm realm = Realm();
        newConversation(realm, currentUserId, peerId, active);
        realm.close();
    }

    public synchronized static Conversation newConversation(Realm realm, String currentUserId, String peerId) {
        return newConversation(realm, currentUserId, peerId, false);
    }

    public synchronized static Conversation newConversation(Realm realm, String currentUserId, String peerId, boolean active) {
        Conversation newConversation = realm.where(Conversation.class).equalTo(Conversation.FIELD_PEER_ID, peerId).findFirst();
        realm.beginTransaction();
        if (newConversation == null) {
            newConversation = realm.createObject(Conversation.class, peerId);
//            newConversation.setPeerId(peerId);
            newConversation.setActive(active);
            newConversation.setLastActiveTime(new Date());
            newConversation.setSummary("no message");
        }
        newSession(realm, currentUserId, newConversation);
        realm.commitTransaction();
        return newConversation;
    }

    public synchronized static Conversation newConversationWithoutSession(Realm realm, String peerId, boolean active) {
        Conversation newConversation = realm.where(Conversation.class).equalTo(Conversation.FIELD_PEER_ID, peerId).findFirst();
        if (newConversation == null) {
            realm.beginTransaction();
            newConversation = realm.createObject(Conversation.class, peerId);
//            newConversation.setPeerId(peerId);
            newConversation.setActive(active);
            newConversation.setLastActiveTime(new Date());
            newConversation.setSummary("no message");
            realm.commitTransaction();
        }
        return newConversation;
    }

    @Deprecated //use Realm();
    public static Realm Realm(Context context) {
        return Message.REALM(context);
    }

    public static Realm Realm() {
        return Message.REALM();
    }

    public static Conversation copy(Conversation conversation) {
        Conversation clone = new Conversation();
        clone.setActive(conversation.isActive());
        clone.setLastActiveTime(conversation.getLastActiveTime());
        clone.setPeerId(conversation.getPeerId());
        clone.setSummary(conversation.getSummary());
        clone.setLastMessage(conversation.getLastMessage());
        return clone;
    }

    public static boolean newSession(@NonNull Realm realm, @NonNull String currentUserId,
                                     @NonNull Conversation conversation, long timestamp) {

        Calendar calendar = GregorianCalendar.getInstance();
        calendar.setTime(new Date(timestamp));
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        Date time = calendar.getTime();
        String formatted = SimpleDateUtil.formatSessionDate(time);
        String messageId = conversation.getPeerId() + formatted;
        Message message = realm.where(Message.class)
                .equalTo(Message.FIELD_ID, messageId)
                .findFirst();
        if (message == null) { //session not yet set up!
            message = realm.createObject(Message.class, messageId);
//            message.setId(messageId);
            message.setMessageBody(formatted);
            message.setTo(currentUserId);
            message.setFrom(conversation.getPeerId());
            message.setDateComposed(time);
            message.setType(TYPE_DATE_MESSAGE);
            return true;
        }
        return false;
    }

    public void setNotificationSoundMessageTitle(String notificationSoundMessageTitle) {
        this.notificationSoundMessageTitle = notificationSoundMessageTitle;
    }

    public String getNotificationSoundMessageTitle() {
        return notificationSoundMessageTitle;
    }

    public void setNotificationSoundCallTitle(String notificationSoundCallTitle) {
        this.notificationSoundCallTitle = notificationSoundCallTitle;
    }

    public String getNotificationSoundCallTitle() {
        return notificationSoundCallTitle;
    }
}
