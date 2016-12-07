package com.pairapp;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

import com.crashlytics.android.Crashlytics;
import com.pairapp.data.ContactSyncService;
import com.pairapp.data.Message;
import com.pairapp.messenger.MessageProcessor;
import com.pairapp.messenger.PairAppClient;
import com.pairapp.util.Config;
import com.pairapp.util.ConnectionUtils;
import com.pairapp.util.FileUtils;
import com.pairapp.util.PLog;
import com.pairapp.util.Task;
import com.pairapp.util.TaskManager;
import com.path.android.jobqueue.Job;
import com.path.android.jobqueue.JobManager;
import com.path.android.jobqueue.TagConstraint;
import com.path.android.jobqueue.config.Configuration;
import com.path.android.jobqueue.di.DependencyInjector;
import com.squareup.leakcanary.LeakCanary;

import java.util.regex.Pattern;

import io.fabric.sdk.android.Fabric;

/**
 * @author Null-Pointer on 6/6/2015.
 */
public class PairApp extends Application {
    public static final String TAG = PairApp.class.getName();
    //    //    // Note: Your consumer key and secret should be obfuscated in your source code before shipping.
//    private static final String TWITTER_KEY = "p1KaIqoXt9ujhMaOPcQY4Xxi9";
//    private static final String TWITTER_SECRET = "0m16n21jk5jNpyTusC6DrGvxmLlMcEfRUCRIkINfJoFy8oM1rZ";
    private static final DependencyInjector injector = new DependencyInjector() {
        @Override
        public void inject(Job job) {

        }
    };
    @SuppressWarnings("FieldCanBeLocal") //keeps this alive after application#oncreate
    private JobRunnerImpl jobRunner;

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
    }

    public static void disableComponents() {
        disableComponent(ContactSyncService.class);
        disableComponent(PairAppClient.class);
        disableComponent(MessageProcessor.class);
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
                return context.getString(com.pairapp.R.string.video);
            case Message.TYPE_TEXT_MESSAGE:
                return context.getString(com.pairapp.R.string.message);
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
        if (LeakCanary.isInAnalyzerProcess(this)) {
            return;
        }
        LeakCanary.install(this);
        Fabric.with(this, new Crashlytics());
        PLog.setLogLevel(BuildConfig.DEBUG ? PLog.LEVEL_VERBOSE : PLog.LEVEL_FATAL);
        Config.init(this);
        jobRunner = new JobRunnerImpl(this, injector);
        TaskManager.init(jobRunner);
        ConnectionUtils.init(this);
        PairAppClient.startIfRequired(this);
    }

    @SuppressWarnings("WeakerAccess")
    static final class JobRunnerImpl implements TaskManager.JobRunner {
        private final JobManager jobManager;

        public JobRunnerImpl(Application application, DependencyInjector injector) {
            final Configuration config = new Configuration.Builder(application)
                    .injector(injector)
                    .customLogger(new PLog("JobRunner"))
                    .jobSerializer(new Task.JobSerializer())
                    .build();
            this.jobManager = new JobManager(application, config);
        }

        @Override
        public long runJob(Task task) {
            return jobManager.addJob(task);
        }

        @Override
        public void cancelJobs(String tag) {
            jobManager.cancelJobs(TagConstraint.ALL, tag);
        }

        @Override
        public void start() {
            jobManager.start();
        }
    }


}
