package com.pair.data;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.Log;

import com.pair.util.Config;

import java.util.ArrayList;
import java.util.List;

import io.realm.Realm;
import io.realm.RealmResults;

/**
 * @author Null-Pointer on 6/11/2015.
 */
public class ContactsManager {
    public static final String TAG = ContactsManager.class.getSimpleName();

    public static final ContactsManager INSTANCE = new ContactsManager();

    private ContactsManager() {

    }


    public Cursor findAllContactsCursor(Context context) {
        return getCursor(context);
    }

    public List<Contact> findAllContactsSync() {
        return doFindAllContacts(getCursor(Config.getApplicationContext()));
    }

    public void findAllContacts(FindCallback<List<Contact>> callback) {
        List<Contact> contacts = findAllContactsSync();
        callback.done(contacts);
    }

    private Cursor getCursor(Context context) {
        ContentResolver cr = context.getContentResolver();
        return cr.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER,
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME},
                null, null, null);
    }

    private List<Contact> doFindAllContacts(Cursor cursor) {
        Realm realm = Realm.getInstance(Config.getApplicationContext());
        RealmResults<User> users = realm.where(User.class).findAll();
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
            boolean isRegistered = (user != null);
            ContactsManager.Contact contact = new ContactsManager.Contact(name, phoneNumber, isRegistered);
            Log.d(TAG, contact.name + ":" + contact.phoneNumber);
            contacts.add(contact);
        }
        realm.close();
        return contacts;
    }

    public interface FindCallback<T> {
        void done(T t);
    }

    public static final class Contact {
        public final String name, phoneNumber;
        public final boolean isRegisteredUser;

        public Contact(String name, String phoneNumber, boolean isRegisteredUser) {
            if (name == null) {
                name = "unknown";
            }
            if (phoneNumber == null) {
                phoneNumber = "unknown number";
            }

            this.name = name;
            this.phoneNumber = phoneNumber;
            this.isRegisteredUser = isRegisteredUser;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Contact contact = (Contact) o;

            if (!name.equals(contact.name)) return false;
            return phoneNumber.equals(contact.phoneNumber);

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
}
