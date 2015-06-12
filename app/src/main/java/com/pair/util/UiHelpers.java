package com.pair.util;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.provider.ContactsContract;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;

import com.pair.data.ContactsManager;
import com.pair.pairapp.ChatActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Null-Pointer on 5/28/2015.
 */
public class UiHelpers {

    /**
     * Use an AsyncTask to fetch the user's email addresses on a background thread, and update
     * the email text field with results on the main UI thread.
     */
    public static class AutoCompleter extends AsyncTask<Void, Void, List<String>> {
        public static final String TAG = AutoCompleter.class.getSimpleName();
        private final AutoCompleteTextView autoCompleteTextView;
        private final Context context;

        public AutoCompleter(Context context, AutoCompleteTextView editText) {
            this.context = context;
            this.autoCompleteTextView = editText;
        }

        @Override
        protected List<String> doInBackground(Void... voids) {

            List<String> phoneNumberCollection = new ArrayList<>();

            // Get all phone numbers from the user's contacts and copy them to a list.
            Cursor phoneCur = ContactsManager.INSTANCE.findAllSync(context);
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
                Log.d(TAG, name + ":" + phoneNumber);
                phoneNumberCollection.add(phoneNumber);
            }
            phoneCur.close();
            return phoneNumberCollection;
        }

        @Override
        protected void onPostExecute(List<String> emailAddressCollection) {
            //FIXME implement a cursor adapter for scalability..
            ArrayAdapter<String> arrayAdapter =
                    new ArrayAdapter<>(context,
                            android.R.layout.simple_dropdown_item_1line, emailAddressCollection);
            autoCompleteTextView.setAdapter(arrayAdapter);
            System.out.println("AutoCompleter.onPostExecute");
        }
    }

    public static String getFieldContent(EditText field) {
        String content = field.getText().toString();
        return content.trim();
    }

    public static void showErrorDialog(Context context, String message) {
        new AlertDialog.Builder(context)
                .setTitle("Error")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .create()
                .show();
    }

    public static void enterChatRoom(Context context,String peerId, String peerName) {
        Intent intent = new Intent(context, ChatActivity.class);
        intent.putExtra(ChatActivity.PEER_NAME, peerName);
        intent.putExtra(ChatActivity.PEER_ID, peerId);
        context.startActivity(intent);
    }

}
