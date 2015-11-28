package com.idea.util;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.SystemClock;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Base64;
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
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import static org.apache.commons.io.FileUtils.ONE_GB;
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
        return resolveContentUriToFilePath(uri, false);
    }

    public static String resolveContentUriToFilePath(String uri, boolean loadInfNotFoundLocally) {
        if ((TextUtils.isEmpty(uri))) {
            return null;
        }
        return resolveContentUriToFilePath(Uri.parse(uri), loadInfNotFoundLocally);
    }

    public static String resolveContentUriToFilePath(Uri uri) {
        return resolveContentUriToFilePath(uri, false);
    }

    public static String resolveContentUriToFilePath(Uri uri, boolean loadInfNotFoundLocally) {
        return getPathInternal(Config.getApplicationContext(), uri, loadInfNotFoundLocally);
    }

    public static String getMimeType(String path) {
        String extension = getExtension(path);
        if ("".equals(extension)) return extension;
        //re use extension.
        extension = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        PLog.i(TAG, "mime type of " + path + " is: " + extension);
        return extension;
    }

    public static String getExtension(String path) {
        return getExtension(path, null);
    }

    public static String getExtension(String path, String fallback) {
        String extension = FilenameUtils.getExtension(path);
        if (extension == null || extension.length() < 1) return fallback;
        return extension;
    }

    public static String hash(String source) {
        if (source == null) {
            throw new IllegalArgumentException();
        }
        return hash(source.getBytes());
    }

    public static String hash(byte[] source) {
        if (source == null) {
            throw new IllegalArgumentException("source == null");
        }
        // FIXME: 11/10/2015 use salt
        //noinspection unused
        byte[] salt = {
                1, 127, 0, 98, 83, 2, 89, 12, 12, 45, 90
        };
        try {
            MessageDigest digest = MessageDigest.getInstance("sha1");
            digest.reset();
            //re-use param source
            source = digest.digest(source);
            String hashString = bytesToString(source);
            PLog.d(TAG, "hash: " + hashString);
            return hashString;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e.getCause());
        }
    }

    public static String bytesToString(byte[] source) {
        String hashString = "";
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < source.length; i++) {
            hashString += Integer.toString((source[i] & 0xff) + 0x100, 16).substring(1);
        }
        return hashString;
    }

    public static void save(File file, String url) throws IOException {
        save(file, url, null);
    }


    public static void save(File fileToSave, InputStream in) throws IOException {
        if (fileToSave.exists() && !fileToSave.delete()) {
            throw new IOException(Config.getApplicationContext().getString(R.string.error_saving));
        }
        final byte[] buffer = new byte[4096];
        final File temp = new File(Config.getTempDir(), fileToSave.getName() + ".tmp");
        final BufferedOutputStream bOut = new BufferedOutputStream(new FileOutputStream(temp));
        final BufferedInputStream bIn = new BufferedInputStream(in);
        int read;
        try {
            while ((read = bIn.read(buffer, 0, 4096)) != -1) {
                bOut.write(buffer, 0, read);
            }
            //noinspection ResultOfMethodCallIgnored
            temp.renameTo(fileToSave);
        } finally {
            closeQuietly(bOut);
            closeQuietly(bIn);
        }
    }

    public static String hashFile(File source) {
        try {
            MessageDigest digest = MessageDigest.getInstance("sha1");
            digest.reset();
            byte[] hash;// digest.digest(org.apache.commons.io.FileUtils.readFileToByteArray(source));
            final byte[] buffer = new byte[4096];
            final BufferedInputStream bIn = new BufferedInputStream(new FileInputStream(source));
            int read;
            try {
                while ((read = bIn.read(buffer, 0, 4096)) != -1) {
                    digest.update(buffer, 0, read);
                }
            } finally {
                closeQuietly(bIn);
            }
            hash = digest.digest();
            String hashString = "";
            //noinspection ForLoopReplaceableByForEach
            for (int i = 0; i < hash.length; i++) {
                hashString += Integer.toString((hash[i] & 0xff) + 0x100, 16).substring(1);
            }
            PLog.d(TAG, "hash: " + hashString);
            return hashString;
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new RuntimeException(e.getCause());
        }
    }

    public static void save(File fileToSave, InputStream in, long bytesExpected, ProgressListener listener) throws IOException {
        if (fileToSave.exists()) {
            return;
        }
        if (bytesExpected < 0) {
            throw new IllegalArgumentException("negative stream length");
        }

        if (listener == null) {
            throw new IllegalArgumentException("listener == null");
        }

        byte[] buffer = new byte[1024];
        final File temp = new File(Config.getTempDir(), fileToSave.getName() + ".tmp");
        final BufferedOutputStream bOut = new BufferedOutputStream(new FileOutputStream(temp));
        final BufferedInputStream bIn = new BufferedInputStream(in);
        int read;
        long processed = 0;
        try {
            listener.onProgress(bytesExpected, 0);
            SystemClock.sleep(0);
            while ((read = bIn.read(buffer, 0, 1024)) != -1) {
                bOut.write(buffer, 0, read);
                processed += read;
                if (processed > bytesExpected) {
                    throw new IllegalStateException("inconsistent stream length");
                }
                listener.onProgress(bytesExpected, processed);
                SystemClock.sleep(100);
            }
            //noinspection ResultOfMethodCallIgnored
            temp.renameTo(fileToSave);
        } finally {
            closeQuietly(bOut);
            closeQuietly(bIn);
        }
    }

    public static void save(File file, String url, ProgressListener listener) throws IOException {
        ThreadUtils.ensureNotMain();

        URL location = new URL(url);
        HttpURLConnection connection = ((HttpURLConnection) location.openConnection());
        connection.setReadTimeout(10000);
        long contentLength = connection.getHeaderFieldInt("Content-Length", -1);

        final InputStream in = location.openStream();//the stream will be closed later see save(file,InputStream)
        if (contentLength == -1 || listener == null) {
            save(file, in);
        } else {
            save(file, in, contentLength, listener);
        }
    }

    public static void copyTo(String oldPath, String newPath) throws IOException {
        ThreadUtils.ensureNotMain();
        copyTo(new File(oldPath), new File(newPath));
    }

    public static void copyTo(File source, File destination) throws IOException {
        ThreadUtils.ensureNotMain();
        if (source == null || destination == null) {
            throw new NullPointerException("null!");
        }
        if (destination.isDirectory()) {
            throw new IllegalArgumentException("destination file is a directory");
        }
        if (destination.exists() && destination.getCanonicalPath().equals(source.getCanonicalPath())) { //same file?
            return;
        }
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

    public static String sizeInLowestPrecision(long fileSizeBytes) {
        final Context applicationContext = Config.getApplicationContext();

        if (fileSizeBytes <= 0) {
            throw new IllegalArgumentException(applicationContext.getString(R.string.file_size_invalid));
        }
        if (fileSizeBytes < ONE_KB) {
            return (int) fileSizeBytes + " " + applicationContext.getString(R.string.Byte);
        }
        if (fileSizeBytes < ONE_MB) {
            return ((int) (fileSizeBytes / ONE_KB)) + applicationContext.getString(R.string.kilobytes);
        }
        if (fileSizeBytes <= ONE_GB) {
            return ((int) (fileSizeBytes / ONE_MB)) + applicationContext.getString(R.string.megabytes);
        }
        return ((int) (fileSizeBytes / ONE_GB)) + applicationContext.getString(R.string.gigabytes);
    }

    public static String sizeInLowestPrecision(String filePath) throws IOException {
        File file = new File(filePath);
        final Context applicationContext = Config.getApplicationContext();
        if (!file.exists()) {
            throw new FileNotFoundException(applicationContext.getString(R.string.file_not_found));
        }
        return sizeInLowestPrecision(file.length());
    }

    public interface ProgressListener {
        void onProgress(long expected, long processed);
    }


    /**
     * the code below was  shamelessly copied from paulBurke: https://github.com/ipaulPro/aFileChooser
     * licensed under the apache licence
     */

    /**
     * Get a file path from a Uri. This will get the the path for Storage Access
     * Framework Documents, as well as the _data field for the MediaStore and
     * other file-based ContentProviders.
     *
     * @param context The context.
     * @param uri     The Uri to query.
     * @author paulburke
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    private static String getPathInternal(final Context context, final Uri uri, boolean loadIfNotFoundLocally) {
        final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;

        // DocumentProvider
        if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
            // ExternalStorageProvider
            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory() + "/" + split[1];
                }

                // TODO handle non-primary volumes
            }
            // DownloadsProvider
            else if (isDownloadsDocument(uri)) {

                final String id = DocumentsContract.getDocumentId(uri);
                final Uri contentUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));

                return getDataColumn(context, contentUri, null, null, loadIfNotFoundLocally);
            }
            // MediaProvider
            else if (isMediaDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }

                final String selection = "_id=?";
                final String[] selectionArgs = new String[]{
                        split[1]
                };

                return getDataColumn(context, contentUri, selection, selectionArgs, loadIfNotFoundLocally);
            }
        }
        // MediaStore (and general)
        else if ("content".equalsIgnoreCase(uri.getScheme())) {
            return getDataColumn(context, uri, null, null, loadIfNotFoundLocally);
        }
        // File
        else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }

        return null;
    }


    /**
     * Get the value of the data column for this Uri. This is useful for
     * MediaStore Uris, and other file-based ContentProviders.
     *
     * @param context       The context.
     * @param uri           The Uri to query.
     * @param selection     (Optional) Filter used in the query.
     * @param selectionArgs (Optional) Selection arguments used in the query.
     * @return The value of the _data column, which is typically a file path.
     */
    private static String getDataColumn(Context context, Uri uri, String selection,
                                        String[] selectionArgs, boolean loadIfNotFoundLocally) {

        if (loadIfNotFoundLocally) {
            ThreadUtils.ensureNotMain();
        }
        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = {
                column,
        };

        try {
            final ContentResolver contentResolver = context.getContentResolver();
            cursor = contentResolver.query(uri, projection, selection, selectionArgs,
                    null);
            if (cursor != null && cursor.moveToFirst()) {
                try {
                    final int column_index = cursor.getColumnIndexOrThrow(column);
                    String path = cursor.getString(column_index);

                    if (loadIfNotFoundLocally && path == null) {
                        throw new IllegalArgumentException("path == null");//just to send us to the catch clause
                    }
                    return path;
                } catch (IllegalArgumentException noSuchColumn) {
                    int columnIndex = cursor.getColumnIndex("mime_type");
                    if (columnIndex == -1) {
                        return null;
                    }
                    String mimeType = cursor.getString(columnIndex);
                    if (TextUtils.isEmpty(mimeType)) {
                        return null;
                    }
                    String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
                    if (TextUtils.isEmpty(ext)) {
                        return null;
                    }
                    byte[] bytes = new byte[15];
                    new SecureRandom().nextBytes(bytes);
                    String fileName = Base64.encodeToString(bytes, Base64.URL_SAFE) + "." + ext;
                    File dir;
                    if (MediaUtils.isImage(fileName)) {
                        dir = Config.getAppImgMediaBaseDir();
                    } else if (MediaUtils.isVideo(fileName)) {
                        dir = Config.getAppVidMediaBaseDir();
                    } else {
                        dir = Config.getAppBinFilesBaseDir();
                    }
                    File file = new File(dir, fileName);
                    try {
                        save(file, contentResolver.openInputStream(uri));
                        return file.getAbsolutePath();
                    } catch (IOException e) {
                        PLog.d(TAG, "error opening stream, reason: " + e.getMessage());
                        return null;
                    }
                }
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return null;
    }


    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    public static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    public static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    public static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }
}
