package com.idea.net;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.File;

import retrofit.RetrofitError;
import retrofit.client.Response;

/**
 * @author Null-Pointer on 8/27/2015.
 */
public interface FileApi {

    void saveFileToBackend(File file, FileSaveCallback callback, ProgressListener listener);

    void deleteFileFromBackend(String fileName, FileDeleteCallback callback);

    interface ProgressListener {
        void onProgress(long expected, long transferred);
    }

    abstract class FileSaveCallback implements retrofit.Callback<JsonObject> {
        @Override
        public final void success(JsonObject s, Response response) {
            final JsonElement ele = s.get("href");
            if (ele == null || ele.isJsonNull()) {
                done(new FileClientException("An unknwon error occured", -1), null);
                return;
            }
            done(null, ele.getAsString());
        }

        @Override
        public final void failure(RetrofitError retrofitError) {
            int statusCode = -1;
            if (retrofitError.getKind() == RetrofitError.Kind.HTTP) {
                statusCode = retrofitError.getResponse().getStatus();
            }
            done(new FileClientException(retrofitError.getCause(), statusCode), null);
        }

        public abstract void done(FileClientException e, String url);
    }

    interface FileDeleteCallback {
        void done(FileClientException e);
    }
}
