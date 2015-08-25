package com.pair.util;

import android.app.Activity;
import android.util.DisplayMetrics;
import android.view.Display;

public class ScreenUtility {

    private float dpWidth, dpHeight, pixelsHeight, pixelsWidth, density;

    public ScreenUtility(Activity activity) {
        Display display = activity.getWindowManager().getDefaultDisplay();
        DisplayMetrics outMetrics = new DisplayMetrics();
        display.getMetrics(outMetrics);

        density = activity.getResources().getDisplayMetrics().density;

        pixelsHeight = outMetrics.heightPixels;
        pixelsWidth = outMetrics.widthPixels;

        dpHeight = pixelsHeight / density;
        dpWidth = pixelsWidth / density;
    }

    public float dpToPixels(float dp) {
        return dp * density;
    }

    public float pixelsToDp(float pixels) {
        return pixels / density;
    }

    public float getPixelsHeight() {
        return pixelsHeight;
    }

    public float getPixelsWidth() {
        return pixelsWidth;
    }

    public float getDpHeight() {
        return dpHeight;
    }

    public float getDpWidth() {
        return dpWidth;
    }

    public float getWidth() {
        return dpWidth;
    }

    public float getHeight() {
        return dpHeight;
    }

}
