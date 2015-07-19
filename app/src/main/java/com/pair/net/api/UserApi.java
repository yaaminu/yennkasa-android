package com.pair.net.api;

import com.google.gson.JsonObject;
import com.pair.data.Message;
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
    void getUser(@Path(Message.FIELD_ID) String id, Callback<User> response);

    @GET("/api/v1/users/{id}/groups")
    void getGroups(@Path(Message.FIELD_ID) String id, Callback<List<User>> response);

    @PUT("/api/v1/{placeHolder}/{id}/dp")
    void changeDp(@Path("placeHolder") String userOrGroup, @Path(Message.FIELD_ID) String id, @Body TypedFile file, Callback<HttpResponse> response);

    @FormUrlEncoded
    @POST("/api/v1/groups/")
    void createGroup(@Field("createdBy") String by, @Field("name") String name, @Field("starters") List<String> members, Callback<User> response);

    @GET("/api/v1/groups/{id}")
    void getGroup(@Path(Message.FIELD_ID) String id, Callback<User> group);

    @GET("/api/v1/groups/{id}/members")
    void getGroupMembers(@Path(Message.FIELD_ID) String id, Callback<List<User>> response);

    @FormUrlEncoded
    @PUT("/api/v1/groups/{id}/members/add")
    void addMembersToGroup(@Path(Message.FIELD_ID) String id
            , @Field("by") String by
            , @Field(User.FIELD_MEMBERS) List<String> members
            , Callback<Response> response);

    @FormUrlEncoded
    @PUT("/api/v1/groups/{id}/members/remove")
    void removeMembersFromGroup(@Path(Message.FIELD_ID) String id
            , @Field("by") String by
            , @Field(User.FIELD_MEMBERS) List<String> members
            , Callback<Response> response);

    // FIXME: 7/19/2015 change this when our backend start using sessions
    @FormUrlEncoded
    @PUT("/api/v1/groups/{id}/leave")
    void leaveGroup(@Path("id") String id, @Field("leaver") String userId, @Field("password") String password, Callback<HttpResponse> response);
}
