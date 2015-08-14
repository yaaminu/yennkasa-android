package com.pair.util;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
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

    public static void showErrorDialog(Context context, String message) {
        new AlertDialog.Builder(context)
                .setTitle(R.string.st_error)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .create()
                .show();
    }

    public static void showErrorDialog(Context context, int message) {
        showErrorDialog(context, getString(context, message));
    }

    public static void showErrorDialog(Context context, String message, final DialogInterface.OnClickListener listener) {
        SimpleDialog.Builder builder = new SimpleDialog.Builder(R.style.SimpleDialog) {
            @Override
            public void onPositiveActionClicked(DialogFragment fragment) {
                if (listener == null) return;
                listener.onClick(null, AlertDialog.BUTTON_POSITIVE);
            }
        };
        builder.title(getString(context, R.string.st_error));
        builder.message(message);
        builder.positiveAction(getString(context, android.R.string.ok));
        builder.build(context).show();
    }

    public static void showErrorDialog(Context context,
                                       String title, String message,
                                       String okText, String noText, DialogInterface.OnClickListener ok, DialogInterface.OnClickListener no) {
        new AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(okText, ok)
                .setNegativeButton(noText, no)
                .create()
                .show();
    }

    public static void showErrorDialog(Context context,
                                       int title, int message,
                                       int okText, int noText, final DialogInterface.OnClickListener ok, final DialogInterface.OnClickListener no) {
        SimpleDialog.Builder builder = new SimpleDialog.Builder(R.style.SimpleDialog) {
            @Override
            public void onPositiveActionClicked(DialogFragment fragment) {
                if (ok != null) {
                    ok.onClick(null, AlertDialog.BUTTON_POSITIVE);
                }
            }

            @Override
            public void onNegativeActionClicked(DialogFragment fragment) {
                if (no != null) {
                    no.onClick(null, AlertDialog.BUTTON_NEGATIVE);
                }
            }
        };
        builder.title(getString(context, title));
        builder.message(getString(context, message));
        builder.positiveAction(getString(context, okText))
                .negativeAction(getString((Context) context, (int) noText));
        builder.build(context).show();
    }


    private static String getString(Context context, int resId) {
        return getString(context, resId);
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

}
