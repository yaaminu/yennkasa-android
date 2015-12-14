package com.pairapp.net;


import com.pairapp.util.Config;
import com.pairapp.util.FileUtils;
import com.pairapp.util.PLog;

import java.io.File;
import java.io.IOException;
import java.util.Map;


/**
 * @author Null-Pointer on 10/3/2015.
 */
class DisplayPictureFileClient {
    private final FileClientImpl fileClient;
    private static final String TAG = DisplayPictureFileClient.class.getSimpleName();
    private DisplayPictureFileClient(String key, String password) {
        fileClient = new FileClientImpl(key, password, "DisplayPictures");
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


    void changeDp(final String userId, File file, final FileApi.FileSaveCallback callback, FileApi.ProgressListener listener) {
        File dp = new File(Config.getTempDir(),
                userId.trim() + ".jpg");
        try {
            FileUtils.copyTo(file, dp);
            fileClient.saveFileToBackend(dp, new FileApi.FileSaveCallback() {
                @Override
                public void done(FileClientException e, String url) {
                    callback.done(e, url);
                }
            }, listener);
        } catch (IOException e) {
            PLog.d(TAG,"failed to copy dp to profile photos dir",e.getCause());
            callback.done(new FileClientException(e.getMessage(), -1), null);
        }
    }


    private class FileClientImpl extends SmartFileClient {

        FileClientImpl(String key, String password, String dir) {
            super(key, password, dir);
        }
    }
}
