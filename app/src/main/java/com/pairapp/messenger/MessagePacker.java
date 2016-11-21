package com.pairapp.messenger;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.pairapp.util.BuildConfig;
import com.pairapp.util.GenericUtils;
import com.pairapp.util.PLog;
import com.pairapp.util.UiHelpers;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.DataFormatException;

import rx.Observable;
import rx.Subscriber;

/**
 * @author aminu on 6/28/2016.
 */
@SuppressWarnings("WeakerAccess")
public class MessagePacker {

    public static final byte HEADER_DELIMITER = '-',
            OFFLINE = 0x1/*offline*/, ONLINE = 0x2/*online*/,
            TYPING = 0x3/*typing*/,
            NOT_TYPING = 0x4/*not typing*/, READABLE_MESSAGE = 0x5, MESSAGE_STATUS_DELIVERED = 0x6, MESSAGE_STATUS_SEEN = 0x7,
            MONITOR_START = 0x4, MONITOR_STOP = 0x8, CALL_PAYLOAD = 0x9;


    private static final byte NO_PERSIST_ONLY_WHEN_ONLINE = 0x1, NO_PERSIST_PUSH_IF_POSSIBLE = 0x2,
            PERSIST_PUSH_IF_POSSIBLE = 0x4,
            NO_TRUNCATE_PUSH = 0x8,
            MONITOR_OR_STATUS = 0x10;
    public static final String TAG = "MessagePacker";

    // TODO: 11/10/2016 why assume userID is a long?
    private final long currentUserId;
    private final Compressor compressor;

    private MessagePacker(String currentUserId, Compressor compressor) {
        // TODO: 11/10/2016 make userID a string
        this.currentUserId = Long.parseLong(currentUserId, 10);
        this.compressor = compressor;
    }

    public static MessagePacker create(String currentUserId, Compressor compressor) {
        GenericUtils.ensureNotEmpty(currentUserId);
        return new MessagePacker(currentUserId, compressor);
    }

    public byte[] pack(String messageJson, String recipient, boolean isGroupMessage) {
        GenericUtils.ensureNotNull(messageJson, recipient);
        byte[] msgBuffer = compressor.compress(messageJson.getBytes());
        int msgLength = 1/*header for server*/
                + 1/*delimiter for header  and payload*/
                + 1/*header for clients*/
                + msgBuffer.length/*body*/;
        if (isGroupMessage) { /*recipient*/
            if (recipient.length() <= 8) {
                throw new IllegalArgumentException("text based recipients must be longer than 8 bytes");
            }
            if (recipient.indexOf('-') != -1) {
                throw new IllegalArgumentException("text based recipients cannot contain the dash character");
            }
            msgLength += recipient.getBytes().length;
        } else {
            msgLength += 8;
        }

        ByteBuffer byteBuffer = ByteBuffer.allocate(msgLength);
        byteBuffer.order(ByteOrder.BIG_ENDIAN);

        /**********************start header*********************/
        byteBuffer.put(PERSIST_PUSH_IF_POSSIBLE);
        if (isGroupMessage) {
            byteBuffer.put(recipient.getBytes());
        } else {
            byteBuffer.putLong(Long.parseLong(recipient));
        }
        byteBuffer.put(HEADER_DELIMITER);
        /**********************end header*********************/

        /**********************start payload*********************/
        byteBuffer.put(READABLE_MESSAGE);
        byteBuffer.put(msgBuffer);
        /**********************end payload*********************/
        return byteBuffer.array();
    }

    public byte[] createStatusMessage(boolean isOnline) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(3);
        byteBuffer.order(ByteOrder.BIG_ENDIAN);
        /**********************start header*********************/
        byteBuffer.put(MONITOR_OR_STATUS);
        byteBuffer.put(HEADER_DELIMITER);
        /**********************end header*********************/

        /**********************start payload*********************/
        byteBuffer.put(isOnline ? ONLINE : OFFLINE);
        /**********************end payload*********************/

