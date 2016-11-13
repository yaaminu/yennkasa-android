package com.pairapp.net;

import android.support.annotation.NonNull;

import com.google.gson.JsonObject;
import com.pairapp.Errors.PairappException;
import com.pairapp.data.Message;
import com.pairapp.data.User;

import java.io.File;
import java.util.Collection;
import java.util.List;

import retrofit.http.Body;
import retrofit.http.DELETE;
import retrofit.http.Field;
import retrofit.http.FormUrlEncoded;
import retrofit.http.GET;
import retrofit.http.POST;
import retrofit.http.PUT;
import retrofit.http.Path;

/**
 * @author by Null-Pointer on 5/27/2015.
 */
public interface UserApiV2 {

    String getAuthToken();

    @SuppressWarnings("unused")
    @POST("/api/v1/users/register")
    void registerUser(@Body JsonObject user, Callback<User> callback);

    @SuppressWarnings("unused")
    @POST("/api/v1/users/login")
    void logIn(@Body JsonObject object, Callback<User> callback);

    void sendVerificationToken(String userId, Callback<HttpResponse> callback);

    @POST("/api/v1/users/register")
    void registerUser(@Body User user, Callback<User> callback);

    @POST("/api/v1/users/login")
    void logIn(@Body User object, Callback<User> callback);

    @POST("/api/v1/users/")
    void syncContacts(@Body List<String> userIds, Callback<List<User>> response);

    @GET("/api/v1/users/{id}")
    void getUser(@Path(Message.FIELD_ID) String id, Callback<User> response);

    @GET("/api/v1/users/{id}/groups")
    void getGroups(@Path(Message.FIELD_ID) String id, Callback<List<User>> response);

    @PUT("/api/v1/{placeHolder}/{id}/dp")
    void changeDp(@Path("placeHolder") String userOrGroup, @Path(Message.FIELD_ID) String id, @Body File file, Callback<HttpResponse> response);

    @FormUrlEncoded
    @POST("/api/v1/groups/")
    void createGroup(@Field("createdBy") String by, @Field("name") String name, @Field("starters") Collection<String> members, Callback<User> response);

    @GET("/api/v1/groups/{id}")
    void getGroup(@Path(Message.FIELD_ID) String id, Callback<User> group);

    @GET("/api/v1/groups/{id}/members")
    void getGroupMembers(@Path(Message.FIELD_ID) String id, Callback<List<User>> response);

    @FormUrlEncoded
    @PUT("/api/v1/groups/{id}/members/add")
    void addMembersToGroup(@Path(Message.FIELD_ID) String id
            , @Field("by") String by
            , @Field(User.FIELD_MEMBERS) Collection<String> members
            , Callback<HttpResponse> response);

    @FormUrlEncoded
    @PUT("/api/v1/groups/{id}/members/remove")
    void removeMembersFromGroup(@Path(Message.FIELD_ID) String id
            , @Field("by") String by
            , @Field(User.FIELD_MEMBERS) List<String> members
            , Callback<HttpResponse> response);

    // FIXME: 7/19/2015 change this when our backend start using sessions
    @FormUrlEncoded
    @PUT("/api/v1/groups/{id}/leave")
    void leaveGroup(@Path("id") String id, @Field("leaver") String userId, @Field("password") String password, Callback<HttpResponse> response);


    @FormUrlEncoded
    @POST("/api/v1/users/{id}/verify")
    void verifyUser(@Path("id") String userId, @Field("token") String token, String pushID, Callback<SessionData> callback);

    @FormUrlEncoded
    @POST("/api/v1/users/{id}/resendToken")
    void resendToken(@Path("id") String userId, @Field("password") String password, Callback<HttpResponse> response);

    @DELETE("/api/v1/users/{id}?unverified=1")
    HttpResponse resetUnverifiedAccount(@Path("id") String userId);

    @POST("/api/v1/users/{id}/resetPassword")
    HttpResponse requestPasswordReset(String number);

    void removeGroup(String adminId, String groupId, Callback<HttpResponse> callback);

    boolean isUserAuthenticated();


    @NonNull
    String newAuthToken() throws PairappException;

    interface Callback<T> {
        void done(Exception e, T t);
    }

    interface Preprocessor {
        void process(User user);

        void process(Collection<User> users);
    }

    class SessionData {
        public final String accessToken, userId;

        public SessionData(String accessToken, String ownerId) {
            this.accessToken = accessToken;
            this.userId = ownerId;
        }
    }
}
