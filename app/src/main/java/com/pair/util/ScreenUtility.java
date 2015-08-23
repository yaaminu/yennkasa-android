package com.pair.util;

import android.app.Activity;
import android.util.DisplayMetrics;
import android.view.Display;

public class ScreenUtility {

    private Activity activity;
    private float dpWidth, dpHeight, pixelsHeight, pixelsWidth;

    public ScreenUtility(Activity activity) {
        this.activity = activity;

        Display display = activity.getWindowManager().getDefaultDisplay();
        DisplayMetrics outMetrics = new DisplayMetrics();
        display.getMetrics(outMetrics);

        float density = activity.getResources().getDisplayMetrics().density;
        dpHeight = outMetrics.heightPixels / density;
        dpWidth = outMetrics.widthPixels / density;
        pixelsHeight = outMetrics.heightPixels;
        pixelsWidth = outMetrics.widthPixels;
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
