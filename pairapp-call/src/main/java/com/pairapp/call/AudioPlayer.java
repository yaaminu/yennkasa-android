package com.pairapp.call;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.FileInputStream;
import java.io.IOException;

/**
 * @author aminu on 7/17/2016.
 */
class AudioPlayer {

    static final String LOG_TAG = AudioPlayer.class.getSimpleName();

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
            mp.release();
        }
    };

    void playRingtone() {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        // Honour silent mode
        switch (audioManager.getRingerMode()) {
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
                    Log.e(LOG_TAG, "Could not setup media player for ringtone");
                    player = null;
                    return;
                }
                player.setLooping(true);
                player.start();
                break;
        }
    }

    void stopRingtone() {
        if (player != null) {
            player.stop(); //will be released in the OnCompletionListener
            player = null;
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
