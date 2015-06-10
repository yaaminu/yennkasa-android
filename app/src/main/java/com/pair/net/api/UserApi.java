package com.pair.net.api;

import com.google.gson.JsonObject;
import com.pair.data.User;

import java.util.List;

import retrofit.Callback;
import retrofit.http.Body;
import retrofit.http.Headers;
import retrofit.http.POST;

/**
 * Created by Null-Pointer on 5/27/2015.
 */
public interface UserApi {

    @POST("/api/v1/users/register")
    @Headers("Authorization:kiiboda+=s3cr3te")
    void registerUser(@Body JsonObject user, Callback<User> callback);

    @POST("/api/v1/users/login")
    @Headers("Authorization:kiiboda+=s3cr3te")
    void logIn(@Body JsonObject object,Callback<User> callback);

    @POST("/api/v1/users/")
    @Headers("Authorization:kiiboda+=s3cr3te")
    void fetchFriends(@Body List<String> userIds,Callback<List<User>> response);
}
