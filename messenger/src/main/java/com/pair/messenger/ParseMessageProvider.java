package com.pair.messenger;

import android.app.AlarmManager;
import android.util.Log;

import com.pair.data.Message;
import com.pair.data.MessageJsonAdapter;
import com.pair.data.UserManager;
import com.parse.ParseException;
import com.parse.ParseObject;
import com.parse.ParseQuery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * @author _2am on 9/8/2015.
 */
class ParseMessageProvider implements MessagesProvider {

    static final String RETRIEVED = "retrieved";
    static final String MESSAGE = "message";
    static final String EXPIRES = "expires";
    static final String MESSAGE_CLASS_NAME = "message";
    static final String TARGET = "target";
    static final String IS_GROUP_MESSAGE = "groupMessage";
    static final long MAX_AGE = AlarmManager.INTERVAL_DAY * 10;
    private static final String TAG = ParseMessageProvider.class.getSimpleName();


    @Override
    public List<Message> retrieveMessages() {
        String recipient = UserManager.getMainUserId();
        List<Message> messages = new ArrayList<>();
        final Set<ParseObject> toBeDeleted = new HashSet<>(),
                toBeUpdated = new HashSet<>();

        ParseQuery<ParseObject> query = ParseQuery.getQuery(MESSAGE_CLASS_NAME);
        try {
            List<ParseObject> objects = query.whereEqualTo(TARGET, recipient)
                    .whereNotEqualTo(RETRIEVED, recipient).find();
            for (ParseObject object : objects) {
                Message message = MessageJsonAdapter.INSTANCE.fromJson(object.getString(MESSAGE));
                if (!object.getBoolean(IS_GROUP_MESSAGE) || object.getList(RETRIEVED).size() + 1 >= object.getList(TARGET).size()) {
                    toBeDeleted.add(object);
                } else {
                    toBeUpdated.add(object);
                }
                messages.add(message);
            }
            worker.execute(new Runnable() {
                @Override
                public void run() {
                    runUpdates(toBeDeleted, toBeUpdated);
                }
            });
            return messages;
        } catch (ParseException e) {
            Log.e(TAG, e.getMessage());
        }
        return Collections.emptyList();
    }

    private void runUpdates(Set<ParseObject> toBeDeleted, Set<ParseObject> toBeUpdated) {
        for (ParseObject object : toBeDeleted) {
            object.deleteEventually();
        }

        for (ParseObject object : toBeUpdated) {
            object.addUnique(RETRIEVED, UserManager.getMainUserId());
            object.saveEventually();
        }
    }

    private final Executor worker = Executors.newSingleThreadExecutor();
}
