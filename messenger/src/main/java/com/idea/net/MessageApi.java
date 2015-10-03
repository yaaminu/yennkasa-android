package com.idea.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import retrofit.Callback;
import retrofit.http.Body;
import retrofit.http.Headers;
import retrofit.http.Multipart;
import retrofit.http.POST;
import retrofit.http.Part;
import retrofit.http.Query;
import retrofit.mime.TypedFile;


/**
 * @author by Null-Pointer on 5/26/2015.
 */
public interface MessageApi {
    String BASE_URL = "/api/v1";

    @POST(BASE_URL + "/messages")
    @Headers({
            "Content-Type:application/json"
    })
    void sendMessage(@Body JsonObject message, @Query("group") int toGroup, Callback<HttpResponse> responseCallback);

    @SuppressWarnings("unused")
    @POST(BASE_URL + "/messages")
    @Headers({
            "Content-Type:application/json"
    })
    void sendMessage(@Body JsonArray messages, Callback<HttpResponse> responseCallback);

    @Multipart
    @POST(BASE_URL + "/messages?bin=1")
    void sendMessage(@Part("message") JsonObject message, @Part("bin") TypedFile binary, @Query("group") int toGroup, Callback<HttpResponse> responseCallback);

}
