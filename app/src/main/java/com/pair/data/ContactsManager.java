package com.pair.data;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;
import android.provider.ContactsContract;

/**
 * @author Null-Pointer on 6/11/2015.
 */
public class ContactsManager {
    public static final String TAG = ContactsManager.class.getSimpleName();

    public static final ContactsManager INSTANCE = new ContactsManager();

    private ContactsManager() {

    }

    public void findAll(final Context context, final FindCallback callback) {
        new TaskRunner(context, callback).execute();
    }

    public Cursor findAllSync(Context context) {
        return getCursor(context);
    }

    private Cursor getCursor(Context context) {
        ContentResolver cr = context.getContentResolver();
        return cr.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER,
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME},
                null, null, null);
    }

    public interface FindCallback {
        void done(Cursor cursor);
    }

    private class TaskRunner extends AsyncTask<Void, Void, Cursor> {
        Context context;
        FindCallback callback;

        TaskRunner(Context context, FindCallback callback) {
            this.context = context;
            this.callback = callback;
        }

        @Override
        protected Cursor doInBackground(Void... params) {
            return getCursor(this.context);
        }

        @Override
        protected void onPostExecute(Cursor cursor) {
            callback.done(cursor);
        }
    }

    public final class Contact {
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
    }
}
