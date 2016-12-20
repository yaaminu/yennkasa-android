package com.pairapp.messenger;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import rx.Subscriber;
import rx.Subscription;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author aminu on 6/28/2016.
 */

public class MessagePackerTest {

    private final ZlibCompressor compressor = new ZlibCompressor();
    private boolean onCompleteCalled = false;
    private final Subscriber<MessagePacker.DataEvent> subscriber = new Subscriber<MessagePacker.DataEvent>() {
        @Override
        public void onCompleted() {
            System.out.println("completed");
            onCompleteCalled = true;
        }

        @Override
        public void onError(Throwable e) {
            e.printStackTrace();
        }

        @Override
        public void onNext(MessagePacker.DataEvent event) {
            dataEvent = event;
        }
    };
    private MessagePacker messagePacker;

    @Before
    public void setUp() throws Exception {
        onCompleteCalled = false;
        messagePacker = MessagePacker.create("233266349205", new ZlibCompressor());
    }

    @After
    public void tearDown() throws Exception {
        if (messagePacker != null) {
            messagePacker.close();
        }
    }

    @Test
    public void testCreate() throws Exception {
        try {
            messagePacker = MessagePacker.create("2222a", new ZlibCompressor());
            fail("must throw");
        } catch (NumberFormatException expected) {
            //better
        }
        messagePacker = MessagePacker.create("233266564229", new ZlibCompressor());
        assertNotNull(messagePacker);
    }

    @Test
    public void testPack() throws Exception {
        try {
            messagePacker.packNormalMessage("hello world", "132fgroup", false);
            fail("if a message is not a group message, it should not allow ids that cannot be coalesced into long");
        } catch (NumberFormatException e) {
            //expected
        }
        try {
            messagePacker.packNormalMessage("hello world", "aaffa", false);
            fail("if a message is not a group message, it should not allow ids that cannot be coalesced into long");
        } catch (NumberFormatException e) {
            //expected
        }
        try {
            messagePacker.packNormalMessage("hello world", "group", true);
            fail("if a message is a group message, it should not allow a recipient id less than 8 bytes");
        } catch (IllegalArgumentException e) {
            //expected
        }
        try {
            messagePacker.packNormalMessage("hello world", "group-namewhichislongenough", true);
            fail("if a message is a group message, it should not allow a recipient id to contain the dash character");
        } catch (IllegalArgumentException e) {
            //expected
        }
        JSONObject object = new JSONObject();
        object.put("from", "1234567890");
        long recipient = 987654321;
        object.put("type", 10);
        object.put("messageBody", "hello world");
        ByteBuffer buffer = ByteBuffer.wrap(messagePacker.packNormalMessage(object.toString(), recipient + "", false));
        buffer.order(ByteOrder.BIG_ENDIAN);
        assertEquals("length is invalid", 11 + compressor.compress(object.toString().getBytes()).length, buffer.array().length);
        assertEquals("message header must be persistable", 0x4, buffer.get());
        long actual = buffer.getLong();
        assertEquals("recipient inconsistent", recipient, actual);
        assertEquals("must delimit header with a dash", '-', buffer.get());
        assertEquals("message header for client invalid", MessagePacker.READABLE_MESSAGE, buffer.get());

        //group messages
        String recipientGroup = "brothersfromonehood";
        buffer = ByteBuffer.wrap(messagePacker.packNormalMessage(object.toString(), recipientGroup, true));
        buffer.order(ByteOrder.BIG_ENDIAN);
        assertEquals("length is invalid", 3 + recipientGroup.getBytes().length + compressor.compress(object.toString().getBytes()).length, buffer.array().length);
        assertEquals("message header must be persistable", 0x4, buffer.get());
        byte[] groupId = new byte[recipientGroup.getBytes().length];
        buffer.get(groupId);
        assertEquals("recipient inconsistent", recipientGroup, new String(groupId));
        assertEquals("must delimit header with a dash", '-', buffer.get());
        assertEquals("message header for client invalid", MessagePacker.READABLE_MESSAGE, buffer.get());
    }

