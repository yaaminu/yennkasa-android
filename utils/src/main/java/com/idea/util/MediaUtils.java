package com.idea.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.v4.BuildConfig;

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
        videos.add("3gp");
        videos.add("3gpp");
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
        attachIntent.putExtra(MediaStore.EXTRA_SIZE_LIMIT, 7.5*FileUtils.ONE_MB);
        activity.startActivityForResult(attachIntent, requestCode);
    }

    public static void recordVideo(android.support.v4.app.Fragment fragment, Uri outputUri, int requestCode) {
        Intent attachIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        attachIntent.putExtra(MediaStore.EXTRA_OUTPUT, outputUri);
        attachIntent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1);
        attachIntent.putExtra(MediaStore.EXTRA_SIZE_LIMIT, 7.5*FileUtils.ONE_MB);
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
}
