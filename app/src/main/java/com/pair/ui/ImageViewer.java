package com.pair.ui;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import com.pair.pairapp.R;
import com.pair.util.ScreenUtility;
import com.pair.util.UiHelpers;
import com.rey.material.widget.SnackBar;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

import java.io.File;

public class ImageViewer extends PairAppActivity {
    private ImageView imageView;
    private Picasso picasso;
    private int WIDTH, HEIGHT;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_image);
        // Show the Up button in the action bar.
        final ScreenUtility utility = new ScreenUtility(this);

        WIDTH = ((int) utility.getPixelsWidth());
        HEIGHT = (int) utility.getPixelsHeight();
        imageView = (android.widget.ImageView) findViewById(R.id.imageView);
        picasso = Picasso.with(this);
        showImage();
    }

    @Override
    protected SnackBar getSnackBar() {
        return ((SnackBar) findViewById(R.id.notification_bar));
    }

    private void showImage() {
        final Uri imageUri = getIntent().getData();
        String path = imageUri.getPath();
        File file = new File(path);
        if (file.exists()) {
            picasso.load(file).skipMemoryCache().resize(WIDTH, (int) (HEIGHT / 2)).into(imageView, callback);
        } else {
            if (imageUri.getScheme().equals("http") || imageUri.getScheme().equals("https") || imageUri.getScheme().equals("ftp")) {
                picasso.load(imageUri.toString()).resize(WIDTH, (int) (HEIGHT / 1.5)).into(imageView, callback);
                return;
            }
            UiHelpers.showErrorDialog(this, getString(R.string.error_failed_to_open_image), listener);
        }
    }

    private UiHelpers.Listener listener = new UiHelpers.Listener() {
        @Override
        public void onClick() {
            finish();
        }
    };
    private final Callback callback = new Callback() {
        @Override
        public void onSuccess() {
            findViewById(R.id.pb_progress).setVisibility(View.GONE);
        }

        @Override
        public void onError() {
            findViewById(R.id.pb_progress).setVisibility(View.GONE);
            UiHelpers.showErrorDialog(ImageViewer.this, getString(R.string.error_failed_to_open_image), listener);
            imageView.setVisibility(View.GONE);
        }
    };

    @Override
    public void onBackPressed() {
        picasso.cancelRequest(imageView);
        super.onBackPressed();
    }
}
