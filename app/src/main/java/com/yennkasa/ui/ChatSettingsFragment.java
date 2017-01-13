package com.yennkasa.ui;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.View;

import com.yennkasa.R;
import com.yennkasa.adapter.YennkasaBaseAdapter;
import com.yennkasa.data.Conversation;
import com.yennkasa.data.UserManager;
import com.yennkasa.util.PLog;
import com.yennkasa.util.UiHelpers;

import java.util.ArrayList;
import java.util.List;

import butterknife.Bind;
import io.realm.Realm;

import static android.content.ContentValues.TAG;

/**
 * Created by yaaminu on 1/12/17.
 */
public class ChatSettingsFragment extends BaseFragment {

    private static final int PICK_RINGTONE_REQUEST_CODE_MESSAGE = 2000;
    private static final int PICK_RINGTONE_REQUEST_CODE_CALL = 2001;

    @Bind(R.id.recycler_view)
    RecyclerView recyclerView;
    List<ChatSettingsItem> items;

    @Bind(R.id.mini_toolbar)
    Toolbar toolbar;
    private ChatSettingsAdapter adapter;
    private Conversation currConversation;
    private String[] titles;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (!(context instanceof UserProvider)) {
            throw new ClassCastException("parent activity must implement:" + UserProvider.class.getName());
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        items = new ArrayList<>(13);
        currConversation = ((UserProvider) getContext()).getConversation();
        titles = getResources().getStringArray(R.array.chat_settings_titles);
        for (int i = 0; i < 13; i++) {
            items.add(get(i));
        }
    }

    @Nullable
    private ChatSettingsItem get(int position) {
        String title = titles[position],
                summary;
        int value = -1;
        if (isHeader(position)) return new ChatSettingsItem(title, "", -1);

        switch (position) {
            case 1:
                summary = currConversation.getNotificationSoundMessageTitle();
                summary = summary == null ? getString(R.string.use_default) : summary;
                break;
            case 2:
                summary = currConversation.getNotificationSoundCallTitle();
                summary = summary == null ? getString(R.string.use_default) : summary;
                break;
            case 3:
                summary = getString(R.string.mute_chat_summary);
                value = currConversation.isMute() ? 1 : 0;
                break;
            case 5:
                // TODO: 1/13/17 use value
                summary = getString(R.string.hide_chat_summary);
                break;
            case 6:
                // TODO: 1/13/17 use value
                summary = getString(R.string.lock_chat_summary);
                break;
            case 8:
                // TODO: 1/13/17 use real value;
                summary = getString(R.string.background_image_summary);
                break;
            case 9:
                value = currConversation.getTextSize();
                summary = getString(R.string.message_text_size_summary);
                break;
            case 11:
                value = currConversation.getAutoDownloadWifi();
                summary = getString(R.string.auto_download_wifi_summary);
                break;
            case 12:
                value = currConversation.getAutoDownloadMobile();
                summary = getString(R.string.auto_download_mobile_summary);
                break;
            default:
                summary = "";
        }
        return new ChatSettingsItem(title, summary, value);
    }

    private boolean isHeader(int position) {
        return position == 0 ||
                position == 4 ||
                position == 7 ||
                position == 10;
    }

    @Override
    protected int getLayout() {
        return R.layout.chat_settings_layout;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        adapter = new ChatSettingsAdapter(delegate);
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        toolbar.setTitle(R.string.chat_settings);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK && (requestCode == PICK_RINGTONE_REQUEST_CODE_MESSAGE || requestCode == PICK_RINGTONE_REQUEST_CODE_CALL)) {
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
            Realm realm = ((UserProvider) getContext()).conversationRealm();
            realm.beginTransaction();
            if (requestCode == PICK_RINGTONE_REQUEST_CODE_CALL) {
                currConversation.setNotificationSoundCall(ringToneUri);
                currConversation.setNotificationSoundCallTitle(title);
                items.get(2).summary = currConversation.getNotificationSoundCallTitle();
            } else if (requestCode == PICK_RINGTONE_REQUEST_CODE_MESSAGE) { //explicitly check for surety
                currConversation.setNotificationSoundMessage(ringToneUri);
                currConversation.setNotificationSoundMessageTitle(title);
                items.get(1).summary = currConversation.getNotificationSoundMessageTitle();
            } else {
                throw new AssertionError();
            }
            realm.commitTransaction();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        adapter.notifyDataChanged();
    }

