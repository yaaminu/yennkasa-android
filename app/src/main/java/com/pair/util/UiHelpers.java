package com.pair.util;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;
import android.provider.ContactsContract;
import android.util.Log;
import android.widget.Adapter;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;

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
   public static class EmailAutoCompleteTask extends AsyncTask<Void, Void, List<String>> {


        public static final String TAG = EmailAutoCompleteTask.class.getSimpleName();
        private final AutoCompleteTextView autoCompleteTextView;
        private final Context context;

        public EmailAutoCompleteTask(Context context, AutoCompleteTextView editText) {
            this.context = context;
            this.autoCompleteTextView = editText;
        }

        @Override
        protected List<String> doInBackground(Void... voids) {
            ArrayList<String> phoneNumberCollection = new ArrayList<String>();

            // Get all emails from the user's contacts and copy them to a list.
            ContentResolver cr = this.context.getContentResolver();
            Cursor phoneCur = cr.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null,
                    null, null, null);
            while (phoneCur.moveToNext()) {
                String phoneNumber = phoneCur.getString(phoneCur.getColumnIndex(ContactsContract
                        .CommonDataKinds.Phone.DATA));
                Log.d(TAG,phoneNumber);
                phoneNumberCollection.add(phoneNumber);
            }
            phoneCur.close();

            return phoneNumberCollection;
        }

        @Override
        protected void onPostExecute(List<String> emailAddressCollection) {
            ArrayAdapter<String> arrayAdapter =
                    new ArrayAdapter<String>(context,
                            android.R.layout.simple_dropdown_item_1line, emailAddressCollection);
            autoCompleteTextView.setAdapter(arrayAdapter);
        }
    }

    public static String  getFieldContent(EditText field) {
        String content = field.getText().toString();
        return content.trim();
    }
    public static void showErrorDialog(Context context,String message){
        new AlertDialog.Builder(context)
                .setTitle("Error")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .create()
                .show();
    }
}