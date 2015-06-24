package com.pair.net.api;

import com.google.gson.JsonObject;
import com.pair.data.User;

import java.util.List;

import retrofit.Callback;
import retrofit.client.Response;
import retrofit.http.Body;
import retrofit.http.GET;
import retrofit.http.Multipart;
import retrofit.http.POST;
import retrofit.http.PUT;
import retrofit.http.Part;
import retrofit.http.Path;
import retrofit.http.Streaming;
import retrofit.mime.TypedFile;

/**
 * Created by Null-Pointer on 5/27/2015.
 */
public interface UserApi {

    @POST("/api/v1/users/register")
    void registerUser(@Body JsonObject user, Callback<User> callback);

    @POST("/api/v1/users/login")
    void logIn(@Body JsonObject object,Callback<User> callback);

    @POST("/api/v1/users/")
    void fetchFriends(@Body List<String> userIds,Callback<List<User>> response);

    @GET("/api/v1/users/{id}")
    void getUser(@Path("id") String id, Callback<User> response);

    @Streaming
    @GET("/api/v1/users/{id}/dp")
    void getUserDp(@Path("id") String id, Callback<Response> response);

    @Multipart
    @PUT("/api/v1/users/{id}/dp")
    void changeDp(@Path("id") String id, @Part("bin") TypedFile file, @Part("password") String password, Callback<Response> response);
}
