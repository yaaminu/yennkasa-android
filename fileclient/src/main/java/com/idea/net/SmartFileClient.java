package com.idea.net;

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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import retrofit.RequestInterceptor;
import retrofit.RestAdapter;
import retrofit.RetrofitError;
import retrofit.mime.TypedFile;

/**
 * @author Null-Pointer on 10/6/2015.
 */
abstract class SmartFileClient implements FileApi {

    private static final String TAG = SmartFileClient.class.getSimpleName();
    private static final Executor WORKER = Executors.newCachedThreadPool();
    public static final String CACHED_LINKS = "cachedLinks";
    private final String authorization;
    private static final String ENDPOINT = "https://app.smartfile.com/api/2";
    private final SmartFileService api;
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
                .setLog(logger)
                .setLogLevel(RestAdapter.LogLevel.FULL)
                .setExecutors(WORKER, WORKER)
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
            api.saveFile(dir, countingTypedFile);
            // create link
            String cachedLink = getCachedLink();
            String url;
            if (TextUtils.isEmpty(cachedLink)) {
                Calendar calendar = new GregorianCalendar();
                calendar.set(Calendar.YEAR, 2038);
                JsonObject object = api.getLink(true, true, calendar.getTime().getTime(), this.dir);
                final String link = object.getAsJsonArray("results").get(0).getAsJsonObject().get("href").getAsString();
                url = link + countingTypedFile.fileName();
                cacheLink(link);
            } else {
                url = cachedLink + countingTypedFile.fileName();
            }
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
        Config.getApplicationWidePrefs().edit().putString(SmartFileClient.class.getName() + this.dir, link).apply();

    }

    private String getCachedLink() {
        return Config.getApplicationWidePrefs().getString(SmartFileClient.class.getName()+this.dir, "");
    }

    @Override
    public void deleteFileFromBackend(String fileName, FileApi.FileDeleteCallback callback) {
        throw new UnsupportedOperationException();
    }

    static class CountingTypedFile extends TypedFile {

        private final File file;
        private final FileApi.ProgressListener listener;

        public CountingTypedFile(String mimeType, File file, FileApi.ProgressListener listener) {
            super(mimeType, file);
            this.file = file;
            this.listener = listener;
        }

        @Override
        public void writeTo(OutputStream out) throws IOException {
            byte[] buffer = new byte[4096];
            FileInputStream in = new FileInputStream(this.file);

            int read;
            long processed = 0L, expected = this.file.length();
            try {
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    if (listener != null) {
                        processed += read;
                        listener.onProgress(expected, processed);
                    }
                }
            } finally {
                in.close();
            }
        }

        @Override
        public String fileName() {
            try {
                MessageDigest digest = MessageDigest.getInstance("sha1");
                byte[] hash = digest.digest(org.apache.commons.io.FileUtils.readFileToByteArray(this.file));
                String fileNameHashed = "";
                //noinspection ForLoopReplaceableByForEach
                for (int i = 0; i < hash.length; i++) {
                    fileNameHashed += Integer.toString((hash[i] & 0xff) + 0x100, 16).substring(1);
                }
                return fileNameHashed + (this.file.getName().replace("\\s+", "_"));
            } catch (NoSuchAlgorithmException | IOException e) {
                throw new RuntimeException(e.getCause());
            }
        }
    }

}
