package com.pair.util;

import android.app.Activity;
import android.content.Intent;
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
    private static final List<String> pictures = new ArrayList<>(5), videos = new ArrayList<>(5);

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
        attachIntent.putExtra(MediaStore.EXTRA_DURATION_LIMIT, 60 * 10);
        activity.startActivityForResult(attachIntent, requestCode);
    }

    public static void recordVideo(android.support.v4.app.Fragment fragment, Uri outputUri, int requestCode) {
        Intent attachIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        attachIntent.putExtra(MediaStore.EXTRA_OUTPUT, outputUri);
        attachIntent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1);
        attachIntent.putExtra(MediaStore.EXTRA_DURATION_LIMIT, 60 * 10);
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
}
