package com.idea.net;

import retrofit.client.Response;
import retrofit.http.FieldMap;
import retrofit.http.FormUrlEncoded;
import retrofit.http.POST;

import java.util.Map;

/**
 * @author Null-Pointer on 10/22/2015.
 */
public interface MetaApi {

    @FormUrlEncoded
    @POST("/")
    Response saveLink(@FieldMap Map<String, String> fields);
}
