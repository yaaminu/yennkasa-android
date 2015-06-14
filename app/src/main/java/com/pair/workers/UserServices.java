package com.pair.workers;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;

import com.pair.data.ContactsManager;
import com.pair.data.User;
import com.pair.util.UserManager;

import java.util.ArrayList;
import java.util.List;

import io.realm.Realm;
import io.realm.exceptions.RealmException;

/**
 * @author Null-Pointer on 6/9/2015.
 */
public class UserServices extends IntentService {
    public static final String TAG = UserServices.class.getSimpleName();
    public static final String ACTION_FETCH_FRIENDS = "fetchFriends";
    public static final String ACTION = "action";

    /**
     * A service(fake sync adapter) primarily for synchronizing users.
     * this class will be used until we implement a sync adapter
     */
    public UserServices() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent.getStringExtra(ACTION).equals(ACTION_FETCH_FRIENDS)) {
            //do work here
            UserManager manager = UserManager.getInstance(this.getApplication());
            ContactsManager.Filter<ContactsManager.Contact> filter = new ContactsManager.Filter<ContactsManager.Contact>() {
                @Override
                public boolean accept(ContactsManager.Contact contact) {
                    return !contact.isRegisteredUser;
                }
            };
            List<ContactsManager.Contact> numbers = ContactsManager.INSTANCE.findAllContactsSync(filter, null);

            Log.i(TAG, numbers.toString());
            if (numbers.isEmpty()) { //all contacts fetched.. this should rarely happen
                Log.i(TAG, "all friends synced");
                return;
            }
            List<String> onlyNumbers = new ArrayList<>(numbers.size() + 1);
            for (ContactsManager.Contact contact : numbers) {
                onlyNumbers.add(contact.phoneNumber);
            }

            doFetchFriends(manager, onlyNumbers);

        }
    }

    private void doFetchFriends(UserManager manager, List<String> onlyNumbers) {
        manager.fetchFriends(onlyNumbers, new UserManager.FriendsFetchCallback() {
            @Override
            public void done(Exception e, List<User> users) {
                if (e == null) {
                    saveUsers(users);
                    Log.i(TAG, "finished fetching friends");
                } else {
                    Log.e(TAG, "error while fetching friends");
                }
            }
        });
    }

    private void saveUsers(List<User> users) {
        if (users.isEmpty()) {
            Log.i(TAG, "no new friend");
            return;
        }
        Realm realm = Realm.getInstance(this);
        realm.beginTransaction();
        try {
            realm.copyToRealm(users);
            Log.i(TAG, "added " + users.size() + " new users");
        } catch (RealmException e) { //primary keys violation
            //never mind
        } finally {
            realm.commitTransaction();
            realm.close();
        }
    }
}
