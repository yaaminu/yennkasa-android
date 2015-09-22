package com.pair;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import com.pair.data.Message;
import com.pair.data.UserManager;
import com.pair.messenger.MessageCenter;
import com.pair.messenger.MessageProcessor;
import com.pair.messenger.PairAppClient;
import com.pair.pairapp.R;
import com.pair.parse_client.ParseClient;
import com.pair.util.Config;
import com.pair.workers.ContactSyncService;

/**
 * @author Null-Pointer on 6/6/2015.
 */
public class PairApp extends Application {
    public static final String TAG = PairApp.class.getName();

    private static void enableComponent(Class clazz) {
        Log.d(TAG, "enabling " + clazz.getSimpleName());
        ComponentName receiver = new ComponentName(Config.getApplication(), clazz);

        PackageManager pm = Config.getApplication().getPackageManager();

        pm.setComponentEnabledSetting(receiver,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);
    }

    private static void disableComponent(Class clazz) {
        Log.d(TAG, "disabling " + clazz.getSimpleName());
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
                return context.getString(com.pair.pairapp.R.string.video);
            case Message.TYPE_BIN_MESSAGE:
                return context.getString(com.pair.pairapp.R.string.file);
            case Message.TYPE_TEXT_MESSAGE:
                return context.getString(com.pair.pairapp.R.string.message);
            default:
                throw new AssertionError("Unknown message type");
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Config.init(this);
        ParseClient.init(this);
        if (UserManager.getInstance().isUserVerified()) {
            PairAppClient.startIfRequired(this);
        }
    }

}
