package com.yennkasa.net.sockets;

import com.yennkasa.net.sockets.MessageQueue.Hooks;
import com.yennkasa.util.PLog;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static com.yennkasa.net.sockets.MessageQueue.Hooks.FORCEFULLY_REMOVED;
import static com.yennkasa.net.sockets.MessageQueue.Hooks.INVALID_REASON_FOR_TESTING;
import static com.yennkasa.net.sockets.MessageQueue.Hooks.WAITING_FOR_ACK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author aminu on 7/10/2016.
 */

@SuppressWarnings("EmptyCatchBlock")
public class MessageQueueImplTest {
    @Test
    public void reScheduleAllProcessingItemsForProcessing() throws Exception {
        highWaterMark = -1; //tame next()
        messageQueue.initBlocking();
        messageQueue.start();
        messageQueue.onProcessed(queueDataSource.nextItem(), true);
        messageQueue.onProcessed(queueDataSource.nextItem(), true);
        assertEquals(2, messageQueue.getWaitingForAck());
        messageQueue.reScheduleAllProcessingItemsForProcessing();
        assertEquals(0, messageQueue.getWaitingForAck());
        assertEquals(2, messageQueue.getPending());
    }

    @Test
    public void ackWaitingItems() throws Exception {
        //check no. of  waiting messages
        highWaterMark = -1; //tame next()
        messageQueue.initBlocking();
        messageQueue.start();
        int waitingForAck = messageQueue.getWaitingForAck();
        Sendable item = queueDataSource.nextItem();
        messageQueue.onProcessed(item, true);
        assertEquals("should call us with WAITING_FOR_ACK", Hooks.WAITING_FOR_ACK, onItemRemovedReason);
        assertEquals("must mark message as waiting for ack ", waitingForAck + 1, messageQueue.getWaitingForAck());

        //now lets test that no of messages waiting for acks are zero after successful call to messagequee#ackWaitingItems
        assertTrue(messageQueue.getWaitingForAck() > 0);
        messageQueue.ackWaitingItems();
        assertEquals(0, messageQueue.getWaitingForAck());
    }

    private MessageQueueImpl messageQueue;
    private QueueDataSourceMock queueDataSource;
    private boolean consumeCalled;
    private Sendable consumedCalledWith;
    private int highWaterMark = 2;
    private boolean resetHighWaterMark;

    private final MessageQueueImpl.Consumer consumer = new MessageQueueImpl.Consumer() {

        @Override
        public void consume(Sendable item) {
            consumeCalled = true;
            consumedCalledWith = item;
            messageQueue.onProcessed(item, true);
            if (resetHighWaterMark) {
                highWaterMark = 0;
            }
        }

        @Override
        public int highWaterMark() {
            return highWaterMark;
        }
    };

    static {
        PLog.setLogLevel(PLog.LEVEL_NONE);
    }


    @Before
    public void setUp() throws Exception {
        int capacity = 2;
        List<Sendable> items = new ArrayList<>(capacity);
        for (int i = 0; i < capacity; i++) {
            Sendable item = new Sendable.Builder()
                    .data("someData" + i)
                    .collapseKey("somecollapseKey" + i)
                    .build();
            item.setIndex(i);
            items.add(item);
        }
        queueDataSource = new QueueDataSourceMock(items);
        messageQueue = new MessageQueueImpl(queueDataSource, hooks, consumer);
        resetCallbacksFlags();
    }

    Sendable hooksCalledWithItem;
    int onItemRemovedReason;
    boolean onBeginProcessCalled = false, onItemRemovedCalled = false, onItemAddedCalled = false;
    private long onItemRemovdCallTimes;
    private long onItemAddCallTimes;
    private long beginProcessingCallTimes;
    Hooks hooks = new Hooks() {
        @Override
        public void onItemRemoved(Sendable item, int reason) {
            hooksCalledWithItem = item;
            onItemRemovedReason = reason;
            onItemRemovedCalled = true;
            onItemRemovdCallTimes++;
        }

        @Override
        public void onItemAdded(Sendable item) {
            hooksCalledWithItem = item;
            onItemAddedCalled = true;
            onItemAddCallTimes++;
        }

        @Override
        public void onBeginProcess(Sendable item) {
            hooksCalledWithItem = item;
            onBeginProcessCalled = true;
            beginProcessingCallTimes++;
        }
    };

