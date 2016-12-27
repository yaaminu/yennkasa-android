package com.yennkasa.data;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.provider.ContactsContract;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;

import com.google.i18n.phonenumbers.NumberParseException;
import com.yennkasa.Errors.PairappException;
import com.yennkasa.util.Config;
import com.yennkasa.util.PLog;
import com.yennkasa.util.PhoneNumberNormaliser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.realm.Realm;

import static com.yennkasa.util.Config.getApplicationContext;

/**
 * @author Null-Pointer on 6/11/2015.
 */
public class ContactsManager {
    private static final ContactsManager INSTANCE = new ContactsManager();
    private static final String TAG = ContactsManager.class.getSimpleName();
    private static final String[] PROJECT_NAME_PHONE = new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME};

    private ContactsManager() {

    }

    public static ContactsManager getInstance() {
        return INSTANCE;
    }

    public Cursor findAllContactsCursor(Context context) {
        return getCursor(context);
    }

    public List<Contact> findAllContactsSync(Filter<Contact> filter, Comparator<Contact> comparator)
            throws PairappException {
        int results = ContextCompat.checkSelfPermission(Config.getApplicationContext(), Manifest.permission.READ_CONTACTS);
        if (results == PackageManager.PERMISSION_DENIED) {
            throw new PairappException("Permission denied");
        }
        return doFindAllContacts(filter, comparator, getCursor(Config.getApplicationContext()));
    }

    public final Contact findContact(String userId) {
        Cursor cursor = getCursor(getApplicationContext());
        String phoneNumber, standardisedNumber;
        Realm userRealm = User.Realm(getApplicationContext());
        try {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    phoneNumber = cursor.getString(cursor.getColumnIndex(ContactsContract
                            .CommonDataKinds.Phone.NUMBER));
                    if (TextUtils.isEmpty(phoneNumber)) {
                        PLog.i(TAG, "strange!: no phone number for this contact, ignoring");
                        continue;
                    }
                    try {
                        standardisedNumber = PhoneNumberNormaliser.toIEE(phoneNumber, UserManager.getInstance().getUserCountryISO(userRealm));
                        if (userId.equals(standardisedNumber)) {
                            String name = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
                            if (TextUtils.isEmpty(name)) { //some users can store numbers with no name; am a victim :-P
                                name = phoneNumber;
                            }
                            return new Contact(name, phoneNumber, false, "avatar_empty", standardisedNumber);
                        }
                    } catch (IllegalArgumentException | NumberParseException e) {
                        PLog.w(TAG, "failed to format the number: " + phoneNumber + "to IEE number: " + e.getMessage());
                    }
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            userRealm.close();
        }
        return null;
    }

    public void findAllContacts(final Filter<Contact> filter, final Comparator<Contact> comparator,
                                final FindCallback<List<Contact>> callback) {
        //noinspection ConstantConditions
        final Handler handler = new Handler(Looper.myLooper() != null ? Looper.myLooper() : Looper.getMainLooper());
        new Thread(new Runnable() {
            @Override
            public void run() {
                android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                final List<Contact> contacts;
                try {
                    contacts = findAllContactsSync(filter, comparator);
                    //run on the thread on which clients originally called us if possible
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.done(null, contacts);
                        }
                    });
                } catch (final PairappException e) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.done(e, null);
                        }
                    });
                }
            }
        }).start();
    }

    private Cursor getCursor(Context context) {
        ContentResolver cr = context.getContentResolver();
        return cr.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, PROJECT_NAME_PHONE,
                null, null, null);
    }

    private List<Contact> doFindAllContacts(Filter<Contact> filter, Comparator<Contact> comparator, Cursor cursor) {
        if (cursor == null || cursor.getCount() == 0) {
            return Collections.emptyList();
        }
        Context context = getApplicationContext();
        Realm realm = User.Realm(context);
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
                    PLog.i(TAG, "strange!: no phone number for this contact, ignoring");
                    continue;
                }
                try {
                    standardisedNumber = PhoneNumberNormaliser.toIEE(phoneNumber, UserManager.getInstance().getUserCountryISO(realm));
                } catch (IllegalArgumentException e) {
                    PLog.e(TAG, "failed to format the number: " + standardisedNumber + "to IEE number: " + e.getMessage());
                    continue;
                } catch (NumberParseException e) {
                    PLog.e(TAG, "failed to format the number: " + standardisedNumber + "to IEE number: " + e.getMessage());
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
                    name = phoneNumber;
                }
                ContactsManager.Contact contact = new ContactsManager.Contact(name, phoneNumber, isRegistered, DP, standardisedNumber);
                try {
                    if ((filter != null) && !filter.accept(contact)) {
                        continue;
                    }
                } catch (Exception e) {
                    break;
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
        void done(Exception e, T t);
    }

    public interface Filter<T> {
        boolean accept(T t) throws AbortOperation;

        class AbortOperation extends Exception {
            public AbortOperation(String message) {
                super(message);
            }
        }
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
}
