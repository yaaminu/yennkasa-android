package com.idea.net;

import java.util.Map;

/**
 * @author Null-Pointer on 10/3/2015.
 */
public class MessageFileClient extends BaseFileClient {

    private static final String TAG = MessageFileClient.class.getSimpleName();

    private MessageFileClient(String endpoint, Map<String, String> credentials) {
        super(endpoint, credentials);
    }

    public static MessageFileClient createInstance(String endpoint, Map<String, String> credentials) {
        return new MessageFileClient(endpoint, credentials);
    }
}
