package com.idea.messenger;

import android.support.annotation.NonNull;

import com.idea.data.Message;

import org.junit.Test;

import java.util.Date;

/**
 * @author Null-Pointer on 8/30/2015.
 */
public class ParseDispatcherTest {

    @Test
    public void testDispatchToGroup() throws Exception {
//        Message message = getMessage();
//        ((ParseDispatcher) ParseDispatcher.newInstance()).dispatchToUser(message);
    }

    @Test
    public void customTest() {

    }

    @NonNull
    private Message getMessage() {
        Message message = new Message();
        message.setTo("recipient");
        message.setFrom("sender");
        message.setDateComposed(new Date());
        message.setId("uniqueId");
        message.setState(Message.STATE_PENDING);
        message.setType(Message.TYPE_TEXT_MESSAGE);
        message.setMessageBody("hello where are you");
        return message;
    }

    @Test
    public void testDispatchToUser() throws Exception {
//        Message message = getMessage();
//        ((ParseDispatcher) ParseDispatcher.newInstance()).dispatchToUser(message);
    }
}