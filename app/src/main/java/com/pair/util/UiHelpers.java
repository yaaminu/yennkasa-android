package com.pair.util;

import android.content.Context;
import android.content.Intent;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.view.ViewGroup;
import android.widget.EditText;

import com.pair.pairapp.Config;
import com.pair.pairapp.ProfileActivity;
import com.pair.pairapp.R;
import com.pair.pairapp.ui.ChatActivity;
import com.rey.material.app.Dialog;
import com.rey.material.app.DialogFragment;
import com.rey.material.app.SimpleDialog;

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

    public static void showErrorDialog(FragmentActivity context, String message) {
        showErrorDialog(context, message, null);
    }

    public static void showErrorDialog(FragmentActivity context, int message) {
        showErrorDialog(context, getString(context, message));
    }

    public static void showErrorDialog(FragmentActivity context, String message, final Listener listener) {
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
        fragment.show(context.getSupportFragmentManager(), null);
    }

    public static void showErrorDialog(FragmentActivity context,
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
        final FragmentManager fragmentManager = context.getSupportFragmentManager();
        fragment.show(fragmentManager, null);
    }

    public static void showErrorDialog(FragmentActivity context,
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
        fragment.setCancelable(false);
        return fragment;
    }

    private static String getString(Context context, int resId) {
        return context.getString(resId).toUpperCase();
    }


    public static void promptAndExit(final FragmentActivity activity) {
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
        makeText(Config.getApplicationContext(), message, LENGTH_SHORT).show();
    }

    @SuppressWarnings("ConstantConditions")
    public static void showToast(int message) {
        makeText(Config.getApplicationContext(), message, LENGTH_SHORT).show();
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

    public interface Listener {
        void onClick();
    }
}
