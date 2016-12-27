package com.yennkasa.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;

import com.yennkasa.data.BuildConfig;
import com.yennkasa.util.PLog;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;

import java.io.File;

/**
 * @author Null-Pointer on 8/31/2015.
 */
public class ImageLoader {
    public static final String TAG = ImageLoader.class.getSimpleName();

    public static RequestCreator load(Context context, String imagePath) {
        return load(context, -1, -1, imagePath);
    }

    public static RequestCreator load(Context context, String imagePath, Drawable placeHolder, Drawable error) {
        return load(context, -1, -1, imagePath, placeHolder, error);
    }

    public static RequestCreator load(Context context, int width, int height, String imagePath, Drawable placeHolder, Drawable error) {
        if (TextUtils.isEmpty(imagePath)) {
            if (BuildConfig.DEBUG) {
                throw new IllegalStateException("imagePath == null");
            }
            imagePath = "avatar_empty";
        }
        PLog.d(TAG, "imagePath: %s", imagePath);
        // TODO: 11/5/2015 set up our own PicassoDownloader
        File dpFile = new File(imagePath);
        RequestCreator creator;
        if (dpFile.exists()) {
            creator = Picasso.with(context).load(dpFile);
        } else {
            creator = Picasso.with(context).load(imagePath);
        }

        if (placeHolder != null) {
            creator.placeholder(placeHolder);
        }
        if (error != null) {
            creator.error(error);
        }

        if (width > 0 && height > 0) {
            PLog.v(TAG, "resizing to w:%s , h:%s", width, height);
            creator.resize(width, height).onlyScaleDown();
        }

        return creator;
    }

    public static RequestCreator load(Context context, int width, int height, String imagePath) {
        return load(context, width, height, imagePath, null, null);
    }

//    public static RequestCreator load(Context context, String imagePath, boolean resize) {
//        return load(context, resize ? R.dimen.thumbnail_width : -1, resize ? R.dimen.thumbnail_height : -1, imagePath);
//    }


}
