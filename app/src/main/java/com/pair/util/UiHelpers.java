package com.pair.util;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.provider.ContactsContract;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ImageView;

import com.pair.data.ContactsManager;
import com.pair.pairapp.BuildConfig;
import com.pair.pairapp.R;
import com.pair.pairapp.ui.ChatActivity;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static android.widget.Toast.LENGTH_SHORT;
import static android.widget.Toast.makeText;

/**
 * @author by Null-Pointer on 5/28/2015.
 */
public class UiHelpers {

    private static final String TAG = UiHelpers.class.getSimpleName();

    public static String getFieldContent(EditText field) {
        String content = field.getText().toString();
        return content.trim();
    }

    public static void showErrorDialog(Context context, String message) {
        new AlertDialog.Builder(context)
                .setTitle(R.string.st_error)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .create()
                .show();
    }

    public static void showToast(String message) {
        makeText(Config.getApplicationContext(), message, LENGTH_SHORT).show();
    }

    public static void enterChatRoom(Context context, String peerId) {
        Intent intent = new Intent(context, ChatActivity.class);
        intent.putExtra(ChatActivity.EXTRA_PEER_ID, peerId);
        context.startActivity(intent);
    }


    /**
     * Use an AsyncTask to fetch the user's email addresses on a background thread, and update
     * the phone text field with results on the main UI thread.
     */
    public static class AutoCompleter extends AsyncTask<Void, Void, List<ContactsManager.Contact>> {
        public static final String TAG = AutoCompleter.class.getSimpleName();
        private final AutoCompleteTextView autoCompleteTextView;
        private final Context context;

        public AutoCompleter(Context context, AutoCompleteTextView editText) {
            this.context = context;
            this.autoCompleteTextView = editText;
        }

        @Override
        protected List<ContactsManager.Contact> doInBackground(Void... voids) {

            List<ContactsManager.Contact> phoneNumberCollection = new ArrayList<>();

            // Get all phone numbers from the user's contacts and copy them to a list.
            Cursor phoneCur = ContactsManager.INSTANCE.findAllContactsCursor(context);
            while (phoneCur.moveToNext()) {
                String phoneNumber = phoneCur.getString(phoneCur.getColumnIndex(ContactsContract
                        .CommonDataKinds.Phone.NUMBER));
                String name = phoneCur.getString(phoneCur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
                //TODO do this with regexp
                if (phoneNumber == null) {
                    Log.i(TAG, "no phone number for this contact, continuing");
                    continue;
                }
                phoneNumber = phoneNumber.replace("(", "").replace(")", "").replace("-", "");
                ContactsManager.Contact contact = new ContactsManager.Contact(name, phoneNumber, null, false);
                phoneNumberCollection.add(contact);
            }
            phoneCur.close();
            return phoneNumberCollection;

        }

        @Override
        protected void onPostExecute(List<ContactsManager.Contact> contacts) {
            //FIXME implement a cursor adapter for scalability..
            ArrayAdapter<ContactsManager.Contact> arrayAdapter =
                    new ArrayAdapter<>(context,
                            android.R.layout.simple_dropdown_item_1line, contacts);
            autoCompleteTextView.setAdapter(arrayAdapter);
        }
    }

    public static void loadImageIntoIv(final File file, final ImageView iv) {

        new AsyncTask<Void, Void, Bitmap>() {

            @Override
            protected Bitmap doInBackground(Void... params) {
                return BitmapFactory.decodeFile(file.getAbsolutePath());
            }

            @Override
            protected void onPostExecute(Bitmap bitmap) {
                if (bitmap != null)
                    iv.setImageBitmap(bitmap);
            }
        }.execute();
    }

    public static void loadImageIntoIv(final String resource, final ImageView iv) {

        new AsyncTask<Void, Void, Bitmap>() {

            @Override
            protected Bitmap doInBackground(Void... params) {
                try {
                    return BitmapFactory.decodeStream(new URL(resource).openStream());
                } catch (IOException e) {
                    if (BuildConfig.DEBUG) {
                        Log.e(TAG, e.getMessage(), e.getCause());
                    } else {
                        Log.e(TAG, e.getMessage());
                    }
                    return null;
                }
            }

            @Override
            protected void onPostExecute(Bitmap bitmap) {
                if (bitmap != null)
                    iv.setImageBitmap(bitmap);
            }
        }.execute();
    }

}
