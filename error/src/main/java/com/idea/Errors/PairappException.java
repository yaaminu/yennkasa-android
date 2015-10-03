package com.idea.Errors;

/**
 * @author Null-Pointer on 8/29/2015.
 */
public class PairappException extends Exception {
    private String errorCode;
    public PairappException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
