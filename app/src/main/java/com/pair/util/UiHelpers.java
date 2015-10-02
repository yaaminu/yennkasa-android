package com.pair.util;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.NavUtils;
import android.support.v4.util.Pair;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Toast;

import com.pair.Errors.PairappException;
import com.pair.data.Message;
import com.pair.data.UserManager;
import com.pair.data.util.MessageUtils;
import com.pair.pairapp.BuildConfig;
import com.pair.pairapp.R;
import com.pair.ui.ChatActivity;
import com.pair.ui.CreateMessageActivity;
import com.pair.ui.ImageViewer;
import com.pair.ui.MainActivity;
import com.pair.ui.PairAppBaseActivity;
import com.pair.ui.ProfileActivity;
import com.pair.ui.SetUpActivity;
import com.pair.ui.SettingsActivity;
import com.pair.ui.UsersActivity;
import com.rey.material.app.Dialog;
import com.rey.material.app.DialogFragment;
import com.rey.material.app.SimpleDialog;
import com.rey.material.widget.CheckBox;
import com.rey.material.widget.TextView;

import java.io.File;
import java.io.IOException;

import static android.widget.Toast.LENGTH_LONG;
import static android.widget.Toast.LENGTH_SHORT;

/**
 * @author by Null-Pointer on 5/28/2015.
 */
public class UiHelpers {

