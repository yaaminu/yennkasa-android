package com.pair.data;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.Log;

import com.google.i18n.phonenumbers.NumberParseException;
import com.pair.util.Config;
import com.pair.util.PhoneNumberNormaliser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.realm.Realm;

/**
 * @author Null-Pointer on 6/11/2015.
 */
public class ContactsManager {
    private static final ContactsManager INSTANCE = new ContactsManager();
    private static final String TAG = ContactsManager.class.getSimpleName();

    private ContactsManager() {

    }

    public static ContactsManager getInstance() {
        return INSTANCE;
    }


    public Cursor findAllContactsCursor(Context context) {
        return getCursor(context);
    }

    public List<Contact> findAllContactsSync(Filter<Contact> filter, Comparator<Contact> comparator) {
        return doFindAllContacts(filter, comparator, getCursor(Config.getApplicationContext()));
    }

    public void findAllContacts(final Filter<Contact> filter, final Comparator<Contact> comparator, final FindCallback<List<Contact>> callback) {
        //noinspection ConstantConditions
        final Handler handler = new Handler(Looper.myLooper() != null ? Looper.myLooper() : Looper.getMainLooper());
        new Thread(new Runnable() {
            @Override
            public void run() {
                android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                final List<Contact> contacts = findAllContactsSync(filter, comparator);
                //run on the thread on which clients originally called us if possible
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.done(contacts);
                    }
                });
            }
        }).start();
    }

    private static final String[] PROJECT_NAME_PHONE = new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME};

    private Cursor getCursor(Context context) {
        ContentResolver cr = context.getContentResolver();
        return cr.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, PROJECT_NAME_PHONE,
                null, null, null);
    }

    private List<Contact> doFindAllContacts(Filter<Contact> filter, Comparator<Contact> comparator, Cursor cursor) {
        Context context = Config.getApplicationContext();
        Realm realm = Realm.getInstance(context);
        //noinspection TryFinallyCanBeTryWithResources
        try {
            Set<Contact> contacts = new HashSet<>();
            String phoneNumber, name, DP, standardisedNumber = "";
            User user;
            boolean isRegistered;
            while (cursor.moveToNext()) {
                phoneNumber = cursor.getString(cursor.getColumnIndex(ContactsContract
                        .CommonDataKinds.Phone.NUMBER));
                if (TextUtils.isEmpty(phoneNumber)) {
                    Log.i(TAG, "strange!: no phone number for this contact, ignoring");
                    continue;
                }
                try {
                    standardisedNumber = PhoneNumberNormaliser.toIEE(phoneNumber, UserManager.getInstance().getUserCountryISO());
                } catch (IllegalArgumentException e) {
                    Log.e(TAG, "failed to format the number: " + standardisedNumber + "to IEE number: " + e.getMessage());
                    continue;
                } catch (NumberParseException e) {
                    Log.e(TAG, "failed to format the number: " + standardisedNumber + "to IEE number: " + e.getMessage());
                    continue;
                }
                user = realm.where(User.class)
                        .equalTo(User.FIELD_ID, standardisedNumber)
                        .findFirst();
                if (user != null) {
                    isRegistered = true;
                    DP = user.getDP();
                } else {
                    isRegistered = false;
                    DP = "";
                }
                name = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
                if (TextUtils.isEmpty(name)) { //some users can store numbers with no name; am a victim :-P
                    name = phoneNumber.substring(0, 4);
                }
                ContactsManager.Contact contact = new ContactsManager.Contact(name, phoneNumber, isRegistered, DP, standardisedNumber);
                if ((filter != null) && !filter.accept(contact)) {
                    continue;
                }
                contacts.add(contact);
            }
            List<Contact> ret = new ArrayList<>(contacts);
            if (comparator != null) {
                Collections.sort(ret, comparator);
            }
            return ret;
        } finally {
            cursor.close();
            realm.close();
        }
    }


    public interface FindCallback<T> {
        void done(T t);
    }

    public static final class Contact {
        public final String name, phoneNumber, DP, numberInIEE_Format;
        public final boolean isRegisteredUser;

        public Contact(String name, String phoneNumber, boolean isRegisteredUser, String DP, String standardisedPhoneNumber) {
            if (TextUtils.isEmpty(name)) {
                name = "unknown";
            }
            if (TextUtils.isEmpty(phoneNumber)) {
                throw new IllegalArgumentException("invalid phone number");
            }

            if (isRegisteredUser && standardisedPhoneNumber == null) {
                throw new IllegalArgumentException("standardised number is null");
            }
            this.numberInIEE_Format = standardisedPhoneNumber;
            this.name = name;
            this.phoneNumber = phoneNumber;
            this.isRegisteredUser = isRegisteredUser;
            this.DP = DP;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Contact contact = (Contact) o;

            return numberInIEE_Format.equals(contact.numberInIEE_Format);

        }

        @Override
        public int hashCode() {
            return numberInIEE_Format.hashCode();
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
