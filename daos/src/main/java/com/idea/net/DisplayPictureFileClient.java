package com.idea.net;


import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;

import com.google.gson.JsonObject;
import com.idea.util.Config;
import com.idea.util.FileUtils;
import java.io.File;
import java.util.Map;


/**
 * @author Null-Pointer on 10/3/2015.
 */
class DisplayPictureFileClient {
    private final FileClientImpl fileClient;
    private final String dir;

    private DisplayPictureFileClient(String key, String password) {
        dir = "DisplayPictures";
        fileClient = new FileClientImpl(key, password, dir);
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
        File dp = new File(Config.getAppProfilePicsBaseDir(),
            userId.trim()+"."+FileUtils.getExtension(file.getAbsolutePath()));
        FileUtils.copyTo(file,dp);
        fileClient.saveFileToBackend(dp, new FileApi.FileSaveCallback() {
            @Override
            public void done(FileClientException e, String url) {
                callback.done(e, url);
            }
        }, listener);
    }


    private class FileClientImpl extends SmartFileClient {

        FileClientImpl(String key, String password, String dir) {
            super(key, password, dir);
        }
    }
}
