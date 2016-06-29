package com.pairapp.util;

import android.os.Looper;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.rule.PowerMockRule;

import static com.pairapp.util.Echo.echo;
import static com.pairapp.util.EventBus.EventsListener;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * author Null-Pointer on 1/16/2016.
 */
@PrepareForTest(Looper.class)
public class EventBusTest {

    EventBus bus;
    boolean called = false;
    private EventBus.Event testEvent = new EventBus.Event("tag", null, null); //various test can change the way they like
    int threadMode = EventBus.ANY;
    boolean isSticky = false;
    Thread mainThreadMock;
    EventsListener listener = new EventsListener() {
        @Override
        public void onEvent(EventBus bus, EventBus.Event event) {
            called = true;
            assertEquals(event, testEvent);
            assertEquals(event.tag, testEvent.tag);
            assertEquals(event.error, testEvent.error);
            assertEquals(event.data, testEvent.data);
        }

        @Override
        public boolean sticky() {
            return isSticky;
        }

        @Override
        public int threadMode() {
            return threadMode;
        }
    };

    @Rule
    public PowerMockRule rule = new PowerMockRule();

    static {
        PLog.setLogLevel(PLog.LEVEL_NONE);
    }

    @Before
    public void setup() throws Exception {
        mainThreadMock = new Thread("dummy thread");
        mockStatic(Looper.class);
        Looper mockLooper = mock(Looper.class);
        when(Looper.getMainLooper()).thenReturn(mockLooper);
        when(mockLooper.getThread()).thenReturn(mainThreadMock);
        bus = new EventBus();
    }

    @After
    public void tearDown() throws Exception {
        bus = null;
    }

    @Test
    public void testGetStickEvent() throws Exception {
        bus.postSticky(testEvent);
        assertNotNull(bus.getStickyEvent(testEvent.tag));
        bus.removeStickyEvent(testEvent);
        assertNull(bus.getStickyEvent(testEvent.tag));
        assertNull(bus.getStickyEvent(null));
    }

    @Test
    public void testRemoveStickyEvent() throws Exception {
        try {
            bus.removeStickyEvent(null); //must not throw
        } catch (Exception e) {
            fail("must accept null");
        }
        isSticky = true;
        bus.postSticky(testEvent);
        bus.register(testEvent.tag, listener);
        assertTrue(called);
        isSticky = false;
        called = false;
        bus.removeStickyEvent(new EventBus.Event("ffa", null, null));
        EventBus.Event event = bus.getStickyEvent(testEvent.tag);
        assertNotNull(event);
        bus.removeStickyEvent(testEvent);
        event = bus.getStickyEvent(testEvent.tag);
        assertNull(event);
    }

    @Test
    public void testPostSticky() throws Exception {

        try {
            bus.postSticky(null);
            fail("must throw");
        } catch (IllegalArgumentException ignored) {

        }
        isSticky = true;
        bus.postSticky(testEvent);
        bus.register(testEvent.tag, listener);
        assertTrue(called);
        called = false;
        bus.unregister(testEvent.tag, listener);
        bus.register(testEvent.tag, listener);
        assertTrue(called);
        called = false;
        isSticky = false;
        bus.register(testEvent.tag, listener);
        assertFalse(called);
        bus.unregister(testEvent.tag, listener);
        isSticky = true;
        bus.register(testEvent.tag, listener);
        EventBus.Event e = new EventBus.Event("lfakf;af", null, null);
        assertFalse("must return false when no listner exist for a given event", bus.postSticky(e));
        bus.removeStickyEvent(e);
        bus.register(testEvent.tag, listener);
        isSticky = false;
        bus.register(testEvent.tag, listener);
        assertTrue("must return true when no listner exist for a given event", bus.postSticky(testEvent));
        bus.removeStickyEvent(testEvent);
        bus.unregister(testEvent.tag, listener);
        isSticky = false;
    }