    @Test
    public void testCreateStatusMessage() throws Exception {
        byte[] statusMsg = messagePacker.createStatusMessage(false);
        assertEquals(statusMsg[0], 0x10);
        assertEquals(statusMsg[1], '-');
        assertEquals(statusMsg[statusMsg.length - 1], 0x1);
        assertEquals(statusMsg.length, 3);
        statusMsg = messagePacker.createStatusMessage(true);
        assertEquals(statusMsg[0], 0x10);
        assertEquals(statusMsg[1], '-');
        assertEquals(statusMsg[statusMsg.length - 1], 0x2);
        assertEquals(statusMsg.length, 3);
    }

    @Test
    public void testCreateTypingMessage() throws Exception {
        byte[] statusMsg = messagePacker.createTypingMessage("12345876", false);
        ByteBuffer buffer = ByteBuffer.wrap(statusMsg);
        buffer.order(ByteOrder.BIG_ENDIAN);
        assertEquals(buffer.get(), 0x1);
        assertEquals(12345876, buffer.getLong());
        assertEquals(buffer.get(), '-');
        assertEquals(buffer.get(), MessagePacker.NOT_TYPING);
        assertEquals(buffer.getLong(), 233266349205L);
        assertEquals(statusMsg.length, 19);
    }

    @Test
    public void testCreateTypingMessage1() throws Exception {
        try {
            messagePacker.createTypingMessage("aff", false);
            fail("must not allow recipients not longer than 8 chars");
        } catch (IllegalArgumentException expected) {
            //better
        }
        try {
            messagePacker.createTypingMessage("", false);
            fail("must not allow recipients not longer than 8 chars");
        } catch (IllegalArgumentException expected) {
            //better
        }
        byte[] statusMsg = messagePacker.createTypingMessage("afolanya@2000", false);
        ByteBuffer buffer = ByteBuffer.wrap(statusMsg);
        assertEquals(0x2, buffer.get() & 0x2);
        byte[] nameBytes = new byte[13];
        assertEquals(statusMsg.length, 11 + nameBytes.length);
        buffer.get(nameBytes);
        assertEquals("afolanya@2000", new String(nameBytes));
        assertEquals(buffer.get(), '-');
        assertEquals(buffer.get(), MessagePacker.NOT_TYPING);
        assertEquals(buffer.getLong(), 233266349205L);
    }

    @Test
    public void testCreateMsgStatusMessage() throws Exception {
        String msgId = "hello@2000";
        byte[] packed = messagePacker.createMsgStatusMessage("1234567890", msgId, true);
        assertEquals(packed.length, 11 + msgId.getBytes().length);
        ByteBuffer buffer = ByteBuffer.wrap(packed);
        assertEquals(0x4, buffer.get(0) & 0x4);
        assertEquals(0x8, buffer.get(0) & 0x8);
        assertEquals((0x4 | 0x8), buffer.get());
        assertEquals(Long.parseLong("1234567890"), buffer.getLong());
        assertEquals('-', buffer.get());
        assertEquals(MessagePacker.MESSAGE_STATUS_DELIVERED, buffer.get());
        byte[] idbytes = new byte[msgId.getBytes().length];
        buffer.get(idbytes);
        assertEquals(msgId, new String(idbytes));

        packed = messagePacker.createMsgStatusMessage("1234567890", msgId, false);
        assertEquals(packed.length, 11 + msgId.getBytes().length);
        buffer = ByteBuffer.wrap(packed);
        assertEquals(0x4, buffer.get(0) & 0x4);
        assertEquals(0x8, buffer.get(0) & 0x8);
        assertEquals(0x8 | 0x4, buffer.get());
        assertEquals(Long.parseLong("1234567890"), buffer.getLong());
        assertEquals('-', buffer.get());
        assertEquals(MessagePacker.MESSAGE_STATUS_SEEN, buffer.get());
        idbytes = new byte[msgId.getBytes().length];
        buffer.get(idbytes);
        assertEquals(msgId, new String(idbytes));
    }

    private MessagePacker.DataEvent dataEvent;

