package com.pairapp.net.sockets;

import org.junit.Before;
import org.junit.Test;

import static com.pairapp.net.sockets.Sendable.DEFAULT_MAX_RETRIES;
import static com.pairapp.net.sockets.Sendable.INVALID_INDEX;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author aminu on 7/10/2016.
 */
public class BuilderTest {

    private final String collapseKey = "collapseKey";
    private final String someData = "someData";
    private Sendable.Builder builder;
    private Sendable item;

    @Before
    public void setup() {
        builder = new Sendable.Builder();
    }

    @SuppressWarnings("EmptyCatchBlock")
    @Test
    public void testBuild1() throws Exception { //test default behaviours
        try {
            builder.build();
        } catch (IllegalArgumentException e) {

        }
        builder.collapseKey(collapseKey);
        builder.data(someData);
        item = builder.build();
        assertEquals(item.getData(), someData);
        assertEquals(item.getCollapseKey(), collapseKey);
        assertEquals(item.getMaxRetries(), DEFAULT_MAX_RETRIES);
        assertEquals(item.getIndex(), INVALID_INDEX);
        assertEquals(item.getRetries(), 0);
        assertTrue(item.getValidUntil() > System.currentTimeMillis());
        assertFalse(item.isProcessing());
        assertFalse(item.exceededRetries());
        assertFalse(item.isExpired());
    }

    @SuppressWarnings("EmptyCatchBlock")
    @Test
    public void testBuild2() throws Exception { //test invalid values handling
        try {
            builder.data("");
            fail("must not allow empty data fields");
        } catch (IllegalArgumentException e) {
            builder.data(someData);//go to next
        }
        try {
            builder.collapseKey("");
            fail("must not allow empty collapse keys");
        } catch (IllegalArgumentException e) {
            builder.collapseKey(collapseKey);
        }
        long testValidUntil = System.currentTimeMillis() + 1000 * 60;
        try {
            builder.validUntil(System.currentTimeMillis() - 2000);
            fail("must not allow valid until in the past");
        } catch (IllegalArgumentException e) {
            builder.validUntil(testValidUntil);//one minute
        }

        int testMaxRetries = 2;
        try {
            builder.maxRetries(-2);
            fail("must not allow empty data fields");
        } catch (IllegalArgumentException e) {
            builder.maxRetries(testMaxRetries);
        }
        item = builder.build();

        assertEquals(item.getData(), someData);
        assertEquals(item.getCollapseKey(), collapseKey);
        assertEquals(item.getMaxRetries(), testMaxRetries);
        assertEquals(item.getIndex(), INVALID_INDEX);
        assertEquals(item.getRetries(), 0);
        assertFalse(item.isProcessing());
        assertEquals(item.getValidUntil(), testValidUntil);
        assertFalse(item.exceededRetries());
        assertFalse(item.isExpired());

        builder.surviveRestarts(true);
        assertTrue(builder.build().surviveRestarts());

        builder.surviveRestarts(false);
        assertFalse(builder.build().surviveRestarts());


        try {
            builder.startProcessingAt(-1);
            fail("must not allow invalid date times");
        } catch (IllegalArgumentException e) {

        }
        try {
            builder.startProcessingAt(-9991);
            fail("must not allow invalid date times");
        } catch (IllegalArgumentException e) {

        }
        long time = System.currentTimeMillis();
        builder.startProcessingAt(time);
        assertEquals(time, builder.build().getStartProcessingAt());

        time = 9999;
        builder.startProcessingAt(time);
        assertEquals(time, builder.build().getStartProcessingAt());

    }
}
