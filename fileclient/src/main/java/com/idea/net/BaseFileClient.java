package com.idea.net;

import android.text.TextUtils;

import com.idea.util.FileUtils;
import com.idea.util.TaskManager;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.Map;

/**
 * @author Null-Pointer on 10/3/2015.
 */
public class BaseFileClient implements FileApi {
    private static final String TAG = BaseFileClient.class.getSimpleName();
    private final FileClient fileClient;

    BaseFileClient(String endpoint, Map<String, String> credentials) {
        try {
            //noinspection unused
            URL url = new URL(endpoint);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("invalid endpoint");
        }

        if (credentials == null) {
            credentials = Collections.emptyMap();
        }
        this.fileClient = FileClient.newInstance(endpoint, Collections.unmodifiableMap(credentials));
    }

    @Override
    public  void saveFileToBackend(final File file, final FileSaveCallback callback, final FileApi.ProgressListener listener) {
        if (file == null || !file.exists()) {
            throw new IllegalArgumentException("invalid file");
        }

        if (callback == null) {
            throw new IllegalArgumentException("callback is required");
        }

        String mimeType = FileUtils.getMimeType(file.getAbsolutePath());
        if (TextUtils.isEmpty(mimeType)) {
            mimeType = "application/octet-stream";
        }
        final String mimeType2 = mimeType;
        TaskManager.execute(new Runnable() {
            @Override
            public void run() {
                fileClient.upload(file, mimeType2, listener,callback);
            }
        });
    }

    @Override
    public void deleteFileFromBackend(String fileName, FileDeleteCallback callback) {
        throw new UnsupportedOperationException();
    }

}