        return byteBuffer.array();
    }

    private byte[] createTypingMessageUser(long recipient, boolean isTyping) {
        int msgLength = 19;
        ByteBuffer byteBuffer = ByteBuffer.allocate(msgLength);
        byteBuffer.order(ByteOrder.BIG_ENDIAN);

        /**********************start header*********************/
        byteBuffer.put(NO_PERSIST_ONLY_WHEN_ONLINE);//1
        byteBuffer.putLong(recipient);//8
        byteBuffer.put(HEADER_DELIMITER);//1
        /**********************end header*********************/

        /**********************start payload*********************/
        byteBuffer.put(isTyping ? TYPING : NOT_TYPING);//1
        byteBuffer.putLong(currentUserId);//8
        /**********************end payload*********************/

        return byteBuffer.array();
    }

    public byte[] createTypingMessage(String recipient, boolean isTyping) {
        try {
            return createTypingMessageUser(Long.parseLong(recipient), isTyping);
        } catch (NumberFormatException e) {
            return createGroupTypingMessage(recipient, isTyping);
        }
    }

    private byte[] createGroupTypingMessage(String recipient, boolean isTyping) {
        byte[] recipientBytes = recipient.getBytes();
        if (recipientBytes.length <= 8)
            throw new IllegalArgumentException("invalid recipient id");

        int msgLength = 11 + recipientBytes.length;
        ByteBuffer byteBuffer = ByteBuffer.allocate(msgLength);
        byteBuffer.order(ByteOrder.BIG_ENDIAN);

        /**********************start header*********************/
        //we are using no persist push since this is a typing message to a group
        byteBuffer.put((byte) (NO_PERSIST_PUSH_IF_POSSIBLE | NO_TRUNCATE_PUSH));//1
        byteBuffer.put(recipientBytes);//recipient.getBytes().length
        byteBuffer.put(HEADER_DELIMITER);//1
        /**********************end header*********************/

        /***************************start payload***************/
        byteBuffer.put(isTyping ? TYPING : NOT_TYPING); //1
        byteBuffer.putLong(currentUserId);//8
        /***************************end payload****************/

        return byteBuffer.array();
    }


    public byte[] createMonitorMessage(String target, boolean start) {
        long targetLong = Long.parseLong(target);
        if (targetLong <= 0) {
            throw new IllegalArgumentException("invalid user id");
        }
        ByteBuffer byteBuffer = ByteBuffer.allocate(11);
        byteBuffer.order(ByteOrder.BIG_ENDIAN);
        /**********************start header*********************/
        byteBuffer.put(MONITOR_OR_STATUS);
        byteBuffer.putLong(targetLong);
        byteBuffer.put(HEADER_DELIMITER);
        /**********************end header*********************/

        /**********************start payload*********************/
        byteBuffer.put(start ? MONITOR_START : MONITOR_STOP);
        /**********************end payload*********************/

        return byteBuffer.array();
    }

    public byte[] createMsgStatusMessage(String to, String msgId, boolean isDelivery) {
        GenericUtils.ensureNotEmpty(to, msgId);
        long recipient = Long.parseLong(to);
        byte[] msgIdBytes = msgId.getBytes();
        ByteBuffer byteBuffer = ByteBuffer.allocate(1/*header*/ + 8/*recipients*/ + 1/*delimiter*/ + 1/*clientHeader*/ + msgIdBytes.length/*body*/);
        byteBuffer.order(ByteOrder.BIG_ENDIAN);

        /**********************start header*********************/
        byteBuffer.put((byte) (PERSIST_PUSH_IF_POSSIBLE | NO_TRUNCATE_PUSH));
        byteBuffer.putLong(recipient);
        byteBuffer.put(HEADER_DELIMITER);
        /**********************end header*********************/

        /**********************start payload*********************/
        byteBuffer.put(isDelivery ? MESSAGE_STATUS_DELIVERED : MESSAGE_STATUS_SEEN);
        byteBuffer.put(msgIdBytes);
        /**********************end payload*********************/
        return byteBuffer.array();
    }

    @NonNull
    public byte[] packCallMessage(@NonNull String recipient, @NonNull String payload) {
        int capacity = 1/*server*/ + recipient.getBytes().length + 1/*delimiter*/ + 1/*clientHeader*/ + payload.getBytes().length;
        ByteBuffer byteBuffer = ByteBuffer.allocate(capacity);
        byteBuffer.order(ByteOrder.BIG_ENDIAN);
        byteBuffer.put((byte) (NO_PERSIST_PUSH_IF_POSSIBLE | NO_TRUNCATE_PUSH));
        byteBuffer.put(recipient.getBytes());
        byteBuffer.put(HEADER_DELIMITER);
        byteBuffer.put(CALL_PAYLOAD);
        byteBuffer.put(payload.getBytes());
        return byteBuffer.array();
    }

    public void unpack(byte[] data) {
        if (onSubscribe.subscriber == null || onSubscribe.subscriber.isUnsubscribed()) {
            throw new IllegalStateException("can't invoke unpack without an observer.");
        }
        DataEvent event = doUnpack(data);
        if (event != null) {
            onSubscribe.onMessageAvailable(event);
        } else {
            PLog.f(TAG, "failed to unpack data");
        }
    }

    @Nullable
    private DataEvent doUnpack(byte[] data) {
        if (data == null || data.length <= 0) {
            throw new IllegalArgumentException("invalid data");
        }
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(ByteOrder.BIG_ENDIAN);

        byte header = buffer.get();
        DataEvent event;
        switch (header) {
            case ONLINE://fall through
            case OFFLINE:
            case TYPING:
            case NOT_TYPING:
                String targetId;
                if (data.length > 17) {
                    byte[] idbytes = new byte[data.length - (1 + 8)]; //minus header
                    targetId = new String(buffer.get(idbytes, 0, idbytes.length).array());
                } else {
                    targetId = String.valueOf(buffer.getLong());
                }
                event = new DataEvent(header, targetId);
                break;
            case READABLE_MESSAGE:
                try {
                    byte[] msgBytes = new byte[data.length - (1 + 8)]; //1 for header and 8 for server-timestamp
                    buffer.get(msgBytes, 0, msgBytes.length);
                    msgBytes = compressor.decompress(msgBytes);
                    event = new DataEvent(header, new String(msgBytes), buffer.getLong());
                } catch (DataFormatException e) {
                    PLog.f(TAG, e.getMessage(), e);
                    if (com.pairapp.BuildConfig.DEBUG) {
                        throw new AssertionError(e);
                    }
                    event = null;
                }
                break;
            case MESSAGE_STATUS_DELIVERED:
            case MESSAGE_STATUS_SEEN:
                byte[] msgId = new byte[data.length - (1 + 8)];
                buffer.get(msgId, 0, msgId.length);
                event = new DataEvent(header, new String(msgId), buffer.getLong());
                break;
            case CALL_PAYLOAD:
                byte[] payload = new byte[data.length - (1 + 8)];
                buffer.get(payload, 0, payload.length);
                event = new DataEvent(header, new String(payload), buffer.getLong());
                break;
            default:
                if (BuildConfig.DEBUG) {
                    throw new AssertionError();
                }
                event = null;

        }
        return event;
    }

    public static class DataEvent {
        private final int opCode;
        @NonNull
        private final String data;
        private final long serverTimeStamp;

        private DataEvent(int opCode, @NonNull String data) {
            this(opCode, data, 0L);
        }

        private DataEvent(int opCode, @NonNull String data, long serverTimeStamp) {
            this.opCode = opCode;
            this.data = data;
            this.serverTimeStamp = serverTimeStamp;
        }

        public long getServerTimeStamp() {
            return serverTimeStamp;
        }

        public int getOpCode() {
            return opCode;
        }

        @NonNull
        public String getData() {
            return data;
        }
    }


    public void close() {
        if (this.onSubscribe.subscriber != null && !this.onSubscribe.subscriber.isUnsubscribed()) {
            this.onSubscribe.subscriber.onCompleted();
            this.onSubscribe.subscriber.unsubscribe();
        }
    }

    public Observable<DataEvent> observe() {
        return internalObservable;
    }

    private final MessagePackerOnSubscribe onSubscribe = new MessagePackerOnSubscribe();
    private final Observable<DataEvent> internalObservable = Observable.create(onSubscribe);

    private static class MessagePackerOnSubscribe implements Observable.OnSubscribe<DataEvent> {

        private Subscriber<? super DataEvent> subscriber;

        @Override
        public void call(Subscriber<? super DataEvent> subscriber) {
            if (this.subscriber != null) {
                throw new IllegalStateException("this observable only allows one observer");
            }
            this.subscriber = subscriber;
        }

        private void onMessageAvailable(DataEvent dataEvent) {
            this.subscriber.onNext(dataEvent);
        }
    }

    public interface Compressor {
        @NonNull
        byte[] compress(@NonNull byte[] input);

        @NonNull
        byte[] decompress(@NonNull byte[] input) throws DataFormatException;
    }
}
