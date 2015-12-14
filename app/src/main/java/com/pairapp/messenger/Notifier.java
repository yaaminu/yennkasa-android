package com.pairapp.messenger;

import android.content.Context;

import com.pairapp.data.Message;

/**
 * @author Null-Pointer on 8/12/2015.
 */
public interface Notifier {
    enum location {
        BACKGROUND,
        FORE_GROUND
    }

    void notifyUser(Context context, Message message, String sender);

    void clearNotifications();

    location where();


}
