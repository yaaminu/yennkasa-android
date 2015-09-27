package com.pair.workers;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import com.pair.data.ContactsManager;
import com.pair.data.UserManager;
import com.pair.util.PLog;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Null-Pointer on 6/9/2015.
 */
public class ContactSyncService extends IntentService {
    public static final String TAG = ContactSyncService.class.getSimpleName();
    public static final String SYNC_CONTACTS = "syncContacts";
    public static final String ACTION = "action";

    /**
     * A service(fake sync adapter) primarily for synchronizing users.
     * this class will be used until we implement a sync adapter
     */
    public ContactSyncService() {
        super(TAG);
    }

    public static void syncIfRequired(Context context) {
        if (!UserManager.getInstance().isUserVerified()) {
            return;
        }
        Intent intent = new Intent(context, ContactSyncService.class);
        intent.putExtra(ContactSyncService.ACTION, ContactSyncService.SYNC_CONTACTS);
        PendingIntent operation = PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager manager = ((AlarmManager) context.getSystemService(Context.ALARM_SERVICE));
        //noinspection ConstantConditions
        manager.setRepeating(AlarmManager.ELAPSED_REALTIME, 5000, AlarmManager.INTERVAL_HOUR, operation); //start now
    }

    public static void syncNow(Context context) {

    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent.getStringExtra(ACTION).equals(SYNC_CONTACTS)) {
            //do work here
            UserManager manager = UserManager.getInstance();
            ContactsManager.Filter<ContactsManager.Contact> filter = new ContactsManager.Filter<ContactsManager.Contact>() {
                @Override
                public boolean accept(ContactsManager.Contact contact) {
                    return !contact.isRegisteredUser;
                }
            };
            List<ContactsManager.Contact> numbers = ContactsManager.getInstance().findAllContactsSync(filter, null);

            if (numbers.isEmpty()) { //all contacts fetched.. this should rarely happen
                PLog.i(TAG, "all contacts synced");
                return;
            }

            List<String> onlyNumbers = new ArrayList<>(numbers.size() + 1);
            for (ContactsManager.Contact contact : numbers) {
                //we need to convert contacts to the format our backend understands so that it can
                //correctly retrieve the right users.
                onlyNumbers.add(contact.numberInIEE_Format);
            }
            PLog.d(TAG, onlyNumbers.toString());
            doSync(manager, onlyNumbers);
        }
    }

    private void doSync(UserManager manager, List<String> onlyNumbers) {
        manager.syncContacts(onlyNumbers);
    }
}
