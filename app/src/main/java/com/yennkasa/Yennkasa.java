package com.yennkasa;

import android.app.Application;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.support.multidex.MultiDexApplication;

import com.crashlytics.android.Crashlytics;
import com.path.android.jobqueue.Job;
import com.path.android.jobqueue.JobManager;
import com.path.android.jobqueue.TagConstraint;
import com.path.android.jobqueue.config.Configuration;
import com.path.android.jobqueue.di.DependencyInjector;
import com.yennkasa.messenger.MessageCenter2;
import com.yennkasa.messenger.MessageProcessor;
import com.yennkasa.messenger.SmsReciever;
import com.yennkasa.messenger.YennkasaClient;
import com.yennkasa.util.Config;
import com.yennkasa.util.ConnectionUtils;
import com.yennkasa.util.ConnectivityReceiver;
import com.yennkasa.util.PLog;
import com.yennkasa.util.Task;
import com.yennkasa.util.TaskManager;
import com.yennkasa.workers.BootReceiver;

import io.fabric.sdk.android.Fabric;
import io.realm.Realm;
import uk.co.chrisjenx.calligraphy.CalligraphyConfig;

/**
 * @author Null-Pointer on 6/6/2015.
 */
public class Yennkasa extends MultiDexApplication {
    public static final String TAG = Yennkasa.class.getName();
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
        enableComponent(YennkasaClient.class);
        enableComponent(MessageProcessor.class);
        enableComponent(MessageCenter2.class);
    }

    public static void disableComponents() {
        disableComponent(BootReceiver.class);
        disableComponent(ConnectivityReceiver.class);
        disableComponent(YennkasaClient.class);
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
        PLog.setLogLevel(BuildConfig.DEBUG ? PLog.LEVEL_VERBOSE : PLog.LEVEL_WARNING);
        Config.init(this);
        ConnectionUtils.init();
        CalligraphyConfig config = new CalligraphyConfig.Builder().setDefaultFontPath(null)
                .build();
        CalligraphyConfig.initDefault(config);
        YennkasaClient.startIfRequired(Config.getApplicationContext());
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
