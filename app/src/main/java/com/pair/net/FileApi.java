package com.pair.net;

import java.io.File;
import java.io.IOException;

/**
 * @author Null-Pointer on 8/27/2015.
 */
public interface FileApi {

    void saveFileToBackend(File file, FileSaveCallback callback, ProgressListener listener) throws IOException;

    interface ProgressListener {
        void onProgress(int percentComplete);
    }

    interface FileSaveCallback {
        void done(Exception e, String url);
    }
}
