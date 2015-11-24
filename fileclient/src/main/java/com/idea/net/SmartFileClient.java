package com.idea.net;

import android.text.TextUtils;
import android.util.Base64;

import com.google.gson.JsonObject;
import com.idea.net.file_service.BuildConfig;
import com.idea.util.Config;
import com.idea.util.ConnectionUtils;
import com.idea.util.FileUtils;
import com.idea.util.PLog;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.util.UUID;

import retrofit.RequestInterceptor;
import retrofit.RestAdapter;
import retrofit.RetrofitError;
import retrofit.mime.TypedFile;

/**
 * @author Null-Pointer on 10/6/2015.
 */
abstract class SmartFileClient implements FileApi {

    private static final String TAG = SmartFileClient.class.getSimpleName();
    private final String authorization;
    private static final String ENDPOINT = //Config.getMessageApiEndpoint();//STOPSHIP
            "https://app.smartfile.com";
    private final SmartFileService api, linkApi;
    private final String dir;

    SmartFileClient(String key, String password, String dir) {
        if (TextUtils.isEmpty(key) || TextUtils.isEmpty(password) || TextUtils.isEmpty(dir)) {
            throw new IllegalArgumentException("either key or password or dir is invalid");
        }
        this.dir = dir;
        authorization = "Basic " + Base64.encodeToString((key + ":" + password).getBytes(), Base64.NO_WRAP);
        RequestInterceptor reqInterceptor = new RequestInterceptor() {
            @Override
            public void intercept(RequestFacade requestFacade) {
                requestFacade.addHeader("Authorization", authorization);
            }
        };

        RestAdapter.Log log = new RestAdapter.Log() {

            @Override
            public void log(String s) {
                PLog.d(TAG, s);
            }
        };
        api = new RestAdapter.Builder().setEndpoint(ENDPOINT)
                .setLog(log)
                .setLogLevel(RestAdapter.LogLevel.FULL)
                .setRequestInterceptor(reqInterceptor)
                .build().create(SmartFileService.class);
        linkApi = new RestAdapter.Builder().setEndpoint(Config.linksEndPoint())
                .setLog(log)
                .setLogLevel(RestAdapter.LogLevel.FULL)
                .setRequestInterceptor(reqInterceptor)
                .build().create(SmartFileService.class);
    }


    @Override
    public void saveFileToBackend(File file, FileApi.FileSaveCallback callback, FileApi.ProgressListener listener) {
        if (callback == null) {
            throw new IllegalArgumentException("callback is required");
        }

        if (file == null || !file.exists() || file.isDirectory()) {
            if (BuildConfig.DEBUG) {
                throw new IllegalArgumentException("invalid file");
            }
            callback.done(new FileClientException("invalid file", -1), null);
            return;
        }

        String mimeType = FileUtils.getMimeType(file.getAbsolutePath());
        if (TextUtils.isEmpty(mimeType)) {
            mimeType = "application/octet-stream";
        }

        try {
            final TypedFile countingTypedFile = new CountingTypedFile(mimeType, file, listener);
            api.saveFile(this.dir, countingTypedFile);
            try {
//                body.addProperty("read", true);
//                body.addProperty("list", false);
//                body.addProperty("cache", 31536000);
                JsonObject object = linkApi.getLink(this.dir);
                String link = object.get("href").getAsString();
                link = link.trim() + (link.endsWith("/") ? "" : "/") + countingTypedFile.fileName();
                PLog.d(TAG, link);
                callback.done(null, link);
            } catch (RetrofitError err) {
                Throwable cause = err.getCause();
                if (cause instanceof SocketTimeoutException
                        || cause instanceof UnknownHostException) {
                    if (ConnectionUtils.isActuallyConnected()) {
                        //switch to a new heroku dyno.
                    }
                }
                // TODO: 11/5/2015 more error handling like deleting the file etc
                callback.done(new FileClientException(cause, err.getResponse().getStatus()), null);
            }
        } catch (RetrofitError err) {
            if (err.getKind().equals(RetrofitError.Kind.HTTP)) {
                int code = err.getResponse().getStatus();
                if (code == 409) { //dir does not exist
                    try {
                        api.createDir(dir, "dummyField");
                        saveFileToBackend(file, callback, listener);
                    } catch (RetrofitError err2) {
                        callback.done(new FileClientException(err2.getCause(), err2.getResponse().getStatus()), null);
                    }
                    return;
                }
            }
            callback.done(new FileClientException(err.getCause(), -1), null);
        }
    }

    @Override
    public void deleteFileFromBackend(String fileName, FileApi.FileDeleteCallback callback) {
        throw new UnsupportedOperationException();
    }

    static class CountingTypedFile extends TypedFile {

        private final File file;
        private final FileApi.ProgressListener listener;
        private long expected;

        public CountingTypedFile(String mimeType, File file, FileApi.ProgressListener listener) {
            super(mimeType, file);
            this.file = file;
            this.expected = this.file.length();
            this.listener = listener;
            if (expected > 0 && this.listener != null) {
                listener.onProgress(expected, 0);
            }
        }

        @Override
        public void writeTo(OutputStream out) throws IOException {
            int bufferSize = this.file.length() > FileUtils.ONE_MB ? 4096 : 512;
            byte[] buffer = new byte[bufferSize];
            FileInputStream in = new FileInputStream(this.file);

            int read;
            long processed = 0L;
            try {
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    if (listener != null) {
                        processed += read;
                        listener.onProgress(expected, processed);
                        //quick fix to force progress to be noticeable
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
                            PLog.d(TAG, "interrupted while uploading file!");
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                }
            } finally {
                in.close();
            }
        }
    }

    SecureRandom random = new SecureRandom();

    private String randomString(int length) {
        length = Math.min(120, length);
        StringBuilder uid = new StringBuilder();
        byte[] buffer = new byte[length];
        while (uid.length() < length) {
            random.nextBytes(buffer);
            uid.append(UUID.nameUUIDFromBytes(buffer));
        }

        if (uid.length() > length) {
            uid.substring(0, length);
        }
        PLog.i(TAG, "random string: " + uid.toString());
        return uid.toString();

    }
}
