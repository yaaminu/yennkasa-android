package com.pair.pairapp;


import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.pair.data.User;
import com.pair.util.Config;
import com.pair.util.FileHelper;
import com.pair.util.UserManager;

import io.realm.Realm;
import io.realm.RealmChangeListener;


/**
 * A simple {@link Fragment} subclass.
 */
public class ProfileFragment extends Fragment implements RealmChangeListener {

    public static final String ARG_USER_ID = "user_id";
    public static final String TAG = ProfileFragment.class.getSimpleName();
    private static final int PICK_PHOTO_REQUEST = 0x3e9;

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
        realm = Realm.getInstance(getActivity());
        String id = getArguments().getString(ARG_USER_ID);
        user = realm.where(User.class).equalTo("_id", id).findFirst();
        if (user == null) {
            Log.wtf(TAG, "invalid user id passed");
            throw new IllegalStateException("invalid user id");
        }
        final UserManager userManager = UserManager.getInstance(Config.getApplication());
        if (userManager.isMainUser(user)) {
            changeDpButton.setVisibility(View.VISIBLE);
            changeDpButton.setOnClickListener(CHANGE_DP);
            userName.setCompoundDrawables(null, null, getResources().getDrawable(android.R.drawable.ic_menu_edit), null);
        } else {
            changeDpButton.setVisibility(View.GONE);
            userName.setCompoundDrawables(null, null, null, null);
        }
        //common to all
        userName.setText("@" + user.getName());
        userPhone.setText(user.get_id());
        Bitmap bitmap = BitmapFactory.decodeFile(user.getDP());
        if (bitmap != null) {
            changeDpButton.setText("Change");
            displayPicture.setImageBitmap(bitmap);
        } else {
            changeDpButton.setText("Add Picture"); //if its not main user this will be View#GONE
        }

        //asynchronously check online refresh user details
        userManager.refreshUserDetails(user);
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
        final Bitmap bitmap = BitmapFactory.decodeFile(user.getDP());
        if (bitmap != null) {
            displayPicture.setImageBitmap(bitmap);
        }
        userName.setText(user.getName());
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
                if (uri.getScheme().equals("content")) {
                    filePath = FileHelper.resolveContentUriToFilePath(uri);
                } else {
                    filePath = uri.getPath();
                }
                displayPicture.setImageBitmap(BitmapFactory.decodeFile(filePath));
                UserManager userManager = UserManager.getInstance(getActivity());
                userManager.changeDp(filePath, DP_CALLBACK);
            } else {
                showToast(R.string.error_canceled);
            }
        }
    }

    private void showToast(int message) {
        Toast toast = Toast.makeText(getActivity(), message, Toast.LENGTH_LONG);
        toast.setGravity(Gravity.CENTER, 0, 0);
        toast.show();
    }

    private final UserManager.DpChangeCallback DP_CALLBACK = new UserManager.DpChangeCallback() {
        @Override
        public void done(Exception e) {
            if (e == null) {
                //success
                showToast(R.string.dp_changed_successfully);
            } else {
                showToast(R.string.error_occured);
            }
        }
    };

    @Override
    public void onDestroy() {
        realm.close();
        super.onDestroy();
    }
}
