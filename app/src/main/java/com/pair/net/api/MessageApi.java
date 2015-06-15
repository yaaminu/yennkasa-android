package com.pair.net.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.pair.net.HttpResponse;

import retrofit.Callback;
import retrofit.http.Body;
import retrofit.http.Headers;
import retrofit.http.POST;


/**
 * @author by Null-Pointer on 5/26/2015.
 */
public interface MessageApi {
    String BASE_URL = "/api/v1";

    @POST(BASE_URL + "/messages")
    @Headers({
            "Content-Type:application/json"
    })
    void sendMessage(@Body JsonObject message, Callback<HttpResponse> responseCallback);

    @SuppressWarnings("unused")
    @POST(BASE_URL + "/messages")
    @Headers({
            "Content-Type:application/json"
    })
    void sendMessage(@Body JsonArray messages, Callback<HttpResponse> responseCallback);
}
