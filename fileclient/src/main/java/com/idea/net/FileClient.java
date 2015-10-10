package com.idea.net;

import com.google.gson.JsonObject;
import com.idea.util.PLog;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import retrofit.Callback;
import retrofit.RequestInterceptor;
import retrofit.RestAdapter;
import retrofit.RetrofitError;
import retrofit.mime.TypedFile;

/**
 * @author Null-Pointer on 10/3/2015.
 */
public class FileClient {

    private static final String TAG = FileClient.class.getSimpleName();
    public static final Executor WORKER = Executors.newCachedThreadPool();
    private final String path;
    private final Map<String, String> credentials;
    @SuppressWarnings("FieldCanBeLocal")
    private final RequestInterceptor reqInterceptor = new RequestInterceptor() {
        @Override
        public void intercept(RequestFacade requestFacade) {
            for (String s : credentials.keySet()) {
                requestFacade.addHeader(s, credentials.get(s));
            }
        }
    };
    private final Api api;
    private final String endpoint;

    public static FileClient newInstance(String endPoint, Map<String, String> credentials) {
        try {
            URL url = new URL(endPoint);
            String path = url.getPath();

            if ("".equals(path))
                path = "/";
            else if (path.startsWith("/") && path.length() > 1) {
                path = path.substring(1);
            }
            url = new URL(url.getProtocol(), url.getHost(), url.getPort(), "");
            endPoint = url.toExternalForm();
            if (credentials == null) {
                credentials = Collections.emptyMap();
            } else {
                credentials = Collections.unmodifiableMap(credentials);
            }
            return new FileClient(endPoint, path, credentials);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("invalid endpoint");
        }

    }

    private FileClient(String endpoint, String path, Map<String, String> credentials) {
        this.path = path;
        this.credentials = credentials;
        this.endpoint = endpoint;
        api = new RestAdapter.Builder().setEndpoint(this.endpoint)
                .setLog(logger)
                .setLogLevel(RestAdapter.LogLevel.FULL)
                .setExecutors(WORKER, WORKER)
                .setRequestInterceptor(reqInterceptor)
                .build().create(Api.class);
    }

    @SuppressWarnings("FieldCanBeLocal")
    private final RestAdapter.Log logger = new RestAdapter.Log() {
        @Override
        public void log(String s) {
            try {
                PLog.v(TAG, s + "");
            } catch (Exception e) { //testing evironment
                System.out.println(TAG + " : " + s);
            }
        }
    };

    public String getEndPoint() {
        return this.endpoint;
    }

    public String getpath() {
        return this.path;
    }

    public void upload(File file, String mimeType, FileApi.ProgressListener listener, Callback<JsonObject> fileSaveCallback) {
        if (file == null || !file.exists() || file.isDirectory()) {
            throw new IllegalArgumentException("file may not be null or directory and should exist");
        }
        if (fileSaveCallback == null) {
            throw new IllegalArgumentException("callback is null");
        }
        if (mimeType == null) {
//            mimeType = FileUtils.getMimeType(file.getPath());
            throw new IllegalArgumentException("mime type is null");
        }
        if ("".equals(mimeType.trim())) {
            mimeType = "application/octet-stream";
        }
        api.upload(this.path, new CountingTypedFile(mimeType, file, listener), fileSaveCallback);
    }

    public String upload(File file, String mimeType, FileApi.ProgressListener listener) throws IOException {
        if (file == null || !file.exists() || file.isDirectory()) {
            throw new IllegalArgumentException("file may not be null or directory and should exist");
        }

        if (mimeType == null) {
//            mimeType = FileUtils.getMimeType(file.getPath());
            throw new IllegalArgumentException("mime type is null");
        }
        if ("".equals(mimeType.trim())) {
            mimeType = "application/octet-stream";
        }
        try {
            final JsonObject response = api.upload(this.path, new CountingTypedFile(mimeType, file, listener));
            return response.get("href").getAsString();
        } catch (RetrofitError e) {
            throw new IOException(e.getCause());
        }
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
