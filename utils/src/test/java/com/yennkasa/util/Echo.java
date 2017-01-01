package com.yennkasa.util;

/**
 * author Null-Pointer on 1/20/2016.
 */
public class Echo {

    public static void echo(String message, Object... args) {
        System.out.println(String.format(message, args));
    }
}