    @Test
    public void testPost() throws Exception {
        try {
            bus.post(null);
            fail("must throw");
        } catch (IllegalArgumentException ignored) {

        }
        int i = 5;
        Exception error = null;
        do {
            testEvent = new EventBus.Event("tag" + i, error, "fooz" + i);
            bus.register(testEvent.tag, listener);
            bus.post(testEvent);
            assertTrue(called);
            called = false;
            bus.unregister(testEvent.tag, listener);
            if (i % 2 == 0) {
                error = new Exception();
            }
        } while (i++ < 5);
        bus.register(testEvent.tag, listener);
        EventBus.Event e = new EventBus.Event("lfakf;af", null, null);
        assertFalse("must return false when no listner exist for a given event", bus.post(e));
        bus.removeStickyEvent(e);
        bus.register(testEvent.tag, listener);
        isSticky = false;
        bus.register(testEvent.tag, listener);
        assertTrue("must return true when no listner exist for a given event", bus.post(testEvent));
        bus.removeStickyEvent(testEvent);
        bus.unregister(testEvent.tag, listener);
        isSticky = false;
    }

    @Test
    public void testRegister() throws Exception {
        try {
            bus.register(testEvent.tag, null);
            fail("must throw");
        } catch (IllegalArgumentException ignored) {
        }
        try {
            bus.register(null, null);
            fail("must throw");
        } catch (IllegalArgumentException ignored) {
        }
        try {
            bus.register(null, listener);
            fail("must throw");
        } catch (IllegalArgumentException ignored) {
        }

        bus.register(testEvent.tag, listener);
        bus.post(testEvent);
        assertTrue(called);
        called = false;
        bus.unregister(testEvent.tag, listener);
        bus.register("bar", listener);
        bus.post(testEvent);
        assertFalse(called);
        called = false;
        bus.unregister("bar", listener);
    }

    @Test
    public void testUnregister() throws Exception {
        try {
            bus.unregister(testEvent.tag, null);
            fail("must throw");
        } catch (IllegalArgumentException ignored) {
        }
        try {
            bus.unregister(null, null);
            fail("must throw");
        } catch (IllegalArgumentException ignored) {
        }
        try {
            bus.unregister(null, listener);
            fail("must throw");
        } catch (IllegalArgumentException ignored) {
        }
        bus.register(testEvent.tag, listener);
        bus.post(testEvent);
        assertTrue(called);
        called = false;
        bus.unregister(testEvent.tag, listener);
        bus.post(testEvent);
        assertFalse(called);
    }

    @Test
    public void testRegister2() throws Exception {
        try {
            bus.register(null, testEvent.tag, (String[]) null);
            fail("must throw");
        } catch (IllegalArgumentException ignored) {
        }
        try {
            bus.register(listener, null, (String[]) null);
            fail("must throw");
        } catch (IllegalArgumentException ignored) {
        }
        try {
            bus.register(null, null, null, null);
            fail("must throw");
        } catch (IllegalArgumentException ignored) {
        }
        final String tag1 = "tl;ljkag1";
        final String tag2 = "taalfalfkag2";
        bus.register(listener, testEvent.tag, tag1, tag2);
        bus.post(testEvent);
        assertTrue(called);
        called = false;
        testEvent = new EventBus.Event(tag2, null, null);
        bus.post(testEvent);
        assertTrue(called);
        called = false;
        testEvent = new EventBus.Event(tag1, null, null);
        bus.post(testEvent);
        assertTrue(called);
        called = false;
        testEvent = new EventBus.Event("foobar", new Exception("foo"), null);
        bus.post(testEvent);
        assertFalse(called);
    }

    @Test
    public void testHasListeners() throws Exception {
        echo("testing hasListeners()");
        echo("must return true if an event with a particular tag has listeners already");
        bus.register(testEvent.tag, listener);
        assertTrue(bus.hasListeners(testEvent));
        assertFalse(bus.hasListeners(new EventBus.Event("lakfafa", null, null)));
        bus.unregister(testEvent.tag, listener);
        assertFalse(bus.hasListeners(testEvent));
        assertFalse(bus.hasListeners(new EventBus.Event("", null, null)));

        echo("must throw if null is passed as a tag");
        try {
            bus.hasListeners(null);
            fail("mus throw an IllegalArgument exception");
        } catch (IllegalArgumentException e) {
            echo("correctly threw");
        }
    }
}
