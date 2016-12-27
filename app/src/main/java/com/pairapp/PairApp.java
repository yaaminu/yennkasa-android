package com.pairapp;

import android.app.Application;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.support.multidex.MultiDexApplication;

import com.crashlytics.android.Crashlytics;
import com.pairapp.messenger.MessageCenter2;
import com.pairapp.messenger.MessageProcessor;
import com.pairapp.messenger.PairAppClient;
import com.pairapp.messenger.SmsReciever;
import com.pairapp.util.Config;
import com.pairapp.util.ConnectivityReceiver;
import com.pairapp.util.PLog;
import com.pairapp.util.Task;
import com.pairapp.util.TaskManager;
import com.pairapp.workers.BootReceiver;
import com.path.android.jobqueue.Job;
import com.path.android.jobqueue.JobManager;
import com.path.android.jobqueue.TagConstraint;
import com.path.android.jobqueue.config.Configuration;
import com.path.android.jobqueue.di.DependencyInjector;

import io.fabric.sdk.android.Fabric;
import io.realm.Realm;
import uk.co.chrisjenx.calligraphy.CalligraphyConfig;

/**
 * @author Null-Pointer on 6/6/2015.
 */
public class PairApp extends MultiDexApplication {
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

    public static void enableComponent(Class clazz) {
        PLog.d(TAG, "enabling " + clazz.getSimpleName());
        ComponentName receiver = new ComponentName(Config.getApplication(), clazz);

        PackageManager pm = Config.getApplication().getPackageManager();

        pm.setComponentEnabledSetting(receiver,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);
    }

    public static void disableComponent(Class clazz) {
        PLog.d(TAG, "disabling " + clazz.getSimpleName());
        ComponentName receiver = new ComponentName(Config.getApplication(), clazz);

        PackageManager pm = Config.getApplication().getPackageManager();
        pm.setComponentEnabledSetting(receiver,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }

    public static void enableComponents() {
        enableComponent(BootReceiver.class);
        enableComponent(ConnectivityReceiver.class);
        enableComponent(PairAppClient.class);
        enableComponent(MessageProcessor.class);
        enableComponent(MessageCenter2.class);
    }

    public static void disableComponents() {
        disableComponent(BootReceiver.class);
        disableComponent(ConnectivityReceiver.class);
        disableComponent(PairAppClient.class);
        disableComponent(MessageProcessor.class);
        disableComponent(MessageCenter2.class);
        disableComponent(SmsReciever.class);
    }

    @Override
    public void onCreate() {
        super.onCreate();
//        if (LeakCanary.isInAnalyzerProcess(this)) {
//            return;
//        }
//        LeakCanary.install(this);
        Realm.init(this);
        PLog.setLogLevel(BuildConfig.DEBUG ? PLog.LEVEL_VERBOSE : PLog.LEVEL_FATAL);
        Config.init(this);
        CalligraphyConfig config = new CalligraphyConfig.Builder().setDefaultFontPath(null)
                .build();
        CalligraphyConfig.initDefault(config);
        PairAppClient.startIfRequired(Config.getApplicationContext());
        new Thread(new Runnable() {
            @Override
            public void run() {
                Fabric.with(Config.getApplication(), new Crashlytics());
                jobRunner = new JobRunnerImpl(Config.getApplication(), injector);
                TaskManager.init(jobRunner);
            }
        }).start();
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
            new Thread(new Runnable() {
                @Override
                public void run() {
                    jobManager.start();
                }
            }).start();
        }
    }


}
