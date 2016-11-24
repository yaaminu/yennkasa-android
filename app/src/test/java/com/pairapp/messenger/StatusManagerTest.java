package com.pairapp.messenger;

import com.pairapp.net.sockets.SendListener;
import com.pairapp.net.sockets.Sendable;
import com.pairapp.net.sockets.Sender;
import com.pairapp.util.Event;
import com.pairapp.util.EventBus;
import com.pairapp.util.PLog;
import com.pairapp.util.ThreadUtils;

import org.bouncycastle.util.encoders.Base64;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.rule.PowerMockRule;

import static com.pairapp.messenger.StatusManager.ON_USER_OFFLINE;
import static com.pairapp.messenger.StatusManager.ON_USER_ONLINE;
import static com.pairapp.messenger.StatusManager.ON_USER_STOP_TYPING;
import static com.pairapp.messenger.StatusManager.ON_USER_TYPING;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotSame;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;


/**
 * @author aminu on 6/29/2016.
 */
@PrepareForTest(ThreadUtils.class)
public class StatusManagerTest {

    private final EventBus bus = EventBus.getDefault();
    private Event statusEvent;
    private boolean listenerCalled;
    private boolean sendCalled;
    byte[] sendArgs;
    private Sender sender = new Sender() {
        @Override
        public void sendMessage(Sendable payload) {
            sendArgs = stringToBytes(payload.getData());
            sendCalled = true;
        }

        @Override
        public boolean unsendMessage(Sendable sendable) {
            return false;
        }

        @Override
        public void updateSentMessage(Sendable sendable) {

        }

        @Override
        public void addSendListener(SendListener sendListener) {

        }

        @Override
        public void removeSendListener(SendListener sendListener) {

        }

        @Override
        public void shutdownSafely() {

        }

        @Override
        public String bytesToString(byte[] data) {
            return new String(Base64.encode(data));

        }

        @Override
        public byte[] stringToBytes(String data) {
            return Base64.decode(data.getBytes());
        }
    };
    private final MessagePacker encoder = MessagePacker.create("1234567890", new ZlibCompressor());
    private StatusManager manager;
    int threadMode = EventBus.ANY;
    @Rule
    public PowerMockRule rule = new PowerMockRule();

    static {
        PLog.setLogLevel(PLog.LEVEL_NONE);
    }


    @Before
    public void setup() throws Exception {
        mockStatic(ThreadUtils.class);
        when(ThreadUtils.isMainThread()).thenReturn(false);
        manager = StatusManager.create(sender, encoder, bus);
        sendCalled = false;
        sendArgs = null;
        listenerCalled = false;
        statusEvent = null;
    }

    EventBus.EventsListener listener = new EventBus.EventsListener() {
        @Override
        public void onEvent(EventBus bus, Event event) {
            listenerCalled = true;
            statusEvent = event;
        }

        @Override
        public boolean sticky() {
            return true;
        }

        @Override
        public int threadMode() {
            return threadMode;
        }
    };

