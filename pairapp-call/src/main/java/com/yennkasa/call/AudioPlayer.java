package com.yennkasa.call;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.Vibrator;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.yennkasa.data.UserManager;
import com.yennkasa.util.Config;
import com.yennkasa.util.PLog;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

/**
 * @author aminu on 7/17/2016.
 */
class AudioPlayer {

    static final String LOG_TAG = AudioPlayer.class.getSimpleName();

    @Nullable
    Timer timer = null;
    public static final int VIBRATION_DURATION = 1000;

    @NonNull
    private final Context context;
    @Nullable
    private AudioTrack progressTone;
    @Nullable
    private MediaPlayer player;

    private final static int SAMPLE_RATE = 16000;

    AudioPlayer(Context context) {
        this.context = context.getApplicationContext();
    }

    private final MediaPlayer.OnCompletionListener onCompletionListener = new MediaPlayer.OnCompletionListener() {
        @Override
        public void onCompletion(MediaPlayer mp) {
            synchronized (AudioPlayer.this) {
                mp.release();
                player = null;
            }
        }
    };

    synchronized void playRingtone() {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        int ringerMode = audioManager.getRingerMode();
        vibrateIfAllowed(ringerMode);
        // Honour silent mode
        switch (ringerMode) {
            case AudioManager.RINGER_MODE_NORMAL:
                if (player != null) {
                    if (player.isPlaying()) {
                        player.stop();
                    }
                }
                player = new MediaPlayer();
                player.setOnCompletionListener(onCompletionListener);
                player.setAudioStreamType(AudioManager.STREAM_RING);

                try {
                    player.setDataSource(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE));
                    player.prepare();
                } catch (IOException e) {
                    Log.e(LOG_TAG, "Coulds not setup media player for ringtone");
                    Log.e(LOG_TAG, e.getMessage(), e);
                    player.release();
                    player = null;
                    return;
                }
                player.setLooping(true);
                player.start();
                break;
        }

    }

    public void vibrateIfAllowed(int ringerMode) {
        if (ringerMode == AudioManager.RINGER_MODE_VIBRATE || ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            if (UserManager.getInstance().getBoolPref(UserManager.VIBRATE, false)) {
                if (timer != null) {
                    timer.cancel();
                }
                timer = new Timer();
                timer.scheduleAtFixedRate(new TimerTask() {
                    @Override
                    public void run() {
                        doVibrate();
                    }
                }, 100, 1500);
            }
        }
    }

    static void doVibrate() {
        PLog.v(LOG_TAG, "vibrating....");
        Vibrator vibrator = (Vibrator) Config.getApplicationContext().getSystemService(Context.VIBRATOR_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            if (vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    AudioAttributes audioAttributes = new AudioAttributes.Builder().setFlags(AudioAttributes.USAGE_NOTIFICATION).build();
                    vibrator.vibrate(VIBRATION_DURATION, audioAttributes);
                } else {
                    vibrator.vibrate(VIBRATION_DURATION);
                }
            }
        } else {
            vibrator.vibrate(VIBRATION_DURATION);
        }
    }

    synchronized void stopRingtone() {
        if (player != null) {
            player.stop(); //will be released in the OnCompletionListener
            player = null;
        }
        if (timer != null) {
            timer.cancel();
        }
    }

    void playProgressTone() {
        stopProgressTone();
        try {
            progressTone = createProgressTone(context);
            progressTone.play();
        } catch (Exception e) {
            Log.e(LOG_TAG, "Could not play progress tone", e);
        }
    }

    void stopProgressTone() {
        if (progressTone != null) {
            progressTone.stop();
            progressTone.release();
            progressTone = null;
        }
    }

    private static AudioTrack createProgressTone(Context context) throws IOException {
        AssetFileDescriptor fd = context.getResources().openRawResourceFd(R.raw.progress_tone);
        int length = (int) fd.getLength();

        AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_VOICE_CALL, SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, length, AudioTrack.MODE_STATIC);

        byte[] data = new byte[length];
        readFileToBytes(fd, data);

        audioTrack.write(data, 0, data.length);
        audioTrack.setLoopPoints(0, data.length / 2, 30);

        return audioTrack;
    }

    private static void readFileToBytes(AssetFileDescriptor fd, byte[] data) throws IOException {
        FileInputStream inputStream = fd.createInputStream();

        int bytesRead = 0;
        while (bytesRead < data.length) {
            int res = inputStream.read(data, bytesRead, (data.length - bytesRead));
            if (res == -1) {
                break;
            }
            bytesRead += res;
        }
    }
}
