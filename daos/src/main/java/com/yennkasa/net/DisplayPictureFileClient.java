package com.yennkasa.net;


import com.yennkasa.util.Config;
import com.yennkasa.util.FileUtils;
import com.yennkasa.util.PLog;

import java.io.File;
import java.io.IOException;

/**
 * @author Null-Pointer on 10/3/2015.
 */
class DisplayPictureFileClient {
    private static final String TAG = DisplayPictureFileClient.class.getSimpleName();
    FileApi fileClient;

    static DisplayPictureFileClient createInstance(FileApi api) {
        return new DisplayPictureFileClient(api);
    }

    DisplayPictureFileClient(FileApi api) {
        this.fileClient = api;
    }

    void changeDp(final String userId, File file, final FileApi.FileSaveCallback callback, FileApi.ProgressListener listener) {
        final File dp = new File(Config.getTempDir(),
                userId.trim() + ".jpg");
        try {
            FileUtils.copyTo(file, dp);
            fileClient.saveFileToBackend(dp, new FileApi.FileSaveCallback() {
                @Override
                public void done(FileClientException e, String url) {
                    if (e != null) {
                        PLog.d(TAG, e.getMessage(), e.getCause());
                    }
                    callback.done(e, url);
                    //noinspection ResultOfMethodCallIgnored
                    dp.delete();
                }
            }, listener);
        } catch (IOException e) {
            PLog.d(TAG, "failed to copy dp to profile photos dir", e.getCause());
            callback.done(new FileClientException(e.getMessage(), -1), null);
        }
    }
}
