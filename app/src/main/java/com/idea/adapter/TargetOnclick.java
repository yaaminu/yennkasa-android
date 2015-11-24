package com.idea.adapter;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;

import com.idea.util.UiHelpers;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

/**
 * Created by Null-Pointer on 11/20/2015.
 */
class TargetOnclick implements View.OnClickListener, Target {

    private final String peerId;
    private Bitmap image;
    private ImageView iv;

    public TargetOnclick(ImageView iv, String peerId) {
        this.iv = iv;
        this.peerId = peerId;
    }

    @Override
    public void onClick(View v) {
        UiHelpers.gotoProfileActivity(v.getContext(), peerId, image, image);
    }

    @Override
    public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom loadedFrom) {
        image = bitmap;
        iv.setImageBitmap(bitmap);
    }

    @Override
    public void onBitmapFailed(Drawable drawable) {
        iv.setImageDrawable(drawable);
    }

    @Override
    public void onPrepareLoad(Drawable drawable) {
        iv.setImageDrawable(drawable);
    }
}
