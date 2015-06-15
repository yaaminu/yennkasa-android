package com.pair.net.api;

import com.google.gson.JsonObject;
import com.pair.data.User;

import java.util.List;

import retrofit.Callback;
import retrofit.http.Body;
import retrofit.http.POST;

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
}
