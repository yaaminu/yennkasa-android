package com.pair.util;

import android.graphics.Typeface;

import com.rey.material.util.TypefaceUtil;

/**
 * Created by Null-Pointer on 9/27/2015.
 */
public class TypeFaceUtil {

    public static final String PREFIX_ASSET = "asset:";

    private TypeFaceUtil() {
    }

    public static Typeface loadFromAssets(String fontName) {
        return TypefaceUtil.load(Config.getApplicationContext(), PREFIX_ASSET + fontName, Typeface.NORMAL);
    }

    public static Typeface loadFromAssets(String fontName, int style) {
        return TypefaceUtil.load(Config.getApplicationContext(), PREFIX_ASSET + fontName, style);
    }

    public static Typeface load(String fontName, int style) {
        return TypefaceUtil.load(Config.getApplicationContext(), fontName, style);
    }

    public static Typeface load(String fontName) {
        return TypefaceUtil.load(Config.getApplicationContext(), fontName, Typeface.NORMAL);
    }
}
