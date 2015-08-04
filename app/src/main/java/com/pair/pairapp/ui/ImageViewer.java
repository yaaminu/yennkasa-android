package com.pair.pairapp.ui;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageView;

import com.pair.pairapp.R;
import com.squareup.picasso.Picasso;

public class ImageViewer extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_image);
        // Show the Up button in the action bar.

        ImageView imageView = (android.widget.ImageView) findViewById(R.id.imageView);
        Uri imageUri = getIntent().getData();
        Picasso.with(this)
                .load(imageUri.toString())
                .placeholder(R.drawable.avatar_empty)
                .into(imageView);
    }


}
