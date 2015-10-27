package com.idea.net;

import retrofit.client.Response;
import retrofit.http.Field;
import retrofit.http.FormUrlEncoded;
import retrofit.http.PUT;

/**
 * @author Null-Pointer on 10/22/2015.
 */
public interface DpApi {

    @FormUrlEncoded
    @PUT("/")
    Response markForDeletion(@Field("userId") String userId);
}
