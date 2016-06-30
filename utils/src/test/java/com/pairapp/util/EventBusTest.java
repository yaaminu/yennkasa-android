package com.pairapp.util;

import android.os.Looper;
import android.support.annotation.NonNull;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.rule.PowerMockRule;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

import static com.pairapp.util.Echo.echo;
import static com.pairapp.util.EventBus.EventsListener;
import static com.pairapp.util.EventBus.getDefault;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertSame;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * author Null-Pointer on 1/16/2016.
 */
@SuppressWarnings("ConstantConditions")
@PrepareForTest(Looper.class)
public class EventBusTest {

    EventBus bus;
    boolean called = false;
    private Event testEvent;//s = Event.create("tag", null, null); //various test can change the way they like
    int threadMode = EventBus.ANY;
    boolean isSticky = false;
    Thread mainThreadMock;
    EventsListener listener = new EventsListener() {
        @Override
        public void onEvent(EventBus bus, Event event) {
            called = true;
            assertEquals(event, testEvent);
            assertEquals(event.getTag(), testEvent.getTag());
            assertEquals(event.getError(), testEvent.getError());
            assertEquals(event.getData(), testEvent.getData());
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
        bus = EventBus.getDefault();
        usedLock = false;
        testEvent = Event.create("tag", null, null);
    }

    @After
    public void tearDown() throws Exception {
        bus = null;
    }

    boolean usedLock = false;
    Lock fakeLock = new Lock() {
        @Override
        public void lock() {
            usedLock = true;
        }

        @Override
        public void lockInterruptibly() throws InterruptedException {

        }

        @Override
        public boolean tryLock() {
            return false;
        }

        @Override
        public boolean tryLock(long time, @SuppressWarnings("NullableProblems") TimeUnit unit) throws InterruptedException {
            return false;
        }

        @Override
        public void unlock() {

        }

        @NonNull
        @Override
        public Condition newCondition() {
            return null;
        }
    };

    @Test
    public void testIfUsesLock() throws Exception {
        try {
            //noinspection ConstantConditions
            EventBus.getBusOrCreate(getClass(), null);
            fail("must not accept null locks");
        } catch (IllegalArgumentException e) {
            //better
        }
        testEvent = Event.createSticky("tag");
        bus = EventBus.getBusOrCreate(getClass(), fakeLock);
        bus.post(Event.create("tag"));
        assertTrue("must used the custom lock passed", usedLock);
        usedLock = false;
        bus.postSticky(testEvent);
        assertTrue("must used the custom lock passed", usedLock);
        usedLock = false;
        bus.removeStickyEvent(testEvent);
        assertTrue("must used the custom lock passed", usedLock);
        usedLock = false;
        bus.getStickyEvent(testEvent);
        assertTrue("must used the custom lock passed", usedLock);
        usedLock = false;
        bus.hasListeners(testEvent);
        assertTrue("must used the custom lock passed", usedLock);
        usedLock = false;
        bus.register("tag", listener);
        assertTrue("must used the custom lock passed", usedLock);
        usedLock = false;
        bus.unregister("tag", listener);
        assertTrue("must used the custom lock passed", usedLock);
        usedLock = false;
        bus.register(listener, "tag2", "tag3");
        assertTrue("must used the custom lock passed", usedLock);
        usedLock = false;
        bus.unregister("tag2", listener);
        assertTrue("must used the custom lock passed", usedLock);
        usedLock = false;
        bus.unregister("tag3", listener);
        assertTrue("must used the custom lock passed", usedLock);
        usedLock = false;
    }

    @Test
    public void testGetStickEvent() throws Exception {
        testEvent = Event.createSticky("sticky");
        bus.postSticky(testEvent);
        Object tag = testEvent.getTag();
        assertNotNull(bus.getStickyEvent(tag));
        bus.removeStickyEvent(testEvent);
        assertNull(bus.getStickyEvent(tag));
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
        testEvent = Event.createSticky("tag");
        bus.postSticky(testEvent);
        bus.register(testEvent.getTag(), listener);
        assertTrue(called);
        isSticky = false;
        called = false;
        bus.removeStickyEvent(Event.create("ffa", null, null));
        Event event = bus.getStickyEvent(testEvent.getTag());
        assertNotNull(event);

        assertFalse(testEvent.isRecycled()); //remove sticky event must recycle

        Object tag = testEvent.getTag();
        bus.removeStickyEvent(testEvent);

        assertTrue(testEvent.isRecycled());
        event = bus.getStickyEvent(tag);
        assertNull(event);
    }

    @Test
    public void testPostSticky() throws Exception {

        try {
            bus.postSticky(null);
            fail("must throw");
        } catch (IllegalArgumentException ignored) {

        }
        try {
            bus.postSticky(Event.create("not sticky"));
            fail("must only accept sticky events");
        } catch (IllegalArgumentException ignored) {

        }
        //noinspection EmptyCatchBlock
        try {
            Event recycled = Event.create("bar");
            recycled.recycle();
            bus.postSticky(recycled);
            fail("must not allow a recycled event to be posted");
        } catch (IllegalArgumentException e) {

        }
        isSticky = true;
        testEvent = Event.createSticky("stikcy");
        bus.postSticky(testEvent);
        bus.register(testEvent.getTag(), listener);
        assertTrue(called);
        called = false;
        bus.unregister(testEvent.getTag(), listener);
        bus.register(testEvent.getTag(), listener);
        assertTrue(called);
        called = false;
        isSticky = false;
        bus.register(testEvent.getTag(), listener);
        assertFalse(called);
        bus.unregister(testEvent.getTag(), listener);
        isSticky = true;
        bus.register(testEvent.getTag(), listener);
        Event e = Event.createSticky("lfakf;af", null, null);
        assertFalse("must return false when no listner exist for a given event", bus.postSticky(e));
        bus.removeStickyEvent(e);
        bus.register(testEvent.getTag(), listener);
        isSticky = false;
        bus.register(testEvent.getTag(), listener);
        assertTrue("must return true when no listner exist for a given event", bus.postSticky(testEvent));
        Object tag = testEvent.getTag();
        bus.removeStickyEvent(testEvent);
        bus.unregister(tag, listener);
        isSticky = false;
    }

    @Test
    public void testPost() throws Exception {
        try {
            bus.post(null);
            fail("must throw");
        } catch (IllegalArgumentException ignored) {

        }
        //noinspection EmptyCatchBlock
        try {
            bus.post(Event.createSticky("fooobar"));
            fail("must not allow sticky events");
        } catch (IllegalArgumentException e) {

        }
        //noinspection EmptyCatchBlock
        try {
            Event recycled = Event.create("bar");
            recycled.recycle();
            bus.post(recycled);
            fail("must not allow a recycled event to be posted");
        } catch (IllegalArgumentException e) {

        }
        int i = 5;
        Exception error = null;
        String tag = "tag";
        do {
            testEvent = Event.create(tag + i, error, "fooz" + i);
            bus.register(tag + i, listener);
            bus.post(testEvent);
            assertFalse(testEvent.isRecycled());
            testEvent.recycle();
            assertTrue(testEvent.isRecycled());
            assertTrue(called);
            called = false;
            bus.unregister(tag + i, listener);
            if (i % 2 == 0) {
                error = new Exception();
            }
        } while (i++ < 5);
        bus.register(tag, listener);
        Event e = Event.create("lfakf;af", null, null);
        assertFalse("must return false when no listener exist for a given event", bus.post(e));


        testEvent = Event.create("barr");
        bus.register(testEvent.getTag(), listener);
        assertTrue("must return true when a listener exist for a given event", bus.post(testEvent));
        bus.unregister(tag, listener);


        AtomicBoolean called1 = new AtomicBoolean(false),
                called2 = new AtomicBoolean(false),
                called3 = new AtomicBoolean(false);

        EventsListener2 listener = new EventsListener2(called1, bus, testEvent),
                listener2 = new EventsListener2(called2, bus, testEvent),
                listener3 = new EventsListener2(called3, bus, testEvent);
        bus.register("tag", listener);
        bus.register("tag", listener2);
        bus.register("tag", listener3);

        Event event = Event.create("tag");
        assertFalse(event.isRecycled());

        //post this onto the bus
        bus.post(event);

        //must notify all listeners
        assertTrue(called1.get());
        assertTrue(called2.get());
        assertTrue(called3.get());

        //after post, we must call the recycle exactly 3 times before it will be recycled
        event.recycle();
        assertFalse(event.isRecycled());

        event.recycle();
        assertFalse(event.isRecycled());

        event.recycle();
        assertTrue(event.isRecycled());

        bus.unregister("tag", listener);
        bus.unregister("tag", listener2);
        bus.unregister("tag", listener3);
    }

    @Test
    public void testRegister() throws Exception {
        try {
            bus.register(testEvent.getTag(), null);
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
        String tag = testEvent.getTag().toString();
        bus.register(tag, listener);
        bus.post(testEvent);
        assertTrue(called);
        called = false;
        bus.unregister(tag, listener);
        bus.register("bar", listener);
        bus.post(Event.create("fobar"));
        assertFalse(called);
        called = false;
        bus.unregister("bar", listener);
    }

    @Test
    public void testUnregister() throws Exception {
        try {
            bus.unregister(testEvent.getTag(), null);
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
        Object tag = testEvent.getTag();
        bus.register(tag, listener);
        bus.post(testEvent);
        assertTrue(called);
        called = false;
        bus.unregister(tag, listener);
        bus.post(testEvent);
        assertFalse(called);
    }

    @Test
    public void testRegister2() throws Exception {
        try {
            bus.register(null, testEvent.getTag(), (String[]) null);
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
        bus.register(listener, testEvent.getTag(), tag1, tag2);
        bus.post(testEvent);
        assertTrue(called);
        called = false;
        Event orignalEvent = testEvent;
        testEvent = Event.create(tag2, null, null);
        bus.post(testEvent);
        assertTrue(called);
        called = false;
        testEvent = Event.create(tag1, null, null);
        bus.post(testEvent);
        assertTrue(called);
        called = false;
        testEvent = Event.create("foobar", new Exception("foo"), null);
        bus.post(testEvent);
        assertFalse(called);
        testEvent = orignalEvent;
    }

    @Test
    public void testHasListeners() throws Exception {
        echo("testing hasListeners()");
        echo("must return true if an event with a particular tag has listeners already");
        bus.register(testEvent.getTag(), listener);
        assertTrue(bus.hasListeners(testEvent));
        assertFalse(bus.hasListeners(Event.create("lakfafa", null, null)));
        bus.unregister(testEvent.getTag(), listener);
        assertFalse(bus.hasListeners(testEvent));
        assertFalse(bus.hasListeners(Event.create("", null, null)));

        echo("must throw if null is passed as a tag");
        try {
            bus.hasListeners(null);
            fail("mus throw an IllegalArgument exception");
        } catch (IllegalArgumentException e) {
            echo("correctly threw");
        }
    }


    @SuppressWarnings("EmptyCatchBlock")
    @Test
    public void testGetOrCreate1() throws Exception {
        try {
            EventBus.getBusOrCreate(null);
            fail("must not accept null class");
        } catch (IllegalArgumentException e) {

        }
        EventBus bus = EventBus.getBusOrCreate(Assert.class);
        assertNotNull(bus);
        Event fooEvent = Event.create("tag");
        assertFalse(bus.hasListeners(fooEvent));
        bus.register(fooEvent.getTag(), listener);
        EventBus oldBus = bus;
        bus = EventBus.getBusOrCreate(Assert.class);
        assertTrue("must return existing bus if not exist", oldBus == bus);
        assertTrue(bus.hasListeners(fooEvent));
        bus.unregister(fooEvent, listener);
    }

    @SuppressWarnings("EmptyCatchBlock")
    @Test
    public void testGetOrCreate2() throws Exception {
        try {
            //noinspection ConstantConditions
            EventBus.getBusOrCreate(null, null);
            fail("must not accept null class");
        } catch (IllegalArgumentException e) {

        }
        try {
            //noinspection ConstantConditions
            EventBus.getBusOrCreate(null, fakeLock);
            fail("must not accept null class");
        } catch (IllegalArgumentException e) {

        }
        try {
            //noinspection ConstantConditions
            EventBus.getBusOrCreate(Assert.class, null);
            fail("must not accept null class");
        } catch (IllegalArgumentException e) {

        }
        EventBus bus = EventBus.getBusOrCreate(Object.class, fakeLock);
        assertNotNull(bus);
        Event fooEvent = Event.create("tag");
        usedLock = false;
        assertFalse(bus.hasListeners(fooEvent));
        assertTrue(usedLock);
        usedLock = false;
        bus.register(fooEvent.getTag(), listener);
        assertTrue(usedLock);
        EventBus oldBus = bus;
        bus = EventBus.getBusOrCreate(Object.class, fakeLock);
        assertTrue("must return existing bus if not exist", oldBus == bus);
        usedLock = false;
        assertTrue(bus.hasListeners(fooEvent));
        assertTrue(usedLock);
        usedLock = false;
        bus.unregister(fooEvent, listener);
        assertTrue(usedLock);
    }


    @Test
    public void testGetDefault() throws Exception {
        assertNotNull(getDefault());
        EventBus bus = getDefault(),
                bus2 = getDefault();
        assertSame("must return same bus", bus, bus2);

        Event event = Event.create("bar", null, null);
        bus.register(event.getTag(), listener);
        assertTrue(bus2.hasListeners(event));
        assertTrue(bus.hasListeners(event));
        bus.unregister(event.getTag(), listener);
        assertFalse(bus2.hasListeners(event));
        assertFalse(bus.hasListeners(event));

    }

    EventsListener listener2 = new EventsListener() {
        @Override
        public int threadMode() {
            return EventBus.ANY;
        }

        @Override
        public void onEvent(EventBus yourBus, Event event) {
            assertNotNull(event);
            assertFalse(event.isRecycled());
        }

        @Override
        public boolean sticky() {
            return true;
        }
    };

    @Test
    public void testResetBus() throws Exception {
        EventBus bus = EventBus.getBusOrCreate(Class.class);
        Event fooEvent = Event.create("bar");
        bus.register(fooEvent.getTag(), listener2);
        assertTrue(bus.hasListeners(fooEvent));
        assertTrue(bus.post(fooEvent));
        EventBus.resetBus(Class.class);
        assertFalse(bus.hasListeners(fooEvent));
        assertFalse(bus.post(fooEvent));


        bus = EventBus.getBusOrCreate(Objects.class, fakeLock);
        fooEvent = Event.create("bar");
        usedLock = false;
        bus.register(fooEvent.getTag(), listener2);
        assertTrue(usedLock);
        usedLock = false;
        assertTrue(bus.hasListeners(fooEvent));
        assertTrue(usedLock);
        usedLock = false;
        assertTrue(bus.post(fooEvent));
        assertTrue(usedLock);
        usedLock = false;
        EventBus.resetBus(Objects.class);
        assertTrue(usedLock);
        usedLock = false;
        assertFalse(bus.hasListeners(fooEvent));
        assertTrue(usedLock);
        usedLock = false;
        assertFalse(bus.post(fooEvent));
        assertTrue(usedLock);


        assertNull(EventBus.getBus(Map.class));
        EventBus.resetBus(Map.class); //must be able to handle classed that map to no bus
    }

    @Test
    public void testGetBus() throws Exception {
        assertNull(EventBus.getBus(Thread.class));
        assertNull(EventBus.getBus(Lock.class));

        EventBus bus = EventBus.getBusOrCreate(Thread.class);
        assertNotNull(bus); ///ensure we are not comparing two nulls
        assertSame(bus, EventBus.getBus(Thread.class));
        bus = EventBus.getBusOrCreate(Lock.class, fakeLock);
        assertNotNull(bus);
        assertSame(bus, EventBus.getBus(Lock.class));
    }

    private static class EventsListener2 implements EventsListener {

        private final AtomicBoolean called2;
        private final EventBus bus;

        public EventsListener2(AtomicBoolean called2, EventBus bus, @SuppressWarnings("UnusedParameters") Event testEvent) {
            this.called2 = called2;
            this.bus = bus;
        }

        @Override
        public int threadMode() {
            return EventBus.ANY;
        }

        @Override
        public void onEvent(EventBus yourBus, Event event) {
            assertNotNull(event);
            assertSame(bus, yourBus);
            called2.set(true);
        }

        @Override
        public boolean sticky() {
            return false;
        }
    }
}
