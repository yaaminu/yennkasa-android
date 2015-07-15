package com.pair.net.api;

import com.google.gson.JsonObject;
import com.pair.data.User;
import com.pair.net.HttpResponse;

import java.util.List;

import retrofit.Callback;
import retrofit.client.Response;
import retrofit.http.Body;
import retrofit.http.Field;
import retrofit.http.FormUrlEncoded;
import retrofit.http.GET;
import retrofit.http.POST;
import retrofit.http.PUT;
import retrofit.http.Path;
import retrofit.mime.TypedFile;

/**
 * Created by Null-Pointer on 5/27/2015.
 */
public interface UserApi {

    @POST("/api/v1/users/register")
    void registerUser(@Body JsonObject user, Callback<User> callback);

    @POST("/api/v1/users/login")
    void logIn(@Body JsonObject object, Callback<User> callback);

    @POST("/api/v1/users/")
    void fetchFriends(@Body List<String> userIds, Callback<List<User>> response);

    @GET("/api/v1/users/{id}")
    void getUser(@Path("id") String id, Callback<User> response);

    @GET("/api/v1/users/{id}/groups")
    void getGroups(@Path("id") String id, Callback<List<User>> response);

    @PUT("/api/v1/{placeHolder}/{id}/dp")
    void changeDp(@Path("placeHolder") String userOrGroup, @Path("id") String id, @Body TypedFile file, Callback<HttpResponse> response);

    @POST("/api/v1/groups/")
    void createGroup(@Body JsonObject group, Callback<User> response);

    @GET("/api/v1/groups/{id}")
    void getGroup(@Path("id") String id, Callback<User> group);

    @GET("/api/v1/groups/{id}/members")
    void getGroupMembers(@Path("id") String id, Callback<List<User>> response);

    @FormUrlEncoded
    @PUT("/api/v1/groups/{id}/members/add")
    void addMembersToGroup(@Path("id") String id
            , @Field("by") String by
            , @Field("members") List<String> members
            , Callback<Response> response);

    @FormUrlEncoded
    @PUT("/api/v1/groups/{id}/members/remove")
    void removeMembersFromGroup(@Path("id") String id
            , @Field("by") String by
            , @Field("members") List<String> members
            , Callback<Response> response);


}
