package com.pair.util;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;
import android.util.Log;

import com.pair.pairapp.BuildConfig;
import com.pair.pairapp.R;

/**
 * @author Null-Pointer on 8/24/2015.
 */
public class MediaUtils {
    public static final String TAG = MediaUtils.class.getSimpleName();

    public static void takePhoto(Activity context, Uri outPutUri, int requestCode) {
        try {
            Intent attachIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            attachIntent.putExtra(MediaStore.EXTRA_OUTPUT, outPutUri);
            context.startActivityForResult(attachIntent, requestCode);
        } catch (Exception e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, e.getMessage(), e.getCause());
                throw new RuntimeException(e.getCause());
            }
            Log.e(TAG, e.getMessage());
            UiHelpers.showToast(R.string.error_occurred);
        }
    }

    public static void takePhoto(Fragment context, Uri outPutUri, int requestCode) {
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

    public static void recordVideo(Fragment fragment, Uri outputUri, int requestCode) {
        Intent attachIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        attachIntent.putExtra(MediaStore.EXTRA_OUTPUT, outputUri);
        attachIntent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1);
        attachIntent.putExtra(MediaStore.EXTRA_DURATION_LIMIT, 60 * 10);
        fragment.startActivityForResult(attachIntent, requestCode);
    }

}
