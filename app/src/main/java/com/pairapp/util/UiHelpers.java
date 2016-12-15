package com.pairapp.util;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.NavUtils;
import android.support.v4.util.Pair;
import android.support.v7.app.AlertDialog;
import android.telephony.SmsManager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import com.pairapp.BuildConfig;
import com.pairapp.Errors.PairappException;
import com.pairapp.R;
import com.pairapp.adapter.SimpleAdapter;
import com.pairapp.data.ContactsManager;
import com.pairapp.data.Message;
import com.pairapp.data.User;
import com.pairapp.data.UserManager;
import com.pairapp.data.util.MessageUtils;
import com.pairapp.ui.ChatActivity;
import com.pairapp.ui.CreateMessageActivity;
import com.pairapp.ui.ImageViewer;
import com.pairapp.ui.MainActivity;
import com.pairapp.ui.ProfileActivity;
import com.pairapp.ui.SetUpActivity;
import com.pairapp.ui.SettingsActivity;
import com.rey.material.widget.CheckBox;
import com.rey.material.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static android.widget.Toast.LENGTH_LONG;
import static android.widget.Toast.LENGTH_SHORT;

/**
 * @author by Null-Pointer on 5/28/2015.
 */
public class UiHelpers {

    private static final String TAG = UiHelpers.class.getSimpleName();
    public static final int ADD_TO_CONTACTS_REQUEST = 0x3ec;
    private static Uri mMediaUri; //this is safe as at any point in time one activity may be doing this
    private static DialogInterface.OnClickListener dialogListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            handleAction(which);
        }
    };

    private static void handleAction(int which) {
        switch (which) {
            case ChatActivity.TAKE_PHOTO_REQUEST:
                takePhoto();
                break;
            case ChatActivity.TAKE_VIDEO_REQUEST:
                recordVideo();
                break;
            case ChatActivity.PICK_PHOTO_REQUEST:
                choosePicture();
                break;
            case ChatActivity.PICK_VIDEO_REQUEST:
                chooseVideo();
                break;
            case ChatActivity.PICK_FILE_REQUEST:
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("*/*");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.addCategory(Intent.CATEGORY_DEFAULT);
                try {
                    NavigationManager.getCurrentActivity().startActivityForResult(intent, ChatActivity.PICK_FILE_REQUEST);
                } catch (NavigationManager.NoActiveActivityException e) {
                    crashAndBurn(e);
                }
                break; //safety!
            default:
                break;
        }
    }

    public static void showPlainOlDialog(Context context, String message) {
        showPlainOlDialog(context, message, null);
    }

    public static void showPlainOlDialog(Context context, String message, DialogInterface.OnClickListener listener) {
        showPlainOlDialog(context, message, listener, true);
    }

    public static void showPlainOlDialog(Context context, String message, DialogInterface.OnClickListener listener, boolean cancelable) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        if (!cancelable && listener == null) {
            if (BuildConfig.DEBUG) {
                throw new RuntimeException();
            }
            cancelable = false;
        }
        builder.setMessage(message).setCancelable(cancelable).setPositiveButton(android.R.string.ok, listener);
        if (listener != null) {
            builder.setNegativeButton(R.string.no, null);
        }
        builder.create().show();
    }

    public static void showErrorDialog(FragmentActivity context, String message) {
        showPlainOlDialog(context, message);
    }

    public static void showErrorDialog(FragmentActivity context, int message) {
        showPlainOlDialog(context, getString(context, message));
    }

    public static void showErrorDialog(FragmentActivity context, String message, final Listener listener) {
        showPlainOlDialog(context, message, new OnclickListenerAdapter(listener), false);
    }


    public static void showErrorDialog(FragmentActivity context,
                                       String message,
                                       String okText, String noText, final Listener ok, final Listener no) {
        final DialogInterface.OnClickListener listener = new OnclickListenerAdapter(ok, no);
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(message).setPositiveButton(okText, listener);
        builder.setNegativeButton(noText, listener);
        builder.create().show();
    }

    public static void showErrorDialog(FragmentActivity context,
                                       int message,
                                       int okText, int noText, final Listener ok, final Listener no) {
        String messageText = getString(context, message),
                okTxt = getString(context, okText),
                noTxt = getString(context, noText);
        showErrorDialog(context, messageText, okTxt, noTxt, ok, no);
    }

    public static void showStopAnnoyingMeDialog(FragmentActivity activity, final String key, int message, int ok, int no, Listener okListener, Listener noListener) {
        showStopAnnoyingMeDialog(activity, key, getString(activity, message), getString(activity, ok), getString(activity, no), okListener, noListener);
    }

    public static void showStopAnnoyingMeDialog(FragmentActivity activity, final String key, int stopAnnoyingMe, int message, int ok, int no, Listener okListener, Listener noListener) {
        showStopAnnoyingMeDialog(activity, key, getString(activity, stopAnnoyingMe), getString(activity, message), getString(activity, ok), getString(activity, no), okListener, noListener);
    }

    private static String getString(Context context, int resId) {
        return context.getString(resId);
    }

    public static void promptAndExit(final FragmentActivity toFinish) {
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
        showStopAnnoyingMeDialog(toFinish,
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


    public static void gotoProfileActivity(Context context, String id, Bitmap placeHolder, Bitmap error) {
        Intent intent = new Intent(context, ProfileActivity.class);
        intent.putExtra(ProfileActivity.EXTRA_USER_ID, id);
        if (placeHolder != null) {
            intent.putExtra(ProfileActivity.EXTRA_AVARTAR_PLACEHOLDER, placeHolder);
        }
        if (error != null) {
            intent.putExtra(ProfileActivity.EXTRA_AVARTAR_ERROR, error);
        }
        context.startActivity(intent);
    }

    public static void gotoProfileActivity(Context context, String id) {
        gotoProfileActivity(context, id, null, null);
    }

    public static void attach(FragmentActivity appBaseActivity) {
        AlertDialog.Builder builder = new AlertDialog.Builder(appBaseActivity);
        int[] res = {
                R.drawable.ic_photo_camera_black_24dp,
                R.drawable.ic_videocam_black_24dp,
                R.drawable.ic_image_black_24dp,
                R.drawable.ic_movie_black_24dp,
                R.drawable.ic_folder_black_24dp
        };
        builder.setAdapter(new SimpleAdapter(appBaseActivity, res, appBaseActivity.getResources().getStringArray(R.array.attach_options)), dialogListener);
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    public static void gotoCreateMessageActivity(FragmentActivity context) {
        final Intent intent = new Intent(context, CreateMessageActivity.class);
        context.startActivity(intent);
    }

    public static void attemptToViewFile(FragmentActivity context, String path) throws PairappException {
        attemptToViewFile(context, new File(path));
    }

    public static void attemptToViewFile(FragmentActivity context, File file) throws PairappException {
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

    public static void gotoMainActivity(FragmentActivity activity) {
        final Intent intent = new Intent(activity, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        activity.startActivity(intent);
        activity.finish();
    }

    public static void gotoSetUpActivity(FragmentActivity context) {
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
            NavigationManager.getCurrentActivity().startActivityForResult(intent, ChatActivity.PICK_VIDEO_REQUEST);
        } catch (NavigationManager.NoActiveActivityException e) {
            crashAndBurn(e);
        }
    }

    public static void choosePicture() {
        Intent attachIntent;
        attachIntent = new Intent(Intent.ACTION_GET_CONTENT);
        attachIntent.setType("image/*");
        attachIntent.addCategory(Intent.CATEGORY_DEFAULT);
        try {
            NavigationManager.getCurrentActivity().startActivityForResult(attachIntent, ChatActivity.PICK_PHOTO_REQUEST);
        } catch (NavigationManager.NoActiveActivityException e) {
            crashAndBurn(e);
        }
    }

    private static void crashAndBurn(Exception e) {
        throw new RuntimeException(e.getCause());
    }

    private static void recordVideo() {
        try {
            File file = new File(Config.getAppVidMediaBaseDir(), "vid_" + SimpleDateUtil.timeStampNow() + ".mp4");
            mMediaUri = Uri.fromFile(file);
            MediaUtils.recordVideo(NavigationManager.getCurrentActivity(), mMediaUri, ChatActivity.TAKE_VIDEO_REQUEST);
        } catch (NavigationManager.NoActiveActivityException e) {
            crashAndBurn(e);
        }
    }

    public static void takePhoto() {
        try {
            File file = new File(Config.getAppImgMediaBaseDir(), "img_" + SimpleDateUtil.timeStampNow() + ".jpg");
            mMediaUri = Uri.fromFile(file);
            MediaUtils.takePhoto(NavigationManager.getCurrentActivity(), mMediaUri, ChatActivity.TAKE_PHOTO_REQUEST);
        } catch (NavigationManager.NoActiveActivityException e) {
            crashAndBurn(e);
        }
    }

    public static void recordAudio() {
        try {
            MediaUtils.recordAudio(NavigationManager.getCurrentActivity(), ChatActivity.RECORD_AUDIO_REQUEST);
        } catch (NavigationManager.NoActiveActivityException e) {
            crashAndBurn(e);
        }
    }

    private static String getActualPath(Intent data) {
        return FileUtils.resolveContentUriToFilePath(data.getData(), true);
    }

    public static Pair<String, Integer> completeAttachIntent(int requestCode, Intent data) throws PairappException {
        String actualPath;
        /*************************************************
         we cannot rely on external programs to return the right type of file based on the
         intent we fired.
         a well-known android app com.estrongs.android.pop-1 (Es File Explorer) allows users
         to pick any file type irrespective of the mime type bundle with the intent.

         also even though we expect camera/camcoder apps to store files in the uri we provided
         via the MediaStore.EXTRA_OUTPUT, we still check if they really adhered to that by checking
         the size and  the validity of the file there. if we dont find it we look for the uri from the intent.
         ****************************************************/
        switch (requestCode) {
            case ChatActivity.RECORD_AUDIO_REQUEST:
                actualPath = getActualPath(data);
                File file1 = new File(actualPath);
                File file = new File(Config.appAudioBaseDir(),
                        file1.getName().split("\\Q.\\E")[0] + "_" + SimpleDateUtil.timeStampNow() + ".amr");
                if (file1.renameTo(file)) {
                    actualPath = file.getAbsolutePath();
                } else {
                    throw new PairappException(Config.getApplicationContext().getString(R.string.audio_record_failed));
                }
                break;
            case ChatActivity.TAKE_PHOTO_REQUEST:
                //fall through
            case ChatActivity.TAKE_VIDEO_REQUEST:
                actualPath = mMediaUri.getPath();
                File f2 = new File(actualPath);
                if (f2.exists() && f2.length() > 0) { //some apps do not conform to standard
                    break;
                }
                //noinspection ResultOfMethodCallIgnored
                f2.delete();
                //fall through
            case ChatActivity.PICK_VIDEO_REQUEST:
                //fall through
            case ChatActivity.PICK_PHOTO_REQUEST:
                //fall through
            case ChatActivity.PICK_FILE_REQUEST:
                actualPath = getActualPath(data);
                break;
            default:
                throw new AssertionError("impossible");
        }
        if (TextUtils.isEmpty(actualPath)) {
            throw new PairappException(getString(Config.getApplicationContext(), R.string.error_use_file_manager), MessageUtils.ERROR_FILE_DOES_NOT_EXIST);
        }
        if (new File(actualPath).length() <= 0) {
            throw new PairappException(getString(Config.getApplicationContext(),
                    R.string.attachment_invalid) + Config.getApplicationContext().getString(R.string.new_line_path)
                    + actualPath + Config.getApplicationContext().getString(R.string.path));
        }
        int type = Message.TYPE_BIN_MESSAGE;
        if (MediaUtils.isImage(actualPath)) {
            type = Message.TYPE_PICTURE_MESSAGE;
        } else if (MediaUtils.isVideo(actualPath)) {
            type = Message.TYPE_VIDEO_MESSAGE;
        }
        return new Pair<>(actualPath, type);
    }

    public static void gotoSettingsActivity(Context context, int item) {
        Intent intent = new Intent(context, SettingsActivity.class);
        intent.putExtra(SettingsActivity.EXTRA_ITEM, item);
        context.startActivity(intent);
    }

    public static void doInvite(final Context context, final ContactsManager.Contact contact) {
        final String message = context.getString(R.string.invite_message);
        PackageManager manager = context.getPackageManager();
        final Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, message);
        // context.startActivity(intent);

        final List<ResolveInfo> infos = manager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
//        List<ResolveInfo> noPairap = new Arr
        PLog.d(TAG, "resolved: " + infos.size());
        if (infos.isEmpty() && contact == null) {
            showToast(context.getString(R.string.no_app_for_sharing));
        }

        if (contact != null && infos.isEmpty()) {
            final Listener listener = new Listener() {
                @Override
                public void onClick() {
                    SmsManager.getDefault().sendTextMessage("+" + contact.numberInIEE_Format, null, message, null, null);
                }
            };
            showErrorDialog((FragmentActivity) context,
                    context.getString(R.string.charges_may_apply),
                    context.getString(android.R.string.ok),
                    context.getString(android.R.string.cancel),
                    listener, null);
        }
        if (!infos.isEmpty()) {
            List<CharSequence> titles = new ArrayList<>();
            List<Drawable> icons = new ArrayList<>();
            final List<ActivityInfo> activityInfos = new ArrayList<>();
            for (int i = 0; i < infos.size(); i++) {
                ActivityInfo activityInfo = infos.get(i).activityInfo;
                String packageName = activityInfo.packageName;
                if (packageName.contains("whatsapp") || packageName.contains("viber") || packageName.contains("telegram")
                        || packageName.contains("facebook")
                        || packageName.contains("twitter")
                        || packageName.contains("tango")
                        || packageName.contains("com.android.mms")) {
                    titles.add(activityInfo.loadLabel(manager));
                    icons.add(activityInfo.loadIcon(manager));
                    activityInfos.add(activityInfo);
                }
            }
            if (activityInfos.isEmpty() && contact != null) {
                final Listener listener = new Listener() {
                    @Override
                    public void onClick() {
                        SmsManager.getDefault().sendTextMessage("+" + contact.numberInIEE_Format, null, message, null, null);
                    }
                };
                showErrorDialog((FragmentActivity) context,
                        context.getString(R.string.charges_may_apply),
                        context.getString(android.R.string.ok),
                        context.getString(android.R.string.cancel),
                        listener, null);
            } else {
                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                SimpleAdapter adapter = new SimpleAdapter(icons, titles);
                builder.setAdapter(adapter, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ActivityInfo activityInfo = activityInfos.get(which);
                        intent.setClassName(activityInfo.packageName, activityInfo.name);
                        context.startActivity(intent);
                    }
                }).setTitle(context.getString(R.string.invite_via));
                builder.create().show();
            }
        }
    }

    public static void addToContact(FragmentActivity activity, User user) {
        Intent intent = new Intent(ContactsContract.Intents.Insert.ACTION);
        // Sets the MIME type to match the Contacts Provider
        intent.setType(ContactsContract.RawContacts.CONTENT_TYPE);

        intent.putExtra(ContactsContract.Intents.Insert.PHONE, PhoneNumberNormaliser.toLocalFormat(user.getUserId(), user.getCountry()));
        intent.putExtra(ContactsContract.Intents.Insert.PHONE, PhoneNumberNormaliser.toLocalFormat(user.getUserId(), user.getCountry()));
        String name = user.getName();
        intent.putExtra(ContactsContract.Intents.Insert.NAME, name.startsWith("@") ? name.substring(1) : name);
        try {
            activity.startActivityForResult(intent, ADD_TO_CONTACTS_REQUEST);
        } catch (ActivityNotFoundException e) {
            // TODO: 8/23/2015 should we tell the user or is it that our intent was wrongly targeted?
            showPlainOlDialog(activity, activity.getString(R.string.no_contact_app_on_device));
        }
    }

    public interface Listener {
        void onClick();

    }

    private static final class OnclickListenerAdapter implements DialogInterface.OnClickListener {
        private final Listener ok;
        private final Listener no;
        private final Listener neutral;

        OnclickListenerAdapter(Listener ok, Listener no, Listener neutral) {
            this.ok = ok;
            this.no = no;
            this.neutral = neutral;
        }

        OnclickListenerAdapter(Listener ok, Listener no) {
            this(ok, no, null);
        }

        OnclickListenerAdapter(Listener ok) {
            this(ok, null);
        }


        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which) {
                case DialogInterface.BUTTON_POSITIVE:
                    if (ok != null) {
                        ok.onClick();
                    }
                    break;
                case DialogInterface.BUTTON_NEGATIVE:
                    if (no != null) {
                        no.onClick();
                    }
                    break;
                case DialogInterface.BUTTON_NEUTRAL:
                    if (neutral != null) {
                        neutral.onClick();
                    }
                    break;
                default:
                    throw new AssertionError();
            }
        }
    }

    public static void showStopAnnoyingMeDialog(FragmentActivity activity,
                                                final String key, final String stopAnnoyingMeText,
                                                final String message, String ok, String no, final Listener okListener, final Listener noListener) {
        boolean stopAnnoyingMe = UserManager.getInstance().getBoolPref(key, false);
        if (stopAnnoyingMe) {
            if (okListener != null) {
                okListener.onClick();
            }
            return;
        }

        @SuppressLint("InflateParams")
        View view = LayoutInflater.from(activity).inflate(R.layout.stop_annoying_me_dialog, null);
        TextView textView = (TextView) view.findViewById(R.id.tv_dialog_message);
        ViewUtils.setTypeface(textView, TypeFaceUtil.ROBOTO_REGULAR_TTF);
        textView.setText(message);

        textView = (TextView) view.findViewById(R.id.tv_stop_annoying_me);
        textView.setText(stopAnnoyingMeText);

        ViewUtils.setTypeface(textView, TypeFaceUtil.ROBOTO_REGULAR_TTF);
        final CheckBox checkBox = ((CheckBox) view.findViewById(R.id.cb_stop_annoying_me));

        final DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == AlertDialog.BUTTON_POSITIVE) {
                    if (okListener != null) {
                        okListener.onClick();
                    }
                } else {
                    if (noListener != null) {
                        noListener.onClick();
                    }
                }
                if (checkBox.isChecked()) {
                    UserManager.getInstance().putStandAlonePref(key, true);
                }
            }
        };
        new AlertDialog.Builder(activity)
                .setView(view)
                .setPositiveButton(ok, listener)
                .setNegativeButton(no, listener)
                .create().show();
    }

    public static void showStopAnnoyingMeDialog(FragmentActivity activity, final String key, final String message, String ok, String no, final Listener okListener, final Listener noListener) {
        showStopAnnoyingMeDialog(activity, key, getString(activity, R.string.do_not_ask_me_again), message, ok, no, okListener, noListener);
    }

}
