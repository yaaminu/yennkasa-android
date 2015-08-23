package com.pair.pairapp.ui;

import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.View;
import android.widget.ImageView;

import com.pair.pairapp.R;
import com.pair.util.UiHelpers;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

import java.io.File;

public class ImageViewer extends ActionBarActivity {
    private ImageView imageView;
    private Picasso picasso;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_image);
        // Show the Up button in the action bar.
        imageView = (android.widget.ImageView) findViewById(R.id.imageView);
        picasso = Picasso.with(this);
        showImage();
    }

    private void showImage() {
        final Uri imageUri = getIntent().getData();
        String path = imageUri.getPath();
        File file = new File(path);
        if (file.exists()) {
            picasso.load(file).into(imageView, callback);
        } else {
            if (imageUri.getScheme().equals("http") || imageUri.getScheme().equals("https") || imageUri.getScheme().equals("ftp")) {
                picasso.load(imageUri.toString()).into(imageView, callback);
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
