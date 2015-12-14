package com.pairapp.ui;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import com.pairapp.R;
import com.pairapp.util.UiHelpers;
import com.rey.material.widget.SnackBar;
import com.squareup.picasso.Callback;

import java.io.File;

import uk.co.senab.photoview.PhotoViewAttacher;

public class ImageViewer extends PairAppActivity {
    private ImageView imageView;
    private PhotoViewAttacher attacher;

    public static final String EXTRA_TARGET_BITMAP = "IMAGEVIEW.TARGET_BITMAP";
    public static final String EXTRA_PLACEHOLDER = "placeHolder";
    public static final String EXTRA_ERROR = "placeHolder";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_image);
        // Show the Up button in the action bar.
        imageView = (android.widget.ImageView) findViewById(R.id.imageView);
        showImage();
    }

    @Override
    protected SnackBar getSnackBar() {
        return ((SnackBar) findViewById(R.id.notification_bar));
    }

    private void showImage() {
        final Uri imageUri = getIntent().getData();
        Bitmap image1 = getIntent().getParcelableExtra(EXTRA_PLACEHOLDER),
                image2 = getIntent().getParcelableExtra(EXTRA_ERROR);
        if (imageUri == null) {
            throw new RuntimeException("image viewer needs a uri or a bitmap");
        }

        String path;
        path = imageUri.getPath();
        File file = new File(path);
        if (file.exists()) {
            path = file.getAbsolutePath();
        } else if (imageUri.getScheme().equals("http") || imageUri.getScheme().equals("https") || imageUri.getScheme().equals("ftp")) {
            path = imageUri.toString();
        } else {
            callback.onError();
            return;
        }
        Drawable placeHolder = null;
        Drawable errorDrawable = null;
        if (image1 != null) {
            placeHolder = new BitmapDrawable(getResources(), image1);
        }
        if (image2 != null) {
            errorDrawable = new BitmapDrawable(getResources(), image2);
        }
        ImageLoader.load(this, path, placeHolder, errorDrawable).noFade().into(imageView, callback);
    }


    private UiHelpers.Listener listener = new UiHelpers.Listener() {
        @Override
        public void onClick() {
            finish();
        }
    };
    boolean errorOccurred = false;
    private final Callback callback = new Callback() {
        @Override
        public void onSuccess() {
            setUpImageView();
        }

        @Override
        public void onError() {
            errorOccurred = true;
            findViewById(R.id.pb_progress).setVisibility(View.GONE);
            UiHelpers.showErrorDialog(ImageViewer.this, getString(R.string.error_failed_to_open_image), listener);
            imageView.setVisibility(View.GONE);
        }
    };

    private void setUpImageView() {
        findViewById(R.id.pb_progress).setVisibility(View.GONE);
        attacher = new PhotoViewAttacher(imageView, true);
    }

    @Override
    public void onBackPressed() {
        if (errorOccurred) {
            finish();
        } else if (attacher != null && attacher.getScale() == attacher.getMaximumScale()) {
            attacher.setScale(attacher.getMediumScale(), true);
        } else if (attacher != null && attacher.getScale() == attacher.getMediumScale()) {
            attacher.update();
            attacher.cleanup();
            attacher = new PhotoViewAttacher(imageView, true);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected int getSnackBarStyle() {
        return R.style.snackbar_white;
    }
}
