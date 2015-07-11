package com.pair.util;

import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;
import android.webkit.MimeTypeMap;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import retrofit.mime.TypedFile;

/**
 * @author Null-Pointer on 6/16/2015.
 */
public class FileHelper {
    public static final String TAG = FileHelper.class.getSimpleName();
    public static final int MEDIA_TYPE_IMAGE = 0x0;
    public static final int MEDIA_TYPE_VIDEO = 0x1;

    public static Uri getOutputUri(int mediaType) throws Exception {
        if (mediaType != MEDIA_TYPE_IMAGE && mediaType != MEDIA_TYPE_VIDEO) {
            throw new IllegalArgumentException("you can only pass either: " + MEDIA_TYPE_IMAGE + " or " + MEDIA_TYPE_VIDEO);
        }
        StringBuilder pathBuilder = new StringBuilder((mediaType == MEDIA_TYPE_IMAGE) ? "IMG_" : "VID_");
        File file = (mediaType == MEDIA_TYPE_IMAGE) ? Config.APP_IMG_MEDIA_BASE_DIR : Config.APP_VID_MEDIA_BASE_DIR;
        Date now = new Date();
        pathBuilder.append(new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(now));
        switch (mediaType) {
            case MEDIA_TYPE_IMAGE:
                pathBuilder.append(".jpeg");
                return doCreateOutputFile(pathBuilder.toString(), file);
            case MEDIA_TYPE_VIDEO:
                pathBuilder.append(".mp4");
                return doCreateOutputFile(pathBuilder.toString(), file);
            default:
                return null;
        }
    }

    private static Uri doCreateOutputFile(String path, File parentDir) throws Exception {
        if (parentDir != null) {
            if (!parentDir.isDirectory()) {
                if (!parentDir.mkdirs()) {
                    throw new Exception("Could not create directory, check you SD card");
                }
            }
            return Uri.fromFile(new File(parentDir, path));
        }
        return null;
    }

    public static String resolveContentUriToFilePath(Uri uri) {
        String[] projections = {
                MediaStore.Files.FileColumns.DATA
        };
        Cursor cursor = Config.getApplication().getContentResolver().query(uri, projections, null, null, null);
        if (cursor == null || cursor.getCount() < 1) {
            throw new IllegalArgumentException("uri passed does not point to any resource");
        }
        cursor.moveToFirst();
        String path = cursor.getString(cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA));
        Log.i(TAG, path);
        cursor.close();
        return path;
    }

    public static String getMimeType(String path) {
        String extension = getExtension(path);
        if ("".equals(extension)) return extension;
        //re use extension.
        extension = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        Log.i(TAG, "mime type of " + path + " is: " + extension);
        return extension;
    }

    public static String getExtension(String path) {
        if (path == null) return "";
        int extStart = path.lastIndexOf(".");
        if (extStart == -1 || path.endsWith(".")) return "";
        return path.substring(extStart + 1);
    }

    public static void save(File profilePicture, InputStream in) throws IOException {
        byte[] imageBytes = IOUtils.toByteArray(in);
        if (profilePicture.exists()) {
            return;
        }
        FileUtils.writeByteArrayToFile(profilePicture, imageBytes);
    }

    public static void copyTo(String oldPath, String newPath) throws IOException {
        FileUtils.copyFile(new File(oldPath), new File(newPath));
    }

    public class CountingTypedFile extends TypedFile {
        private final ProgressListener listener;

        public CountingTypedFile(String mime, File file, ProgressListener listener) {
            super(mime, file);
            if (listener != null) {
                this.listener = listener;
            } else {
                this.listener = DUMMY_PROGRESS_LISTENER;
            }
        }

        /**
         * @param out output stream to write to
         * @throws IOException <P> copied from
         *                     {@link retrofit.mime.TypedFile#writeTo(OutputStream)} </p>
         */
        @Override
        public void writeTo(OutputStream out) throws IOException {
            byte[] buffer = new byte[4096];
            FileInputStream in = new FileInputStream(file());

            int read;
            long length = length();
            long processed = 0L;
            boolean callOnComplete = false;
            try {
                callOnComplete = listener.onStart(length);
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    processed += read;
                    listener.onProgress(length, processed);
                }
            } finally {
                if (callOnComplete) {
                    listener.onComplete();
                }
                in.close();
            }
        }

        private ProgressListener DUMMY_PROGRESS_LISTENER = new ProgressListener() {
            @Override
            public boolean onStart(long expected) {
                return false;
            }

            @Override
            public void onProgress(long expected, long received) {
            }

            @Override
            public void onComplete() {

            }
        };
    }

    public interface ProgressListener {
        boolean onStart(long expected);

        void onProgress(long expected, long received);

        void onComplete();
    }
}
