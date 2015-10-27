package com.idea.net;


import com.idea.data.UserManager;
import com.idea.net.file_service.BuildConfig;
import com.idea.util.Config;

import java.io.File;
import java.util.Map;

import retrofit.RequestInterceptor;
import retrofit.RestAdapter;
import retrofit.RetrofitError;
import retrofit.android.AndroidLog;

/**
 * @author Null-Pointer on 10/3/2015.
 */
class DisplayPictureFileClient {
    private final FileClientImpl fileClient;

    private DisplayPictureFileClient(String key, String password) {
        fileClient = new FileClientImpl(key, password, "DisplayPics");
    }

    static DisplayPictureFileClient createInstance(Map<String, String> credentials) {
        if (credentials == null || credentials.isEmpty())
            throw new IllegalArgumentException("credentials");

        String key = credentials.get("key");
        if (key == null) throw new IllegalArgumentException("key not in credentials");


        String password = credentials.get("password");
        if (password == null) throw new IllegalArgumentException("password not in credentials");

        return new DisplayPictureFileClient(key, password);
    }


    void changeDp(String userId, File file, FileApi.FileSaveCallback callback, FileApi.ProgressListener listener) {
        if (fileClient.markDpForDeletion(userId)) {
            fileClient.saveFileToBackend(file, callback, listener);
            return;
        }
        callback.done(new FileClientException("error occurred", -1), null);
    }


    private class FileClientImpl extends SmartFileClient {
        private final DpApi api;

        FileClientImpl(String key, String password, String dir) {
            super(key, password, dir);
            RestAdapter adapter = new RestAdapter.Builder()
                    .setEndpoint(Config.getFilesMetaDataApiUrl())
                    .setLog(new AndroidLog(DisplayPictureFileClient.class.getSimpleName()))
                    .setLogLevel(BuildConfig.DEBUG ? RestAdapter.LogLevel.FULL : RestAdapter.LogLevel.NONE)
                    .setRequestInterceptor(new RequestInterceptor() {
                        @Override
                        public void intercept(RequestFacade requestFacade) {
                            requestFacade.addHeader("Authorization", "kiibodaS3crite");
                        }
                    }).build();
            api = adapter.create(DpApi.class);
        }

        private boolean markDpForDeletion(String userId) {
            try {
                api.markForDeletion(userId);
            } catch (RetrofitError e) {
                return false;
            }
            return true;
        }
    }
}
