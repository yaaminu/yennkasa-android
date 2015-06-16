package com.pair.util;

import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * @author Null-Pointer on 6/16/2015.
 */
public class FileHelper {
    public static final String TAG = FileHelper.class.getSimpleName();
    public static final int MEDIA_TYPE_IMAGE = 0x0;
    public static final int MEDIA_TYPE_VIDEO = 0x1;

    public static Uri getOutputUri(int mediaType) throws Exception {
        switch (mediaType) {
            case MEDIA_TYPE_IMAGE:
                Date now = new Date();
                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(now);
                File file = Config.APP_IMG_MEDIA_BASE_DIR;
                if (file != null) {
                    if (!file.isDirectory()) {
                        if (!file.mkdirs()) {
                            throw new Exception("Could not create File, check you SD card");
                        }
                    }
                    return Uri.fromFile(new File(file, timestamp));
                }
                return null;
            case MEDIA_TYPE_VIDEO:
                throw new UnsupportedOperationException("not yet supported");
            default:
                return null;
        }
    }

    public static String resolveUriToFile(Uri uri) {
        String[] projections = {
                MediaStore.Files.FileColumns.DATA
        };
        Cursor cursor = Config.getApplication().getContentResolver().query(uri, projections, null, null, null);
        cursor.moveToFirst();
        String path = cursor.getString(cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA));
        Log.i(TAG, path);
        return path;
    }
}
