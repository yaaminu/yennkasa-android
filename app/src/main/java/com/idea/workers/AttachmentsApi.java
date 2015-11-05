package com.idea.workers;

import org.json.JSONObject;

import java.util.List;
import java.util.Map;

import retrofit.client.Response;
import retrofit.http.Body;
import retrofit.http.FieldMap;
import retrofit.http.FormUrlEncoded;
import retrofit.http.PUT;

/**
 * @author Null-Pointer on 10/22/2015.
 */
public interface AttachmentsApi {

    @FormUrlEncoded
    @PUT("/")
    Response markForDeletion(@FieldMap Map<String, String> body);


    @PUT("/?multi=true")
    Response markMultipleForDeletion(@Body List<JSONObject> array);
}