    @Test
    public void testArraySame() throws Exception {
        byte[] arr1 = {1, 2, 3};
        byte[] arr2 = {1, 2, 3};

        assertFalse(isArraySame(null, arr2));
        assertFalse(isArraySame(arr1, null));
        assertTrue(isArraySame(null, null));
        assertTrue(isArraySame(arr1, arr2));

        arr1[1] = 9;
        assertFalse(isArraySame(arr1, arr2));

        arr1[1] = 2;
        assertTrue(isArraySame(arr1, arr2));
        arr2[0] = -1;
        assertFalse(isArraySame(arr1, arr2));
        arr2[0] = 1;
        assertTrue(isArraySame(arr1, arr2));

        byte[] arr3 = {4};
        assertFalse(isArraySame(arr1, arr3));

        assertFalse(isArraySame(arr2, arr3));

    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void testCreate() throws Exception {
        try {
            StatusManager.create(null, null, null);
            fail("must not accept empty  user ids");
        } catch (IllegalArgumentException e) {
            //expected
        }
        try {
            StatusManager.create(null, null, bus);
            fail("must not accsept empty  user ids");
        } catch (IllegalArgumentException e) {
            //expected
        }

        try {
            StatusManager.create(null, encoder, null);
            fail("must not accept user ids that cannot be parsed into numbers");
        } catch (IllegalArgumentException e) {
            //expected
        }

        try {
            StatusManager.create(sender, null, null);
            fail("must not accept null event bus");
        } catch (IllegalArgumentException e) {
            //expected
        }
        StatusManager.create(sender, encoder, bus); //must be able to create the manager without exceptions
    }

    @Test
    public void testStartMonitoringUser() throws Exception {
        sendCalled = false;
        sendArgs = null;
        listenerCalled = false;
        statusEvent = null;
        bus.register(listener, ON_USER_ONLINE, ON_USER_OFFLINE, ON_USER_TYPING);
        String userId = "12345678";
        manager.startMonitoringUser(userId);

        assertTrue("must send the message", sendCalled);
        byte[] startMonitorMessage = encoder.createMonitorMessage(userId, true);
        assertTrue("it must sends the message encoded in the correct format", isArraySame(sendArgs, startMonitorMessage));

        //test that it notifies us about the current state of user we are monitoring

        assertTrue("must post an event to the bus notifying all listeners about the state of the user been monitored", listenerCalled);
        assertFalse("user must be offline", manager.isOnline(userId));
        assertEquals("must post the right event", StatusManager.ON_USER_OFFLINE, statusEvent.getTag());
        assertEquals("must post the right event", userId, statusEvent.getData());

        //take user online
        manager.handleStatusAnnouncement(userId, true);
        assertTrue("user must be online", manager.isOnline(userId));

        //we expect to post the event but we resetting it since we are not testing that functionality here
        statusEvent = null;
        listenerCalled = false;

        manager.startMonitoringUser(userId);
        assertTrue("must post an event to the bus notifying all listeners about the state of the user been monitored", listenerCalled);
        assertTrue("user must be online", manager.isOnline(userId));
        assertEquals("must post the right event", StatusManager.ON_USER_ONLINE, statusEvent.getTag());
        assertEquals("must post the right event", userId, statusEvent.getData());

        //if the user is typing it must tell us its typing rather than online
        manager.handleTypingAnnouncement(userId, true);
        statusEvent = null; //again reset
        listenerCalled = false;

        manager.startMonitoringUser(userId);
        assertTrue("user must be online", manager.isTypingToUs(userId));
        assertTrue("must post an event to the bus notifying all listeners about the state of the user been monitored", listenerCalled);
        assertEquals("must post the right event", StatusManager.ON_USER_TYPING, statusEvent.getTag());
        assertEquals("must post the right event", userId, statusEvent.getData());
    }

    @Test
    public void testAnnounceStatusChange() throws Exception {
        sendCalled = false;
        sendArgs = null;

        manager.announceStatusChange(true);
        assertTrue(sendCalled);
        assertTrue(isArraySame(encoder.createStatusMessage(true), sendArgs));

        //must handle duplicate announcements
        sendCalled = false;
        sendArgs = null;
        manager.announceStatusChange(true);
        assertFalse(sendCalled);
        assertNull(sendArgs);


        manager.announceStatusChange(false);
        assertTrue(sendCalled);
        assertTrue(isArraySame(encoder.createStatusMessage(false), sendArgs));

        sendCalled = false;
        sendArgs = null;
        manager.announceStatusChange(false); //handle duplicate
        assertFalse(sendCalled);
        assertNull(sendArgs);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void testAnnounceStartTyping() throws Exception {
        String userId = "123456789";
        try {
            manager.announceStartTyping("   ");
            fail("it must not allow empty user ids");
        } catch (IllegalArgumentException e) {
            //expected
        }
        try {
            manager.announceStartTyping(null);
            fail("it must not allow null user ids");
        } catch (IllegalArgumentException e) {
            //expected
        }
        manager.announceStartTyping(userId);
        assertEquals("must set typing with to currentUser typing with", userId, manager.getCurrentUserTypingWith());
        assertFalse("must only send when the recipient is known to be online", sendCalled);
        assertNull(sendArgs);


        //bring the user online
        manager.handleStatusAnnouncement(userId, true);


        sendCalled = false;
        sendArgs = null;
        manager.announceStartTyping(userId);
        assertEquals("must set typing with to currentUser typing with", userId, manager.getCurrentUserTypingWith());
        assertTrue("must send when the recipient is  online", sendCalled);
        assertTrue(isArraySame(sendArgs, encoder.createTypingMessage(userId, true)));
    }

    @Test
    public void testAnnounceStopTyping() throws Exception {
        String userId = "123456789";
        try {
            manager.announceStopTyping("   ");
            fail("it must not allow empty user ids");
        } catch (IllegalArgumentException e) {
            //expected
        }
        try {
            //noinspection ConstantConditions
            manager.announceStopTyping(null);
            fail("it must not allow null user ids");
        } catch (IllegalArgumentException e) {
            //expected
        }
        manager.announceStopTyping(userId);
        assertNotSame("must set typing with to currentUser typing with", userId, manager.getCurrentUserTypingWith());
        assertFalse("must only send when the recipient is known to be online", sendCalled);
        assertNull(sendArgs);

        //bring the user online
        manager.handleStatusAnnouncement(userId, true);

        //force this user to type with current user
        manager.announceStartTyping(userId);
        assertEquals(userId, manager.getCurrentUserTypingWith());

        sendCalled = false;
        sendArgs = null;
        manager.announceStopTyping(userId);
        assertNotSame("must set typing with to currentUser typing with", userId, manager.getCurrentUserTypingWith());
        assertTrue("must send when the recipient is  online", sendCalled);
        assertTrue(isArraySame(sendArgs, encoder.createTypingMessage(userId, false)));
    }

    @Test
    public void testGetCurrentUserTypingWith() throws Exception {
        assertNull("user typing with must be null on construction", manager.getCurrentUserTypingWith());
        String userId = "12345678";
        manager.announceStartTyping(userId);
        assertEquals("user typing with must be consistent", userId, manager.getCurrentUserTypingWith());
    }

    @Test
    public void testHandleStatusAnnouncement() throws Exception {
        try {
            //noinspection ConstantConditions
            manager.handleStatusAnnouncement(null, true);
            fail("must throw when a null userId is passed as userId");
        } catch (IllegalArgumentException e) {
            //expected
        }
        try {
            manager.handleStatusAnnouncement("  ", true);
            fail("must throw when an empty userId is passed as userId");
        } catch (IllegalArgumentException e) {
            //expected
        }

        bus.register(listener, ON_USER_ONLINE);
        final String testUserId = "11111111";
        manager.handleStatusAnnouncement(testUserId, true);
        assertTrue("must report user to be online", manager.isOnline(testUserId));
        assertTrue("must post this to the bus", listenerCalled);
        assertEquals(ON_USER_ONLINE, statusEvent.getTag());
        assertEquals(testUserId, statusEvent.getData());
        assertNull(statusEvent.getError());

        statusEvent = null;
        listenerCalled = false;

        //if we are typing with this user and we know very well that this user was offline,
        //we have to tell this client that we are typing since it may have no idea.

        sendCalled = false;
        sendArgs = null;
        manager.handleStatusAnnouncement(testUserId, false); //force this user to be offline
        manager.announceStartTyping(testUserId);
        manager.handleStatusAnnouncement(testUserId, true); //bring the user back online
        assertTrue(sendCalled);//it must attempt to notify it
        assertTrue(isArraySame(sendArgs, encoder.createTypingMessage(testUserId, true))); //it must attempt to notify it

        //on user online event
        assertTrue(listenerCalled);
        assertEquals(ON_USER_ONLINE, statusEvent.getTag());
        assertEquals(testUserId, statusEvent.getData());

        bus.unregister(ON_USER_OFFLINE, listener);
    }

    @Test
    public void testHandleStatusAnnouncement2() throws Exception {
        bus.register(listener, ON_USER_OFFLINE);
        final String testUserId = "11111111";
        manager.handleStatusAnnouncement(testUserId, false);
        assertFalse("must report user to be online", manager.isOnline(testUserId));
        assertTrue("must post this to the bus", listenerCalled);
        assertEquals(ON_USER_OFFLINE, statusEvent.getTag());
        assertEquals(testUserId, statusEvent.getData());
        assertNull(statusEvent.getError());

        //when a user goes offline,it must mark the user as not typing if its typing with us
        manager.handleTypingAnnouncement(testUserId, true); //force the user to type with us
        assertTrue(manager.isTypingToUs(testUserId));
        manager.handleStatusAnnouncement(testUserId, false); //take the user offline
        assertFalse(manager.isTypingToUs(testUserId)); //must set the user to not typing
        bus.unregister(ON_USER_OFFLINE, listener);
    }

    @Test
    public void testHandleTypingAnnouncement1() throws Exception {
        String user = "11111111111";
        bus.register(listener, StatusManager.ON_USER_TYPING);
        try {
            //noinspection ConstantConditions
            manager.handleTypingAnnouncement(null, true);
            fail("must throw when a null userId is passed as userId");
        } catch (IllegalArgumentException e) {
            //expected
        }
        try {
            manager.handleTypingAnnouncement("  ", true);
            fail("must throw when an empty userId is passed as userId");
        } catch (IllegalArgumentException e) {
            //expected
        }

        assertFalse(manager.isTypingToGroup(user, "groupid"));
        assertFalse(manager.isTypingToUs(user));
        assertFalse(manager.isOnline(user));

        manager.handleTypingAnnouncement(user, true);
        assertFalse(manager.isTypingToGroup(user, "groupid"));
        assertTrue("must add user to online list if user is not already known to be online", manager.isOnline(user));
        assertTrue(manager.isTypingToUs(user));

        manager.handleTypingAnnouncement(user, false);
        assertFalse(manager.isTypingToUs(user));
        assertTrue("must not remove user from online list if user stops typing", manager.isOnline(user));

    }


    @Test
    public void testHandleTypingAnnouncement2() throws Exception {
        String user = "11111111111";
        StatusManager manager = StatusManager.create(sender, encoder, bus);
        bus.register(listener, StatusManager.ON_USER_TYPING, ON_USER_STOP_TYPING);

        manager.handleTypingAnnouncement(user, true);
        assertTrue(listenerCalled);
        assertEquals(user, statusEvent.getData());
        assertEquals(ON_USER_TYPING, statusEvent.getTag());
        assertNull(statusEvent.getError());

        statusEvent = null;
        listenerCalled = false;
        manager.handleTypingAnnouncement(user, false);
        assertTrue(listenerCalled);
        assertEquals(user, statusEvent.getData());
        assertEquals(ON_USER_STOP_TYPING, statusEvent.getTag());
        assertNull(statusEvent.getError());
    }

    @Test
    public void testIsOnline() throws Exception {
        bus.register(listener, ON_USER_ONLINE);
        String user = "3333333";
        manager.handleStatusAnnouncement(user, false);
        assertFalse(manager.isOnline(user));
        manager.handleStatusAnnouncement(user, true);
        assertTrue(manager.isOnline(user));
        manager.handleStatusAnnouncement(user, false);
        assertFalse(manager.isOnline(user));
        manager.handleTypingAnnouncement(user, true);
        assertTrue(manager.isOnline(user));
    }

    @Test
    public void testIsTypingToUs() throws Exception {
        bus.register(listener, ON_USER_TYPING);
        String user = "3333333";
        manager.handleTypingAnnouncement(user, false);
        assertFalse(manager.isTypingToUs(user));
        manager.handleTypingAnnouncement(user, true);
        assertTrue(manager.isTypingToUs(user));
    }

    @Test
    public void testIsTypingToGroup() throws Exception {
        fail("unimplemented");
    }

    static boolean isArraySame(byte[] expected, byte[] actual) {
        if (expected == null || actual == null) {
            return expected == null && actual == null;
        }
        if (expected.length != actual.length) {
            return false;
        }
        int i = 0;
        while (i < expected.length && expected[i] == actual[i]) {
            i++;
        }
        return i == expected.length;
    }
}