    private static final String TAG = UiHelpers.class.getSimpleName();
    private static final int TAKE_PHOTO_REQUEST = 0x0,
            TAKE_VIDEO_REQUEST = 0x1,
            PICK_PHOTO_REQUEST = 0x2,
            PICK_VIDEO_REQUEST = 0x3,
            PICK_FILE_REQUEST = 0x4;
    private static Uri mMediaUri; //this is safe as at any point in time one activity may be doing this
    private static DialogInterface.OnClickListener dialogListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            handleAction(which);
        }
    };

    private static void handleAction(int which) {
        switch (which) {
            case TAKE_PHOTO_REQUEST:
                takePhoto();
                break;
            case TAKE_VIDEO_REQUEST:
                recordVideo();
                break;
            case PICK_PHOTO_REQUEST:
                choosePicture();
                break;
            case PICK_VIDEO_REQUEST:
                chooseVideo();
                break;
            case PICK_FILE_REQUEST:
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("*/*");
                try {
                    NavigationManager.getCurrentActivity().startActivityForResult(intent, PICK_FILE_REQUEST);
                } catch (NavigationManager.NoActiveActivityException e) {
                    crashAndBurn(e);
                }
                break; //safety!
            default:
                break;
        }
    }

    public static String getFieldContent(EditText field) {
        String content = field.getText().toString();
        return content.trim();
    }

    public static void showPlainOlDialog(Context context, String message) {
        showPlainOlDialog(context, message, null);
    }

    public static void showPlainOlDialog(Context context, String message, boolean cancelable) {
        showPlainOlDialog(context, message, null, cancelable);
    }

    public static void showPlainOlDialog(Context context, String message, DialogInterface.OnClickListener listener) {
        showPlainOlDialog(context, message, listener, true);
    }

    public static void showPlainOlDialog(Context context, String message, DialogInterface.OnClickListener listener, boolean cancelable) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(message).setTitle(R.string.error).setCancelable(cancelable).setPositiveButton(android.R.string.ok, listener).create().show();
    }

    public static void showErrorDialog(PairAppBaseActivity context, String message) {
        showErrorDialog(context, message, null);
    }

    public static void showErrorDialog(PairAppBaseActivity context, int message) {
        showErrorDialog(context, getString(context, message));
    }

    public static void showErrorDialog(PairAppBaseActivity context, String message, final Listener listener) {
        SimpleDialog.Builder builder = new SimpleDialog.Builder(R.style.SimpleDialogLight) {
            @Override
            protected void onBuildDone(Dialog dialog) {
                super.onBuildDone(dialog);
                dialog.setCancelable(false);
            }

            @Override
            public void onPositiveActionClicked(DialogFragment fragment) {
                if (listener != null) {
                    listener.onClick();
                }
                super.onPositiveActionClicked(fragment);
            }
        };
        builder.message(message);
        builder.positiveAction(getString(context, android.R.string.ok));

        DialogFragment fragment = DialogFragment.newInstance(builder);
        showErrorDialogInternal(context, message, fragment);
    }

    private static void showErrorDialogInternal(PairAppBaseActivity context, String message, DialogFragment fragment) {
        try {
            fragment.show(context.getSupportFragmentManager(), null);
        } catch (Exception e) { //bad tokens,transaction after onsavedInstanceState, etc
            try {
                showPlainOlDialog(context, message);
            } catch (Exception ignored) { //still bad tokens ets.
                PLog.w(TAG, "failed to show message: " + message);
            }
        }
    }

    public static void showErrorDialog(PairAppBaseActivity context,
                                       String message,
                                       String okText, String noText, final Listener ok, final Listener no) {

        SimpleDialog.Builder builder = new SimpleDialog.Builder(R.style.SimpleDialogLight) {
            @Override
            protected void onBuildDone(Dialog dialog) {
                super.onBuildDone(dialog);
                dialog.setCancelable(false);
                dialog.setCanceledOnTouchOutside(false);
            }

            @Override
            public void onPositiveActionClicked(DialogFragment fragment) {
                if (ok != null) {
                    ok.onClick();
                }
                super.onPositiveActionClicked(fragment);
            }

            @Override
            public void onNegativeActionClicked(DialogFragment fragment) {
                if (no != null) {
                    no.onClick();
                }
                super.onNegativeActionClicked(fragment);
            }
        };

        builder.message(message);
        builder.positiveAction(okText)
                .negativeAction(noText);
        DialogFragment fragment = DialogFragment.newInstance(builder);
        showErrorDialogInternal(context, message, fragment);
    }

    public static void showErrorDialog(PairAppBaseActivity context,
                                       int message,
                                       int okText, int noText, final Listener ok, final Listener no) {
        String messageText = getString(context, message),
                okTxt = getString(context, okText),
                noTxt = getString(context, noText);
        showErrorDialog(context, messageText, okTxt, noTxt, ok, no);
    }

    public static DialogFragment newProgressDialog() {
        return newProgressDialog(false);
    }

    public static DialogFragment newProgressDialog(final boolean cancelable) {

        SimpleDialog.Builder builder = new SimpleDialog.Builder(R.style.SimpleDialogLight) {
            @Override
            protected void onBuildDone(Dialog dialog) {
                dialog.layoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                ViewUtils.setTypeface((TextView) dialog.findViewById(R.id.textView), TypeFaceUtil.DROID_SERIF_REGULAR_TTF);
                dialog.setCancelable(cancelable);
                dialog.setCanceledOnTouchOutside(cancelable);
            }
        };
        builder.contentView(R.layout.progress_dialog_indeterminate);
        DialogFragment fragment = DialogFragment.newInstance(builder);
        fragment.setCancelable(cancelable);
        return fragment;
    }

    public static void showStopAnnoyingMeDialog(PairAppBaseActivity activity, final String key, int message, int ok, int no, Listener okListener, Listener noListener) {
        showStopAnnoyingMeDialog(activity, key, getString(activity, message), getString(activity, ok), getString(activity, no), okListener, noListener);
    }

    public static void showStopAnnoyingMeDialog(PairAppBaseActivity activity, final String key, final String message, String ok, String no, final Listener okListener, final Listener noListener) {
        boolean stopAnnoyingMe = UserManager.getInstance().getBoolPref(key, false);
        if (stopAnnoyingMe) {
            if (okListener != null) {
                okListener.onClick();
            }
            return;
        }
        SimpleDialog.Builder builder = new SimpleDialog.Builder(R.style.SimpleDialogLight) {
            boolean touchedCheckBox = false, checkBoxValue;
            public CompoundButton.OnCheckedChangeListener listener = new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    touchedCheckBox = true;
                    checkBoxValue = isChecked;
                }
            };

            @Override
            protected void onBuildDone(Dialog dialog) {
                dialog.layoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                CheckBox checkBox = ((CheckBox) dialog.findViewById(R.id.cb_stop_annoying_me));
                TextView textView = (TextView) dialog.findViewById(R.id.tv_dialog_message);
                textView.setText(message);
                ViewUtils.setTypeface(textView, TypeFaceUtil.DROID_SERIF_REGULAR_TTF);
                checkBox.setOnCheckedChangeListener(listener);
            }

            @Override
            public void onPositiveActionClicked(DialogFragment fragment) {
                if (okListener != null) {
                    okListener.onClick();
                }
                super.onPositiveActionClicked(fragment);
                updateStopAnnoyingMe(checkBoxValue);
            }

            @Override
            public void onNegativeActionClicked(DialogFragment fragment) {
                if (noListener != null) {
                    noListener.onClick();
                }
                super.onNegativeActionClicked(fragment);
            }

            private void updateStopAnnoyingMe(boolean newValue) {
                if (touchedCheckBox) {
                    UserManager.getInstance().putPref(key, newValue);
                }
            }
        };
        builder.contentView(R.layout.stop_annoying_me_dialog);
        builder.positiveAction(ok)
                .negativeAction(no);
        DialogFragment fragment = DialogFragment.newInstance(builder);
        fragment.show(activity.getSupportFragmentManager(), null);
    }

    private static String getString(Context context, int resId) {
        return context.getString(resId);
    }

    public static void promptAndExit(final PairAppBaseActivity toFinish) {
        final UiHelpers.Listener cancelProgress = new UiHelpers.Listener() {
            @Override
            public void onClick() {
                TaskManager.executeOnMainThread(new Runnable() {
                    @Override
                    public void run() {
                        NavUtils.navigateUpFromSameTask(toFinish);
                    }
                });
            }
        };
        UiHelpers.showStopAnnoyingMeDialog(toFinish,
                toFinish.getClass().getName() + "sureToExit", R.string.st_sure_to_exit, R.string.i_know, android.R.string.no, cancelProgress, null);
    }

    public static void showToast(String message) {
        showToast(message, Toast.LENGTH_SHORT);
    }

    @SuppressWarnings("ConstantConditions")
    public static void showToast(String message, int duration) {
        if (duration != LENGTH_LONG && duration != LENGTH_SHORT) {
            duration = LENGTH_SHORT;
        }
        Toast.makeText(Config.getApplicationContext(), message, duration).show();
    }

    public static void showToast(int message) {
        showToast(getString(Config.getApplicationContext(), message));
    }

    public static void enterChatRoom(Context context, String peerId) {
        Intent intent = new Intent(context, ChatActivity.class);
        intent.putExtra(ChatActivity.EXTRA_PEER_ID, peerId);
        context.startActivity(intent);
    }

    public static void gotoProfileActivity(Context context) {
        gotoProfileActivity(context, UserManager.getMainUserId());
    }

    public static void gotoProfileActivity(Context context, String id) {
        Intent intent = new Intent(context, ProfileActivity.class);
        intent.putExtra(ProfileActivity.EXTRA_USER_ID, id);
        context.startActivity(intent);
    }

    public static void pickRecipient(Context context) {
        Bundle args = new Bundle();
        pickRecipient(context, args);
    }

    public static void pickRecipient(Context context, Bundle bundle) {
        bundle.putString(MainActivity.ARG_TITLE, context.getString(R.string.title_pick_recipient));
        final Intent intent = new Intent(context, UsersActivity.class);
        intent.putExtras(bundle);
        context.startActivity(intent);
    }

    public static void attach(PairAppBaseActivity appBaseActivity) {
        AlertDialog.Builder builder = new AlertDialog.Builder(appBaseActivity);
        builder.setItems(R.array.attach_options, dialogListener);
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    public static void gotoCreateMessageActivity(PairAppBaseActivity context) {
        final Intent intent = new Intent(context, CreateMessageActivity.class);
        context.startActivity(intent);
    }

    public static void attemptToViewFile(PairAppBaseActivity context, String path) throws PairappException {
        attemptToViewFile(context, new File(path));
    }

    public static void attemptToViewFile(PairAppBaseActivity context, File file) throws PairappException {
        if (file.exists()) {
            Intent intent;
            if (MediaUtils.isImage(file.getAbsolutePath())) {
                intent = new Intent(context, ImageViewer.class);
                intent.setData(Uri.fromFile(file));
            } else {
                intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(Uri.fromFile(file), FileUtils.getMimeType(file.getAbsolutePath()));
            }
            try {
                context.startActivity(intent);
            } catch (ActivityNotFoundException e) {
                showErrorDialog(context, R.string.error_sorry_no_application_to_open_file);
            }
        } else {
            throw new PairappException("File not found", MessageUtils.ERROR_FILE_DOES_NOT_EXIST);
        }
    }

    public static void gotoMainActivity(PairAppBaseActivity activity) {
        final Intent intent = new Intent(activity, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        activity.startActivity(intent);
        activity.finish();
    }

    public static void gotoSetUpActivity(PairAppBaseActivity context) {
        Intent intent = new Intent(context, SetUpActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        context.finish();
    }

    private static void chooseVideo() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("video/*");
        try {
            NavigationManager.getCurrentActivity().startActivityForResult(intent, PICK_VIDEO_REQUEST);
        } catch (NavigationManager.NoActiveActivityException e) {
            crashAndBurn(e);
        }
    }

    public static void choosePicture() {
        Intent attachIntent;
        attachIntent = new Intent(Intent.ACTION_GET_CONTENT);
        attachIntent.setType("image/*");
        try {
            NavigationManager.getCurrentActivity().startActivityForResult(attachIntent, PICK_PHOTO_REQUEST);
        } catch (NavigationManager.NoActiveActivityException e) {
            crashAndBurn(e);
        }
    }

    private static void crashAndBurn(Exception e) {
        throw new RuntimeException(e.getCause());
    }

    private static void recordVideo() {
        try {
            if (mMediaUri != null) {
                //something is wrong more than one activity firing intent for results.
                if (BuildConfig.DEBUG) {
                    throw new AssertionError();
                }
                return;
            }
            mMediaUri = FileUtils.getOutputUri(FileUtils.MEDIA_TYPE_VIDEO);
            MediaUtils.recordVideo(NavigationManager.getCurrentActivity(), mMediaUri, TAKE_VIDEO_REQUEST);
        } catch (NavigationManager.NoActiveActivityException | IOException e) {
            crashAndBurn(e);
        }
    }

    public static void takePhoto() {
        try {
            mMediaUri = FileUtils.getOutputUri(FileUtils.MEDIA_TYPE_IMAGE);
            MediaUtils.takePhoto(NavigationManager.getCurrentActivity(), mMediaUri, TAKE_PHOTO_REQUEST);
        } catch (NavigationManager.NoActiveActivityException | IOException e) {
            crashAndBurn(e);
        }
    }

    private static String getActualPath(Intent data) {
        String actualPath;
        Uri uri = data.getData();
        if (uri.getScheme().equals("content")) {
            actualPath = FileUtils.resolveContentUriToFilePath(uri);
        } else {
            actualPath = uri.getPath();
        }
        return actualPath;
    }

    public static Pair<String, Integer> completeAttachIntent(int requestCode, Intent data) throws PairappException {
        String actualPath;//we cannot rely on external programs to return the right type of file based on the
        //intent we fired.
        //a well-known android app com.estrongs.android.pop-1 (Es File Explorer) allows users
        //to pick any file type irrespective of the mime type bundle with the intent.
        switch (requestCode) {
            case TAKE_PHOTO_REQUEST:
                //fall through
            case TAKE_VIDEO_REQUEST:
                actualPath = mMediaUri.getPath();
                break;
            case PICK_VIDEO_REQUEST:
                //fall through
            case PICK_PHOTO_REQUEST:
                //fall through
            case PICK_FILE_REQUEST:
                actualPath = getActualPath(data);
                break;
            default:
                throw new AssertionError("impossible");
        }
        if (TextUtils.isEmpty(actualPath)) {
            throw new PairappException(getString(Config.getApplicationContext(), R.string.error_use_file_manager), MessageUtils.ERROR_FILE_DOES_NOT_EXIST);
        }
        int type = Message.TYPE_BIN_MESSAGE;
        if (MediaUtils.isImage(actualPath)) {
            type = Message.TYPE_PICTURE_MESSAGE;
        } else if (MediaUtils.isVideo(actualPath)) {
            type = Message.TYPE_VIDEO_MESSAGE;
        }
        mMediaUri = null;
        return new Pair<>(actualPath, type);
    }

    public static void dismissProgressDialog(final DialogFragment dialogFragment) {
        if (dialogFragment != null) {
            try {
                FragmentManager supportFragmentManager = dialogFragment.getActivity().getSupportFragmentManager();
                supportFragmentManager.beginTransaction()
                        .remove(dialogFragment)
                        .commitAllowingStateLoss();
                //noinspection ConstantConditions
                    TaskManager.executeOnMainThread(new Runnable() {
                        @Override
                        public void run() {
                            //noinspection EmptyCatchBlock
                            try {
                                dialogFragment.dismiss();
                            } catch (Exception e) {
                            }
                        }
                    });
            } catch (Exception ignored) {
                PLog.e(TAG,ignored.getMessage());
            }
        }
    }

    public static void gotoSettingsActivity(Context context, int item) {
        Intent intent = new Intent(context, SettingsActivity.class);
        intent.putExtra(SettingsActivity.EXTRA_ITEM, item);
        context.startActivity(intent);
    }

    private static AdapterView.OnItemClickListener makeAttachListenr() {
        return attachListClickListener;
    }

    private static final AdapterView.OnItemClickListener attachListClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            handleAction(position);
        }
    };

    public interface Listener {
        void onClick();

    }

    private static DialogFragment makeAttachDialog(final Context context) {
        SimpleDialog.Builder builder = new SimpleDialog.Builder(R.style.SimpleDialogLight) {
            @Override
            protected void onBuildDone(Dialog dialog) {

            }

            @Override
            public void onPositiveActionClicked(DialogFragment fragment) {
                handleAction(getSelectedIndex());
                super.onPositiveActionClicked(fragment);
            }
        };
        builder.items(context.getResources().getStringArray(R.array.attach_options), 0)
                .positiveAction(getString(context, android.R.string.ok));
        return DialogFragment.newInstance(builder);
    }
}
