package com.pair.util;

import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.webkit.MimeTypeMap;

import org.apache.commons.io.FilenameUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import static org.apache.commons.io.FileUtils.ONE_KB;


/**
 * @author Null-Pointer on 6/16/2015.
 */
public class FileUtils {
    public static final String TAG = FileUtils.class.getSimpleName();
    public static final int MEDIA_TYPE_IMAGE = 0x0;
    public static final int MEDIA_TYPE_VIDEO = 0x1;
    public static final long ONE_MB = org.apache.commons.io.FileUtils.ONE_MB;

    public static Uri getOutputUri(int mediaType) throws IOException {
        if (mediaType != MEDIA_TYPE_IMAGE && mediaType != MEDIA_TYPE_VIDEO) {
            throw new IllegalArgumentException("you can only pass either: " + MEDIA_TYPE_IMAGE + " or " + MEDIA_TYPE_VIDEO);
        }
        StringBuilder pathBuilder = new StringBuilder((mediaType == MEDIA_TYPE_IMAGE) ? "IMG_" : "VID_");
        File file = (mediaType == MEDIA_TYPE_IMAGE) ? Config.getAppImgMediaBaseDir() : Config.getAppVidMediaBaseDir();
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


    private static Uri doCreateOutputFile(String path, File parentDir) throws IOException {
        if (parentDir != null) {
            if (!parentDir.isDirectory()) {
                if (!parentDir.mkdirs()) {
                    throw new IOException("Could not create directory");
                }
            }
            return Uri.fromFile(new File(parentDir, path));
        }
        return null;
    }

    public static String resolveContentUriToFilePath(String uri) {
        if ((TextUtils.isEmpty(uri))) {
            return null;
        }
        return resolveContentUriToFilePath(Uri.parse(uri));
    }
    public static String resolveContentUriToFilePath(Uri uri) {
        if (uri.getScheme() == null) {
            return null;
        }
        if (!uri.getScheme().equalsIgnoreCase("content")) {
            if (uri.getScheme().equalsIgnoreCase("file")) {
                return uri.getPath();
            }
            return null;
        }
        String[] projections = {
                MediaStore.Files.FileColumns.DATA
        };
        Cursor cursor = Config.getApplication().getContentResolver().query(uri, projections, null, null, null);
        if (cursor == null || cursor.getCount() < 1) {
            return null;
        }
        cursor.moveToFirst();
        String path = cursor.getString(cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA));
        // STOPSHIP: 9/24/2015 fix this problem that arises on android 5 and up
        CLog.i(TAG, path);
        cursor.close();
        return path;
    }

    public static String getMimeType(String path) {
        String extension = getExtension(path);
        if ("".equals(extension)) return extension;
        //re use extension.
        extension = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        CLog.i(TAG, "mime type of " + path + " is: " + extension);
        return extension;
    }

    public static String getExtension(String path) {
        String extension = FilenameUtils.getExtension(path);
        if (extension == null) return "";
        return extension;
    }

    public static void save(File file, String url) throws IOException {
        if (ThreadUtils.isMainThread()) {
            throw new IllegalStateException("main thread!");
        }
        URL location = new URL(url);
        save(file, location.openStream()); //the stream will be closed in the other overload
    }

    public static void save(File fileToSave, InputStream in) throws IOException {
        if (fileToSave.exists()) {
            throw new IOException("destination file already exists to");
        }
        byte[] buffer = new byte[1024];
        final File temp = new File(Config.getTempDir(), fileToSave.getName() + ".tmp");
        BufferedOutputStream bOut = new BufferedOutputStream(new FileOutputStream(temp));
        BufferedInputStream bIn = new BufferedInputStream(in);
        int read;
        try {
            while ((read = bIn.read(buffer, 0, 1024)) != -1) {
                bOut.write(buffer, 0, read);
            }
            //noinspection ResultOfMethodCallIgnored
            temp.renameTo(fileToSave);
        } finally {
            closeQuietly(bOut);
            closeQuietly(bIn);
        }
    }

    public static void copyTo(String oldPath, String newPath) throws IOException {
        if (ThreadUtils.isMainThread()) {
            throw new IllegalStateException("main thread!");
        }
        copyTo(new File(oldPath), new File(newPath));
    }

    public static void copyTo(File source, File destination) throws IOException {
        if (ThreadUtils.isMainThread()) {
            throw new IllegalStateException("main thread");
        }

        if (source == null || destination == null) {
            throw new NullPointerException("null!");
        }
        if (destination.exists() && destination.isDirectory()) {
            throw new IllegalArgumentException("destination file is a directory");
        }
        if (destination.exists() && !destination.delete()) {
            throw new IOException("destination file exist and could not be deleted");
        }
//        org.apache.commons.io.FileUtils.copyFile(source, destination);
        save(destination, new FileInputStream(source));
    }


    private static void closeQuietly(OutputStream out) {
        if (out != null) {
            //noinspection EmptyCatchBlock
            try {
                out.close();
            } catch (IOException e) {
            }
        }
    }

    private static void closeQuietly(InputStream in) {
        if (in != null) {
            //noinspection EmptyCatchBlock
            try {
                in.close();
            } catch (IOException e) {
            }
        }
    }

    public static String sizeInLowestPrecision(String filePath) throws IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new FileNotFoundException("file does not exist");
        }
        final long fileSizeBytes = file.length();
        if (fileSizeBytes == 0) {
            throw new IOException("Could not determine file size");
        }
        if (fileSizeBytes < ONE_KB) {
            return (int) fileSizeBytes + " " + Config.getApplicationContext().getString(R.string.Byte);
        }
        if (fileSizeBytes < ONE_MB) {
            return ((int) (fileSizeBytes / ONE_KB)) + Config.getApplicationContext().getString(R.string.kilobytes);
        }
        if (fileSizeBytes <= 8 * ONE_MB) {
            return ((int) (fileSizeBytes / ONE_MB)) + Config.getApplicationContext().getString(R.string.megabytes);
        }
        throw new IOException("file too large");
    }
}
