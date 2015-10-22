package com.idea.ui;


import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
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

import com.idea.Errors.ErrorCenter;
import com.idea.data.User;
import com.idea.data.UserManager;
import com.idea.pairapp.R;
import com.idea.util.Config;
import com.idea.util.ConnectionUtils;
import com.idea.util.FileUtils;
import com.idea.util.LiveCenter;
import com.idea.util.MediaUtils;
import com.idea.util.PLog;
import com.idea.util.PhoneNumberNormaliser;
import com.idea.util.ScreenUtility;
import com.idea.util.TypeFaceUtil;
import com.idea.util.UiHelpers;
import com.idea.util.ViewUtils;
import com.idea.view.FrameLayout;
import com.rey.material.app.DialogFragment;
import com.rey.material.widget.FloatingActionButton;
import com.squareup.picasso.Callback;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

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

    private boolean dpLoaded = false;
    private ImageView displayPicture;
    private TextView userName, userPhoneOrAdminName, mutualGroupsOrMembersTv;
    private User user;
    private Realm realm;
    private View progressView, changeDpButton, changeDpButton2;
    private android.widget.Button exitGroupButton, callButton, deleteGroup, sendMessageButton;
    private DialogFragment progressDialog;
    private Uri image_capture_out_put_uri;
    private boolean changingDp = false;
    private final UserManager.CallBack DP_CALLBACK = new UserManager.CallBack() {
        @Override
        public void done(Exception e) {
            if (image_capture_out_put_uri != null) {
                File file = new File(image_capture_out_put_uri.getPath());
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
            hideProgressView();
            changingDp = false;
            ViewUtils.showViews(changeDpButton, changeDpButton2);
            if (e == null) {
                showDp();
            } else {
                ErrorCenter.reportError(TAG, e.getMessage());
            }
        }
    };
    private TextView phoneOrAdminTitle, mutualGroupsOrMembersTvTitle;
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
                case R.id.bt_call:
                    Intent intent = new Intent(Intent.ACTION_DIAL);
                    intent.setData(Uri.parse("tel:" + phoneInlocalFormat));
                    startActivity(intent);
                    break;
                case R.id.iv_display_picture:
                    if (dpLoaded) {
                        File dpFile = new File(user.getDP());
                        intent = new Intent(getActivity(), ImageViewer.class);
                        Uri uri;
                        if (dpFile.exists()) {
                            hideProgressView();
                            uri = Uri.fromFile(dpFile);
                            //noinspection ConstantConditions
                            intent.setData(uri);
                            startActivity(intent);
                            break;
                        } else {//dp not downloaded
                            try {
                                URL url = new URL(user.getDP());
                                Uri data = Uri.parse(url.toExternalForm());
                                intent.setData(data);
                                startActivity(intent);
                                break;
                            } catch (MalformedURLException ignored) {
                            }
                        }
                    }
                    UiHelpers.showToast(R.string.sorry_no_dp);
                    break;
                case R.id.bt_take_photo_change_dp:
                    if (changingDp) {
                        UiHelpers.showToast(getString(R.string.busy));
                        return;
                    }
                    ViewUtils.hideViews(changeDpButton, changeDpButton2);
                    File file = new File(Config.getTempDir(), user.getUserId() + ".jpg");
                    image_capture_out_put_uri = Uri.fromFile(file);
                    MediaUtils.takePhoto(ProfileFragment.this, image_capture_out_put_uri, TAKE_PHOTO_REQUEST);
                    break;
                case R.id.bt_pick_photo_change_dp:
                    if (changingDp) {
                        UiHelpers.showToast(getString(R.string.busy));
                        return;
                    }
                    ViewUtils.hideViews(changeDpButton, changeDpButton2);
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
        ViewUtils.setTypeface(userName, TypeFaceUtil.DROID_SERIF_REGULAR_TTF);

        changeDpButton = view.findViewById(R.id.bt_pick_photo_change_dp);
        changeDpButton2 = view.findViewById(R.id.bt_take_photo_change_dp);
        progressView = view.findViewById(R.id.pb_progress);
        deleteGroup = (android.widget.Button) view.findViewById(R.id.bt_dissolve_group);
        ViewUtils.setTypeface(deleteGroup, TypeFaceUtil.ROBOTO_REGULAR_TTF);

        sendMessageButton = (android.widget.Button) view.findViewById(R.id.bt_message);
        sendMessageButton.setOnClickListener(clickListener);
        ViewUtils.setTypeface(sendMessageButton, TypeFaceUtil.ROBOTO_REGULAR_TTF);

        callButton = (android.widget.Button) view.findViewById(R.id.bt_call);
        ViewUtils.setTypeface(callButton, TypeFaceUtil.ROBOTO_REGULAR_TTF);

        exitGroupButton = (android.widget.Button) view.findViewById(R.id.bt_exit_group);
        ViewUtils.setTypeface(exitGroupButton, TypeFaceUtil.ROBOTO_REGULAR_TTF);

        progressDialog = UiHelpers.newProgressDialog();

        ((FloatingActionButton) changeDpButton2).setIcon(getResources().getDrawable(R.drawable.ic_action_camera), true);
        ((FloatingActionButton) changeDpButton).setIcon(getResources().getDrawable(R.drawable.ic_action_picture), true);

        int screenHeight = (int) new ScreenUtility(getActivity()).getPixelsHeight();

        if (!getResources().getBoolean(R.bool.isLandscape)) {
            displayPicture.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, screenHeight / 2));
        }
        View parent = view.findViewById(R.id.tv_user_phone_group_admin);
        phoneOrAdminTitle = ((TextView) parent.findViewById(R.id.tv_title));
        userPhoneOrAdminName = ((TextView) parent.findViewById(R.id.tv_subtitle));
        ViewUtils.setTypeface(phoneOrAdminTitle, TypeFaceUtil.DROID_SERIF_BOLD_TTF);
        ViewUtils.setTypeface(userPhoneOrAdminName, TypeFaceUtil.DROID_SERIF_REGULAR_TTF);
        //re-use parent
        parent = view.findViewById(R.id.tv_shared_groups_or_group_members);
        mutualGroupsOrMembersTv = ((TextView) parent.findViewById(R.id.tv_subtitle));
        mutualGroupsOrMembersTvTitle = (TextView) parent.findViewById(R.id.tv_title);

        ViewUtils.setTypeface(mutualGroupsOrMembersTv, TypeFaceUtil.DROID_SERIF_REGULAR_TTF);
        ViewUtils.setTypeface(mutualGroupsOrMembersTvTitle, TypeFaceUtil.DROID_SERIF_BOLD_TTF);


        //end view hookup
        realm = User.Realm(getActivity());
        userManager = UserManager.getInstance();

        String id = getArguments().getString(ARG_USER_ID);
        user = UserManager.getInstance().fetchUserIfRequired(realm, id, true);
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
        pDialog = new ProgressDialog(getActivity());
        pDialog.setCancelable(false);
        pDialog.setMessage(getString(R.string.st_please_wait));
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
            changeDpButton.setOnClickListener(clickListener);
            changeDpButton2.setOnClickListener(clickListener);
        } else {
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
        } else {
            exitGroupButton.setOnClickListener(clickListener);
            deleteGroup.setVisibility(View.GONE);
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
        } else {
            final UserManager instance = UserManager.getInstance();
            if (instance.isGroup(user.getUserId()) || instance.isCurrentUser(user.getUserId()))
                ViewUtils.showViews(changeDpButton, changeDpButton2);
        }
    }

    private void doChangeDp(Uri uri) {
        if (changingDp) {
            return;
        }
        ViewUtils.hideViews(changeDpButton, changeDpButton2);
        String filePath = FileUtils.resolveContentUriToFilePath(uri);
        if (filePath == null) {
            ErrorCenter.reportError(TAG, getString(R.string.error_use_file_manager));
            return;
        }
        File file = new File(filePath);

        if (!file.exists()) {
            UiHelpers.showErrorDialog((PairAppBaseActivity) getActivity(), getString(R.string.invalid_image));
            ViewUtils.showViews(changeDpButton, changeDpButton2);
        } else if (!MediaUtils.isImage(filePath)) {
            UiHelpers.showErrorDialog((PairAppBaseActivity) getActivity(), getString(R.string.not_a_bitmap));
            ViewUtils.showViews(changeDpButton, changeDpButton2);
        } else if (file.length() > FileUtils.ONE_MB * 8) {
            UiHelpers.showErrorDialog((PairAppBaseActivity) getActivity(), getString(R.string.image_size_too_large));
            ViewUtils.showViews(changeDpButton, changeDpButton2);
        } else {
            ViewUtils.hideViews(changeDpButton, changeDpButton2);
            changingDp = true;
            showProgressView();
            userManager.changeDp(user.getUserId(), filePath, DP_CALLBACK);
        }
    }

    private void showDp() {
        if (changingDp) {
            return;
        }
        showProgressView();
        DPLoader.load(getActivity(), user.getDP())
                .placeholder(User.isGroup(user) ? R.drawable.group_avatar : R.drawable.user_avartar)
                .error(User.isGroup(user) ? R.drawable.group_avatar : R.drawable.user_avartar)
                .into(displayPicture, dpLoadedCallback);
    }

    private final Callback dpLoadedCallback = new Callback() {
        @Override
        public void onSuccess() {
            dpLoaded = true;
            hideProgressView();
        }

        @Override
        public void onError() {
            dpLoaded = false;
            ProfileFragment.this.hideProgressView();
        }
    };

    @Override
    public void onDestroy() {
        realm.close();
        super.onDestroy();
    }

    private void showProgressView() {
        if (changingDp) {
            pDialog.show();
        } else {
            progressView.setVisibility(View.VISIBLE);
        }
    }

    private void hideProgressView() {
        try {
            if (changingDp)
                pDialog.dismiss();
            else
                progressView.setVisibility(View.GONE);
        } catch (Exception ignored) {
        }
    }

    private void leaveGroup() {
        progressDialog.show(getFragmentManager(), null);
        userManager.leaveGroup(user.getUserId(), new UserManager.CallBack() {
            @Override
            public void done(Exception e) {
                UiHelpers.dismissProgressDialog(progressDialog);
                if (e != null) {
                    ErrorCenter.reportError(TAG, e.getMessage());
                } else {
                    getActivity().finish();
                }
            }
        });
    }

    ProgressDialog pDialog;
}