    @Test
    public void testResetCallbackFlags() {
        onBeginProcessCalled = true;
        onItemAddedCalled = true;
        onItemRemovedCalled = true;
        onItemRemovedReason = FORCEFULLY_REMOVED;
        hooksCalledWithItem = new Sendable.Builder().collapseKey("hello").data("someData").build();
        consumedCalledWith = hooksCalledWithItem;
        consumeCalled = true;
        resetHighWaterMark = true;
        highWaterMark = 8;
        resetCallbacksFlags();
        assertFalse(onBeginProcessCalled);
        assertFalse(onItemRemovedCalled);
        assertFalse(resetHighWaterMark);
        assertFalse(onItemAddedCalled);
        assertNull(hooksCalledWithItem);
        assertEquals(onItemRemovedReason, INVALID_REASON_FOR_TESTING);
        assertEquals(2, highWaterMark);
    }

    @Test
    public void testInitBlocking() throws Exception {

        assertFalse(queueDataSource.isInitialised());
        assertNull("must not  register listeners in the constructor", queueDataSource.queueItemCleanedListener);
        try {
            messageQueue.getPending(); //attempt doing something
            fail("must not allow any operation until its initialised");
        } catch (IllegalStateException e) {
            //expected
        }
        messageQueue.initBlocking();
        assertTrue(queueDataSource.isInitialised());
        assertNotNull("must register this listener latest in init()", queueDataSource.queueItemCleanedListener);

        try {
            messageQueue.initBlocking();
            assertTrue(messageQueue.isInitialised());
        } catch (Exception e) {
            fail("must allow multiple invocations");
        }
    }

    @Test
    public void testNext() throws Exception {
        int tmp = highWaterMark;
        highWaterMark = 0;//tame next() so that start will have no effect
        try {
            messageQueue.next();
            fail("must not allow any operation until its initialised");
        } catch (IllegalStateException e) {
            messageQueue.initBlocking();
            messageQueue.start();
        }
        highWaterMark = tmp; //next will now work
        Sendable item = messageQueue.next();
        assertNotNull(item);
        assertEquals(queueDataSource.mocks.get(0), item);
        item = messageQueue.next();
        assertNotNull(item);
        assertEquals(queueDataSource.mocks.get(1), item);
        assertNull(messageQueue.next());
    }

    @Test
    public void testOnProcessed() throws Exception {

        int tmp = highWaterMark;
        highWaterMark = 0;//tame next() so that start will have no effect
        try {
            messageQueue.onProcessed(null, true);
            messageQueue.onProcessed(null, false);
            fail("must not allow any operation until its initialised");
        } catch (IllegalStateException e) {
            messageQueue.initBlocking();
            messageQueue.start();
        }
        highWaterMark = tmp;
        Sendable item = queueDataSource.nextItem();
        messageQueue.onProcessed(item, true);
        assertTrue(onItemRemovedCalled);
        assertNotNull(hooksCalledWithItem);
        assertEquals(onItemRemovedReason, Hooks.WAITING_FOR_ACK);

        resetCallbacksFlags();

        item.setValidUntil(0L);
        messageQueue.onProcessed(item, false);
        assertTrue(onItemRemovedCalled);
        assertEquals(hooksCalledWithItem, item);
        assertEquals(onItemRemovedReason, Hooks.FAILED_EXPIRED);

        resetCallbacksFlags();

        item.setValidUntil(System.currentTimeMillis() + 1000);
        item.setRetries(item.getMaxRetries() + 10000);
        messageQueue.onProcessed(item, false);
        assertTrue(onItemRemovedCalled);
        assertEquals(hooksCalledWithItem, item);
        assertEquals(onItemRemovedReason, Hooks.FAILED_RETRIES_EXCEEDED);

        resetCallbacksFlags();

        item.setValidUntil(System.currentTimeMillis() + 10000);
        item.setRetries(0);
        assertEquals(onItemRemovedReason, INVALID_REASON_FOR_TESTING); //ensure it actually does not set this itself
        messageQueue.pauseProcessing();
        messageQueue.onProcessed(item, false);
        assertEquals(onItemRemovedReason, Hooks.INVALID_REASON_FOR_TESTING); //test quickly to ensure no one mutates this somewhere
        assertFalse(onItemRemovedCalled);
        messageQueue.resumeProcessing();
        assertNotNull(hooksCalledWithItem);
    }

    @Test
    public void testRemove() throws Exception {
        int tmp = highWaterMark;
        highWaterMark = 0;//tame next() so that start will have no effect
        try {
            messageQueue.remove(null);
            fail("must not allow any operation until its initialised");
        } catch (IllegalStateException e) {
            messageQueue.initBlocking();
            messageQueue.start();

        }
        highWaterMark = tmp;
        Sendable item = queueDataSource.nextItem();
        boolean removed = messageQueue.remove(item);
        assertTrue(removed);
        assertTrue(onItemRemovedCalled);
        assertEquals(hooksCalledWithItem, item);
        assertEquals(onItemRemovedReason, Hooks.FORCEFULLY_REMOVED);
        assertFalse(messageQueue.remove(item));
    }