    @Test
    public void testUnpack() throws Exception {


        try {
            messagePacker.unpack(new byte[0]);
            fail("must throw if there is no subscriber");
        } catch (IllegalStateException expected) {
            //better
        }
        Subscription subscription = messagePacker.observe().subscribe(subscriber);
        try {
            messagePacker.unpack(new byte[0]);
            fail("must handle invalid byte streams");
        } catch (IllegalArgumentException expected) {
            //better
        }
        try {
            messagePacker.unpack(null);
            fail("must handle invalid byte streams");
        } catch (IllegalArgumentException expected) {
            //better
        }


        //test online
        testOnline();

        //test offline
        testOffline();

        testMsgStatus();

        testTyping();

        testReadableMessage();
        testCallPushMessage();
        try {
            subscription.unsubscribe();
            messagePacker.unpack(new byte[9]);
            fail("must throw if unpack is called without a subscribed observer");
        } catch (IllegalStateException e) {
            //better
        }
    }

    private void testCallPushMessage() {
        byte[] message = "hello world".getBytes();
        ByteBuffer buffer = ByteBuffer.allocate(1 + message.length + 8); //8 for timestamp
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.put(MessagePacker.CALL_PAYLOAD);
        buffer.put(message);
        long timestamp = System.currentTimeMillis();
        buffer.putLong(timestamp);
        messagePacker.unpack(buffer.array());
        assertEquals("opcode must be callpayload", MessagePacker.CALL_PAYLOAD, dataEvent.getOpCode());
        assertEquals("inconsistent message body", new String(message), dataEvent.getData());
        assertEquals("inconsistent timestamp", timestamp, dataEvent.getServerTimeStamp());
    }

    private void testOffline() {
        ByteBuffer buffer = ByteBuffer.allocate(9);
        int recipient;
        buffer.put(0, (byte) 0x1);
        recipient = 123456789;
        buffer.putLong(1, recipient);

        messagePacker.unpack(buffer.array());

        assertEquals(dataEvent.getOpCode(), 0x1);
        assertEquals("" + recipient, dataEvent.getData());
    }

    private void testMsgStatus() {
        byte[] idBytes = "msgId".getBytes();
        ByteBuffer buffer = ByteBuffer.allocate(idBytes.length + 1 + 8);//1 for header, 8 for timestamp
        buffer.put(MessagePacker.MESSAGE_STATUS_DELIVERED);
        buffer.put(idBytes);
        long timestamp = System.currentTimeMillis();
        buffer.putLong(timestamp);

        messagePacker.unpack(buffer.array());

        assertEquals(dataEvent.getOpCode(), MessagePacker.MESSAGE_STATUS_DELIVERED);
        assertEquals("inconsistent timestamp", timestamp, dataEvent.getServerTimeStamp());

        assertEquals("msgId", dataEvent.getData());
        buffer = ByteBuffer.allocate(idBytes.length + 1 + 8);
        buffer.put(MessagePacker.MESSAGE_STATUS_SEEN);
        buffer.put(idBytes);
        buffer.putLong(timestamp);


        messagePacker.unpack(buffer.array());

        assertEquals(dataEvent.getOpCode(), MessagePacker.MESSAGE_STATUS_SEEN);
        assertEquals("inconsistent timestamp", timestamp, dataEvent.getServerTimeStamp());
        assertEquals("msgId", dataEvent.getData());
    }

    private void testOnline() {
        ByteBuffer buffer = ByteBuffer.allocate(9);
        buffer.put((byte) 0x2);
        int recipient = 123456789;
        buffer.putLong(recipient);


        messagePacker.unpack(buffer.array());

        assertEquals(dataEvent.getOpCode(), 0x2);
        assertEquals("" + recipient, dataEvent.getData());
    }

    private void testTyping() {
        ByteBuffer buffer = ByteBuffer.allocate(9);
        buffer.put(MessagePacker.TYPING);
        int recipient = 123456789;
        buffer.putLong(recipient);

        messagePacker.unpack(buffer.array());

        assertEquals(dataEvent.getOpCode(), MessagePacker.TYPING);
        assertEquals("" + recipient, dataEvent.getData());

        buffer = ByteBuffer.allocate(9);
        buffer.put(MessagePacker.NOT_TYPING);
        buffer.putLong(recipient);

        messagePacker.unpack(buffer.array());

        assertEquals(dataEvent.getOpCode(), MessagePacker.NOT_TYPING);
        assertEquals("" + recipient, dataEvent.getData());
    }

