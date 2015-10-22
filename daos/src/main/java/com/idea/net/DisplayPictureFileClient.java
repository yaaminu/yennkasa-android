package com.idea.net;

import com.squareup.okhttp.Headers;
import com.squareup.okhttp.OkHttpClient;
import retrofit.client.OkClient;
import retrofit.client.Request;
import retrofit.client.Response;
import retrofit.mime.TypedOutput;

import java.io.IOException;
import java.util.Map;

/**
 * @author Null-Pointer on 10/3/2015.
 */
public class DisplayPictureFileClient extends SmartFileClient {
    private DisplayPictureFileClient(String key, String password) {
        super(key, password, "DisplayPics");
    }

    public static DisplayPictureFileClient createInstance(Map<String, String> credentials) {
        if (credentials == null || credentials.isEmpty())
            throw new IllegalArgumentException("credentials");

        String key = credentials.get("key");
        if (key == null) throw new IllegalArgumentException("key not in credentials");


        String password = credentials.get("password");
        if (password == null) throw new IllegalArgumentException("password not in credentials");

        return new DisplayPictureFileClient(key, password);
    }

}
