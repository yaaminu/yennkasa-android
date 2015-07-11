package com.pair.pairapp.ui;


import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.pair.data.User;
import com.pair.pairapp.R;
import com.pair.util.FileHelper;
import com.pair.util.UiHelpers;
import com.pair.util.UserManager;

import java.io.File;

import io.realm.Realm;
import io.realm.RealmChangeListener;
import io.realm.RealmResults;


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
    private TextView userName, userPhone, listHeading;
    private Button changeDpButton;
    private ListView membersOrMutualGroupsList;
    private User user;
    private Realm realm;
    private BaseAdapter membersAdapter;

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
        listHeading = ((TextView) view.findViewById(R.id.tv_list_heading));
        membersOrMutualGroupsList = ((ListView) view.findViewById(R.id.lv_members_list));


        realm = Realm.getInstance(getActivity());
        realm.addChangeListener(this);
        String id = getArguments().getString(ARG_USER_ID);
        user = realm.where(User.class).equalTo("_id", id).findFirst();

        if (user == null) {
            Log.wtf(TAG, "invalid user id passed");
            throw new IllegalArgumentException("invalid user id");
        }
        final UserManager userManager = UserManager.INSTANCE;

        //common to all
        userName.setText("@" + user.getName());
        displayPicture.setOnClickListener(ONDPCLICKED);
        UiHelpers.loadImageIntoIv(new File(user.getDP()), displayPicture);
        if (userManager.isMainUser(user) || userManager.isAdmin(user.get_id(), userManager.getMainUser().get_id())) {
            changeDpButton.setVisibility(View.VISIBLE);
            imageButton.setVisibility(View.VISIBLE);
            imageButton.setOnClickListener(CHANGE_USERNAME);
            changeDpButton.setOnClickListener(CHANGE_DP);
        } else {
            imageButton.setVisibility(View.GONE);
            changeDpButton.setVisibility(View.GONE);
        }

        if (user.getType() == User.TYPE_GROUP) {
            setUpViewsGroupWay();
        } else {
            setUpViewSingleUserWay();
        }
        userManager.refreshUserDetails(user.get_id()); //async
        return view;
    }

    private void setUpViewSingleUserWay() {
        userPhone.setVisibility(View.VISIBLE);
        userPhone.append(user.get_id());
        listHeading.setText(R.string.st_mutual_groups);
        final RealmResults<User> tmp = realm.where(User.class).equalTo("members._id", user.get_id()).findAll();
        User[] results = realmResultsToArray(tmp);
        membersAdapter = new GroupsOrMembersAdapter(getActivity(), results);
        membersOrMutualGroupsList.setAdapter(membersAdapter);
    }

    @NonNull
    private User[] realmResultsToArray(RealmResults<User> tmp) {
        User[] results = new User[tmp.size()];
        tmp.toArray(results);
        return results;
    }

    private void setUpViewsGroupWay() {
        userPhone.setVisibility(View.GONE);
        listHeading.setText(R.string.st_group_members);
        User[] results = new User[user.getMembers().size()];
        user.getMembers().toArray(results);
        membersAdapter = new GroupsOrMembersAdapter(getActivity(), results);
        membersOrMutualGroupsList.setAdapter(membersAdapter);
    }

    private void choosePicture() {
        Intent attachIntent;
        attachIntent = new Intent(Intent.ACTION_GET_CONTENT);
        attachIntent.setType("image/*");
        startActivityForResult(attachIntent, PICK_PHOTO_REQUEST);
    }

    @Override
    public void onChange() {
        if (!isResumed()) //fragment not in layout
            return;
        userName.setText("@" + user.getName());
        UiHelpers.loadImageIntoIv(new File(user.getDP()), displayPicture);
        //todo probably change status too and last activity
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
                //check if we should really change the dp sometimes the user may pick the same
                //file so we have to just tell the user everything is ok. but we will not make a call to our backend
                if (user.getDP().equals(filePath)) {
                    UiHelpers.showErrorDialog(getActivity(), getResources().getString(R.string.st_choose_a_different_image));
                    return;
                }
                UiHelpers.loadImageIntoIv(new File(user.getDP()), displayPicture);
                UserManager userManager = UserManager.INSTANCE;
                dpChangeProgress = new ProgressDialog(getActivity());
                dpChangeProgress.setMessage(getResources().getString(R.string.st_please_wait));
                dpChangeProgress.setCancelable(false);
                dpChangeProgress.show();
                userManager.changeDp(filePath, DP_CALLBACK);

            }
        }
    }

    private ProgressDialog dpChangeProgress;
    private final UserManager.CallBack DP_CALLBACK = new UserManager.CallBack() {
        @Override
        public void done(Exception e) {
            dpChangeProgress.dismiss();
            if (e == null) {
                UiHelpers.showToast(getActivity().getString(R.string.st_success));
            } else {
                UiHelpers.showErrorDialog(getActivity(), e.getMessage());
            }
        }
    };

    private final View.OnClickListener CHANGE_USERNAME = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            UiHelpers.showToast("not implemented");
        }
    };

    @Override
    public void onDestroy() {
        realm.removeChangeListener(this);
        realm.close();
        super.onDestroy();
    }

    private class GroupsOrMembersAdapter extends ArrayAdapter<User> {

        public GroupsOrMembersAdapter(Context context, User[] users) {
            super(context, android.R.layout.simple_list_item_1, users);

        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_1, parent, false);
            }
            ((TextView) convertView).setText(getItem(position).getName());
            return convertView;
        }
    }

}
