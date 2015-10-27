package com.idea;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

import com.idea.data.ContactSyncService;
import com.idea.data.Message;
import com.idea.data.UserManager;
import com.idea.messenger.MessageCenter;
import com.idea.messenger.MessageProcessor;
import com.idea.messenger.PairAppClient;
import com.idea.pairapp.BuildConfig;
import com.idea.pairapp.R;
import com.idea.util.Config;
import com.idea.util.PLog;
import com.idea.util.TaskManager;
import com.parse.ParseCrashReporting;

/**
 * @author Null-Pointer on 6/6/2015.
 */
public class PairApp extends Application {
    public static final String TAG = PairApp.class.getName();

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

    public static String typeToString(Context context, int type) {
        switch (type) {
            case Message.TYPE_PICTURE_MESSAGE:
                return context.getString(R.string.picture);
            case Message.TYPE_VIDEO_MESSAGE:
                return context.getString(com.idea.pairapp.R.string.video);
            case Message.TYPE_BIN_MESSAGE:
                return context.getString(com.idea.pairapp.R.string.file);
            case Message.TYPE_TEXT_MESSAGE:
                return context.getString(com.idea.pairapp.R.string.message);
            default:
                throw new AssertionError("Unknown message type");
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        PLog.setLogLevel(BuildConfig.DEBUG ? PLog.LEVEL_VERBOSE : PLog.LEVEL_FATAL);
        TaskManager.init(this);
        Config.init(this);
        ParseCrashReporting.enable(this);
        if (UserManager.getInstance().isUserVerified()) {
            PairAppClient.startIfRequired(this);
        }
    }

}
