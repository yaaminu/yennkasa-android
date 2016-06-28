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
        messagePacker = MessagePacker.create("233266349205");
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
            messagePacker = MessagePacker.create("2222a");
            fail("must throw");
        } catch (NumberFormatException expected) {
            //better
        }
        messagePacker = MessagePacker.create("233266564229");
        assertNotNull(messagePacker);
    }

    @Test
    public void testPack() throws Exception {
        try {
            messagePacker.pack("hello world", "132fgroup", false);
            fail("if a message is not a group message, it should not allow ids that cannot be coalesced into long");
        } catch (NumberFormatException e) {
            //expected
        }
        try {
            messagePacker.pack("hello world", "aaffa", false);
            fail("if a message is not a group message, it should not allow ids that cannot be coalesced into long");
        } catch (NumberFormatException e) {
            //expected
        }
        try {
            messagePacker.pack("hello world", "group", true);
            fail("if a message is a group message, it should not allow a recipient id less than 8 bytes");
        } catch (IllegalArgumentException e) {
            //expected
        }
        try {
            messagePacker.pack("hello world", "group-namewhichislongenough", true);
            fail("if a message is a group message, it should not allow a recipient id to contain the dash character");
        } catch (IllegalArgumentException e) {
            //expected
        }
        JSONObject object = new JSONObject();
        object.put("from", "1234567890");
        long recipient = 987654321;
        object.put("type", 10);
        object.put("messageBody", "hello world");
        ByteBuffer buffer = ByteBuffer.wrap(messagePacker.pack(object.toString(), recipient + "", false));
        buffer.order(ByteOrder.BIG_ENDIAN);
        assertEquals("length is invalid", 11 + object.toString().getBytes().length, buffer.array().length);
        assertEquals("message header must be persistable", 0x4, buffer.get());
        assertEquals("recipient inconsistent", recipient, buffer.getLong());
        assertEquals("must delimit header with a dash", '-', buffer.get());
        assertEquals("message header for client invalid", MessagePacker.READABLE_MESSAGE, buffer.get());
        byte[] body = new byte[object.toString().getBytes().length];
        buffer.get(body);
        assertEquals("message body left", object.toString(), new String(body));

        //group messages
        String recipientGroup = "brothersfromonehood";
        buffer = ByteBuffer.wrap(messagePacker.pack(object.toString(), recipientGroup, true));
        buffer.order(ByteOrder.BIG_ENDIAN);
        assertEquals("length is invalid", 3 + recipientGroup.getBytes().length + object.toString().getBytes().length, buffer.array().length);
        assertEquals("message header must be persistable", 0x4, buffer.get());
        byte[] groupId = new byte[recipientGroup.getBytes().length];
        buffer.get(groupId);
        assertEquals("recipient inconsistent", recipientGroup, new String(groupId));
        assertEquals("must delimit header with a dash", '-', buffer.get());
        assertEquals("message header for client invalid", MessagePacker.READABLE_MESSAGE, buffer.get());
        body = new byte[object.toString().getBytes().length];
        buffer.get(body);
        assertEquals("message body left", object.toString(), new String(body));
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
        byte[] statusMsg = messagePacker.createTypingMessage(12345876, false);
        ByteBuffer buffer = ByteBuffer.wrap(statusMsg);
        assertEquals(buffer.get(), 0x1);
        assertEquals(buffer.getLong(), 12345876);
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
        assertEquals(0x2, buffer.get(0) & 0x2);
        assertEquals(0x8, buffer.get(0) & 0x8);
        assertEquals(0xa, buffer.get());
        assertEquals(Long.parseLong("1234567890"), buffer.getLong());
        assertEquals('-', buffer.get());
        assertEquals(MessagePacker.MESSAGE_STATUS_DELIVERED, buffer.get());
        byte[] idbytes = new byte[msgId.getBytes().length];
        buffer.get(idbytes);
        assertEquals(msgId, new String(idbytes));

        packed = messagePacker.createMsgStatusMessage("1234567890", msgId, false);
        assertEquals(packed.length, 11 + msgId.getBytes().length);
        buffer = ByteBuffer.wrap(packed);
        assertEquals(0x2, buffer.get(0) & 0x2);
        assertEquals(0x8, buffer.get(0) & 0x8);
        assertEquals(0xa, buffer.get());
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


        Subscription subscription = messagePacker.observe().subscribe(subscriber);
        //test online
        testOnline();

        //test offline
        testOffline();

        testMsgStatus();

        testTyping();

        testReadableMessage();
        try {
            subscription.unsubscribe();
            messagePacker.unpack(new byte[9]);
            fail("must throw if unpack is called without a subscribed observer");
        } catch (IllegalStateException e) {
            //better
        }
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
        ByteBuffer buffer = ByteBuffer.allocate(idBytes.length + 1);
        buffer.put(MessagePacker.MESSAGE_STATUS_DELIVERED);

        buffer.put(idBytes);

        messagePacker.unpack(buffer.array());

        assertEquals(dataEvent.getOpCode(), MessagePacker.MESSAGE_STATUS_DELIVERED);
        assertEquals("msgId", dataEvent.getData());
        buffer = ByteBuffer.allocate(idBytes.length + 1);
        buffer.put(MessagePacker.MESSAGE_STATUS_SEEN);
        buffer.put(idBytes);

        messagePacker.unpack(buffer.array());

        assertEquals(dataEvent.getOpCode(), MessagePacker.MESSAGE_STATUS_SEEN);
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

    private void testReadableMessage() {
        byte[] message = "hello world".getBytes();
        ByteBuffer buffer = ByteBuffer.allocate(5 + message.length);
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.put(MessagePacker.READABLE_MESSAGE);
        buffer.put(message);
        buffer.putInt(123); //the order of the message
        messagePacker.unpack(buffer.array());
        assertEquals("opcode must be readableMessage", MessagePacker.READABLE_MESSAGE, dataEvent.getOpCode());
        assertEquals("inconsistent message body", new String(message), dataEvent.getData());
        assertEquals("must retrieve the position of this message", 123, dataEvent.getCursorPos());
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
