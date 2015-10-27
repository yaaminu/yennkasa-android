package com.idea.messenger;

import com.google.gson.JsonObject;

import retrofit.Callback;
import retrofit.client.Response;
import retrofit.http.Body;
import retrofit.http.POST;

/**
 * @author Null-Pointer on 8/30/2015.
 */
public interface ParsePushApi {

    @POST("/push")
    void sendPush(@Body JsonObject pushQueryAndData, Callback<Response> responseCallback);
}
