package com.pairapp.util;

import org.junit.Test;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;

/**
 * @author aminu on 6/30/2016.
 */
public class EventTest {

    @Test
    public void testCreate() throws Exception {
        try {
            Event.create(null, null, null);
            fail("must not accept null tags");
        } catch (IllegalArgumentException e) {
        }

        String tag = "tag";
        Event event = Event.create(tag, null, null);
        assertNotNull(event);
        assertFalse(event.isRecycled());
        assertFalse(event.isSticky());
        assertEquals(tag, event.getTag());
        assertEquals(null, event.getError());
        assertEquals(null, event.getData());

        Exception error = new Exception();
        event = Event.create(tag, error, null);
        assertNotNull(event);
        assertFalse(event.isRecycled());
        assertFalse(event.isSticky());
        assertEquals(tag, event.getTag());
        assertEquals(error, event.getError());
        assertEquals(null, event.getData());

        String data = "data";
        event = Event.create(tag, error, data);
        assertNotNull(event);
        assertFalse(event.isSticky());
        assertFalse(event.isRecycled());
        assertEquals(tag, event.getTag());
        assertEquals(error, event.getError());
        assertEquals(data, event.getData());
    }

    @Test
    public void testCreateSticky() throws Exception {
        try {
            Event.createSticky(null, null, null);
            fail("must not accept null tags");
        } catch (IllegalArgumentException e) {
        }

        String tag = "tag";
        Event event = Event.createSticky(tag, null, null);
        assertNotNull(event);
        assertFalse(event.isRecycled());
        assertEquals(tag, event.getTag());
        assertEquals(null, event.getError());
        assertEquals(null, event.getData());
        assertTrue(event.isSticky());

        Exception error = new Exception();
        event = Event.createSticky(tag, error, null);
        assertNotNull(event);
        assertFalse(event.isRecycled());
        assertEquals(tag, event.getTag());
        assertEquals(error, event.getError());
        assertEquals(null, event.getData());
        assertTrue(event.isSticky());

        String data = "data";
        event = Event.createSticky(tag, error, data);
        assertNotNull(event);
        assertFalse(event.isRecycled());
        assertEquals(tag, event.getTag());
        assertEquals(error, event.getError());
        assertEquals(data, event.getData());
        assertTrue(event.isSticky());
    }


    @SuppressWarnings("EmptyCatchBlock")
    @Test
    public void testRecycle() throws Exception {
        Event event = Event.createSticky("stycky");
        event.recycle();
        assertFalse("must recycle sticky events this way", event.isRecycled());

        Exception error = new Exception();
        event = Event.create("tag", error, "data");
        assertEquals("tag", event.getTag());
        assertEquals(error, event.getError());
        assertEquals("data", event.getData());
        assertFalse(event.isRecycled());
        event.recycle();
        assertTrue(event.isRecycled());


        Event oldEvent = event;
        error = new Exception();
        String barDAta = "barDAta";
        event = Event.create("foo", error, barDAta);
        assertTrue(event == oldEvent); //must actually recycle it from the object pool
        assertFalse(event.isRecycled());
        assertEquals("foo", event.getTag());
        assertEquals(error, event.getError());
        assertEquals(barDAta, event.getData());

        event.recycle(); //recycle again
        try {
            //noinspection EqualsWithItself,ResultOfMethodCallIgnored
            event.equals(event);
            fail("object must be inaccessible once its recycled");
        } catch (IllegalStateException e) {

        }
        try {
            //noinspection EqualsWithItself,ResultOfMethodCallIgnored
            event.hashCode();
            fail("object must be inaccessible once its recycled");
        } catch (IllegalStateException e) {

        }
    }

    @SuppressWarnings("EmptyCatchBlock")
    @Test
    public void testRecycleSticky() throws Exception {
        Event event = Event.create("stcky");
        event.recycleSticky();
        assertFalse("must recycle non-sticky events this way", event.isRecycled());
        event.recycle();
        assertTrue(event.isRecycled());

        Exception error = new Exception();
        event = Event.createSticky("tag", error, "data");
        assertEquals("tag", event.getTag());
        assertEquals(error, event.getError());
        assertEquals("data", event.getData());
        assertFalse(event.isRecycled());
        event.recycleSticky();
        assertTrue(event.isRecycled());


        Event oldEvent = event;
        error = new Exception();
        String barDAta = "barDAta";
        event = Event.createSticky("foo", error, barDAta);
        assertTrue(event == oldEvent); //must actually recycle it from the object pool
        assertFalse(event.isRecycled());
        assertEquals("foo", event.getTag());
        assertEquals(error, event.getError());
        assertEquals(barDAta, event.getData());

        event.recycleSticky(); //recycle again
        try {
            //noinspection EqualsWithItself,ResultOfMethodCallIgnored
            event.equals(event);
            fail("object must be inaccessible once its recycled");
        } catch (IllegalStateException e) {

        }
        try {
            //noinspection EqualsWithItself,ResultOfMethodCallIgnored
            event.hashCode();
            fail("object must be inaccessible once its recycled");
        } catch (IllegalStateException e) {

        }
    }