    private void testReadableMessage() throws Exception {
        byte[] message = compressor.compress("hello world".getBytes());
        ByteBuffer buffer = ByteBuffer.allocate(1 + message.length + 8); //8 for timestamp
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.put(MessagePacker.READABLE_MESSAGE);
        buffer.put(message);
        long timestamp = System.currentTimeMillis();
        buffer.putLong(timestamp);
        messagePacker.unpack(buffer.array());
        assertEquals("opcode must be readableMessage", MessagePacker.READABLE_MESSAGE, dataEvent.getOpCode());
        assertEquals("inconsistent message body", new String(compressor.decompress(message)), dataEvent.getData());
        assertEquals("inconsistent timestamp", timestamp, dataEvent.getServerTimeStamp());
    }

    @Test
    public void testCreateMonitorMessage() {
        try {
            messagePacker.createMonitorMessage("4455kf", true);
            fail("must throw when provided with invalid user ids that cannot be coalesced into a number");
        } catch (NumberFormatException e) {
            //better
        }
        try {
            messagePacker.createMonitorMessage("4455kf", false);
            fail("must throw when provided with invalid user ids that cannot be coalesced into a number");
        } catch (NumberFormatException e) {
            //better
        }

        try {
            messagePacker.createMonitorMessage("-12332", true);
            fail("must throw when provided with invalid user ids that produce negative nsumbers");
        } catch (IllegalArgumentException e) {
            //better
        }
        try {
            messagePacker.createMonitorMessage("-12332", false);
            fail("must throw when provided with invalid user ids that produce negative nsumbers");
        } catch (IllegalArgumentException e) {
            //better
        }
        try {
            messagePacker.createMonitorMessage("0", true);
            fail("must throw when provided with invalid user ids that produce 0");
        } catch (IllegalArgumentException e) {
            //better
        }
        try {
            messagePacker.createMonitorMessage("0", false);
            fail("must throw when provided with invalid user ids that produce 0");
        } catch (IllegalArgumentException e) {
            //better
        }
        long target = 1234567890;
        byte[] data = messagePacker.createMonitorMessage("" + target, true);
        assertEquals(data.length, 11);
        ByteBuffer buffer = ByteBuffer.wrap(data);
        assertEquals(buffer.get(), 0x10);
        assertEquals(buffer.getLong(), target);
        assertEquals(buffer.get(), MessagePacker.HEADER_DELIMITER);
        assertEquals(buffer.get(), MessagePacker.MONITOR_START);

        data = messagePacker.createMonitorMessage("" + target, false);
        assertEquals(data.length, 11);
        buffer = ByteBuffer.wrap(data);
        assertEquals(buffer.get(), 0x10);
        assertEquals(buffer.getLong(), target);
        assertEquals(buffer.get(), MessagePacker.HEADER_DELIMITER);
        assertEquals(buffer.get(), MessagePacker.MONITOR_STOP);
    }

    @Test
    public void testPackCallMessage() throws Exception {
        String recipient = "266349205";
        String payload = "somepayload";
        byte[] packed = messagePacker.packCallMessage(recipient, payload);
        assertEquals(packed.length, 1 +/*header-server*/
                1 +/*header delimiter*/
                1 + /*client header*/
                recipient.getBytes().length + payload.getBytes().length);
        ByteBuffer buffer = ByteBuffer.wrap(packed);
        assertEquals(0x2, buffer.get(0) & 0x2);
        assertEquals(0x8, buffer.get(0) & 0x8);
        assertEquals((0x2 | 0x8), buffer.get());
        byte[] recBytes = new byte[recipient.getBytes().length];
        buffer.get(recBytes);
        assertEquals(recipient, new String(recBytes));
        assertEquals('-', buffer.get());
        assertEquals(0x9, buffer.get());
        byte[] payloadBytes = new byte[payload.getBytes().length];
        buffer.get(payloadBytes);
        assertEquals(payload, new String(payloadBytes));
    }

    @Test
    public void testClose() throws Exception {
        //close should be able to detect that there is no subscriber and ignore unsubscription
        messagePacker.close();
        assertFalse(onCompleteCalled);

        messagePacker.observe().subscribe(subscriber);
        messagePacker.close();
        assertTrue(onCompleteCalled);
    }

    @Test
    public void testObserve() throws Exception {
        messagePacker.observe().subscribe(subscriber);
    }
}
