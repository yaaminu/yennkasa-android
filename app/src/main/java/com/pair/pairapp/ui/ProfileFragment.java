package com.pair.pairapp.ui;


import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.pair.data.User;
import com.pair.pairapp.BuildConfig;
import com.pair.pairapp.R;
import com.pair.util.Config;
import com.pair.util.FileHelper;
import com.pair.util.ImageResizer;
import com.pair.util.UiHelpers;
import com.pair.util.UserManager;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import io.realm.Realm;
import io.realm.RealmChangeListener;


/**
 * A simple {@link Fragment} subclass.
 */
public class ProfileFragment extends Fragment implements RealmChangeListener {

    public static final String ARG_USER_ID = "user_id";
    public static final String TAG = ProfileFragment.class.getSimpleName();
    private static final int PICK_PHOTO_REQUEST = 0x3e9;
    private final View.OnClickListener ONDPCLICKED = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            final File file = new File(user.getDP());
            if (file.exists()) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(Uri.fromFile(file), "image/*");
                startActivity(intent);
            }
        }
    };
    private ImageView displayPicture;
    private TextView userName, userPhone;
    private Button changeDpButton;
    private User user;
    private Realm realm;

    public ProfileFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_profile, container, false);
        displayPicture = ((ImageView) view.findViewById(R.id.iv_display_picture));
        userName = ((TextView) view.findViewById(R.id.tv_user_name));
        userPhone = ((TextView) view.findViewById(R.id.tv_user_phone));
        changeDpButton = ((Button) view.findViewById(R.id.bt_change_dp));
        ImageButton imageButton = (ImageButton) view.findViewById(R.id.ib_change_name);
        realm = Realm.getInstance(getActivity());
        realm.addChangeListener(this);
        String id = getArguments().getString(ARG_USER_ID);
        user = realm.where(User.class).equalTo("_id", id).findFirst();
        if (user == null) {
            Log.wtf(TAG, "invalid user id passed");
            throw new IllegalArgumentException("invalid user id");
        }
        final UserManager userManager = UserManager.getInstance(Config.getApplication());
        if (userManager.isMainUser(user)) {
            changeDpButton.setVisibility(View.VISIBLE);
            imageButton.setVisibility(View.VISIBLE);
            imageButton.setOnClickListener(CHANGE_USERNAME);
            changeDpButton.setOnClickListener(CHANGE_DP);
        } else {
            imageButton.setVisibility(View.GONE);
            changeDpButton.setVisibility(View.GONE);
        }
        //common to all
        userName.setText("@" + user.getName());
        userPhone.setText(user.get_id());
        Bitmap bitmap = getDp(user.getDP());
        if (bitmap != null) {
            changeDpButton.setText(R.string.change_dp);
            displayPicture.setImageBitmap(bitmap);
        } else {
            changeDpButton.setText(R.string.add_picture); //if its not main user this will be View#GONE
        }
        displayPicture.setOnClickListener(ONDPCLICKED);
        //asynchronously check online refresh user details
        userManager.refreshUserDetails(user.get_id());
        return view;
    }

    private void choosePicture() {
        Intent attachIntent;
        attachIntent = new Intent(Intent.ACTION_GET_CONTENT);
        attachIntent.setType("image/*");
        startActivityForResult(attachIntent, PICK_PHOTO_REQUEST);
    }

    @Override
    public void onChange() {
        final Bitmap bitmap = getDp(user.getDP());
        if (bitmap != null) {
            displayPicture.setImageBitmap(bitmap);
        }
        userName.setText("@" + user.getName());
        //probably change status too and last activity
    }

    private View.OnClickListener CHANGE_DP = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            choosePicture();
        }
    };

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PICK_PHOTO_REQUEST) {
            if (resultCode == Activity.RESULT_OK) {
                String filePath;
                Uri uri = data.getData();
                // TODO: 6/24/2015 check if we should really change the dp sometimes the user may pick the same  
                //file so we have to just tell the user everything is ok. but we will not make a call to our backend
                if (uri.getScheme().equals("content")) {
                    filePath = FileHelper.resolveContentUriToFilePath(uri);
                } else {
                    filePath = uri.getPath();
                }
                Bitmap bitmap = getDp(filePath);
                if (bitmap == null) return;
                displayPicture.setImageBitmap(null);
                displayPicture.setImageBitmap(bitmap);
                UserManager userManager = UserManager.getInstance(getActivity());
                userManager.changeDp(filePath, DP_CALLBACK);

            }
        }
    }

    @Nullable
    private Bitmap getDp(String filePath) {
        Log.i(TAG, "dp path; " + filePath);
        byte[] imageBytes;
        try {
            imageBytes = IOUtils.toByteArray(new FileInputStream(filePath));
        } catch (IOException e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, e.getMessage(), e.getCause());
            } else {
                Log.e(TAG, e.getMessage());
            }
            return null;
        }
        //FIXME calculate the width and height based on screen size an density
        Bitmap bitmap = ImageResizer.resizeImage(imageBytes, 600, 350);
        if (bitmap == null) {
            UiHelpers.showErrorDialog(getActivity(), getActivity().getString(R.string.error_invalid_bitmap_file));
        }
        return bitmap;
    }

//    private void showToast(int message) {
//        Toast toast = Toast.makeText(Config.getApplicationContext(), message, Toast.LENGTH_LONG);
//        toast.setGravity(Gravity.CENTER, 0, 0);
//        toast.show();
//    }

    private final UserManager.CallBack DP_CALLBACK = new UserManager.CallBack() {
        @Override
        public void done(Exception e) {

        }
    };

    private final View.OnClickListener CHANGE_USERNAME = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Toast.makeText(Config.getApplicationContext(), "not implemented", Toast.LENGTH_LONG).show();
        }
    };

    @Override
    public void onDestroy() {
        realm.removeChangeListener(this);
        realm.close();
        super.onDestroy();
    }
}
