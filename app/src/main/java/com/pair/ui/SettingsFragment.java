package com.pair.ui;


import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;
import android.support.v4.app.ListFragment;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;

import com.pair.adapter.SettingsAdapter;
import com.pair.data.UserManager;
import com.pair.data.settings.PersistedSetting;
import com.pair.pairapp.R;
import com.pair.util.UiHelpers;
import com.rey.material.app.DialogFragment;
import com.rey.material.app.SimpleDialog;

/**
 * A simple {@link Fragment} subclass.
 */
public class SettingsFragment extends ListFragment {


    public static final int PICK_RINGTONE_REQUEST_CODE = 1001;
    private BaseAdapter adapter;
    private UserManager.CallBack callback = new UserManager.CallBack() {
        @Override
        public void done(Exception e) {
            UiHelpers.dismissProgressDialog(dialogFragment);
            refreshDisplay();
        }
    };
    private DialogFragment dialogFragment;

    public SettingsFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        setHasOptionsMenu(true);
        dialogFragment = UiHelpers.newProgressDialog();
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
            dialogFragment.show(getFragmentManager(), null);
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
        adapter = new SettingsAdapter(UserManager.getInstance().userSettings());
        setListAdapter(adapter);
    }

    private static String key;

    @Override
    public void onListItemClick(ListView l, View v, int position, final long id) {
        final PersistedSetting item = (PersistedSetting) l.getAdapter().getItem(position);
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
            SimpleDialog.Builder builder = new SimpleDialog.Builder(R.style.SimpleDialogLight) {
                @Override
                public void onPositiveActionClicked(DialogFragment fragment) {
                    super.onPositiveActionClicked(fragment);
                    int selected = getSelectedIndex();
                    item.setIntValue(selected);
                    item.setSummary(options[selected]);
                    UserManager.getInstance().putPref(key, selected);
                    refreshDisplay();
                }
            };
            builder.title(getString(R.string.deleteOldMessages_title) + " ?");
            builder.positiveAction(getString(android.R.string.ok));
            builder.negativeAction(getString(android.R.string.no));
            builder.items(options, item.getIntValue());
            DialogFragment.newInstance(builder).show(getFragmentManager(), null);
        }
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK && requestCode == PICK_RINGTONE_REQUEST_CODE) {
            String ringToneUri = PersistedSetting.SILENT;
            Uri uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            String title = getString(R.string.no_name);
            if (uri != null) {
                ringToneUri = uri.toString();
                String[] projection = {MediaStore.MediaColumns.TITLE};
                Cursor cursor = getContext().getContentResolver().query(uri, projection, null, null, null);
                if (cursor.getCount() > 0) {
                    cursor.moveToFirst();
                    title = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.TITLE));
                }
                cursor.close();
            }
            UserManager.getInstance().putPrefUpdateSummary(key, ringToneUri, title);
            refreshDisplay();

        }
    }
}
