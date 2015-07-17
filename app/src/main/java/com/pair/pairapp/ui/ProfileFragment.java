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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.pair.data.Message;
import com.pair.data.User;
import com.pair.pairapp.MainActivity;
import com.pair.pairapp.R;
import com.pair.pairapp.UsersActivity;
import com.pair.util.Config;
import com.pair.util.FileHelper;
import com.pair.util.UiHelpers;
import com.pair.util.UserManager;
import com.squareup.picasso.Picasso;

import io.realm.Realm;
import io.realm.RealmChangeListener;
import io.realm.RealmResults;


/**
 * A simple {@link Fragment} subclass.
 */
@SuppressWarnings("FieldCanBeLocal")
public class ProfileFragment extends Fragment implements RealmChangeListener {

    public static final String ARG_USER_ID = "user_id";
    public static final String TAG = ProfileFragment.class.getSimpleName();
    private static final int PICK_PHOTO_REQUEST = 0x3e9;
    @SuppressWarnings("SpellCheckingInspection")
    private final View.OnClickListener ONDPCLICKED = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            // TODO: 7/15/2015 fix this
        }
    };
    private ImageView displayPicture;
    private TextView userName, userPhone, listHeading;
    private Button changeDpButton;
    private ListView mutualGroupsList;
    private User user;
    private Realm realm;
    private BaseAdapter membersAdapter;

    public ProfileFragment() {
        // Required empty public constructor
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_profile, container, false);
        displayPicture = ((ImageView) view.findViewById(R.id.iv_display_picture));
        userName = ((TextView) view.findViewById(R.id.tv_user_name));
        userPhone = ((TextView) view.findViewById(R.id.tv_user_phone));
        changeDpButton = ((Button) view.findViewById(R.id.bt_change_dp));
        ImageButton imageButton = (ImageButton) view.findViewById(R.id.ib_change_name);
        Button sendMessageButton = (Button) view.findViewById(R.id.bt_message);
        sendMessageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UiHelpers.enterChatRoom(getActivity(), user.get_id());
            }
        });
        listHeading = ((TextView) view.findViewById(R.id.tv_list_heading));
        mutualGroupsList = ((ListView) view.findViewById(R.id.lv_mutual_groups_list));
        //end view hookup

        realm = Realm.getInstance(getActivity());
        String id = getArguments().getString(ARG_USER_ID);
        user = realm.where(User.class).equalTo(User.FIELD_ID, id).findFirst();

        if (user == null || UserManager.INSTANCE.isMainUser(id)) {
            Log.wtf(TAG, "invalid user id. program aborting");
            throw new IllegalArgumentException("invalid user id");
        }
        final UserManager userManager = UserManager.INSTANCE;

        //common to all
        userName.setText("@" + user.getName());
        displayPicture.setOnClickListener(ONDPCLICKED);
        if (userManager.isGroup(user.get_id()) && userManager.isAdmin(user.get_id(), userManager.getMainUser().get_id())) {
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

    @Override
    public void onResume() {
        realm.addChangeListener(this);
        showDp();
        super.onResume();
    }

    @Override
    public void onPause() {
        realm.removeChangeListener(this);
        super.onPause();
    }

    private void setUpViewSingleUserWay() {
        userPhone.setVisibility(View.VISIBLE);
        userPhone.append(user.get_id());
        listHeading.setText(R.string.st_mutual_groups);
        setUpMutualMembers();
    }

    private void setUpMutualMembers() {
        final RealmResults<User> tmp = realm.where(User.class).equalTo(Message.FIELD_TYPE, User.TYPE_GROUP).equalTo("members._id", user.get_id()).findAll();
        // TODO: 7/12/2015 this might not scale!
        resetAdapter(realmResultsToArray(tmp));
    }

    private void resetAdapter(User[] results) {
        membersAdapter = new GroupsOrMembersAdapter(getActivity(), results);
        mutualGroupsList.setAdapter(membersAdapter);
        mutualGroupsList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                UiHelpers.gotoProfileActivity(getActivity(), ((User) view.getTag()).get_id());
            }
        });
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
        listHeading.setClickable(true);
        listHeading.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Bundle bundle = new Bundle(3);
                bundle.putString(MainActivity.ARG_TITLE, user.getName() + getResources().getString(R.string.st_group_members_title));
                bundle.putString(UsersFragment.ARG_GROUP_ID, user.get_id());
                bundle.putString(UsersFragment.ARG_ACTION, UsersFragment.ARG_SHOW_GROUP_MEMBERS);
                Intent intent = new Intent(getActivity(), UsersActivity.class);
                intent.putExtras(bundle);
                startActivity(intent);
            }
        });
    }


    private void choosePicture() {
        Intent attachIntent;
        attachIntent = new Intent(Intent.ACTION_GET_CONTENT);
        attachIntent.setType("image/*");
        startActivityForResult(attachIntent, PICK_PHOTO_REQUEST);
    }

    @Override
    public void onChange() {
        try {
            userName.setText("@" + user.getName());
            if (!UserManager.INSTANCE.isGroup(user.get_id())) {
                setUpMutualMembers();
            }
            showDp();
            //todo probably change status too and last activity
        } catch (Exception lateUpdate) {//fragment no more in layout
            Log.e(TAG, lateUpdate.getMessage(), lateUpdate.getCause());
        }
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
                showDp();
                UserManager userManager = UserManager.INSTANCE;
                dpChangeProgress = new ProgressDialog(getActivity());
                dpChangeProgress.setMessage(getResources().getString(R.string.st_please_wait));
                dpChangeProgress.setCancelable(false);
                dpChangeProgress.show();
                userManager.changeDp(user.get_id(), filePath, DP_CALLBACK);

            }
        }
    }

    private ProgressDialog dpChangeProgress;
    private final UserManager.CallBack DP_CALLBACK = new UserManager.CallBack() {
        @Override
        public void done(Exception e) {
            dpChangeProgress.dismiss();
            if (e == null) {
                showDp();
            } else {
                UiHelpers.showErrorDialog(getActivity(), e.getMessage());
            }
        }
    };

    private void showDp() {
        Picasso.with(getActivity())
                .load(Config.DP_ENDPOINT + "/" + user.get_id())
                .placeholder(R.drawable.avatar_empty)
                .error(R.drawable.avatar_empty)
                .resize(320, 150)
                .into(displayPicture);
    }

    private final View.OnClickListener CHANGE_USERNAME = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            UiHelpers.showToast("not implemented");
        }
    };

    @Override
    public void onDestroy() {
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
                //noinspection ConstantConditions
                convertView = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_1, parent, false);
            }
            ((TextView) convertView).setText(getItem(position).getName());
            convertView.setTag(getItem(position));
            return convertView;
        }
    }

}
