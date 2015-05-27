package com.pair.net;

/**
 * Created by Null-Pointer on 5/26/2015.
 */
public class HttpResponse {
    private int status;
    private String message;

    public HttpResponse(int code, String responseString) {
        this.status = code;
        this.message = responseString;
    }

    public int getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
