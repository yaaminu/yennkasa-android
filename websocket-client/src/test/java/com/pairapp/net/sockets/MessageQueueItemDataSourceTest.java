package com.pairapp.net.sockets;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

/**
 * @author aminu on 7/10/2016.
 */
public class MessageQueueItemDataSourceTest {

    @Before
    public void setUp() throws Exception {

    }

    @Test
    public void testNextItem() throws Exception {
        QueueDataSource.QueueItemCleanedListener queueCleanedListener = new QueueDataSource.QueueItemCleanedListener() {
            @Override
            public void onExpiredItemsRemoved(List<Sendable> items) {

            }
        };
        MessageQueueItemDataSource dataSource = new MessageQueueItemDataSource();
    }

    @Test
    public void testRegisterCallback() throws Exception {

    }

    @Test
    public void testClearQueue() throws Exception {

    }

    @Test
    public void testRemoveItem() throws Exception {

    }

    @Test
    public void testAddItem() throws Exception {

    }

    @Test
    public void testPending() throws Exception {

    }

    @Test
    public void testProcessing() throws Exception {

    }

    @Test
    public void testInit() throws Exception {

    }

    @Test
    public void testGetRealm() throws Exception {

    }
}
