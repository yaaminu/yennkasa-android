package com.idea.messenger;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;

import com.idea.Errors.ErrorCenter;
import com.idea.Errors.PairappException;
import com.idea.data.Message;
import com.idea.util.Config;
import com.idea.util.FileUtils;
import com.idea.util.LiveCenter;
import com.idea.util.PLog;
import com.idea.util.TaskManager;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import io.realm.Realm;
import retrofit.RequestInterceptor;
import retrofit.RestAdapter;
import retrofit.RetrofitError;
import retrofit.android.AndroidLog;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p/>
 */
public class Worker extends IntentService {
    private static final String DOWNLOAD = "com.idea.messenger.action.download";

    private static final String MESSAGE_JSON = "com.idea.messenger.extra.MESSAGE";

    private static final String TAG = Worker.class.getName();

    public static void download(Context context, Message message) {
        Intent intent = new Intent(context, Worker.class);
        intent.setAction(DOWNLOAD);
        intent.putExtra(MESSAGE_JSON, Message.toJSON(message));
        context.startService(intent);
    }

    AttachmentsApi api;

    public Worker() {
        super("Worker");
        RestAdapter adapter = new RestAdapter.Builder()
                .setEndpoint(Config.getFilesMetaDataApiUrl())
                .setLog(new AndroidLog(TAG))
                .setLogLevel(com.idea.net.file_service.BuildConfig.DEBUG ? RestAdapter.LogLevel.FULL : RestAdapter.LogLevel.NONE)
                .setRequestInterceptor(new RequestInterceptor() {
                    @Override
                    public void intercept(RequestFacade requestFacade) {
                        requestFacade.addHeader("Authorization", "kiibodaS3crite");
                    }
                }).build();
        api = adapter.create(AttachmentsApi.class);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (DOWNLOAD.equals(action)) {
                final String messageJson = intent.getStringExtra(MESSAGE_JSON);
                Message message = Message.fromJSON(messageJson);
                download(message);
            }
        }
    }

    private static final Set<String> downloading = new HashSet<>();

    private void download(final Message message) {
        synchronized (downloading) {
            if (!downloading.add(message.getId())) {
                PLog.w(TAG, "already  downloading message");
                return;
            }
        }
        try {
            LiveCenter.acquireProgressTag(message.getId());
        } catch (PairappException e) {
            throw new RuntimeException(e.getCause());
        }

        final String messageId = message.getId(),
                messageBody = message.getMessageBody();
        final int type = message.getType();
        TaskManager.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
                        Realm realm = null;
                        final File finalFile;
                        String destination = messageBody.substring(messageBody.lastIndexOf('/'));

                        switch (type) {
                            case Message.TYPE_VIDEO_MESSAGE:
                                finalFile = new File(Config.getAppVidMediaBaseDir(), destination);
                                break;
                            case Message.TYPE_PICTURE_MESSAGE:
                                finalFile = new File(Config.getAppImgMediaBaseDir(), destination);
                                break;
                            case Message.TYPE_BIN_MESSAGE:
                                finalFile = new File(Config.getAppBinFilesBaseDir(), destination);
                                break;
                            default:
                                throw new AssertionError("should never happen");
                        }
                        FileUtils.ProgressListener listener = new FileUtils.ProgressListener() {
                            @Override
                            public void onProgress(long expected, long processed) {
                                double ratio = ((double) processed) / expected;
                                final int progress = (int) (100 * ratio);
                                LiveCenter.updateProgress(messageId, progress);
                            }
                        };
                        try {
                            FileUtils.save(finalFile, messageBody, listener);
                            realm = Message.REALM(Config.getApplicationContext());
                            realm.beginTransaction();
                            Message toBeUpdated = realm.where(Message.class).equalTo(Message.FIELD_ID, messageId).findFirst();
                            toBeUpdated.setMessageBody(finalFile.getAbsolutePath());
                            realm.commitTransaction();
                            onComplete(null);
                        } catch (IOException e) {
                            PLog.d(TAG, e.getMessage(), e.getCause());
                            Exception error = new Exception(Config.getApplicationContext().getString(com.idea.data.R.string.st_unable_to_connect));
                            onComplete(error);
                        } finally {
                            if (realm != null) {
                                realm.close();
                            }
                        }
                    }

                    private void onComplete(final Exception error) {
                        LiveCenter.releaseProgressTag(messageId);
                        synchronized (downloading) {
                            downloading.remove(messageId);
                        }
                        if (error != null) {
                            ErrorCenter.reportError(TAG + "download", error.getMessage());
                        } else {
                            try {
                                api.markForDeletion(messageBody);
                            } catch (RetrofitError err) {

                            }
                        }
                    }
                });
    }
}
