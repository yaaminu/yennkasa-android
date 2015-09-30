package com.pair.ui;


import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.pair.Errors.ErrorCenter;
import com.pair.data.User;
import com.pair.data.UserManager;
import com.pair.pairapp.R;
import com.pair.util.PLog;
import com.pair.util.Config;
import com.pair.util.FileUtils;
import com.pair.util.LiveCenter;
import com.pair.util.MediaUtils;
import com.pair.util.PhoneNumberNormaliser;
import com.pair.util.UiHelpers;
import com.rey.material.app.DialogFragment;
import com.rey.material.widget.FloatingActionButton;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

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
    private boolean changingDp = false;
    private final UserManager.CallBack DP_CALLBACK = new UserManager.CallBack() {
        @Override
        public void done(Exception e) {
            changingDp = false;
            hideProgressView();
            if (e == null) {
                showDp();
            } else {
                ErrorCenter.reportError(TAG, e.getMessage());
            }
        }
    };
    private TextView phoneOrAdminTitle, mutualGroupsOrMembersTvTitle;
    private View sendMessageButton;
    private UserManager userManager;
//    private int DP_HEIGHT;
//    private int DP_WIDTH;
private String phoneInlocalFormat;
    private final View.OnClickListener clickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.bt_message:
                    UiHelpers.enterChatRoom(v.getContext(), user.getUserId());
                    getActivity().finish();
                    break;
                case R.id.bt_exit_group:
                    hideProgressView();
                    UiHelpers.showErrorDialog((PairAppBaseActivity) getActivity(), R.string.leave_group_prompt, R.string.yes, android.R.string.no, new UiHelpers.Listener() {
                        @Override
                        public void onClick() {
                            leaveGroup();
                        }
                    }, null);
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
                    File dpFile = new File(Config.getAppProfilePicsBaseDir(), user.getDP());
                    intent = new Intent(getActivity(), ImageViewer.class);
                    Uri uri;
                    if (dpFile.exists()) {
                        hideProgressView();
                        uri = Uri.fromFile(dpFile);
                        //noinspection ConstantConditions
                        intent.setData(uri);
                        startActivity(intent);
                    } else {//dp not downloaded
                        if (image_capture_out_put_uri != null) {
                            intent.setData(image_capture_out_put_uri);
                            startActivity(intent);
                        } else {
                            UiHelpers.showToast(R.string.sorry_no_dp);
                        }
                    }
                    break;
                case R.id.bt_take_photo_change_dp:
                    if (changingDp) {
                        UiHelpers.showToast(getString(R.string.busy));
                        return;
                    }
                    File file = new File(Config.getTempDir(), user.getUserId() + ".jpg");
                    image_capture_out_put_uri = Uri.fromFile(file);
                    MediaUtils.takePhoto(ProfileFragment.this, image_capture_out_put_uri, TAKE_PHOTO_REQUEST);
                    break;
                case R.id.bt_pick_photo_change_dp:
                    if (changingDp) {
                        UiHelpers.showToast(getString(R.string.busy));
                        return;
                    }
                    choosePicture();
                    break;
                default:
                    throw new AssertionError("unknown view");

            }
        }
    };

    public ProfileFragment() {
        // Required empty public constructor
    }

    @Override
    public void onAttach(Context activity) {
        setRetainInstance(true);
        super.onAttach(activity);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @SuppressLint("CutPasteId")
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

        ((FloatingActionButton) changeDpButton2).setIcon(getResources().getDrawable(R.drawable.ic_action_camera), true);
        ((FloatingActionButton) changeDpButton).setIcon(getResources().getDrawable(R.drawable.ic_action_picture), true);

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
        user = UserManager.getInstance().fetchUserIfRequired(realm, id,true);
        //common to all
        userName.setText(user.getName());
        displayPicture.setOnClickListener(clickListener);
        if (user.getType() == User.TYPE_GROUP) {
            setUpViewsGroupWay();
        } else {
            setUpViewSingleUserWay();
        }
        final ActionBar actionBar = ((ActionBarActivity) getActivity()).getSupportActionBar();
        //noinspection ConstantConditions
        if (userManager.isCurrentUser(user.getUserId())) {
            //noinspection ConstantConditions
            actionBar.setTitle(R.string.you);
            view.findViewById(R.id.user_action_panel).setVisibility(View.GONE); //we don't need this
        } else {
            //noinspection ConstantConditions
            actionBar.setTitle(user.getName());
            if (!User.isGroup(user)) {
                actionBar.setSubtitle(LiveCenter.isOnline(user.getUserId()) ? R.string.st_online : R.string.st_offline);
            }
        }

//        ScreenUtility utility = new ScreenUtility(getActivity());
////        DP_HEIGHT = getResources().getDimensionPixelSize(R.dimen.dp_height);
////        DP_WIDTH = (int) utility.getPixelsWidth();
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        showDp();
    }

    @Override
    public void onResume() {
        super.onResume();
        realm.addChangeListener(this);
    }

    @Override
    public void onPause() {
        realm.removeChangeListener(this);
        super.onPause();
    }

    private void setUpViewSingleUserWay() {
        if (userManager.isCurrentUser(user.getUserId())) {
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
        phoneInlocalFormat = PhoneNumberNormaliser.toLocalFormat("+" + user.getUserId(), userManager.getUserCountryISO());
        userPhoneOrAdminName.setText(phoneInlocalFormat);
    }

    private void setUpViewsGroupWay() {
        //noinspection ConstantConditions
        callButton.setVisibility(View.GONE);
        if (userManager.isAdmin(user.getUserId())) {
            deleteGroup.setVisibility(View.VISIBLE);
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
            if (changingDp) {
                changingDp = false;
                showDp();
            }
            //todo probably change status too and last activity
        } catch (Exception lateUpdate) {//fragment no more in layout or maybe user left group
            PLog.e(TAG, lateUpdate.getMessage(), lateUpdate.getCause());
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
        if (changingDp) {
            return;
        }
        String filePath;
        if (uri.getScheme().equals("content")) {
            filePath = FileUtils.resolveContentUriToFilePath(uri);
        } else {
            filePath = uri.getPath();
        }

        File file = new File(filePath);

        if (!file.exists()) {
            UiHelpers.showErrorDialog((PairAppBaseActivity) getActivity(), getString(R.string.invalid_image));
        } else if (!MediaUtils.isImage(filePath)) {
            UiHelpers.showErrorDialog((PairAppBaseActivity) getActivity(), getString(R.string.not_a_bitmap));
        } else if (file.length() > FileUtils.ONE_MB * 8) {
            UiHelpers.showErrorDialog((PairAppBaseActivity) getActivity(), getString(R.string.image_size_too_large));
        } else {
            Picasso.with(getActivity()).load(file)
                    .placeholder(UserManager.getInstance().isGroup(user.getUserId()) ? R.drawable.group_avatar : R.drawable.user_avartar)
                    .into(displayPicture);
            showProgressView();
            changingDp = true;
            userManager.changeDp(user.getUserId(), filePath, DP_CALLBACK);
        }
    }

    private void showDp() {
        if (changingDp) {
            return;
        }
        showProgressView();
        DPLoader.load(getActivity(), user.getUserId(), user.getDP())
                .placeholder(User.isGroup(user) ? R.drawable.group_avatar : R.drawable.user_avartar)
                .error(User.isGroup(user) ? R.drawable.group_avatar : R.drawable.user_avartar)
                .into(displayPicture, new Callback() {
                    @Override
                    public void onSuccess() {
                        hideProgressView();
                    }

                    @Override
                    public void onError() {
                        ProfileFragment.this.hideProgressView();
                    }
                });
    }

    @Override
    public void onDestroy() {
        realm.close();
        super.onDestroy();
    }

    private void showProgressView() {
        progressView.setVisibility(View.VISIBLE);
    }

    private void hideProgressView() {
        progressView.setVisibility(View.GONE);
    }

    private void leaveGroup() {
        progressDialog.show(getFragmentManager(), null);
        userManager.leaveGroup(user.getUserId(), new UserManager.CallBack() {
            @Override
            public void done(Exception e) {
                try {
                    progressDialog.dismiss();
                } catch (Exception ignored) {
                }
                if (e != null) {
                    ErrorCenter.reportError(TAG, e.getMessage());
                } else {
                    getActivity().finish();
                }
            }
        });
    }

}
