package com.pair.util;

import android.content.Context;
import android.content.Intent;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.widget.EditText;

import com.pair.pairapp.Config;
import com.pair.pairapp.ProfileActivity;
import com.pair.pairapp.R;
import com.pair.pairapp.ui.ChatActivity;
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
        showErrorDialog(context, message, Listener.DUMMY_LISTENER);
    }

    public static void showErrorDialog(FragmentActivity context, int message) {
        showErrorDialog(context, getString(context, message));
    }

    public static void showErrorDialog(FragmentActivity context, String message, final Listener listener) {
        SimpleDialog.Builder builder = new SimpleDialog.Builder(R.style.SimpleDialogLight) {
            @Override
            public void onPositiveActionClicked(DialogFragment fragment) {
                listener.onClick();
                super.onPositiveActionClicked(fragment);
            }
        };
        builder.message(message);
        builder.positiveAction(getString(context, android.R.string.ok));

        DialogFragment fragment = DialogFragment.newInstance(builder);
        fragment.show(context.getSupportFragmentManager(), null);
    }

    public static void showErrorDialog(FragmentActivity context,
                                       String title, String message,
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
                                       int title, int message,
                                       int okText, int noText, final Listener ok, final Listener no) {
        String titleText = getString(context, title),
                messageText = getString(context, message),
                okTxt = getString(context, okText),
                noTxt = getString(context, noText);
        showErrorDialog(context, titleText, messageText, okTxt, noTxt, ok, no);
    }


    private static String getString(Context context, int resId) {
        return context.getString(resId).toUpperCase();
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
        Listener DUMMY_LISTENER = new Listener() {
            @Override
            public void onClick() {
                //do nothing
            }
        };

        void onClick();
    }
}
