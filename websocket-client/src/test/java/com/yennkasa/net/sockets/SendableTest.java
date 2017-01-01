package com.yennkasa.net.sockets;

import org.junit.Test;

import static com.yennkasa.net.sockets.Sendable.Builder;
import static com.yennkasa.net.sockets.Sendable.RETRY_FOREVER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author aminu on 7/10/2016.
 */
public class SendableTest {

    @Test
    public void testIsExpired() throws Exception {
        Sendable item = new Builder()
                .data("somData")
                .collapseKey("collapseKey")
                .validUntil(System.currentTimeMillis() + 1000)
                .build();

        assertFalse(item.isExpired());
        item.setValidUntil(System.currentTimeMillis() - 1);
        assertTrue(item.isExpired());
        item.setValidUntil(System.currentTimeMillis() + 2);
        assertFalse(item.isExpired());
    }

    @Test
    public void testExceededRetries() throws Exception {
        Sendable item = new Builder()
                .data("somData")
                .collapseKey("collapseKey")
                .maxRetries(2)
                .build();

        assertEquals(item.getRetries(), 0);
        assertEquals(item.getMaxRetries(), 2);
        assertFalse(item.exceededRetries());
        item.setRetries(2);
        assertTrue(item.exceededRetries());
        item.setRetries(1);
        assertFalse(item.exceededRetries());
        item = new Builder()
                .data("someData")
                .collapseKey("collapseKey")
                .maxRetries(RETRY_FOREVER)
                .build();

        assertFalse(item.exceededRetries());
        item.setRetries(200000);
        assertFalse(item.exceededRetries());
    }
}
