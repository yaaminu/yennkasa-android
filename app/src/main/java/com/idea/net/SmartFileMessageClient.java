package com.idea.net;

import com.idea.util.Config;
import com.idea.util.FileUtils;
import com.idea.util.PLog;

import java.io.File;
import java.io.IOException;

/**
 * by Null-Pointer on 10/6/2015.
 */
public class SmartFileMessageClient extends SmartFileClient {
    private static final String TAG = SmartFileMessageClient.class.getSimpleName();
    private final String userId;

    public SmartFileMessageClient(String key, String password, String userId) {
        super(key, password, "Attachments");
        this.userId = userId;
    }

    @Override
    public void saveFileToBackend(File file, final FileSaveCallback callback, ProgressListener listener) {
        //noinspection StringBufferReplaceableByString
        StringBuilder hash = new StringBuilder(FileUtils.hashFile(file));
        hash.append("_").append((userId + "_" + file.getName().split("\\Q.\\E")[0]).replaceAll("[\\W]+", "_"));
        final File tmp = new File(Config.getTempDir(), hash.toString() + "." + FileUtils.getExtension(file.getAbsolutePath(), "bin"));
        try {
            FileUtils.copyTo(file, tmp);
            super.saveFileToBackend(tmp, new FileSaveCallback() {
                @Override
                public void done(FileClientException e, String url) {
                    //noinspection ResultOfMethodCallIgnored
                    tmp.delete();
                    callback.done(e, url);
                }
            }, listener);
        } catch (IOException e) {
            PLog.d(TAG, "error while copying file to tmp dir", e.getCause());
            callback.done(new FileClientException(e, -1), null);
        }
    }

}
