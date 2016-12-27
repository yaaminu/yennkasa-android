package com.yennkasa.net;

/**
 * Created by Null-Pointer on 10/7/2015.
 */
public class FileClientException extends RuntimeException {

    private final int code;

    public FileClientException(Throwable cause, int code) {
        super(cause);
        this.code = code;
    }

    public FileClientException(String cause, int code) {
        super(cause);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
