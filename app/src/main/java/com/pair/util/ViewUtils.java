package com.pair.util;

import android.view.View;

/**
 * @author Null-Pointer on 9/20/2015.
 */
public class ViewUtils {

    public static boolean isViewVisible(View view) {
        return view != null && view.getVisibility() == View.VISIBLE;
    }

    private static void hideViewInternal(View view) {
        if (isViewVisible(view)) {
            view.setVisibility(View.GONE);
        }
    }

    private static void showViewInternal(View view) {
        if (!isViewVisible(view)) {
            view.setVisibility(View.VISIBLE);
        }
    }

    public static void hideViews(View... views) {
        for (View view : views) {
            hideViewInternal(view);
        }
    }

    public static void showViews(View... views) {
        for (View view : views) {
            showViewInternal(view);
        }
    }
}
