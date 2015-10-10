package com.idea.net;

import android.test.AndroidTestCase;
import android.test.mock.MockApplication;

import com.idea.util.Config;

import java.io.File;

public class SmartFileMessageClientTest extends AndroidTestCase {

    File testFile;

    public void setUp() throws Exception {
        super.setUp();
        MockApplication application = new MockApplication();
        Config.init(application);
        testFile = new File(Config.getTempDir(), "test.txt");
        org.apache.commons.io.FileUtils.write(testFile, "this is teh test file");
    }

    public void tearDown() throws Exception {
        super.tearDown();
    }

    public void testSaveFileToBackend() throws Exception {
        SmartFileMessageClient client = new SmartFileMessageClient("key", "password", "someUserId");
        client.saveFileToBackend(testFile, callback, listener);
    }

    public void testDeleteFileFromBackend() throws Exception {

    }

    FileApi.FileSaveCallback callback = new FileApi.FileSaveCallback() {
        @Override
        public void done(Exception e, String url) {
            if (e != null) {
                e.printStackTrace();
            } else {
                System.out.println(url);
            }
        }
    };

    FileApi.ProgressListener listener = new FileApi.ProgressListener() {
        double previous = -1;

        @Override
        public void onProgress(long expected, long transferred) {
            double progress = ((double) expected) / transferred;
            if (progress > progress + 1) {
                previous = progress;
                System.out.println("progress: " + progress);
            }
        }
    };
}