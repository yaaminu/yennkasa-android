package com.pairapp.net;

import com.pairapp.util.GenericUtils;
import com.pairapp.util.PLog;
import com.parse.ParseException;
import com.parse.ParseFile;
import com.parse.ProgressCallback;
import com.parse.SaveCallback;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

/**
 * An implementation of {@link FileApi} that uses Parse for persisting files in our backend
 *
 * @author by aminu on 4/24/2016.
 */
public class ParseFileClient implements FileApi {

    private static final String TAG = ParseFileClient.class.getSimpleName();
    private static final FileApi INSTANCE = new ParseFileClient();

    public static FileApi getInstance() {
        return INSTANCE;
    }

    @Override
    public void saveFileToBackend(final File file, final FileSaveCallback callback, final ProgressListener listener) {
        GenericUtils.ensureNotNull(file, callback, listener);
        GenericUtils.ensureConditionTrue(file.exists() && file.isFile(), "invalid file");
        if (file.length() > FileUtils.ONE_MB * 10) {
            callback.done(new FileClientException("file too large", -1), null);
        } else if (Runtime.getRuntime().freeMemory() < file.length() + 2048) {
            callback.done(new FileClientException("system out of memory", -1), null);
        } else {
            final ParseFile pFile;
            try {
                pFile = new ParseFile(file.getName(), FileUtils.readFileToByteArray(file));
                pFile.saveInBackground(new SaveCallback() {
                    @Override
                    public void done(ParseException e) {
                        if (e != null) {
                            PLog.e(TAG, e.getMessage(), e.getCause());
                        }
                        callback.done(e == null ? null : new FileClientException(e.getCause(), -1), pFile.getUrl());
                    }
                }, new ProgressCallback() {
                    @Override
                    public void done(Integer integer) {
                        listener.onProgress(file.length(), (file.length() * integer) / 100);
                    }
                });
            } catch (IOException e) {
                PLog.e(TAG, e.getMessage(), e.getCause());
                callback.done(new FileClientException(e.getMessage(), -1), null);
            }
        }

    }

    @Override
    public void deleteFileFromBackend(String fileName, FileDeleteCallback callback) {
        throw new UnsupportedOperationException();
    }
}
