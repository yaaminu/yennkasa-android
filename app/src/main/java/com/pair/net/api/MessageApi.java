package com.pair.net.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.pair.net.HttpResponse;

import retrofit.Callback;
import retrofit.http.Body;
import retrofit.http.Headers;
import retrofit.http.POST;


/**
 * Created by Null-Pointer on 5/26/2015.
 */
public interface MessageApi {
    String BASE_URL = "/api/v1";

    @POST(BASE_URL + "/messages")
    @Headers({
            "Content-Type:application/json",
            "Authorization:kiiboda+=s3cr3te"
    })
    void sendMessage(@Body JsonObject message, Callback<HttpResponse> responseCallback);

    @POST(BASE_URL + "/messages")
    @Headers({
            "Content-Type:application/json",
            "Authorization:kiiboda+=s3cr3te"
    })
    void sendMessage(@Body JsonArray messages, Callback<HttpResponse> responseCallback);
}