    @Test
    public void testAdd() throws Exception {

        int tmp = highWaterMark;
        highWaterMark = 0;//tame next() so that start will have no effect
        try {
            messageQueue.add(null);
            fail("must not allow any operation until its initialised");
        } catch (IllegalStateException e) {
            messageQueue.initBlocking();
            messageQueue.start();
        }
        highWaterMark = tmp;
        int initialSize = queueDataSource.mocks.size();
        Sendable item = queueDataSource.nextItem();
        messageQueue.pauseProcessing();
        messageQueue.add(item);
        assertEquals(queueDataSource.mocks.size(), initialSize + 1);
        assertNotNull("must actually insert this int to the datasource", hooksCalledWithItem);
        assertTrue(onItemAddedCalled);
    }

    @Test
    public void testClear() throws Exception {
        highWaterMark = 0;
        try {
            messageQueue.clear();
            fail("must not allow any operation until its initialised");
        } catch (IllegalStateException e) {
            messageQueue.initBlocking();
            messageQueue.start();
        }
        int itemsSize = queueDataSource.mocks.size();
        boolean clear = messageQueue.clear();
        assertTrue(itemsSize > 0);
        assertTrue(clear);
        assertEquals(onItemRemovedReason, FORCEFULLY_REMOVED);
        assertEquals(onItemRemovdCallTimes, itemsSize);
        assertTrue(queueDataSource.mocks.isEmpty());
    }

    @Test
    public void testHighWaterMark() throws Exception {
        try {
            messageQueue.highWaterMark();
            fail("must not allow any operation until its initialised");
        } catch (IllegalStateException e) {
            messageQueue.initBlocking();
            messageQueue.start();
        }
        assertEquals(consumer.highWaterMark(), messageQueue.highWaterMark());
    }

    @Test
    public void testPending() throws Exception {
        try {
            messageQueue.getPending();
            fail("must not allow any operation until its initialised");
        } catch (IllegalStateException e) {
            messageQueue.initBlocking();
            messageQueue.start();
        }
        assertEquals(queueDataSource.pending().size(), messageQueue.getPending());
    }

    @Test
    public void testProcessing() throws Exception {
        try {
            messageQueue.getProcessing();
            fail("must not allow any operation until its initialised");
        } catch (IllegalStateException e) {
            messageQueue.initBlocking();
            messageQueue.start();
        }
        assertEquals(queueDataSource.processing().size(), messageQueue.getProcessing());
    }

    @Test
    public void testStart() throws Exception {
        try {
            messageQueue.start();
            fail("must not allow starting without initialisation");
        } catch (IllegalStateException e) {
            messageQueue.initBlocking();
        }
        messageQueue.start();
        assertTrue(messageQueue.isStarted());
        try {
            messageQueue.start();
            fail("must not allow multiple start() invocation");
        } catch (IllegalStateException e) {
            //expected
        }
    }

    @Test
    public void testIsStarted() throws Exception {
        assertFalse(messageQueue.isStarted());
        messageQueue.initBlocking();
        messageQueue.start();
        assertTrue(messageQueue.isStarted());
        assertTrue(messageQueue.isInitialised());
    }

    @Test
    public void testIsInitialised() throws Exception {
        assertFalse(messageQueue.isInitialised());
        messageQueue.initBlocking();
        assertTrue(messageQueue.isInitialised());
    }

    @Test
    public void testMustRespectHighWaterMark() throws Exception {
        highWaterMark = 0;
        messageQueue.initBlocking();
        messageQueue.start();
        highWaterMark = 1;
        assertNotNull(messageQueue.next());
        assertNotNull(hooksCalledWithItem);
        assertTrue(onBeginProcessCalled);
        onItemRemovedCalled = false;
        hooksCalledWithItem = null;
        assertEquals(queueDataSource.pending().size(), messageQueue.getPending());
        assertEquals(1, messageQueue.getPending());
        assertEquals(1, messageQueue.getProcessing());
    }

