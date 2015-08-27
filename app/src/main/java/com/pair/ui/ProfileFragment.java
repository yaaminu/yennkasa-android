package com.pair.ui;


import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.pair.Config;
import com.pair.data.User;
import com.pair.data.UserManager;
import com.pair.pairapp.BuildConfig;
import com.pair.pairapp.R;
import com.pair.util.FileUtils;
import com.pair.util.MediaUtils;
import com.pair.util.PhoneNumberNormaliser;
import com.pair.util.ScreenUtility;
import com.pair.util.UiHelpers;
import com.rey.material.app.DialogFragment;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;

import java.io.File;

import io.realm.Realm;
import io.realm.RealmChangeListener;


/**
 * A simple {@link Fragment} subclass.
 */
public class ProfileFragment extends Fragment implements RealmChangeListener {

    public static final String ARG_USER_ID = "user_id";
    private static final String TAG = ProfileFragment.class.getSimpleName();

    private static final int PICK_PHOTO_REQUEST = 0x3e9,
            TAKE_PHOTO_REQUEST = 0X3ea;

    private ImageView displayPicture;
    private TextView userName, userPhoneOrAdminName, mutualGroupsOrMembersTv;
    private User user;
    private Realm realm;
    private View exitGroupButton, callButton, progressView, editName, changeDpButton, changeDpButton2, deleteGroup;
    private DialogFragment progressDialog;
    private Uri image_capture_out_put_uri;
    private boolean dpChanged = false;
    private Picasso PICASSO;
    private TextView phoneOrAdminTitle, mutualGroupsOrMembersTvTitle;
    private View sendMessageButton;
    private UserManager userManager;
    private String phoneInlocalFormat;


    public ProfileFragment() {
        // Required empty public constructor
    }

    @Override
    public void onAttach(Activity activity) {
        setRetainInstance(true);
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
        changeDpButton = view.findViewById(R.id.bt_pick_photo_change_dp);
        changeDpButton2 = view.findViewById(R.id.bt_take_photo_change_dp);
        progressView = view.findViewById(R.id.pb_progress);
        editName = view.findViewById(R.id.ib_change_name);
        deleteGroup = view.findViewById(R.id.bt_dissolve_group);
        sendMessageButton = view.findViewById(R.id.bt_message);
        sendMessageButton.setOnClickListener(clickListener);
        callButton = view.findViewById(R.id.bt_call);
        exitGroupButton = view.findViewById(R.id.bt_exit_group);
        progressDialog = UiHelpers.newProgressDialog();


        View parent = view.findViewById(R.id.tv_user_phone_group_admin);
        phoneOrAdminTitle = ((TextView) parent.findViewById(R.id.tv_title));
        userPhoneOrAdminName = ((TextView) parent.findViewById(R.id.tv_subtitle));

        //re-use parent
        parent = view.findViewById(R.id.tv_shared_groups_or_group_members);
        mutualGroupsOrMembersTv = ((TextView) parent.findViewById(R.id.tv_subtitle));
        mutualGroupsOrMembersTvTitle = (TextView) parent.findViewById(R.id.tv_title);

        //end view hookup
        realm = Realm.getInstance(getActivity());
        userManager = UserManager.getInstance();

        String id = getArguments().getString(ARG_USER_ID);
        user = realm.where(User.class).equalTo(User.FIELD_ID, id).findFirst();

        if (user == null) {
            if (BuildConfig.DEBUG) {
                Log.wtf(TAG, "invalid user id. program aborting");
                throw new IllegalArgumentException("invalid user id");
            } else {
                UiHelpers.showErrorDialog(getActivity(), "No such user");
            }
            return null;
        }

        //common to all
        userName.setText(user.getName());
        displayPicture.setOnClickListener(clickListener);
        if (user.getType() == User.TYPE_GROUP) {
            setUpViewsGroupWay();
        } else {
            setUpViewSingleUserWay();
        }
        userManager.refreshUserDetails(user.getUserId()); //async
        final ActionBar actionBar = ((ActionBarActivity) getActivity()).getSupportActionBar();
        //noinspection ConstantConditions
        actionBar.setTitle(userManager.isMainUser(user.getUserId()) ? getString(R.string.you) : user.getName());
        if (!User.isGroup(user) && !userManager.isMainUser(user.getUserId())) {
            actionBar.setSubtitle(user.getStatus());
        }
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
        if (userManager.isMainUser(user.getUserId())) {
            callButton.setVisibility(View.GONE);
            sendMessageButton.setVisibility(View.GONE);
            ((View) mutualGroupsOrMembersTvTitle.getParent()).setVisibility(View.GONE);
            editName.setOnClickListener(clickListener);
            changeDpButton.setOnClickListener(clickListener);
            changeDpButton2.setOnClickListener(clickListener);
        } else {
            editName.setVisibility(View.GONE);
            changeDpButton.setVisibility(View.GONE);
            changeDpButton2.setVisibility(View.GONE);
            mutualGroupsOrMembersTvTitle.setText(R.string.shared_groups);
            mutualGroupsOrMembersTv.setText(R.string.groups_you_share_in_common);
            callButton.setOnClickListener(clickListener);
        }
        exitGroupButton.setVisibility(View.GONE);
        deleteGroup.setVisibility(View.GONE);
        //noinspection ConstantConditions
        phoneOrAdminTitle.setText(R.string.phone);
        phoneInlocalFormat = PhoneNumberNormaliser.toLocalFormat("+" + user.getUserId());
        userPhoneOrAdminName.setText(phoneInlocalFormat);
    }

