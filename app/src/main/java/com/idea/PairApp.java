package com.idea;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

import com.crashlytics.android.Crashlytics;
import com.idea.data.ContactSyncService;
import com.idea.data.Message;
import com.idea.data.UserManager;
import com.idea.messenger.MessageCenter;
import com.idea.messenger.MessageProcessor;
import com.idea.messenger.PairAppClient;
import com.idea.pairapp.BuildConfig;
import com.idea.pairapp.R;
import com.idea.util.Config;
import com.idea.util.FileUtils;
import com.idea.util.PLog;
import com.idea.util.TaskManager;

import java.util.regex.Pattern;

import io.fabric.sdk.android.Fabric;

/**
 * @author Null-Pointer on 6/6/2015.
 */
public class PairApp extends Application {
    public static final String TAG = PairApp.class.getName();
//    // Note: Your consumer key and secret should be obfuscated in your source code before shipping.
//    private static final String TWITTER_KEY = "p1KaIqoXt9ujhMaOPcQY4Xxi9";
//    private static final String TWITTER_SECRET = "0m16n21jk5jNpyTusC6DrGvxmLlMcEfRUCRIkINfJoFy8oM1rZ";

    private static void enableComponent(Class clazz) {
        PLog.d(TAG, "enabling " + clazz.getSimpleName());
        ComponentName receiver = new ComponentName(Config.getApplication(), clazz);

        PackageManager pm = Config.getApplication().getPackageManager();

        pm.setComponentEnabledSetting(receiver,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);
    }

    private static void disableComponent(Class clazz) {
        PLog.d(TAG, "disabling " + clazz.getSimpleName());
        ComponentName receiver = new ComponentName(Config.getApplication(), clazz);

        PackageManager pm = Config.getApplication().getPackageManager();
        pm.setComponentEnabledSetting(receiver,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }

    public static void enableComponents() {
//        enableComponent(BootReceiver.class);
        enableComponent(ContactSyncService.class);
        enableComponent(PairAppClient.class);
        enableComponent(MessageProcessor.class);
        enableComponent(MessageCenter.class);
    }

    public static void disableComponents() {
//        disableComponent(BootReceiver.class);
        disableComponent(ContactSyncService.class);
        disableComponent(PairAppClient.class);
        disableComponent(MessageProcessor.class);
        disableComponent(MessageCenter.class);
    }

    private static final Pattern documentPattern = Pattern.compile("pdf|doc|docx|odt|epub|xls|xlsx|csv", Pattern.CASE_INSENSITIVE),
            textPattern = Pattern.compile("txt|html|json", Pattern.CASE_INSENSITIVE),
            appPattern = Pattern.compile("apk", Pattern.CASE_INSENSITIVE),
            presentationPattern = Pattern.compile("ppt|pptx", Pattern.CASE_INSENSITIVE),
            archivePattern = Pattern.compile("zip|tar|bz|rar|7z|gzip|gz", Pattern.CASE_INSENSITIVE),
            audioPattern = Pattern.compile("mp3|amr|wav|m4a|ogg|mp2", Pattern.CASE_INSENSITIVE);


    public static String typeToString(Context context, Message message) {
        switch (message.getType()) {
            case Message.TYPE_PICTURE_MESSAGE:
                return context.getString(R.string.picture);
            case Message.TYPE_VIDEO_MESSAGE:
                return context.getString(com.idea.pairapp.R.string.video);
            case Message.TYPE_TEXT_MESSAGE:
                return context.getString(com.idea.pairapp.R.string.message);
            case Message.TYPE_BIN_MESSAGE:
                String ext = FileUtils.getExtension(message.getMessageBody(), "");
                if (documentPattern.matcher(ext).find()) {
                    return context.getString(R.string.document);
                }
                if (textPattern.matcher(ext).find()) {
                    return context.getString(R.string.text_file);
                }
                if (appPattern.matcher(ext).find()) {
                    return context.getString(R.string.application);
                }
                if (presentationPattern.matcher(ext).find()) {
                    return context.getString(R.string.presentation);
                }

                if (archivePattern.matcher(ext).find()) {
                    return context.getString(R.string.archive);
                }
                if (audioPattern.matcher(ext).find()) {
                    return context.getString(R.string.audio);
                }
                return context.getString(R.string.file);

            default:
                throw new AssertionError("Unknown message type");
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        PLog.setLogLevel(BuildConfig.DEBUG ? PLog.LEVEL_VERBOSE : PLog.LEVEL_FATAL);
//        TwitterAuthConfig config = new TwitterAuthConfig(TWITTER_KEY,TWITTER_SECRET);
        TaskManager.init(this);
        Config.init(this);
        new Thread() {
            public void run() {
                Fabric.with(PairApp.this, new Crashlytics());
                if (UserManager.getInstance().isUserVerified())
                    PairAppClient.startIfRequired(PairApp.this);
            }
        }.start();
    }

}
