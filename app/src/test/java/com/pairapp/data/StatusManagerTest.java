package com.pairapp.data;

import android.support.v4.util.Pair;

import com.pairapp.util.EventBus;
import com.pairapp.util.PLog;
import com.pairapp.util.ThreadUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.rule.PowerMockRule;

import java.util.concurrent.atomic.AtomicBoolean;

import static com.pairapp.data.StatusManager.ANNOUNCE_ONLINE;
import static com.pairapp.data.StatusManager.ANNOUNCE_TYPING;
import static com.pairapp.data.StatusManager.ON_USER_OFFLINE;
import static com.pairapp.data.StatusManager.ON_USER_ONLINE;
import static com.pairapp.data.StatusManager.ON_USER_STOP_TYPING;
import static com.pairapp.data.StatusManager.ON_USER_TYPING;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
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

    private final EventBus bus = new EventBus();
    private EventBus.Event statusEvent;
    private boolean listenerCalled;
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
    }

    EventBus.EventsListener listener = new EventBus.EventsListener() {
        @Override
        public void onEvent(EventBus bus, EventBus.Event event) {
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
    public void testCreate() throws Exception {
        try {
            StatusManager.create("", bus);
            fail("must not accept empty  user ids");
        } catch (IllegalArgumentException e) {
            //expected
        }
        try {
            StatusManager.create("   ", bus);
            fail("must not accept empty  user ids");
        } catch (IllegalArgumentException e) {
            //expected
        }
        try {
            StatusManager.create(null, bus);
            fail("must not accept null user ids");
        } catch (IllegalArgumentException e) {
            //expected
        }
        try {
            StatusManager.create("ab123", bus);
            fail("must not accept user ids that cannot be parsed into numbers");
        } catch (IllegalArgumentException e) {
            //expected
        }
        try {
            StatusManager.create("1233 a", bus);
            fail("must not accept user ids that cannot be parsed into numbers");
        } catch (IllegalArgumentException e) {
            //expected
        }
        try {
            StatusManager.create("2332445830", null);
            fail("must not accept null event bus");
        } catch (IllegalArgumentException e) {
            //expected
        }
        StatusManager.create("2332445830", bus);
    }

    @Test
    public void testAnnounceStatusChange() throws Exception {
        StatusManager manager = StatusManager.create("2332445839", bus);
        try {
            StatusManager.create("23325555", bus).announceStatusChange(true);
            fail("must throw if there is no listener for the posted message");
        } catch (IllegalStateException e) {
            //expected
        }

        bus.register(ANNOUNCE_ONLINE, listener);
        manager.announceStatusChange(true);
        assertTrue(listenerCalled);
        assertEquals("invalid event tag", ANNOUNCE_ONLINE, statusEvent.tag);
        assertEquals("error must be null", null, statusEvent.error);
        assertTrue("data must be true to signify online", (Boolean) statusEvent.data);


        listenerCalled = false;
        statusEvent = null;
        manager.announceStatusChange(true);
        assertFalse("if we already online it must not announce online events", listenerCalled);
        assertNull("if we already online it must not announce online events", statusEvent);

        manager.announceStatusChange(false);
        assertTrue(listenerCalled);
        assertEquals("invalid event tag", ANNOUNCE_ONLINE, statusEvent.tag);
        assertEquals("error must be null", null, statusEvent.error);
        assertFalse("data must be false to signify offline", (Boolean) statusEvent.data);


        listenerCalled = false;
        statusEvent = null;
        manager.announceStatusChange(false);
        assertFalse("if we already offline it must not announce offline events", listenerCalled);
        assertNull("if we already offline it must not announce offline events", statusEvent);
    }

    @Test
    public void testAnnounceStartTyping() throws Exception {
        String userId = "123456789";
        StatusManager manager = StatusManager.create(userId, bus);
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
        bus.register(ANNOUNCE_TYPING, listener);
        manager.announceStartTyping(userId);
        assertTrue(listenerCalled);
        assertEquals("invalid event tag", ANNOUNCE_TYPING, statusEvent.tag);
        assertEquals("error must be null", null, statusEvent.error);
        //noinspection unchecked
        assertTrue("data must be true to signify typing", ((Pair<String, Boolean>) statusEvent.data).second);
        //noinspection unchecked
        assertEquals("data must be true to signify typing", userId, ((Pair<String, Boolean>) statusEvent.data).first);
        assertEquals("must set typing with to currentUser typing with", userId, manager.getCurrentUserTypingWith());

    }

    @Test
    public void testAnnounceStopTyping() throws Exception {
        String userId = "123456789";
        StatusManager manager = StatusManager.create(userId, bus);
        try {
            manager.announceStopTyping("   ");
            fail("it must not allow empty user ids");
        } catch (IllegalArgumentException e) {
            //expected
        }
        try {
            manager.announceStopTyping(null);
            fail("it must not allow null user ids");
        } catch (IllegalArgumentException e) {
            //expected
        }
        bus.register(ANNOUNCE_TYPING, listener);
        manager.announceStopTyping(userId);
        assertTrue(listenerCalled);
        assertEquals("invalid event tag", ANNOUNCE_TYPING, statusEvent.tag);
        assertEquals("error must be null", null, statusEvent.error);
        //noinspection unchecked
        assertFalse("data must be false to signify not typing", ((Pair<String, Boolean>) statusEvent.data).second);
        //noinspection unchecked
        assertEquals("userId must be consistent", userId, ((Pair<String, Boolean>) statusEvent.data).first);
        assertNull("must typing with to null", manager.getCurrentUserTypingWith());
        assertFalse("must set typing with to null", userId.equals(manager.getCurrentUserTypingWith()));

        manager.announceStartTyping(userId);
        manager.announceStopTyping("1111111111");
        assertNotNull("must not set user typing to null after announceTypingWith with different userId", manager.getCurrentUserTypingWith());

        manager.announceStopTyping(userId);
        assertFalse("must set typing with to null after announceTypingWith is called with same id", userId.equals(manager.getCurrentUserTypingWith()));
    }

    @Test
    public void testGetCurrentUserTypingWith() throws Exception {
        StatusManager manager = StatusManager.create("1234556", bus);
        assertNull("user typing with must be null on construction", manager.getCurrentUserTypingWith());
        bus.register(listener, ANNOUNCE_TYPING);
        String userId = "12345678";
        manager.announceStartTyping(userId);
        assertEquals("user typing with must be consistent", userId, manager.getCurrentUserTypingWith());

        manager.announceStopTyping(userId);
        assertNull("must set user typing to null after announceTypingWith with same userId", manager.getCurrentUserTypingWith());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testHandleStatusAnnouncement() throws Exception {
        StatusManager manager = StatusManager.create("1234566", bus);
        try {
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

        bus.register(listener, ON_USER_OFFLINE, ON_USER_ONLINE);
        final String testUserId = "11111111";
        manager.handleStatusAnnouncement(testUserId, true);
        assertTrue("must report user to be online", manager.isOnline(testUserId));
        assertTrue("must post this to the bus", listenerCalled);
        assertEquals(ON_USER_ONLINE, statusEvent.tag);
        assertEquals(testUserId, statusEvent.data);
        assertNull(statusEvent.error);

        statusEvent = null;
        listenerCalled = false;
        final AtomicBoolean secondListenerCalled = new AtomicBoolean(false);//atomic boolean so that we can mutate it
        //if we are typing with this user and we know very well that this user was offline,
        //we have to tell this client that we are typing since it may have no idea.
        bus.register(new EventBus.EventsListener() {
            @Override
            public int threadMode() {
                return EventBus.ANY;
            }

            @Override
            public void onEvent(EventBus yourBus, EventBus.Event event) {
                secondListenerCalled.set(true);
                assertEquals(ANNOUNCE_TYPING, event.tag);
                assertEquals(testUserId, ((Pair<String, Boolean>) event.data).first);
                assertTrue(((Pair<String, Boolean>) event.data).second);
            }

            @Override
            public boolean sticky() {
                return false;
            }
        }, ANNOUNCE_TYPING);
        manager.handleStatusAnnouncement(testUserId, false); //false this user to be offline
        manager.announceStartTyping(testUserId);
        manager.handleStatusAnnouncement(testUserId, true);

        assertTrue(secondListenerCalled.get());

        //on user online event
        assertTrue(listenerCalled);
        assertEquals(ON_USER_ONLINE, statusEvent.tag);
        assertEquals(testUserId, statusEvent.data);
    }

    @Test
    public void testHandleStatusAnnouncement2() throws Exception {
        StatusManager manager = StatusManager.create("1234566", bus);
        bus.register(listener, ON_USER_OFFLINE, ON_USER_ONLINE);
        final String testUserId = "11111111";
        manager.handleStatusAnnouncement(testUserId, false);
        assertFalse("must report user to be online", manager.isOnline(testUserId));
        assertTrue("must post this to the bus", listenerCalled);
        assertEquals(ON_USER_OFFLINE, statusEvent.tag);
        assertEquals(testUserId, statusEvent.data);
        assertNull(statusEvent.error);

        statusEvent = null;
        listenerCalled = false;
        final AtomicBoolean secondListenerCalled = new AtomicBoolean(false);//atomic boolean so that we can mutate it

        //if we are typing with this user and we know very well that this user was offline,
        //we have to tell this client that we are typing since it may have no idea.
        bus.register(new EventBus.EventsListener() {
            @Override
            public int threadMode() {
                return EventBus.ANY;
            }

            @Override
            public void onEvent(EventBus yourBus, EventBus.Event event) {
                secondListenerCalled.set(true);
            }

            @Override
            public boolean sticky() {
                return false;
            }
        }, ANNOUNCE_TYPING);
        manager.announceStartTyping(testUserId);
        secondListenerCalled.set(false); //calling announceStartTyping will invoke the listener so we need to reset it
        manager.handleStatusAnnouncement(testUserId, false);

        assertFalse(secondListenerCalled.get());
        //on user online event
        assertTrue(listenerCalled);
        assertEquals(ON_USER_OFFLINE, statusEvent.tag);
        assertEquals(testUserId, statusEvent.data);
    }

    @Test
    public void handleStatusAnnouncement3() throws Exception {
        String currentUser = "12223333";
        StatusManager manager = StatusManager.create("122333", bus);
        bus.register(listener, ANNOUNCE_TYPING, ANNOUNCE_ONLINE, ON_USER_OFFLINE, ON_USER_ONLINE);
        assertFalse(manager.isOnline(currentUser));
        assertFalse(manager.isTypingToUs(currentUser));
        assertFalse(manager.isTypingToGroup(currentUser, "someGroup"));

        manager.handleStatusAnnouncement(currentUser, false);
        assertFalse(manager.isOnline(currentUser));
        assertFalse(manager.isTypingToUs(currentUser));
        assertFalse(manager.isTypingToGroup(currentUser, "someGroup"));

        manager.handleStatusAnnouncement(currentUser, true);
        assertTrue(manager.isOnline(currentUser));

        manager.handleTypingAnnouncement(currentUser, true);

        manager.handleStatusAnnouncement(currentUser, false);
        assertFalse("must be offline", manager.isOnline(currentUser));
        assertFalse("if user goes offline, it must also be removed from the typing list", manager.isTypingToUs(currentUser));
        assertFalse("if user goes offline it must also be removed from the typing list", manager.isTypingToGroup(currentUser, "some groupId"));

        bus.unregister(ANNOUNCE_ONLINE, listener);
        bus.unregister(ANNOUNCE_TYPING, listener);
        bus.unregister(ON_USER_ONLINE, listener);
        bus.unregister(ON_USER_OFFLINE, listener);
    }

    @Test
    public void testHandleTypingAnnouncement1() throws Exception {
        String user = "11111111111";
        StatusManager manager = StatusManager.create("88877744747", bus);
        bus.register(listener, StatusManager.ON_USER_TYPING);
        try {
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
        StatusManager manager = StatusManager.create("88877744747", bus);
        bus.register(listener, StatusManager.ON_USER_TYPING, ON_USER_STOP_TYPING);

        manager.handleTypingAnnouncement(user, true);
        assertTrue(listenerCalled);
        assertEquals(user, statusEvent.data);
        assertEquals(ON_USER_TYPING, statusEvent.tag);
        assertNull(statusEvent.error);

        statusEvent = null;
        listenerCalled = false;
        manager.handleTypingAnnouncement(user, false);
        assertTrue(listenerCalled);
        assertEquals(user, statusEvent.data);
        assertEquals(ON_USER_STOP_TYPING, statusEvent.tag);
        assertNull(statusEvent.error);
    }

    @Test
    public void testIsOnline() throws Exception {
        StatusManager manager = StatusManager.create("333333", bus);
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
        StatusManager manager = StatusManager.create("333333", bus);
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
}
