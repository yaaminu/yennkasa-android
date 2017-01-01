package com.yennkasa.messenger;

import android.app.IntentService;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.text.TextUtils;
import android.util.Base64;

import com.yennkasa.BuildConfig;
import com.yennkasa.Errors.ErrorCenter;
import com.yennkasa.Errors.YennkasaException;
import com.yennkasa.R;
import com.yennkasa.data.Message;
import com.yennkasa.data.User;
import com.yennkasa.security.MessageEncryptor;
import com.yennkasa.ui.ChatActivity;
import com.yennkasa.util.Config;
import com.yennkasa.util.FileUtils;
import com.yennkasa.util.LiveCenter;
import com.yennkasa.util.PLog;
import com.yennkasa.util.SimpleDateUtil;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

import io.realm.Realm;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p>
 */
public class Worker extends IntentService {
    private static final String DOWNLOAD = "com.idea.messenger.action.download";

    private static final String MESSAGE_JSON = "com.idea.messenger.extra.MESSAGE";

    private static final String TAG = Worker.class.getName();
    private static final String PARRALLEL_DOWNLOAD = "parrallelDownload";
    static final int MAX_PARRALLEL_DOWNLOAD = 10;
    private static final String CANCEL = "com.pair.messenger.worker.cancel";
    private static final String MESSAGE_ID = "com.idea.messenger.msgid";

    static void download(Context context, Message message) {
        download(context, message, false);
    }

    static void download(Context context, Message message, boolean fromBackground) {
        Intent intent = new Intent(context, Worker.class);
        intent.setAction(DOWNLOAD);
        intent.putExtra(MESSAGE_JSON, Message.toJSON(message));
        intent.putExtra(FROM_BACKGROUND, fromBackground);
        context.startService(intent);
    }

    public Worker() {
        super("Worker");
    }

    private static final String FROM_BACKGROUND = Worker.class.getName() + "FROM_BACKGROUND";

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (DOWNLOAD.equals(action)) {
                final String messageJson = intent.getStringExtra(MESSAGE_JSON);
                Message message = Message.fromJSON(messageJson);
                boolean fromBackground = intent.getBooleanExtra(FROM_BACKGROUND, false);
                download(message, fromBackground);
            } else if (CANCEL.equals(action)) {
                String messageId = intent.getStringExtra(
                        MESSAGE_ID);
                if (messageId == null) {
                    throw new IllegalArgumentException("no message id");
                }
                Future f = runningDownloads.get(messageId);
                if (f != null) {
                    f.cancel(true);
                }
                //we don't rely on whether we found the task or not we clear all notifications
                LiveCenter.releaseProgressTag(messageId);
            }
        }
    }

    private static final Set<String> downloading = new HashSet<>();

    static int getCurrentActiveDownloads() {
        synchronized (downloading) {
            return downloading.size();
        }
    }

    private static final Semaphore downloadLock = new Semaphore(5, true);

    private void download(final Message message, final boolean isAutoDownload) {
        synchronized (downloading) {
            if (downloading.size() > MAX_PARRALLEL_DOWNLOAD && !isAutoDownload) {
                ErrorCenter.reportError(PARRALLEL_DOWNLOAD, getString(R.string.too_many_parallel_download), 1);
                return;
            }
            if (!downloading.add(message.getId()) && !isAutoDownload) {
                PLog.w(TAG, "already  downloading message with id %s", message.getId());
                ErrorCenter.reportError(PARRALLEL_DOWNLOAD, getString(R.string.already_downloading), 1);
                return;
            }
        }
        try {
            LiveCenter.acquireProgressTag(message.getId());
            runningDownloads.put(message.getId(), service.submit(new DownloadRunnable(message, isAutoDownload)));
        } catch (YennkasaException e) {
            throw new RuntimeException(e.getCause());
        }
    }

    private static final Map<String, Future> runningDownloads = new ConcurrentHashMap<>();
    private static final ExecutorService service = Executors.newCachedThreadPool();

    static void cancelDownload(Context applicationContext, Message message) {
        Intent intent = new Intent(Config.getApplicationContext(), Worker.class);
        intent.setAction(CANCEL);
        intent.putExtra(MESSAGE_ID, message.getId());
        applicationContext.startService(intent);
    }

    private static class DownloadRunnable implements Runnable {

        private final String messageId;
        private final String messageBody;
        private final String peer;
        private int type;
        private boolean fromBackground;

        public DownloadRunnable(Message message, boolean fromBackground) {
            Realm userRealm = User.Realm(Config.getApplicationContext());
            try {
                messageId = message.getId();
                messageBody = message.getMessageBody();
                peer = Message.isGroupMessage(userRealm, message) ? message.getTo() : message.getFrom();
                type = message.getType();
                this.fromBackground = fromBackground;
            } finally {
                userRealm.close();
            }
        }

        @Override
        public void run() {
            try {
                downloadLock.acquire();
                try {
                    doDownload();
                } finally {
                    synchronized (downloading) {
                        downloading.remove(messageId);
                    }
                    LiveCenter.releaseProgressTag(messageId);
                    runningDownloads.remove(messageId);
                    downloadLock.release();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }


        private void doDownload() {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            final File finalFile;
            String destination = Uri.parse(messageBody).getLastPathSegment();//.substring(messageBody.lastIndexOf('/'));
            String extension = FileUtils.getExtension(destination);
            destination = FileUtils.sha1(destination);
            if (!TextUtils.isEmpty(extension)) {
                destination = destination + "." + extension;
            }
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
                public void onProgress(long expected, long processed) throws IOException {
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
                                .setSmallIcon(R.drawable.ic_file_download_white_36dp).build();
                        NotificationManagerCompat manager = NotificationManagerCompat.from(Config.getApplicationContext());// getSystemService(NOTIFICATION_SERVICE));
                        manager.notify(messageId, YennkasaClient.notId, notification);
                    }
                    if (progress == 100 && !done) {
                        done = true;
                    }
                }
            };
            Realm realm = Message.REALM(Config.getApplicationContext());
            File tmp = new File(Config.getTempDir(), finalFile.getName() + SimpleDateUtil.timeStampNow());
            try {
                FileUtils.save(tmp, messageBody, listener);
                String encryptedKey = Uri.parse(messageBody).getQueryParameter("t");
                MessageEncryptor encryptor = new MessageEncryptor(PrivatePublicKeySourceImpl.getInstance(), BuildConfig.DEBUG);
                encryptor.decryptFile(Base64.decode(encryptedKey, Base64.URL_SAFE), tmp, finalFile);
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
                //noinspection ResultOfMethodCallIgnored
                finalFile.delete();
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
                PLog.d(TAG, e.getMessage(), e.getCause());
                Exception error = new Exception(Config.getApplicationContext().getString(R.string.download_failed), e.getCause());
                onComplete(messageId, error);
            } catch (MessageEncryptor.EncryptionException e) {
                if (BuildConfig.DEBUG) {
                    throw new RuntimeException(e);
                }
                PLog.d(TAG, e.getMessage(), e.getCause());
                //noinspection ResultOfMethodCallIgnored
                finalFile.delete();
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
                Exception error = new Exception(Config.getApplicationContext().getString(R.string.download_failed), e.getCause());
                onComplete(messageId, error);
            } finally {
                if (realm != null) {
                    realm.close();
                }
            }
        }

        private void onComplete(String messageId, final Exception error) {
            if (error != null && !Thread.currentThread().isInterrupted() && !fromBackground) {
                ErrorCenter.reportError("downloadFailed", error.getMessage(), 1);
            }
        }
    }
}
