package com.pair.data;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.os.Handler;
import android.os.Process;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.Log;

import com.google.i18n.phonenumbers.NumberParseException;
import com.pair.Config;
import com.pair.pairapp.R;
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
        final Handler handler = new Handler();
        new Thread(new Runnable() {
            @Override
            public void run() {
                android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
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
            String phoneNumber, name, status, DP, standardisedNumber;
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
                } catch (IllegalArgumentException invalidPhoneNumber) {
                    Log.e(TAG, "failed to format to IEE number: " + invalidPhoneNumber.getMessage());
                    continue;
                } catch (NumberParseException e) {
                    Log.e(TAG, "failed to format to IEE number: " + e.getMessage());
                    continue;
                }
                user = realm.where(User.class)
                        .equalTo(User.FIELD_ID, standardisedNumber)
                        .findFirst();
                if (user != null) {
                    isRegistered = true;
                    status = user.getStatus();
                    DP = user.getDP();
                } else {
                    isRegistered = false;
                    status = "";
                    DP = "";
                }
                name = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
                if (TextUtils.isEmpty(name)) { //some users can store numbers with no name; am a victim :-P
                    name = context.getString(R.string.st_unknown);
                }

                ContactsManager.Contact contact = new ContactsManager.Contact(name, phoneNumber, status, isRegistered, DP, standardisedNumber);
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

    public Contact findContactByPhoneSync(String id, String isoRegionCode) {
        if (!PhoneNumberNormaliser.isIEE_Formatted(id, isoRegionCode)) {
            return null;
        }
        Cursor cursor = Config.getApplicationContext()
                .getContentResolver()
                .query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        PROJECT_NAME_PHONE, null, null, null
                );
        //noinspection ConstantConditions
        if (cursor == null) {
            return null;
        }
        String name, phoneNumber;
        while (cursor.moveToNext()) {
            phoneNumber = cursor.getString(cursor.getColumnIndex(ContactsContract
                    .CommonDataKinds.Phone.NUMBER));
            if (TextUtils.isEmpty(phoneNumber)) continue;

            try {
                phoneNumber = PhoneNumberNormaliser.toIEE(phoneNumber, isoRegionCode);
                if (phoneNumber.equals(id)) {
                    name = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
                    return new Contact(name, phoneNumber, null, false, null, null);
                }
            } catch (NumberParseException e) {
            } finally {
                cursor.close();
            }
        }
        return null;
    }

    public interface FindCallback<T> {
        void done(T t);
    }

    public static final class Contact {
        public final String name, phoneNumber, status, DP, numberInIEE_Format;
        public final boolean isRegisteredUser;

        public Contact(String name, String phoneNumber, String status, boolean isRegisteredUser, String DP, String standardisedPhoneNumber) {
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
            if (isRegisteredUser && standardisedPhoneNumber == null) {
                throw new IllegalArgumentException("standardised number is null");
            }
            this.numberInIEE_Format = standardisedPhoneNumber;
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
