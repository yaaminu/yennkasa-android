package com.idea.messenger;

import android.app.IntentService;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.widget.Toast;

import com.idea.Errors.ErrorCenter;
import com.idea.Errors.PairappException;
import com.idea.data.Message;
import com.idea.pairapp.R;
import com.idea.ui.ChatActivity;
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

    public Worker() {
        super("Worker");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (DOWNLOAD.equals(action)) {
                synchronized (downloading) {
                    if (downloading.size() > 8) {
                        if (Config.isAppOpen())
                            Toast.makeText(Worker.this, getString(R.string.max_download_reached), Toast.LENGTH_SHORT).show();
                        else
                            ErrorCenter.reportError(TAG + "duplicate", getString(R.string.max_download_reached), null);
                        return;
                    }
                }
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
                PLog.w(TAG, "already  downloading message with id %s", message.getId());
                Toast.makeText(this, R.string.busy, Toast.LENGTH_SHORT).show();
                return;
            }
        }
        try {
            LiveCenter.acquireProgressTag(message.getId());
        } catch (PairappException e) {
            throw new RuntimeException(e.getCause());
        }

        final String messageId = message.getId(),
                messageBody = message.getMessageBody(),
                peer = Message.isGroupMessage(message) ? message.getTo() : message.getFrom();
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
                            boolean done = false;

                            @Override
                            public void onProgress(long expected, long processed) {
                                if (done) {
                                    return;
                                }
                                int progress = (int) ((100 * processed) / expected);
                                LiveCenter.updateProgress(messageId, progress);

                                if (!peer.equals(Config.getCurrentActivePeer())) {
                                    Intent intent = new Intent(Worker.this, ChatActivity.class);
                                    intent.putExtra(ChatActivity.EXTRA_PEER_ID, peer);
                                    Notification notification = new NotificationCompat.Builder(Worker.this)
                                            .setContentTitle(getString(R.string.downloading))
                                            .setProgress(100, progress, progress <= 0)
                                            .setContentIntent(PendingIntent.getActivity(Worker.this, 1003, intent, PendingIntent.FLAG_UPDATE_CURRENT))
                                            .setSubText(progress > 0 ? getString(R.string.downloaded) + FileUtils.sizeInLowestPrecision(processed) + "/" + FileUtils.sizeInLowestPrecision(expected) : getString(R.string.loading))
                                            .setSmallIcon(R.drawable.ic_stat_icon).build();
                                    NotificationManagerCompat manager = NotificationManagerCompat.from(Worker.this);// getSystemService(NOTIFICATION_SERVICE));
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
                            realm.beginTransaction();
                            Message toBeUpdated = realm.where(Message.class).equalTo(Message.FIELD_ID, messageId).findFirst();
                            toBeUpdated.setMessageBody(finalFile.getAbsolutePath());
                            realm.commitTransaction();
                            onComplete(null);
                        } catch (IOException e) {
                            PLog.d(TAG, e.getMessage(), e.getCause());
                            Exception error = new Exception(Config.getApplicationContext().getString(R.string.download_failed));
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

                        Intent intent = new Intent();
                        if (error != null) {
                            NotificationManagerCompat manager = NotificationManagerCompat.from(Worker.this);// getSystemService(NOTIFICATION_SERVICE));
                            manager.cancel(messageId, PairAppClient.notId);
                            intent.setClass(Worker.this, ChatActivity.class);
                            intent.putExtra(ChatActivity.EXTRA_PEER_ID, peer);
                            ErrorCenter.reportError(messageId, error.getMessage(), intent);
                        }
                    }
                });
    }
}