    @Test
    public void testPauseProcessing() throws Exception {
        highWaterMark = 1;
        Sendable first = queueDataSource.mocks.get(0);
        try {
            messageQueue.pauseProcessing();
            fail("must initialise first");
        } catch (IllegalStateException e) {
//            resetHighWaterMark = true;
            messageQueue.initBlocking();
            messageQueue.start();
        }

        assertTrue(onBeginProcessCalled);
        assertNotNull(consumedCalledWith);
        assertTrue(consumeCalled);

        resetCallbacksFlags(); //reset state
        highWaterMark = 10;
        messageQueue.pauseProcessing();
        messageQueue.add(first);
        assertFalse(onBeginProcessCalled);
        assertNull(consumedCalledWith);
        assertFalse(consumeCalled);

        resetCallbacksFlags();

        messageQueue.resumeProcessing();

        assertTrue(onBeginProcessCalled);
        assertNotNull(consumedCalledWith);
        assertTrue(consumeCalled);
        try {
            messageQueue.stopProcessing();
            messageQueue.pauseProcessing();
            fail("must throw when in stopped state");
        } catch (IllegalStateException e) {
        }

    }

    @Test
    public void testResumeProcessing() throws Exception {
        Sendable item = queueDataSource.mocks.get(0);
        try {
            messageQueue.resumeProcessing();
            fail("must initialise first");
        } catch (IllegalStateException e) {
            messageQueue.initBlocking();
            messageQueue.start();
        }

        assertTrue(consumeCalled);
        assertNotNull(consumedCalledWith);
        assertTrue(onBeginProcessCalled);
        assertEquals(onItemRemovedReason, WAITING_FOR_ACK);
        assertEquals(2, onItemRemovdCallTimes);

        resetCallbacksFlags();
        messageQueue.pauseProcessing();
        messageQueue.add(item);
        assertFalse(consumeCalled);
        assertNull(consumedCalledWith);
        assertFalse(onBeginProcessCalled);
        assertEquals(onItemRemovedReason, INVALID_REASON_FOR_TESTING);
        assertFalse(onItemRemovedCalled);

        messageQueue.resumeProcessing();
        assertTrue(consumeCalled);
        assertNotNull(consumedCalledWith);
        assertTrue(onBeginProcessCalled);
        assertEquals(onItemRemovedReason, WAITING_FOR_ACK);
        assertEquals(1, onItemRemovdCallTimes);

    }

    @Test
    public void testStopProcessing() throws Exception {
        try {
            messageQueue.stopProcessing();
            fail("must initialise first");
        } catch (IllegalStateException e) {
            messageQueue.initBlocking();
            messageQueue.start();
        }
        assertTrue(messageQueue.isStarted());


        try {
            messageQueue.stopProcessing();
            assertFalse(messageQueue.isStarted());
            messageQueue.stopProcessing();
            fail("must throw if called multiple times");
        } catch (IllegalStateException e) {
        }
        try {
            messageQueue.add(null);
            fail("must not allow any operation when stopped");
        } catch (IllegalStateException e) {

        }
        try {
            messageQueue.remove(null);
            fail("must not allow any operation when stopped");
        } catch (IllegalStateException e) {

        }
        try {
            messageQueue.onProcessed(null, true);
            fail("must not allow any operation when stopped");
        } catch (IllegalStateException e) {

        }
        try {
            messageQueue.onProcessed(null, false);
            fail("must not allow any operation when stopped");
        } catch (IllegalStateException e) {

        }
        try {
            messageQueue.clear();
            fail("must not allow any operation when stopped");
        } catch (IllegalStateException e) {

        }
        try {
            messageQueue.next();
            fail("must not allow any operation when stopped");
        } catch (IllegalStateException e) {

        }
        try {
            messageQueue.start();
            fail("must not allow any operation when stopped");
        } catch (IllegalStateException e) {

        }
        try {
            messageQueue.resumeProcessing();
            fail("must not allow any operation when stopped");
        } catch (IllegalStateException e) {

        }
        try {
            messageQueue.pauseProcessing();
            fail("must not allow any operation when stopped");
        } catch (IllegalStateException e) {

        }
        try {
            messageQueue.initBlocking();
            fail("must not allow any operation when stopped");
        } catch (IllegalStateException e) {

        }

    }

    private void resetCallbacksFlags() {
        hooksCalledWithItem = consumedCalledWith = null;
        onItemRemovedReason = INVALID_REASON_FOR_TESTING;//ensure its not one of the valid reasons
        resetHighWaterMark = onBeginProcessCalled = onItemAddedCalled = onItemRemovedCalled = consumeCalled = false;
        onItemAddCallTimes = onItemRemovdCallTimes = beginProcessingCallTimes = 0;
        highWaterMark = 2;
    }


}
