package com.pair.pairapp;


import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.pair.data.User;
import com.pair.util.Config;
import com.pair.util.UserManager;

import io.realm.Realm;


/**
 * A simple {@link Fragment} subclass.
 */
public class ProfileFragment extends Fragment {

    public static final String ARG_USER_ID = "user_id";
    public static final String TAG = ProfileFragment.class.getSimpleName();

    private ImageView displayPicture;
    private TextView userName, userPhone;
    private Button changeDpButton;
    private User user;

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
        Realm realm = Realm.getInstance(getActivity());
        String id = getArguments().getString(ARG_USER_ID);
        user = realm.where(User.class).equalTo("_id", id).findFirst();
        if (user == null) {
            Log.wtf(TAG, "invalid user id passed");
            throw new IllegalStateException("invalid user id");
        }
        final UserManager userManager = UserManager.getInstance(Config.getApplication());
        if (userManager.isMainUser(user)) {
            changeDpButton.setVisibility(View.VISIBLE);
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

        //asynchronously check online for user's DP.
        userManager.refreshUserDetails(user, refreshCallback);
        return view;
    }

    private UserManager.RefreshCallback refreshCallback = new UserManager.RefreshCallback() {
        @Override
        public void done(Exception e) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                 /* TODO: 6/23/2015 the developers of realm claim that all realm objects are updated whenever a
                 change is committed so  no need to run the query again
                 */
                    displayPicture.setImageBitmap(BitmapFactory.decodeFile(user.getDP()));
                    userName.setText(user.getName());
                    //probably change status too
                }
            });
        }
    };
}
