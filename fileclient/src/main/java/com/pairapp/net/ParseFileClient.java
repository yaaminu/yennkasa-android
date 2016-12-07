package com.pairapp.net;

import com.pairapp.util.GenericUtils;
import com.pairapp.util.PLog;
import com.parse.ParseException;
import com.parse.ParseFile;

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
        } else {
            if (Runtime.getRuntime().freeMemory() < file.length() + 2048) {
                PLog.d(TAG, "forcing gc");
                PLog.d(TAG, "before gc %s", Runtime.getRuntime().freeMemory());
                System.gc();
                PLog.d(TAG, "after gc %s", Runtime.getRuntime().freeMemory());
            }
            final ParseFile pFile;
            try {
                pFile = new ParseFile(file.getName(), FileUtils.readFileToByteArray(file));
                listener.onProgress(file.length(), 0L);
                pFile.save();
                listener.onProgress(file.length(), file.length());
                callback.done(null, pFile.getUrl());
            } catch (IOException | ParseException e) {
                PLog.e(TAG, e.getMessage(), e);
                callback.done(new FileClientException(e.getMessage(), -1), null);

            }
        }

    }

    @Override
    public void deleteFileFromBackend(String fileName, FileDeleteCallback callback) {
        throw new UnsupportedOperationException();
    }
}
