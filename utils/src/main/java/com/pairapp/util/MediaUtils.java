package com.pairapp.util;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.annotation.RawRes;
import android.support.v4.BuildConfig;
import android.support.v4.app.FragmentActivity;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


/**
 * @author Null-Pointer on 8/24/2015.
 */
public class MediaUtils {
    public static final String TAG = MediaUtils.class.getSimpleName();
    private static final List<String> pictures = new ArrayList<>(5), videos = new ArrayList<>(7);

    static {
        pictures.add("png");
        pictures.add("jpg");
        pictures.add("jpeg");
        pictures.add("gif");
        videos.add("mp4");
        videos.add("avi");
        videos.add("flv");
        videos.add("mkv");
        videos.add("mpeg");
    }

    public static void takePhoto(Activity context, Uri outPutUri, int requestCode) {
        try {
            Intent attachIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            attachIntent.putExtra(MediaStore.EXTRA_OUTPUT, outPutUri);
            context.startActivityForResult(attachIntent, requestCode);
        } catch (Exception e) {
            if (BuildConfig.DEBUG) {
                PLog.e(TAG, e.getMessage(), e.getCause());
                throw new RuntimeException(e.getCause());
            }
            PLog.e(TAG, e.getMessage());
        }
    }

    public static void takePhoto(android.support.v4.app.Fragment context, Uri outPutUri, int requestCode) {
        Intent attachIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        attachIntent.putExtra(MediaStore.EXTRA_OUTPUT, outPutUri);
        context.startActivityForResult(attachIntent, requestCode);
    }

    public static void recordVideo(Activity activity, Uri outputUri, int requestCode) {
        Intent attachIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        attachIntent.putExtra(MediaStore.EXTRA_OUTPUT, outputUri);
        attachIntent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1);
        attachIntent.putExtra(MediaStore.EXTRA_SIZE_LIMIT, 16 * FileUtils.ONE_MB);
        activity.startActivityForResult(attachIntent, requestCode);
    }

    public static void recordVideo(android.support.v4.app.Fragment fragment, Uri outputUri, int requestCode) {
        Intent attachIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        attachIntent.putExtra(MediaStore.EXTRA_OUTPUT, outputUri);
        attachIntent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1);
        attachIntent.putExtra(MediaStore.EXTRA_SIZE_LIMIT, 16 * FileUtils.ONE_MB);
        fragment.startActivityForResult(attachIntent, requestCode);
    }

    public static boolean isImage(String actualPath) {
        String extension = FileUtils.getExtension(actualPath);
        return pictures.contains(extension);
    }

    public static boolean isVideo(String actualPath) {
        String extension = FileUtils.getExtension(actualPath);
        return videos.contains(extension);
    }

    public static void playTone(Context context, Uri uri) {
        Ringtone ringtone = RingtoneManager.getRingtone(context, uri);
        if (ringtone != null) {
            ringtone.play();
        } else {
            PLog.d(TAG, "unable to play ringtone");
        }
    }

    public static void recordAudio(FragmentActivity currentActivity, int recordAudioRequest) {
        Intent intent = new Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION);
        try {
            currentActivity.startActivityForResult(intent, recordAudioRequest);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(currentActivity, R.string.no_app_audio_record, Toast.LENGTH_SHORT).show();
        }
    }


    /**
     * plays the specified raw resource
     *
     * @param context context to for accessing {@link android.content.res.Resources}
     * @param res     the resource to be played
     * @throws IOException
     * @throws IllegalStateException if called on the android main thread
     * @see #playSound(Context, MediaPlayer, int)
     */
    public static void playSound(Context context, @RawRes int res) throws IOException {
        ThreadUtils.ensureNotMain();
        final MediaPlayer player = new MediaPlayer();
        player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mediaPlayer) {
                player.release();
            }
        });
        playSound(context, player, res);
    }

    /**
     * plays the specified raw resource using this player
     *
     * @param context context for accessing {@link android.content.res.Resources}
     * @param player  player to be used for playing the raw resource,its your responsibility to make sure the player is in a valid state.
     *                it is also your duty to release it when you are done
     * @param res     the resource to be played
     * @throws IOException
     * @throws IllegalStateException    if called on the android main thread
     * @throws IllegalArgumentException if  the {@link MediaPlayer} is null or in an invalid state
     * @see #playSound(Context, int)
     */
    public static void playSound(Context context, final MediaPlayer player, @RawRes int res) throws IOException {
        ThreadUtils.ensureNotMain();
        if (player == null) {
            throw new IllegalArgumentException("player == null");
        }
        if (player.isPlaying()) {
            throw new IllegalArgumentException("player is invalid");
        }
        AssetFileDescriptor fd = context.getResources().openRawResourceFd(res);
        player.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
        player.prepare();
        fd.close();
        player.setLooping(false);
        player.setVolume(1f,1f);
        player.start();
    }
}
