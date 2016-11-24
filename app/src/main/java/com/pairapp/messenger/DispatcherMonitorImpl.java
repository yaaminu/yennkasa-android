package com.pairapp.messenger;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.util.Pair;

import com.pairapp.R;
import com.pairapp.data.Message;
import com.pairapp.data.UserManager;
import com.pairapp.ui.ChatActivity;
import com.pairapp.util.Config;
import com.pairapp.util.LiveCenter;
import com.pairapp.util.PLog;

import java.io.IOException;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import io.realm.Realm;

/**
 * @author aminu on 6/30/2016.
 */
class DispatcherMonitorImpl implements Dispatcher.DispatcherMonitor {
    public static final String TAG = "DispatcherMonitor";
    final Map<String, Pair<String, Integer>> progressMap = new ConcurrentHashMap<>();
    private final Context context;
    private final Map<String, Future<?>> dispatchingThread;
    private static final int not_id = 10987;
    private MediaPlayer mediaPlayer;
    private boolean playerInUse = false;
    private final Timer timer = new Timer(true);
    private TimerTaskIMpl currTask;


    public DispatcherMonitorImpl(Context context, Map<String, Future<?>> dispatchingThread) {
        this.context = context;
        this.dispatchingThread = dispatchingThread;
    }

    @Override
    public void onDispatchFailed(String messageId, String reason) {
        PLog.d(TAG, "message with id : %s dispatch failed with reason: " + reason, messageId);
        LiveCenter.releaseProgressTag(messageId);
        progressMap.remove(messageId);
        dispatchingThread.remove(messageId);
        cancelNotification(messageId);
    }

    @Override
    public void onDispatchSucceeded(String messageId) {
        PLog.d(TAG, "message with id : %s dispatched successfully", messageId);
        LiveCenter.releaseProgressTag(messageId);
        progressMap.remove(messageId);
        dispatchingThread.remove(messageId);
        cancelNotification(messageId);
        Realm realm = Message.REALM(context);
        Message message = realm.where(Message.class).equalTo(Message.FIELD_ID, messageId).findFirst();
        if (message != null
                && Config.getCurrentActivePeer().equals(message.getTo())
                && UserManager.getInstance().getBoolPref(UserManager.IN_APP_NOTIFICATIONS, true)) {
            playSound();
        }
        realm.close();
    }

    private void cancelNotification(String messageId) {
        NotificationManagerCompat manager = NotificationManagerCompat.from(context);// get
        manager.cancel(messageId, not_id);
    }

    @Override
    public void onProgress(String id, int progress, int max) {
        LiveCenter.updateProgress(id, progress);
        Pair<String, Integer> previousProgress = progressMap.get(id);
        if (previousProgress != null && previousProgress.first != null && previousProgress.second != null) {
            if (previousProgress.second >= progress) {
                PLog.d(TAG, "late progress report");
                return;
            }
        } else {
            Realm messageRealm = Message.REALM(context);
            Message message = messageRealm.where(Message.class).equalTo(Message.FIELD_ID, id).findFirst();
            if (message == null) {
                return;
            }
            previousProgress = new Pair<>(message.getTo(), progress);
            progressMap.put(id, previousProgress);
            messageRealm.close();
        }

        Intent intent = new Intent(context, ChatActivity.class);
        intent.putExtra(ChatActivity.EXTRA_PEER_ID, previousProgress.first);
        Notification notification = new NotificationCompat.Builder(context)
                .setContentTitle(context.getString(R.string.upload_progress))
                .setProgress(100, 1, true)
                .setContentIntent(PendingIntent.getActivity(context, 1003, intent, PendingIntent.FLAG_UPDATE_CURRENT))
                .setContentText(context.getString(R.string.uploading))
                .setSmallIcon(R.drawable.ic_stat_icon).build();
        NotificationManagerCompat manager = NotificationManagerCompat.from(context);// getSystemService(NOTIFICATION_SERVICE));
        manager.notify(id, not_id, notification);
    }

    @Override
    public void onAllDispatched() {

    }

    AtomicBoolean shouldPlaySound = new AtomicBoolean(true);

    private synchronized void playSound() {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        if (audioManager.getRingerMode() == AudioManager.RINGER_MODE_NORMAL && shouldPlaySound.getAndSet(false)) {
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    shouldPlaySound.set(true);
                }
            }, 1500);
            if (playerInUse) return;
            playerInUse = true;
            try {
                if (mediaPlayer == null) {
                    mediaPlayer = new MediaPlayer();
                }
                mediaPlayer.reset();
                mediaPlayer.setOnCompletionListener(onCompletionListener);
                AssetFileDescriptor fd = context.getResources().openRawResourceFd(R.raw.beep);
                mediaPlayer.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
                mediaPlayer.prepare();
                fd.close();
                mediaPlayer.setLooping(false);
                mediaPlayer.setVolume(1f, 1f);
                mediaPlayer.start();
            } catch (IOException e) {
                PLog.d(TAG, "failed to play sound");
            }
        } else {
            PLog.d(TAG, "not playing sound, played one not too lon ago");
        }
    }

    private final MediaPlayer.OnCompletionListener onCompletionListener = new MediaPlayer.OnCompletionListener() {
        @Override
        public void onCompletion(MediaPlayer mp) {
            synchronized (DispatcherMonitorImpl.this) {
                playerInUse = false;
                if (currTask != null) {
                    currTask.cancel();
                }
                currTask = new TimerTaskIMpl();
                timer.schedule(currTask, 30000);
            }
        }
    };

    private class TimerTaskIMpl extends TimerTask {
        public void run() {
            synchronized (DispatcherMonitorImpl.this) {
                if (!playerInUse && mediaPlayer != null) {
                    mediaPlayer.release();
                    mediaPlayer = null;
                }
            }
        }
    }

    ;
}
