package com.pairapp.net;
import java.io.File;

/**
 * @author Null-Pointer on 8/27/2015.
 */
public interface FileApi {

    void saveFileToBackend(File file, FileSaveCallback callback, ProgressListener listener);

    void deleteFileFromBackend(String fileName, FileDeleteCallback callback);

    interface ProgressListener {
        void onProgress(long expected, long transferred);
    }

    abstract class FileSaveCallback {

        public abstract void done(FileClientException e, String url);
    }

    interface FileDeleteCallback {
        void done(FileClientException e);
    }
}
