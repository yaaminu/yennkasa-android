package com.pair.util;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.util.Pair;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import com.pair.Errors.PairappException;
import com.pair.data.Message;
import com.pair.data.util.MessageUtils;
import com.pair.pairapp.BuildConfig;
import com.pair.pairapp.R;
import com.pair.ui.ChatActivity;
import com.pair.ui.CreateMessageActivity;
import com.pair.ui.ImageViewer;
import com.pair.ui.MainActivity;
import com.pair.ui.PairAppBaseActivity;
import com.pair.ui.ProfileActivity;
import com.pair.ui.UsersActivity;
import com.rey.material.app.Dialog;
import com.rey.material.app.DialogFragment;
import com.rey.material.app.SimpleDialog;

import java.io.File;
import java.io.IOException;

import static android.widget.Toast.LENGTH_SHORT;

/**
 * @author by Null-Pointer on 5/28/2015.
 */
public class UiHelpers {

    private static final String TAG = UiHelpers.class.getSimpleName();

    public static String getFieldContent(EditText field) {
        String content = field.getText().toString();
        return content.trim();
    }

    public static void showPlainOlDialog(Context context, String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(message).setTitle(R.string.error).setPositiveButton(android.R.string.ok, null).create().show();
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
                Log.w(TAG, "failed to show message: " + message);
            }
        }
    }

    public static void showErrorDialog(PairAppBaseActivity context,
                                       String message,
                                       String okText, String noText, final Listener ok, final Listener no) {

        SimpleDialog.Builder builder = new SimpleDialog.Builder(R.style.SimpleDialogLight) {
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
                dialog.setCancelable(cancelable);
                dialog.setCanceledOnTouchOutside(cancelable);
            }
        };
        builder.contentView(R.layout.progress_dialog_indeterminate);
        DialogFragment fragment = DialogFragment.newInstance(builder);
        fragment.setCancelable(cancelable);
        return fragment;
    }

    private static String getString(Context context, int resId) {
        return context.getString(resId).toUpperCase();
    }


    public static void promptAndExit(final PairAppBaseActivity activity) {
        final UiHelpers.Listener cancelProgress = new UiHelpers.Listener() {
            @Override
            public void onClick() {
                activity.finish();
            }
        };
        UiHelpers.showErrorDialog(activity, R.string.st_sure_to_exit, R.string.i_know, android.R.string.no, cancelProgress, null);
    }

    @SuppressWarnings("ConstantConditions")
    public static void showToast(String message) {
        Toast.makeText(Config.getApplicationContext(), message, LENGTH_SHORT).show();
    }

    @SuppressWarnings("ConstantConditions")
    public static void showToast(int message) {
        Toast.makeText(Config.getApplicationContext(), message, LENGTH_SHORT).show();
    }

    public static void enterChatRoom(Context context, String peerId) {
        Intent intent = new Intent(context, ChatActivity.class);
        intent.putExtra(ChatActivity.EXTRA_PEER_ID, peerId);
        context.startActivity(intent);
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

    public interface Listener {
        void onClick();
    }

    private static final int TAKE_PHOTO_REQUEST = 0x0,
            TAKE_VIDEO_REQUEST = 0x1,
            PICK_PHOTO_REQUEST = 0x2,
            PICK_VIDEO_REQUEST = 0x3,
            PICK_FILE_REQUEST = 0x4;

    private static DialogInterface.OnClickListener dialogListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
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
    };


    private static void chooseVideo() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("video/*");
        try {
            NavigationManager.getCurrentActivity().startActivityForResult(intent, PICK_VIDEO_REQUEST);
        } catch (NavigationManager.NoActiveActivityException e) {
            crashAndBurn(e);
        }
    }

    private static void choosePicture() {
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

    private static Uri mMediaUri; //this is safe as at any point in time one activity may be doing this

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

    private static void takePhoto() {
        try {
            if (mMediaUri != null) {
                //something is wrong more than one activity firing intent for results.
                if (BuildConfig.DEBUG) {
                    throw new AssertionError();
                }
                return;
            }
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
        int type = Message.TYPE_BIN_MESSAGE;
        if (MediaUtils.isImage(actualPath)) {
            type = Message.TYPE_PICTURE_MESSAGE;
        } else if (MediaUtils.isVideo(actualPath)) {
            type = Message.TYPE_VIDEO_MESSAGE;
        }
        mMediaUri = null;
        return new Pair<>(actualPath, type);
    }

}
