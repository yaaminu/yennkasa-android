package com.idea.messenger;

import android.app.AlarmManager;

import com.idea.data.Message;
import com.idea.data.UserManager;
import com.idea.util.PLog;
import com.idea.util.TaskManager;
import com.parse.ParseException;
import com.parse.ParseObject;
import com.parse.ParseQuery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author _2am on 9/8/2015.
 */
class ParseMessageProvider implements MessagesProvider {

    public static final String TO = "to";
    static final String RETRIEVED = "retrieved";
    static final String MESSAGE = "message";
    static final String EXPIRES = "expires";
    static final String MESSAGE_CLASS_NAME = "message";
    static final String TARGET = "targets";
    static final String IS_GROUP_MESSAGE = "isGroupMessage";
    static final String FROM = "from";
    static final long MAX_AGE = AlarmManager.INTERVAL_DAY * 10;
    private static final String TAG = ParseMessageProvider.class.getSimpleName();


    @Override
    public synchronized List<Message> retrieveMessages() {
        String recipient = UserManager.getMainUserId();
        List<Message> messages = new ArrayList<>();
        final List<ParseObject> toBeDeleted = new ArrayList<>(), toBeUpdated = new ArrayList<>();
        ParseQuery<ParseObject> query = ParseQuery.getQuery(MESSAGE_CLASS_NAME);
        try {
            List<ParseObject> objects = query.whereEqualTo(TARGET, recipient).whereNotEqualTo(RETRIEVED, recipient).find();
            for (ParseObject object : objects) {
                if (!object.getBoolean(IS_GROUP_MESSAGE) || (object.getList(RETRIEVED).size() == object.getList(TARGET).size())) {
                    toBeDeleted.add(object);
                } else {
                    toBeUpdated.add(object);
                }
                Message message = Message.fromJSON(object.getString(MESSAGE));
                messages.add(message);
            }
            TaskManager.execute(new Runnable() {
                @Override
                public void run() {
                    runUpdates(toBeDeleted, toBeUpdated);
                }
            });
            return messages;
        } catch (ParseException e) {
            PLog.e(TAG, "error retrieving messages: " + e.getMessage());
        }
        return Collections.emptyList();
    }

    private void runUpdates(List<ParseObject> toBeDeleted, List<ParseObject> toBeUpdated) {
        for (ParseObject object : toBeDeleted) {
            object.deleteEventually();
        }

        final String mainUserId = UserManager.getMainUserId();
        for (ParseObject object : toBeUpdated) {
            object.addUnique(RETRIEVED, mainUserId);
            object.saveEventually();
        }
    }
}
