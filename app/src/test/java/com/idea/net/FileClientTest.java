package com.idea.net;

import junit.framework.TestCase;

import org.junit.After;
import org.junit.Test;

import java.io.File;

public class FileClientTest extends TestCase {

    @After
    public void tearDown() throws Exception {

    }

    @Test
    public void testNewInstance() throws Exception {
        try {
            FileClient client = FileClient.newInstance("localhost", null);
            fail();
        } catch (Exception e) {
            assertSame(IllegalArgumentException.class, e.getClass());
        }

        FileClient client = FileClient.newInstance("http://localhost:5000/some/path", null);
        String endPoint = client.getEndPoint(), path = client.getpath();
        System.out.println(endPoint);
        System.out.println(path);
        assertEquals(endPoint, "http://localhost:5000");
        assertEquals(path, "some/path");
        client = FileClient.newInstance("http://localhost/some/path", null);
        endPoint = client.getEndPoint();
        path = client.getpath();
        System.out.println(endPoint);
        System.out.println(path);
        assertEquals(endPoint, "http://localhost");
        assertEquals(path, "some/path");

        client = FileClient.newInstance("http://localhost:5000", null);
        endPoint = client.getEndPoint();
        path = client.getpath();
        System.out.println(endPoint);
        System.out.println(path);
        assertEquals(endPoint, "http://localhost:5000");
        assertEquals(path, "/");

        client = FileClient.newInstance("http://localhost:5000/", null);
        path = client.getpath();
        assertEquals(path, "/");

    }

    @Test
    public void testUpload() throws Exception {
        FileClient client = FileClient.newInstance("http://localhost:5000/fileApi/message", null);
        String url = client.upload(file, mimeType, listener);
        System.out.println(url);
        //client.upload();
    }

    File file = new File("C:\\Users\\Null-Pointer\\Desktop\\web_hi_res_512.png");
    String mimeType = "image/png";
    FileApi.ProgressListener listener = new FileApi.ProgressListener() {
        @Override
        public void onProgress(long expected, long transferred) {
            double ratio = (double)transferred/expected;
            System.out.println("progress: " + (int)(100 * ratio));
        }
    };

    FileApi.FileSaveCallback fileSaveCallback = new FileApi.FileSaveCallback() {
        @Override
        public void done(Exception e, String url) {
            if (e != null) {
                e.printStackTrace();
            } else {
                System.out.println(url);
            }
        }
    };
}