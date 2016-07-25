package com.pairapp.ui;


import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;
import android.support.v4.app.ListFragment;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;

import com.pairapp.R;
import com.pairapp.adapter.SettingsAdapter;
import com.pairapp.data.PersistedSetting;
import com.pairapp.data.UserManager;
import com.pairapp.util.PLog;

/**
 * A simple {@link Fragment} subclass.
 */
public class SettingsFragment extends ListFragment {


    public static final int PICK_RINGTONE_REQUEST_CODE = 1001;
    public static final String TAG = SettingsFragment.class.getSimpleName();
    private UserManager.CallBack callback = new UserManager.CallBack() {
        @Override
        public void done(Exception e) {
            dialogFragment.dismiss();
            refreshDisplay();
        }
    };
    private ProgressDialog dialogFragment;
    private SettingsAdapter.Delegate delegate = new SettingsAdapter.Delegate() {
        @Override
        public void onItemClick(AdapterView parent, View view, int position, long itemId) {
            final PersistedSetting item = (PersistedSetting) parent.getAdapter().getItem(position);
            key = item.getKey();
            if (key.equals(UserManager.NEW_MESSAGE_TONE)) {
                Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
                if (!item.getStringValue().equals(UserManager.DEFAULT)) {
                    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(item.getStringValue()));
                }
                startActivityForResult(intent, PICK_RINGTONE_REQUEST_CODE);
            } else if (key.equals(UserManager.DELETE_OLDER_MESSAGE)) {
                final String[] options = getResources().getStringArray(R.array.deleteOldMessages_options);
                new AlertDialog.Builder(getContext())
                        .setItems(options, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int selected) {
                                item.setIntValue(selected);
                                item.setSummary(options[selected]);
                                UserManager.getInstance().putPref(key, selected);
                                refreshDisplay();
                            }
                        })
                        .setTitle(getString(R.string.deleteOldMessages_title) + " ?")
                        .create()
                        .show();
            }
        }
    };

    public SettingsFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        setHasOptionsMenu(true);
        dialogFragment = new ProgressDialog(getActivity());
        dialogFragment.setMessage(getString(R.string.st_please_wait));
        dialogFragment.setCancelable(false);
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        refreshDisplay();
    }

    @Override
    public void onResume() {
        super.onResume();
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_restore_default_settings) {
            dialogFragment.show();
            UserManager.getInstance().restoreUserDefaultSettings(callback);
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.settings_fragment, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    private void refreshDisplay() {
        BaseAdapter adapter = new SettingsAdapter(UserManager.getInstance().userSettings(), delegate);
        setListAdapter(adapter);
    }

    private static String key;

    @Override
    public void onListItemClick(ListView l, View v, int position, final long id) {

    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK && requestCode == PICK_RINGTONE_REQUEST_CODE) {
            String ringToneUri = UserManager.SILENT;
            Uri uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            String title = getString(R.string.silent);
            if (uri != null) {
                ringToneUri = uri.toString();
                Cursor cursor = null;
                try {
                    String[] projection = {MediaStore.MediaColumns.TITLE};
                    cursor = getContext().getContentResolver().query(uri, projection, null, null, null);
                    if (cursor != null && cursor.getCount() > 0) {
                        cursor.moveToFirst();
                        int columnIndex = cursor.getColumnIndex(MediaStore.MediaColumns.TITLE);
                        title = columnIndex == -1 ? getString(R.string.no_name) : cursor.getString(columnIndex);
                    } else {
                        title = getString(R.string.no_name);
                    }
                } catch (IllegalArgumentException e) {
                    PLog.e(TAG, "error while retrieving ringtone from media store");
                } finally {
                    if (cursor != null) {
                        cursor.close();
                    }
                }
            }
            UserManager.getInstance().putPrefUpdateSummary(key, ringToneUri, title);
            refreshDisplay();

        }
    }
}
