package com.pair.util;

import android.graphics.Typeface;

import com.rey.material.util.TypefaceUtil;

/**
 * Created by Null-Pointer on 9/27/2015.
 */
public class TypeFaceUtil {

    public static final String PREFIX_ASSET = "asset:";
    public static final String DROID_SERIF_REGULAR_TTF = "DroidSerif-Regular.ttf";
    public static final String ROBOTO_REGULAR_TTF = "Roboto-Regular.ttf";
    public static final String ROBOTO_LIGHT_TTF = "Roboto-Light.ttf";
    public final static String DROID_SERIF_BOLD_TTF = "DroidSerif-Bold.ttf";
    public final static String two_d_font  = "2Dumb.ttf";

    private TypeFaceUtil() {
    }

    static Typeface loadFromAssets(String fontName) {
        return TypefaceUtil.load(Config.getApplicationContext(), PREFIX_ASSET + fontName, Typeface.NORMAL);
    }

    static Typeface loadFromAssets(String fontName, int style) {
        return TypefaceUtil.load(Config.getApplicationContext(), PREFIX_ASSET + fontName, style);
    }

}
