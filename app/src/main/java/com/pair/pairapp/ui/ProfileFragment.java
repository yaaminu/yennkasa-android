package com.pair.pairapp.ui;


import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.pair.data.Message;
import com.pair.data.User;
import com.pair.data.UserManager;
import com.pair.pairapp.Config;
import com.pair.pairapp.MainActivity;
import com.pair.pairapp.R;
import com.pair.pairapp.UsersActivity;
import com.pair.util.FileUtils;
import com.pair.util.MediaUtils;
import com.pair.util.ScreenUtility;
import com.pair.util.UiHelpers;
import com.rey.material.app.DialogFragment;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;

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
    private static final int PICK_PHOTO_REQUEST = 0x3e9,
            TAKE_PHOTO_REQUEST = 0X3ea;

    private ImageView displayPicture;
    private TextView userName, userPhone, listHeading;
    private ListView mutualGroupsList;
    private User user;
    private Realm realm;
    private View exitGroupButton, callButton, progressView, imageButton, changeDpButton, changeDpButton2;
    private DialogFragment progressDialog;
    private RequestCreator creator;
    private Uri image_capture_out_put_uri;
    private boolean dpChanged = false;
    private Picasso PICASSO;

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
        displayPicture = ((android.widget.ImageView) view.findViewById(R.id.iv_display_picture));
        userName = ((TextView) view.findViewById(R.id.tv_user_name));
        userPhone = ((TextView) view.findViewById(R.id.tv_user_phone));
        changeDpButton = view.findViewById(R.id.bt_pick_photo_change_dp);
        changeDpButton2 = view.findViewById(R.id.bt_take_photo_change_dp);
        progressView = view.findViewById(R.id.pb_progress);
        imageButton = view.findViewById(R.id.ib_change_name);
        View sendMessageButton = view.findViewById(R.id.bt_message);
        sendMessageButton.setOnClickListener(clickListener);
        callButton = view.findViewById(R.id.bt_call);
        exitGroupButton = view.findViewById(R.id.bt_exit_group);
        listHeading = ((TextView) view.findViewById(R.id.tv_list_heading));
        mutualGroupsList = ((ListView) view.findViewById(R.id.lv_mutual_groups_list));
        mutualGroupsList.setEmptyView(view.findViewById(R.id.empty));
        progressDialog = UiHelpers.newProgressDialog();

        //end view hookup

        realm = Realm.getInstance(getActivity());
        String id = getArguments().getString(ARG_USER_ID);
        user = realm.where(User.class).equalTo(User.FIELD_ID, id).findFirst();

        if (user == null || UserManager.getInstance().isMainUser(id)) {
            Log.wtf(TAG, "invalid user id. program aborting");
            throw new IllegalArgumentException("invalid user id");
        }
        final UserManager userManager = UserManager.getInstance();

        //common to all
        userName.setText("@" + user.getName());
        displayPicture.setOnClickListener(clickListener);
        if (user.getType() == User.TYPE_GROUP) {
            setUpViewsGroupWay();
        } else {
            setUpViewSingleUserWay();
        }
        userManager.refreshUserDetails(user.get_id()); //async
        //noinspection ConstantConditions
        ((ActionBarActivity) getActivity()).getSupportActionBar().setTitle(user.getName());
        PICASSO = Picasso.with(getActivity());
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        realm.addChangeListener(this);
        showDp();
    }

    @Override
    public void onPause() {
        realm.removeChangeListener(this);
        super.onPause();
    }

    private void setUpViewSingleUserWay() {
        changeDpButton.setVisibility(View.GONE);
        changeDpButton2.setVisibility(View.GONE);
        userPhone.setVisibility(View.VISIBLE);
        exitGroupButton.setVisibility(View.GONE);
        imageButton.setVisibility(View.GONE);
        //noinspection ConstantConditions
        callButton.setOnClickListener(clickListener);
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
        BaseAdapter membersAdapter = new GroupsOrMembersAdapter(getActivity(), results);
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
        mutualGroupsList.setVisibility(View.GONE);
        mutualGroupsList.getEmptyView().setVisibility(View.GONE);
        //noinspection ConstantConditions
        callButton.setVisibility(View.GONE);
        if (UserManager.getInstance().isAdmin(user.get_id())) {
            exitGroupButton.setVisibility(View.GONE);
            imageButton.setVisibility(View.VISIBLE);
            imageButton.setOnClickListener(clickListener);
        } else {
            exitGroupButton.setVisibility(View.VISIBLE);
            exitGroupButton.setOnClickListener(clickListener);
            imageButton.setVisibility(View.GONE);
        }
        changeDpButton.setVisibility(View.VISIBLE);
        changeDpButton2.setVisibility(View.VISIBLE);
        changeDpButton2.setOnClickListener(clickListener);
        changeDpButton.setOnClickListener(clickListener);
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
            if (!UserManager.getInstance().isGroup(user.get_id())) {
                setUpMutualMembers();
            }
            if (dpChanged) {
                dpChanged = false;
                showDp();
            }
            //todo probably change status too and last activity
        } catch (Exception lateUpdate) {//fragment no more in layout or maybe user left group
            Log.e(TAG, lateUpdate.getMessage(), lateUpdate.getCause());
        }
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == PICK_PHOTO_REQUEST) {
                Uri uri = data.getData();
                doChangeDp(uri);
            } else if (requestCode == TAKE_PHOTO_REQUEST) {
                doChangeDp(image_capture_out_put_uri);
            }
        }
    }

    private void doChangeDp(Uri uri) {
        String filePath;
        if (uri.getScheme().equals("content")) {
            filePath = FileUtils.resolveContentUriToFilePath(uri);
        } else {
            filePath = uri.getPath();
        }

        UserManager userManager = UserManager.getInstance();
        dpChangeProgress = UiHelpers.newProgressDialog();
        dpChangeProgress.show(getFragmentManager(), null);
        userManager.changeDp(user.get_id(), filePath, DP_CALLBACK);
    }

    private DialogFragment dpChangeProgress;
    private final UserManager.CallBack DP_CALLBACK = new UserManager.CallBack() {
        @Override
        public void done(Exception e) {
            dpChangeProgress.dismiss();
            if (e == null) {
                dpChanged = true;
                showDp();
            } else {
                UiHelpers.showErrorDialog(getActivity(), e.getMessage());
            }
        }
    };

    private void showDp() {
        progressView.setVisibility(View.VISIBLE);
        File localDp = new File(Config.getAppProfilePicsBaseDir(), user.getDP() + ".jpg");
        if (dpChanged) {
            dpChanged = false;
            PICASSO.invalidate(localDp);
        }
        if (localDp.exists()) {
            creator = PICASSO.load(localDp);
            ScreenUtility utility = new ScreenUtility(getActivity());
            creator.resize((int) utility.getPixelsWidth(), getResources().getDimensionPixelSize(R.dimen.dp_height));
            creator.placeholder(User.isGroup(user) ? R.drawable.group_avatar : R.drawable.user_avartar)
                    .error(User.isGroup(user) ? R.drawable.group_avatar : R.drawable.user_avartar)
                    .into(displayPicture, new Callback() {
                        @Override
                        public void onSuccess() {
                            progressView.setVisibility(View.GONE);
                        }

                        @Override
                        public void onError() {
                            progressView.setVisibility(View.GONE);
                        }
                    });
        } else {
            //before we proceed to download the image lets show default image there.
            displayPicture.setImageResource(User.isGroup(user) ? R.drawable.group_avatar : R.drawable.user_avartar);

            UserManager.getInstance().refreshDp(user.get_id(), new UserManager.CallBack() {
                @Override
                public void done(Exception e) {
                    progressView.setVisibility(View.GONE);
                    if (e == null) {
                        dpChanged = true;
                        showDp();
                    }
                }
            });
        }
    }


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

    private final View.OnClickListener clickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.bt_message:
                    UiHelpers.enterChatRoom(v.getContext(), user.get_id());
                    break;
                case R.id.bt_exit_group:
                    progressView.setVisibility(View.GONE);
                    progressDialog.show(getFragmentManager(), null);
                    UserManager.getInstance().leaveGroup(user.get_id(), new UserManager.CallBack() {
                        @Override
                        public void done(Exception e) {
                            progressDialog.dismiss();
                            if (e != null) {
                                try {
                                    UiHelpers.showErrorDialog(getActivity(), e.getMessage());
                                } catch (Exception e2) {
                                    // FIXME: 8/3/2015
                                }
                            } else {
                                getActivity().finish();
                            }
                        }
                    });
                    break;
                case R.id.bt_pick_photo_change_dp:
                    progressView.setVisibility(View.GONE);
                    choosePicture();
                    break;
                case R.id.ib_change_name:
                    UiHelpers.showToast("not implemented");
                    break;
                case R.id.bt_call:
                    Intent intent = new Intent(Intent.ACTION_DIAL);
                    intent.setData(Uri.parse("tel:" + "+" + user.get_id()));
                    startActivity(intent);
                    break;
                case R.id.iv_display_picture:
                    progressView.setVisibility(View.GONE);
                    File dpFile = new File(Config.getAppProfilePicsBaseDir(), user.getDP() + ".jpg");
                    intent = new Intent(getActivity(), ImageViewer.class);
                    Uri uri;
                    if (dpFile.exists()) {
                        uri = Uri.fromFile(dpFile);
                        //noinspection ConstantConditions
                        intent.setData(uri);
                        startActivity(intent);
                    } else {//dp not downloaded
//                        uri = Uri.parse(Config.DP_ENDPOINT+"/"+user.getDP());
                        UiHelpers.showToast(R.string.sorry_no_dp);
                    }
                    break;
                case R.id.bt_take_photo_change_dp:
                    File file = new File(Config.getTempDir(), user.get_id() + ".jpg.tmp");
                    image_capture_out_put_uri = Uri.fromFile(file);
                    MediaUtils.takePhoto(ProfileFragment.this, image_capture_out_put_uri, TAKE_PHOTO_REQUEST);
                    break;
                default:
                    throw new AssertionError("unknown view");

            }
        }
    };

}
