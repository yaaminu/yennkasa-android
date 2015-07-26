package com.pair.data;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.os.Handler;
import android.provider.ContactsContract;
import android.telephony.PhoneNumberUtils;
import android.text.Editable;
import android.text.TextUtils;
import android.util.Log;

import com.pair.pairapp.R;
import com.pair.util.Config;
import com.pair.util.UserManager;
import com.pair.workers.PhoneNumberNormaliser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import io.realm.Realm;

/**
 * @author Null-Pointer on 6/11/2015.
 */
public class ContactsManager {
    public static final ContactsManager INSTANCE = new ContactsManager();
    private static final String TAG = ContactsManager.class.getSimpleName();

    private ContactsManager() {

    }


    public Cursor findAllContactsCursor(Context context) {
        return getCursor(context);
    }

    public List<Contact> findAllContactsSync(Filter<Contact> filter, Comparator<Contact> comparator) {
        return doFindAllContacts(filter, comparator, getCursor(Config.getApplicationContext()));
    }

    public void findAllContacts(final Filter<Contact> filter, final Comparator<Contact> comparator, final FindCallback<List<Contact>> callback) {
        final Handler handler = new Handler();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<Contact> contacts = findAllContactsSync(filter, comparator);
                //run on the thread on which clients originally called us
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.done(contacts);
                    }
                });
            }
        }).start();
    }

    private static final String [] PROJECT_NAME_PHONE =  new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME};
    private Cursor getCursor(Context context) {
        ContentResolver cr = context.getContentResolver();
        return cr.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,PROJECT_NAME_PHONE,
                null, null, null);
    }

    private List<Contact> doFindAllContacts(Filter<Contact> filter, Comparator<Contact> comparator, Cursor cursor) {
        Context context = Config.getApplicationContext();
        Realm realm = Realm.getInstance(context);
        //noinspection TryFinallyCanBeTryWithResources
        try {
            List<Contact> contacts = new ArrayList<>();
            String phoneNumber,name,status="",DP="";
            User user;
            boolean isRegistered = false;
            while (cursor.moveToNext()) {
                 phoneNumber = cursor.getString(cursor.getColumnIndex(ContactsContract
                        .CommonDataKinds.Phone.NUMBER));
                if (TextUtils.isEmpty(phoneNumber)) {
                    Log.i(TAG, "strange!: no phone number for this contact, ignoring");
                    continue;
                }
                 name = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
                if (TextUtils.isEmpty(name)) { //some users can store numbers with no name; am a victim :-P
                    name = context.getString(R.string.st_unknown);
                }
                user = realm.where(User.class)
                        .equalTo(User.FIELD_ID, PhoneNumberNormaliser.normalise(phoneNumber, UserManager.getInstance().getDefaultCCC()))
                        .findFirst();
                if (user != null) {
                    isRegistered = true;
                    status = user.getStatus();
                    DP = user.getDP();
                }
                ContactsManager.Contact contact = new ContactsManager.Contact(name, phoneNumber, status, isRegistered, DP);
                if ((filter != null) && !filter.accept(contact)) {
                    continue;
                }
                contacts.add(contact);
            }
            if (comparator != null) {
                Collections.sort(contacts, comparator);
            }
            return contacts;
        }finally {
            cursor.close();
            realm.close();
        }
    }

    public interface FindCallback<T> {
        void done(T t);
    }

    public static final class Contact {
        public final String name, phoneNumber, status, DP;
        public final boolean isRegisteredUser;

        public Contact(String name, String phoneNumber, String status, boolean isRegisteredUser, String DP) {
            Context context = Config.getApplicationContext();
            if (TextUtils.isEmpty(name)) {
                name = context.getString(R.string.st_unknown);
            }
            if (TextUtils.isEmpty(phoneNumber)) {
                throw new IllegalArgumentException("invalid phone number");
            }

            if (TextUtils.isEmpty(status)) {
                status = context.getString(R.string.st_offline);
            }
            this.name = name;
            this.phoneNumber = phoneNumber;
            this.isRegisteredUser = isRegisteredUser;
            this.status = status;
            this.DP = DP;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Contact contact = (Contact) o;
            return (isRegisteredUser == contact.isRegisteredUser) && name.equals(contact.name) && phoneNumber.equals(contact.phoneNumber);

        }

        @Override
        public int hashCode() {
            int result = name.hashCode();
            result = 31 * result + phoneNumber.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return this.phoneNumber;
        }
    }

    public interface Filter<T> {
        boolean accept(T t);
    }
}
