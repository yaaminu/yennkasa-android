package com.idea.net;

import java.io.File;

/**
 * @author Null-Pointer on 8/27/2015.
 */
public interface FileApi {

    void saveFileToBackend(File file, FileSaveCallback callback, ProgressListener listener);

    interface ProgressListener {
        void onProgress(int percentComplete);
    }

    interface FileSaveCallback {
        void done(Exception e, String url);
    }
}
