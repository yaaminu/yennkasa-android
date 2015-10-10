package com.idea.net;

/**
 * Created by Null-Pointer on 10/6/2015.
 */
public class SmartFileMessageClient extends SmartFileClient {
    public SmartFileMessageClient(String key, String password,String userId) {
        super(key, password, "Attachments");
    }
}
