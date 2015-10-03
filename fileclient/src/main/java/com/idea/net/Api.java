package com.idea.net;

import retrofit.Callback;
import retrofit.client.Response;
import retrofit.http.Multipart;
import retrofit.http.POST;
import retrofit.http.Part;
import retrofit.mime.TypedFile;

/**
 * @author Null-Pointer on 10/3/2015.
 */
public interface Api {

    @Multipart
    @POST("/fileApi/dp")
    Response upload(@Part("bin") TypedFile file);

    @Multipart
    @POST("/fileApi/dp")
    void upload(@Part("bin") TypedFile file, Callback<Response> responseCallback);

}