    @Test
    public void testIsRecycled() throws Exception {
        Event event = Event.create("tag", null, null);
        assertFalse(event.isRecycled());
        event.recycle();
        assertTrue(event.isRecycled());
    }

    @Test
    public void testGetTag() throws Exception {
        Event event = Event.create("tag", null, null);
        assertEquals("tag", event.getTag());
        event.recycle();
        try {
            event.getData();
            fail("object must be inaccessible once its recycled");
        } catch (IllegalStateException e) {

        }
    }

    @Test
    public void testGetError() throws Exception {
        Event event = Event.create("tag", null, null);
        assertEquals(null, event.getError());
        event.recycle();
        try {
            //noinspection ThrowableResultOfMethodCallIgnored
            event.getError();
            fail("object must be inaccessible once its recycled");
        } catch (IllegalStateException e) {

        }
    }

    @Test
    public void testGetData() throws Exception {
        Event event = Event.create("tag", null, "data");
        assertEquals("data", event.getData());
        event.recycle();
        try {
            //noinspection ThrowableResultOfMethodCallIgnored
            event.getData();
            fail("object must be inaccessible once its recycled");
        } catch (IllegalStateException e) {

        }
    }

    @SuppressWarnings("EmptyCatchBlock")
    @Test
    public void testCreate1() throws Exception {
        Event event = Event.create("tag");
        assertFalse(event.isRecycled());
        assertFalse(event.isSticky());
        assertEquals("tag", event.getTag());
        assertEquals(null, event.getError());
        assertEquals(null, event.getData());
        try {
            Event.create(null);
            fail("must accept null tags");
        } catch (IllegalArgumentException e) {

        }
    }

    @SuppressWarnings("EmptyCatchBlock")
    @Test
    public void testCreateSticky1() throws Exception {
        Event event = Event.createSticky("tag");
        assertTrue(event.isSticky());
        assertFalse(event.isRecycled());
        assertEquals("tag", event.getTag());
        assertEquals(null, event.getError());
        assertEquals(null, event.getData());
        try {
            Event.createSticky(null);
            fail("must accept null tags");
        } catch (IllegalArgumentException e) {

        }
    }

    @SuppressWarnings("EmptyCatchBlock")
    @Test
    public void testCreate2() throws Exception {
        Event event = Event.create("tag", null);
        assertFalse(event.isRecycled());
        assertEquals("tag", event.getTag());
        assertFalse(event.isSticky());
        assertFalse(event.isSticky());
        assertEquals(null, event.getError());
        assertEquals(null, event.getData());

        Exception error = new Exception();
        event = Event.create("tag", error);
        assertFalse(event.isRecycled());
        assertFalse(event.isSticky());
        assertEquals("tag", event.getTag());
        assertEquals(error, event.getError());
        assertEquals(null, event.getData());
        try {
            Event.create(null, null);
            fail("must accept null tags");
        } catch (IllegalArgumentException e) {

        }
        try {
            Event.create(null, error);
            fail("must accept null tags");
        } catch (IllegalArgumentException e) {

        }
    }

    @SuppressWarnings("EmptyCatchBlock")
    @Test
    public void testCreateSticky2() throws Exception {
        Event event = Event.createSticky("tag", null);
        assertFalse(event.isRecycled());
        assertTrue(event.isSticky());
        assertEquals("tag", event.getTag());
        assertEquals(null, event.getError());
        assertEquals(null, event.getData());

        Exception error = new Exception();
        event = Event.createSticky("tag", error);
        assertTrue(event.isSticky());
        assertFalse(event.isRecycled());
        assertEquals("tag", event.getTag());
        assertEquals(error, event.getError());
        assertEquals(null, event.getData());
        try {
            Event.createSticky(null, null);
            fail("must accept null tags");
        } catch (IllegalArgumentException e) {

        }
        try {
            Event.createSticky(null, error);
            fail("must accept null tags");
        } catch (IllegalArgumentException e) {

        }
    }
}
