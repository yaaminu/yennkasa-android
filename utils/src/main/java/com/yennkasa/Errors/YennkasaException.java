package com.yennkasa.Errors;

/**
 * @author Null-Pointer on 8/29/2015.
 */
public class YennkasaException extends Exception {
    public static final String ERROR_UNKNOWN = "unknown";
    private String errorCode;

    public YennkasaException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public YennkasaException(String message) {
        this(message, ERROR_UNKNOWN);
    }

    public String getErrorCode() {
        return errorCode;
    }
}
