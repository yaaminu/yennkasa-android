package com.pairapp.net;

import com.google.gson.JsonObject;

import retrofit.client.Response;
import retrofit.http.DELETE;
import retrofit.http.Field;
import retrofit.http.FormUrlEncoded;
import retrofit.http.GET;
import retrofit.http.Multipart;
import retrofit.http.POST;
import retrofit.http.PUT;
import retrofit.http.Part;
import retrofit.http.Path;
import retrofit.http.Query;
import retrofit.mime.TypedFile;

public interface SmartFileService {

    @Multipart
    @POST("/api/2/path/data/{dir}")
    Response saveFile(@Path(value = "dir", encode = false) String path, @Part("bin") TypedFile file);

    @Multipart
    @POST("/")
    JsonObject saveFile(@Part("bin") TypedFile file);


//    @FormUrlEncoded
//    @POST("/api/2/link")
//    JsonObject getLink(@Field("read") boolean read,
//                       @Field("list") boolean list,
//                       @Field("cache") int cache,
//                       @Field("name") String name,
//                       @Field(value = "path", encodeValue = false) String path);

    @GET("/link")
    JsonObject getLink(@Query("pd") String dir);

    @FormUrlEncoded
    @PUT("/api/2/path/oper/mkdir/{dir}")
    Response createDir(@Path(value = "dir", encode = false) String path, @Field("dummy") String dummy);

    @DELETE("/api/2/path/oper/remove/{file}")
    JsonObject deleteFile(@Path(value = "file", encode = false) String path);
}
