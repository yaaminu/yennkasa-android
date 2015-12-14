package com.pairapp.messenger;

import com.pairapp.data.Message;

import java.util.List;

/**
 * @author _2am on 9/8/2015.
 */
interface MessagesProvider {
    List<Message> retrieveMessages();
}
