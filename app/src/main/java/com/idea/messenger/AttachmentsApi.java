package com.idea.messenger;

import retrofit.client.Response;
import retrofit.http.Field;
import retrofit.http.FormUrlEncoded;
import retrofit.http.PUT;

/**
 * @author Null-Pointer on 10/22/2015.
 */
public interface AttachmentsApi {

    @FormUrlEncoded
    @PUT("/")
    Response markForDeletion(@Field("link") String link);
}
