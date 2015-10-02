package com.pair.ui;


import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.DrawableRes;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.pair.Errors.ErrorCenter;
import com.pair.pairapp.R;
import com.pair.util.Config;
import com.pair.util.FileUtils;
import com.pair.util.MediaUtils;
import com.pair.util.PLog;
import com.pair.util.SimpleDateUtil;
import com.pair.util.TypeFaceUtil;
import com.pair.util.UiHelpers;
import com.pair.util.ViewUtils;
import com.rey.material.util.TypefaceUtil;
import com.rey.material.util.ViewUtil;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import java.io.File;

/**
 * A simple {@link Fragment} subclass.
 */
public class ChooseDisplayPictureFragment extends Fragment {


    public static final int TAKE_PHOTO_REQUEST = 1001;
    public static final int PICK_PHOTO_REQUEST = 1002;
    public static final String TAG = ChooseDisplayPictureFragment.class.getSimpleName();
    private static String dp;
    private Callbacks callback;
    private TextView previewLabel;
    private Picasso picasso;
    private ImageView displayPicture;
    private Uri outPutUri;
    private final View.OnClickListener listener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.bt_pick_photo_change_dp:
                    changeDp(0);
                    break;
                case R.id.bt_take_photo_change_dp:
                    changeDp(1);
                    break;
                case R.id.riv_group_avatar_preview:
                    viewImage();
                    break;
                case R.id.choose_dp_later:
                    callback.onCancelled();
                    break;
                default:
                    throw new AssertionError();
            }
        }

        private void viewImage() {

            if (dp != null) {
                final File dpFile = new File(dp);
                if (dpFile.exists()) {
                    Intent intent = new Intent(getActivity(), ImageViewer.class);
                    intent.setData(Uri.fromFile(dpFile));
                    startActivity(intent);
                }
            }
        }

        private void changeDp(int i) {
            Intent intent = new Intent();
            if (i == 0) {
                intent.setAction(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                startActivityForResult(intent, PICK_PHOTO_REQUEST);
            } else if (i == 1) {
                outPutUri = Uri.fromFile(new File(Config.getAppProfilePicsBaseDir(),
                        SimpleDateUtil.timeStampNow() + ".jpg"));
                MediaUtils.takePhoto(ChooseDisplayPictureFragment.this,
                        outPutUri, TAKE_PHOTO_REQUEST);
            }
        }
    };
    private CharSequence noDpNotice;
    Target target = new Target() {

        @Override
        public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom loadedFrom) {
            PLog.d(TAG, "loaded");
            if (bitmap.getHeight() == 0) {
                ErrorCenter.reportError(TAG, getString(R.string.error_failed_to_open_image));
                displayPicture.setImageResource(placeHolderDp);
            } else {
                displayPicture.setImageBitmap(bitmap);
                UiHelpers.showErrorDialog(((PairAppBaseActivity) getActivity()), getString(R.string.dp_prompt),
                        getString(R.string.yes), getString(R.string.no), new UiHelpers.Listener() {
                            @Override
                            public void onClick() {
                                previewLabel.setText("");
                                callback.onDp(dp);
                            }
                        }, new UiHelpers.Listener() {
                            @Override
                            public void onClick() {
                                previewLabel.setText(noDpNotice);
                                displayPicture.setImageResource(placeHolderDp);
                            }
                        });
            }
        }

        @Override
        public void onBitmapFailed(Drawable drawable) {
            PLog.d(TAG, "failed");
            previewLabel.setText(noDpNotice);
            displayPicture.setImageDrawable(drawable);
        }

        @Override
        public void onPrepareLoad(Drawable drawable) {
            PLog.d(TAG, "before load");
            displayPicture.setImageDrawable(drawable);
            previewLabel.setText(R.string.loading);
        }
    };
    private boolean dpShown = false;
    private int placeHolderDp;


    public ChooseDisplayPictureFragment() {
        // Required empty public constructor
    }

    @Override
    public void onAttach(Context activity) {
        super.onAttach(activity);
        setRetainInstance(true);
        try {
            callback = (Callbacks) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException("Activity" + activity.getClass().getSimpleName() + " must implement interface" + Callbacks.class.getName());
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_choose_display_picture, container, false);
        previewLabel = ((TextView) view.findViewById(R.id.tv_dp_preview_label));
        ViewUtils.setTypeface(previewLabel, TypeFaceUtil.ROBOTO_REGULAR_TTF);

        TextView takePhotoButton =(TextView) view.findViewById(R.id.bt_take_photo_change_dp);
        takePhotoButton.setOnClickListener(listener);
        ViewUtils.setTypeface(takePhotoButton, TypeFaceUtil.ROBOTO_REGULAR_TTF);

        TextView pickPhotobutton = (TextView) view.findViewById(R.id.bt_pick_photo_change_dp);
        pickPhotobutton.setOnClickListener(listener);
        ViewUtils.setTypeface(pickPhotobutton, TypeFaceUtil.DROID_SERIF_REGULAR_TTF);

        displayPicture = ((ImageView) view.findViewById(R.id.riv_group_avatar_preview));
        final View cancelButton = view.findViewById(R.id.choose_dp_later);
        ViewUtils.setTypeface((TextView) cancelButton,TypeFaceUtil.ROBOTO_REGULAR_TTF);

        if (!callback.allowCancelling()) {
            cancelButton.setVisibility(View.GONE);
        } else {
            cancelButton.setVisibility(View.VISIBLE);
            cancelButton.setOnClickListener(listener);
        }

        dp = callback.defaultDp();
        placeHolderDp = callback.placeHolderDp();
        if (placeHolderDp == 0) {
            throw new IllegalArgumentException("invalid drawable resource");
        }
        noDpNotice = callback.noDpNotice() != null ? callback.noDpNotice() : getString(R.string.pick_an_optional_dp);
        previewLabel.setText(noDpNotice);
        displayPicture.setOnClickListener(listener);
        picasso = Picasso.with(getActivity());
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadDp();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        dpShown = false;
        boolean dpChanged = false;
        String newDp = null;
        if (resultCode == Activity.RESULT_OK) {
            switch (requestCode) {
                case PICK_PHOTO_REQUEST:
                    Uri uri = data.getData();
                    if (uri != null) {
                        newDp = FileUtils.resolveContentUriToFilePath(uri);
                        if (!MediaUtils.isImage(newDp)) {
                            ErrorCenter.reportError(TAG, getString(R.string.not_a_bitmap));
                        } else {
                            dpChanged = true;
                        }
                    }
                    break;
                case TAKE_PHOTO_REQUEST:
                    if (outPutUri != null) {
                        //noinspection ConstantConditions
                        newDp = outPutUri.getPath();
                        if (newDp != null && new File(newDp).exists()) {
                            newDp = outPutUri.getPath();
                            if (!MediaUtils.isImage(newDp)) {
                                ErrorCenter.reportError(TAG, getString(R.string.not_a_bitmap));
                            } else {
                                dpChanged = true;
                            }
                        }

                    }
                    break;
            }
        }
        if (newDp != null && new File(newDp).exists() && new File(newDp).length() > 8 * FileUtils.ONE_MB) {
            ErrorCenter.reportError(TAG, getString(R.string.image_size_too_large));
        } else {
            dpChanged = true;
            dp = newDp;
        }
        if (dpChanged) {
            loadDp();
        }
    }

    private void loadDp() {
        if (dp != null && !dpShown) {
            if (dp.startsWith("http")) {
                dpShown = true;
                picasso.load(dp)
                        .placeholder(placeHolderDp)
                        .error(placeHolderDp)
                        .into(target);
            } else {
                File dpFile = new File(dp);
                if (dpFile.exists()) {
                    dpShown = true;
                    picasso.load(dpFile)
                            .placeholder(placeHolderDp)
                            .error(placeHolderDp)
                            .into(target);
                }
            }
//            BitmapFactory.Options options = new BitmapFactory.Options();
//            options.outHeight = R.dimen.dp_thumbnail_height_large;
//            options.outWidth = R.dimen.dp_thumbnail_width_large;
//            displayPicture.setImageBitmap(BitmapFactory.decodeFile(dp,options));
        }
    }

    public interface Callbacks {
        void onDp(String newDp);

        void onCancelled();

        boolean allowCancelling();

        CharSequence noDpNotice();

        String defaultDp();

        @DrawableRes
        int placeHolderDp();
    }

}