    ChatSettingsAdapter.Delegate delegate = new ChatSettingsAdapter.Delegate() {
        @Override
        public int getViewLayout(int position) {
            switch (position) {
                case 0:
                case 4:
                case 7:
                case 10:
                    return R.layout.category_list_item;
                case 1:
                case 2:
                case 6:
                case 8:
                case 9:
                case 11:
                case 12:
                    return R.layout.list_list_item;
                case 3:
                case 5:
                    return R.layout.true_or_false_list_item;
                default:
                    throw new AssertionError();
            }
        }

        @Override
        public String getUserId() {
            return ((UserProvider) getContext()).currentUser().getUserId();
        }

        @Override
        public boolean hideDivider(int position) {
            return position == 3 || position == 6 || position == 9 || position == 12;
        }

        @Override
        public void onItemClick(YennkasaBaseAdapter<ChatSettingsItem> adapter, View view, int position, long id) {
            if (!isHeader(position)) {
                handleClick(position);
            }
        }

        @Override
        public boolean onItemLongClick(YennkasaBaseAdapter<ChatSettingsItem> adapter, View view, int position, long id) {
            return false;
        }

        @NonNull
        @Override
        public List<ChatSettingsItem> dataSet() {
            return items;
        }

        @Override
        public Realm userRealm() {
            return ((UserProvider) getContext()).realm();
        }
    };

    private void handleClick(int position) {
        switch (position) {
            case 1:
                UiHelpers.pickRingtone(this, currConversation.getNotificationSoundMessage(), PICK_RINGTONE_REQUEST_CODE_MESSAGE);
                break;
            case 2:
                UiHelpers.pickRingtone(this, currConversation.getNotificationSoundCall(), PICK_RINGTONE_REQUEST_CODE_CALL);
                break;
            case 3:
                Realm realm = ((UserProvider) getActivity()).conversationRealm();
                realm.beginTransaction();
                currConversation.setMute(!currConversation.isMute());
                realm.commitTransaction();
                items.get(3).value = currConversation.isMute() ? 1 : 0;
                refreshDisplay();
                break;
            case 9:
                showTextSizeOptions(position);
                break;
            case 11:
                showAutoDownloadOptions(position);
                break;
            case 12:
                break;
            default:
                throw new AssertionError();
        }
    }

    private void showAutoDownloadOptions(int position) {
        final String[] tmp = getResources().getStringArray(R.array.attachment_types);

    }

    private void showTextSizeOptions(final int position) {
        final String[] tmp = getResources().getStringArray(R.array.text_size_options);
        new AlertDialog.Builder(getContext())
                .setSingleChoiceItems(tmp,
                        getSelectedPosition(currConversation.getTextSize()),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Realm realm = ((UserProvider) getActivity()).conversationRealm();
                                realm.beginTransaction();
                                currConversation.setTextSize(getSize(which));
                                realm.commitTransaction();
                                items.get(position).value = currConversation.getTextSize();
                                refreshDisplay();
                            }
                        }).setPositiveButton(android.R.string.ok, null)
                .setCancelable(false)
                .create().show();
    }

    private int getSize(int position) {
        //return 14+(2*position)??
        switch (position) {
            case 0:
                return 75;
            case 1:
                return 100;
            case 2:
                return 125;
            case 3:
                return 150;
            case 4:
                return 200;
            default:
                return 100;
        }
    }

    private int getSelectedPosition(int textSize) {
        switch (textSize) {
            case 75:
                return 0;
            case 100:
                return 1;
            case 125:
                return 2;
            case 150:
                return 3;
            case 200:
                return 4;
            default:
                return 1;
        }
    }

    private void refreshDisplay() {
        adapter.notifyDataChanged();
    }

}
