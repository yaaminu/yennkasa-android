package com.pair.data;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.os.Handler;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.Log;

import com.pair.util.Config;

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

    private Cursor getCursor(Context context) {
        ContentResolver cr = context.getContentResolver();
        return cr.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER,
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME},
                null, null, null);
    }

    private List<Contact> doFindAllContacts(Filter<Contact> filter, Comparator<Contact> comparator, Cursor cursor) {
        Realm realm = Realm.getInstance(Config.getApplicationContext());
        List<Contact> contacts = new ArrayList<>();
        while (cursor.moveToNext()) {
            String phoneNumber = cursor.getString(cursor.getColumnIndex(ContactsContract
                    .CommonDataKinds.Phone.NUMBER));
            //TODO do this with regexp
            if (TextUtils.isEmpty(phoneNumber)) {
                Log.i(TAG, "no phone number for this contact, continuing");
                continue;
            }
            // TODO use string#replace(regExp).
            phoneNumber = phoneNumber.replace("(", "").replace(")", "").replace("-", "");

            String name = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
            if (TextUtils.isEmpty(name)) {
                name = "No name";
            }
            User user = realm.where(User.class).equalTo("_id", phoneNumber).findFirst();
            boolean isRegistered = false;
            String status = "";
            if (user != null) {
                isRegistered = true;
                status = user.getStatus();
            }
            ContactsManager.Contact contact = new ContactsManager.Contact(name, phoneNumber, status, isRegistered);
            Log.d(TAG, contact.name + ":" + contact.phoneNumber);
            if ((filter != null) && !filter.accept(contact)) {
                continue;
            }
            contacts.add(contact);
        }
        cursor.close();
        realm.close();
        if (comparator != null) {
            Collections.sort(contacts, comparator);
        }
        return contacts;
    }

    public interface FindCallback<T> {
        void done(T t);
    }

    public static final class Contact {
        public final String name, phoneNumber, status;
        public final boolean isRegisteredUser;

        public Contact(String name, String phoneNumber, String status, boolean isRegisteredUser) {
            if (TextUtils.isEmpty(name)) {
                name = "unknown";
            }
            if (TextUtils.isEmpty(phoneNumber)) {
                phoneNumber = "unknown number";
            }

            if (TextUtils.isEmpty(status)) {
                status = "No status set";
            }
            this.name = name;
            this.phoneNumber = phoneNumber;
            this.isRegisteredUser = isRegisteredUser;
            this.status = status;
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
