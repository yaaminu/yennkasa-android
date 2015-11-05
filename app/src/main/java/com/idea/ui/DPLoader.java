package com.idea.ui;

import android.content.Context;
import android.text.TextUtils;

import com.idea.data.BuildConfig;
import com.idea.util.PLog;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;

import java.io.File;

/**
 * @author Null-Pointer on 8/31/2015.
 */
public class DPLoader {
    public static final String TAG = DPLoader.class.getSimpleName();

    public static RequestCreator load(Context context, String userDp) {
        if (TextUtils.isEmpty(userDp)) {
            if (BuildConfig.DEBUG) {
                throw new IllegalStateException("userDp == null");
            }
            userDp = "avatar_empty";
        }
        PLog.d(TAG, "userDp: %s", userDp);
        final File dpFile = new File(userDp);
        if (dpFile.exists()) {
            PLog.d(TAG, "userDp: available locally");
            return Picasso.with(context).load(dpFile);
        }
        return Picasso.with(context).load(userDp);
    }

}
