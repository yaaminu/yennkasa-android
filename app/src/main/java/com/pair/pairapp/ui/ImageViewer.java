package com.pair.pairapp.ui;

import android.app.Activity;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import com.pair.pairapp.R;
import com.pair.util.UiHelpers;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

import java.io.File;

public class ImageViewer extends Activity {
    private ImageView imageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_image);
        // Show the Up button in the action bar.
        imageView = (android.widget.ImageView) findViewById(R.id.imageView);
        final Uri imageUri = getIntent().getData();
        File file = new File(imageUri.toString());
        if (file.exists()) {
            Picasso.with(this).load(file).into(imageView, callback);
        } else {
            Picasso.with(this).load(imageUri.toString()).into(imageView, callback);
        }
    }

    private final Callback callback = new Callback() {
        @Override
        public void onSuccess() {
            findViewById(R.id.pb_progress).setVisibility(View.GONE);
        }

        @Override
        public void onError() {
            findViewById(R.id.pb_progress).setVisibility(View.GONE);
            UiHelpers.showErrorDialog(imageView.getContext(), "Sorry! Failed to open image", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    finish();
                }
            });
            imageView.setVisibility(View.GONE);
        }
    };
}