    private void setUpViewsGroupWay() {
        //noinspection ConstantConditions
        callButton.setVisibility(View.GONE);
        if (userManager.isAdmin(user.getUserId())) {
            deleteGroup.setVisibility(View.GONE);
            exitGroupButton.setVisibility(View.GONE);
            editName.setOnClickListener(clickListener);
        } else {
            exitGroupButton.setOnClickListener(clickListener);
            deleteGroup.setVisibility(View.GONE);
            editName.setVisibility(View.GONE);
        }
        //any member can change dp of a group
        changeDpButton2.setOnClickListener(clickListener);
        changeDpButton.setOnClickListener(clickListener);

        // TODO: 8/25/2015 add a lock drawable to the right of this text view
        phoneOrAdminTitle.setText(R.string.admin);
        userPhoneOrAdminName.setText(userManager.isAdmin(user.getUserId())
                ? getString(R.string.you) : user.getAdmin().getName());

        ((View) phoneOrAdminTitle.getParent()).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Fragment fragment = new ProfileFragment();
                Bundle bundle = new Bundle(1);
                bundle.putString(ARG_USER_ID, user.getAdmin().getUserId());
                fragment.setArguments(bundle);
                getFragmentManager().beginTransaction()
                        .replace(R.id.container, fragment)
                        .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                        .commit();
            }
        });
        mutualGroupsOrMembersTvTitle.setText(R.string.st_group_members);
        mutualGroupsOrMembersTv.setText(user.getMembers().size() + getString(R.string.Members));
        ((View) mutualGroupsOrMembersTv.getParent()).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Bundle bundle = new Bundle(1);
                bundle.putString(UsersActivity.EXTRA_GROUP_ID, user.getUserId());
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
            userName.setText(user.getName());
            if (!userManager.isGroup(user.getUserId())) {
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

    private void setUpMutualMembers() {
        // TODO: 8/25/2015 implement this method
        throw new UnsupportedOperationException("not implemented");
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

        dpChangeProgress = UiHelpers.newProgressDialog();
        dpChangeProgress.show(getFragmentManager(), null);
        userManager.changeDp(user.getUserId(), filePath, DP_CALLBACK);
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
            RequestCreator creator = PICASSO.load(localDp);
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
            userManager.refreshDp(user.getUserId(), new UserManager.CallBack() {
                @Override
                public void done(Exception e) {
                    if (isResumed()) {
                        progressView.setVisibility(View.GONE);
                        if (e == null) {
                            dpChanged = true;
                            showDp();
                        }
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

    private final View.OnClickListener clickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.bt_message:
                    UiHelpers.enterChatRoom(v.getContext(), user.getUserId());
                    break;
                case R.id.bt_exit_group:
                    progressView.setVisibility(View.GONE);
                    UiHelpers.showErrorDialog(getActivity(), R.string.leave_group_prompt, R.string.yes, android.R.string.no, new UiHelpers.Listener() {
                        @Override
                        public void onClick() {
                            leaveGroup();
                        }
                    }, null);
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
                    intent.setData(Uri.parse("tel:" + phoneInlocalFormat));
                    startActivity(intent);
                    break;
                case R.id.iv_display_picture:
                    File dpFile = new File(Config.getAppProfilePicsBaseDir(), user.getDP() + ".jpg");
                    intent = new Intent(getActivity(), ImageViewer.class);
                    Uri uri;
                    if (dpFile.exists()) {
                        progressView.setVisibility(View.GONE);
                        uri = Uri.fromFile(dpFile);
                        //noinspection ConstantConditions
                        intent.setData(uri);
                        startActivity(intent);
                    } else {//dp not downloaded
                        UiHelpers.showToast(R.string.sorry_no_dp);
                    }
                    break;
                case R.id.bt_take_photo_change_dp:
                    File file = new File(Config.getTempDir(), user.getUserId() + ".jpg.tmp");
                    image_capture_out_put_uri = Uri.fromFile(file);
                    MediaUtils.takePhoto(ProfileFragment.this, image_capture_out_put_uri, TAKE_PHOTO_REQUEST);
                    break;
                default:
                    throw new AssertionError("unknown view");

            }
        }
    };

    private void leaveGroup() {
        progressDialog.show(getFragmentManager(), null);
        userManager.leaveGroup(user.getUserId(), new UserManager.CallBack() {
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
    }

}
