package com.idea.net;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Base64;

import com.google.gson.JsonObject;
import com.idea.net.file_service.BuildConfig;
import com.idea.util.Config;
import com.idea.util.FileUtils;
import com.idea.util.PLog;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import retrofit.RequestInterceptor;
import retrofit.RestAdapter;
import retrofit.RetrofitError;
import retrofit.android.AndroidLog;
import retrofit.mime.TypedFile;

/**
 * @author Null-Pointer on 10/6/2015.
 */
abstract class SmartFileClient implements FileApi {

    private static final String TAG = SmartFileClient.class.getSimpleName();
    private final String linksPrefsKey;
    private static final Executor WORKER = Executors.newCachedThreadPool();
    private final String authorization;
    private static final String ENDPOINT = //Config.getMessageApiEndpoint();//STOPSHIP
            "https://app.smartfile.com/api/2";
    private final SmartFileService api;
    private final String dir;
    private final MetaApi metaApi;

    SmartFileClient(String key, String password, String dir) {
        if (TextUtils.isEmpty(key) || TextUtils.isEmpty(password) || TextUtils.isEmpty(dir)) {
            throw new IllegalArgumentException("either key or password or dir is invalid");
        }
        // dir = "dummy"; //STOPSHIP
        this.dir = dir;
        authorization = "Basic " + Base64.encodeToString((key + ":" + password).getBytes(), Base64.NO_WRAP);
        linksPrefsKey = TAG + this.dir;
        RequestInterceptor reqInterceptor = new RequestInterceptor() {
            @Override
            public void intercept(RequestFacade requestFacade) {
                requestFacade.addHeader("Authorization", authorization);
            }
        };
        RestAdapter.Log logger = new RestAdapter.Log() {
            @Override
            public void log(String s) {
                try {
                    PLog.v(TAG, s + "");
                } catch (Exception e) { //testing environment
                    System.out.println(TAG + " : " + s);
                }
            }
        };
        api = new RestAdapter.Builder().setEndpoint(ENDPOINT)
                .setLog(new AndroidLog(TAG))
                .setLogLevel(RestAdapter.LogLevel.FULL)
                .setExecutors(WORKER, WORKER)
                .setRequestInterceptor(reqInterceptor)
                .build().create(SmartFileService.class);

        metaApi = new RestAdapter.Builder().setEndpoint(Config.getFilesMetaDataApiUrl())
                .setLog(new AndroidLog(TAG))
                .setLogLevel(RestAdapter.LogLevel.FULL)
                .setRequestInterceptor(new RequestInterceptor() {
                    @Override
                    public void intercept(RequestFacade requestFacade) {
                        requestFacade.addHeader("Authorization", "kiibodaS3crite");
                    }
                })
                .build().create(MetaApi.class);
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
            // create link
            String link = getCachedLink();
            String url;
            if (TextUtils.isEmpty(link)) {
                JsonObject object = api.getLink(true, true,0,this.dir);
                PLog.d(TAG,object.toString());
                link = object.get("href").getAsString();
                cacheLink(link);
            }
            url = link + countingTypedFile.fileName();
            callback.done(null, url);
        } catch (RetrofitError err) {
            if (err.getKind().equals(RetrofitError.Kind.HTTP)) {
                int code = err.getResponse().getStatus();
                if (code == 409) { //dir does not exist
                    try {
                        api.createDir(dir, "dummyField");
                        saveFileToBackend(file, callback, listener);
                    } catch (RetrofitError err2) {
                        callback.done(new FileClientException(err2.getCause(), err.getResponse().getStatus()), null);
                    }
                    return;
                }
            }
            callback.done(new FileClientException(err.getCause(), -1), null);
        }
    }

    private void cacheLink(String link) {
        Context context = Config.getApplicationContext();
        SharedPreferences preferences = context.getSharedPreferences(linksPrefsKey, Context.MODE_PRIVATE);
        preferences.edit().putString(getKey(), link).apply();
    }

    private String getKey() {
        return FileUtils.hash((this.authorization+":"+ this.dir+":"+TAG).getBytes());
    }

    private String getCachedLink() {
        Context context = Config.getApplicationContext();
        SharedPreferences preferences = context.getSharedPreferences(linksPrefsKey, Context.MODE_PRIVATE);
        return preferences.getString(getKey(), null);
    }

    protected final String getAuthorization() {
        return authorization;
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

}
