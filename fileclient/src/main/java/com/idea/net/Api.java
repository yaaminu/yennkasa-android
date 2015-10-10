package com.idea.net;

import com.google.gson.JsonObject;

import retrofit.Callback;
import retrofit.http.Multipart;
import retrofit.http.POST;
import retrofit.http.Part;
import retrofit.http.Path;
import retrofit.mime.TypedFile;

public interface Api {

    @Multipart
    @POST("/{path}")
    void upload(@Path(value = "path",encode = false) String path, @Part("bin") TypedFile file, Callback<JsonObject> fileSaveCallback);

    @Multipart
    @POST("/{path}")
    JsonObject upload(@Path(value = "path",encode = false) String path, @Part("bin") TypedFile file);
}
