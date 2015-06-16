package com.pair.util;

import android.net.Uri;

/**
 * @author Null-Pointer on 6/16/2015.
 */
public class FileHelper {
    public static final int MEDIA_TYPE_IMAGE = 0x0;
    public static final int MEDIA_TYPE_VIDEO = 0x1;

    public static Uri getOutputUri(int mediaType) {
        Uri uri = null;
        switch (mediaType) {
            case MEDIA_TYPE_IMAGE:

                break;
            case MEDIA_TYPE_VIDEO:
                throw new UnsupportedOperationException("not yet supported");
            default:
                break;
        }
        return uri;
    }
}
