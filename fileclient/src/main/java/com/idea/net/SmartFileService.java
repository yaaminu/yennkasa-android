package com.idea.net;

import com.google.gson.JsonObject;

import retrofit.client.Response;
import retrofit.http.DELETE;
import retrofit.http.Field;
import retrofit.http.FormUrlEncoded;
import retrofit.http.Multipart;
import retrofit.http.POST;
import retrofit.http.PUT;
import retrofit.http.Part;
import retrofit.http.Path;
import retrofit.mime.TypedFile;

public interface SmartFileService {

    @Multipart
    @POST("/path/data/{dir}")
    Response saveFile(@Path(value = "dir", encode = false) String path, @Part("bin") TypedFile file);

    @Multipart
    @POST("/")
    JsonObject saveFile(@Part("bin") TypedFile file);


    @FormUrlEncoded
    @POST("/link")
    JsonObject getLink(@Field("read") boolean read,
                       @Field("list") boolean list,
                       @Field("cahe")long age,
                       @Field("path") String name);

    @FormUrlEncoded
    @PUT("/path/oper/mkdir/{dir}")
    Response createDir(@Path(value = "dir", encode = false) String path, @Field("dummy") String dummy);

    @DELETE("/path/oper/remove/{file}")
    JsonObject deleteFile(@Path(value = "file", encode = false) String path);
}
