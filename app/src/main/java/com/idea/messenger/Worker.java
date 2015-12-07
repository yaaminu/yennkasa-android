package com.idea.messenger;

import android.app.IntentService;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;

import com.idea.Errors.ErrorCenter;
import com.idea.Errors.PairappException;
import com.idea.data.Message;
import com.idea.pairapp.R;
import com.idea.ui.ChatActivity;
import com.idea.util.Config;
import com.idea.util.FileUtils;
import com.idea.util.LiveCenter;
import com.idea.util.PLog;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

import io.realm.Realm;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p/>
 */
public class Worker extends IntentService {
    private static final String DOWNLOAD = "com.idea.messenger.action.download";

    private static final String MESSAGE_JSON = "com.idea.messenger.extra.MESSAGE";

    private static final String TAG = Worker.class.getName();
    public static final String PARRALLEL_DOWNLOAD = "parrallelDownload";
    public static final int MAX_PARRALLEL_DOWNLOAD = 10;

    public static void download(Context context, Message message) {
        Intent intent = new Intent(context, Worker.class);
        intent.setAction(DOWNLOAD);
        intent.putExtra(MESSAGE_JSON, Message.toJSON(message));
        context.startService(intent);
    }

    public Worker() {
        super("Worker");
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

    static int getCurrentActiveDownloads() {
        synchronized (downloading) {
            return downloading.size();
        }
    }

    private static Semaphore downloadLock = new Semaphore(5, true);

    private void download(final Message message) {
        synchronized (downloading) {
            if (downloading.size() > MAX_PARRALLEL_DOWNLOAD) {
                ErrorCenter.reportError(PARRALLEL_DOWNLOAD, getString(R.string.too_many_parralel_dowload), 1);
                return;
            }
            if (!downloading.add(message.getId())) {
                PLog.w(TAG, "already  downloading message with id %s", message.getId());
                ErrorCenter.reportError(PARRALLEL_DOWNLOAD, getString(R.string.already_downloading), 1);
                return;
            }
        }
        try {
            LiveCenter.acquireProgressTag(message.getId());
            service.execute(new DownloadRunnable(message));
        } catch (PairappException e) {
            throw new RuntimeException(e.getCause());
        }
    }


    private static final ExecutorService service = Executors.newCachedThreadPool();

    private static class DownloadRunnable implements Runnable {

        private final String messageId;
        private final String messageBody;
        private final String peer;
        private int type;

        public DownloadRunnable(Message message) {
            messageId = message.getId();
            messageBody = message.getMessageBody();
            peer = Message.isGroupMessage(message) ? message.getTo() : message.getFrom();
            type = message.getType();
        }

        @Override
        public void run() {
            try {
                downloadLock.acquire();
                doDownload();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                downloadLock.release();
            }
        }


        private void doDownload() {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            Realm realm = null;
            final File finalFile;
            String destination = Uri.parse(messageBody).getPath();//.substring(messageBody.lastIndexOf('/'));

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
                boolean done = false;

                @Override
                public void onProgress(long expected, long processed) {
                    if (done) {
                        return;
                    }
                    int progress = (int) ((100 * processed) / expected);
                    LiveCenter.updateProgress(messageId, progress);

                    if (!peer.equals(Config.getCurrentActivePeer())) {
                        Intent intent = new Intent(Config.getApplicationContext(), ChatActivity.class);
                        intent.putExtra(ChatActivity.EXTRA_PEER_ID, peer);
                        Notification notification = new NotificationCompat.Builder(Config.getApplicationContext())
                                .setContentTitle(Config.getApplicationContext().getString(R.string.downloading))
                                .setProgress(100, progress, progress <= 0)
                                .setContentIntent(PendingIntent.getActivity(Config.getApplicationContext(), 1003, intent, PendingIntent.FLAG_UPDATE_CURRENT))
                                .setSubText(progress > 0 ? Config.getApplicationContext().getString(R.string.downloaded) + FileUtils.sizeInLowestPrecision(processed) + "/" + FileUtils.sizeInLowestPrecision(expected) : Config.getApplicationContext().getString(R.string.loading))
                                .setSmallIcon(R.drawable.ic_stat_icon).build();
                        NotificationManagerCompat manager = NotificationManagerCompat.from(Config.getApplicationContext());// getSystemService(NOTIFICATION_SERVICE));
                        manager.notify(messageId, PairAppClient.notId, notification);
                    }
                    if (progress == 100 && !done) {
                        done = true;
                    }
                }
            };
            try {
                FileUtils.save(finalFile, messageBody, listener);
                realm = Message.REALM(Config.getApplicationContext());
                Message toBeUpdated = realm.where(Message.class).equalTo(Message.FIELD_ID, messageId).findFirst();
                if (toBeUpdated != null) {
                    realm.beginTransaction();
                    toBeUpdated.setMessageBody(finalFile.getAbsolutePath());
                    realm.commitTransaction();
                } else {
                    PLog.d(TAG, "message not available for update, was it deleted?");
                }
                onComplete(messageId, null);
            } catch (IOException e) {
                PLog.d(TAG, e.getMessage(), e.getCause());
                Exception error = new Exception(Config.getApplicationContext().getString(R.string.download_failed));
                onComplete(messageId, error);
            } finally {
                if (realm != null) {
                    realm.close();
                }
            }
        }

        private void onComplete(String messageId, final Exception error) {
            LiveCenter.releaseProgressTag(messageId);
            synchronized (downloading) {
                downloading.remove(messageId);
            }

            if (error != null) {
                ErrorCenter.reportError("downloadFailed", error.getMessage(), 1);
            }
        }
    }
}
