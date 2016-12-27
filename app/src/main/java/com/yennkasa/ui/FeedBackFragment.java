package com.yennkasa.ui;


import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.yennkasa.BuildConfig;
import com.yennkasa.R;
import com.yennkasa.data.User;
import com.yennkasa.data.UserManager;
import com.yennkasa.messenger.PairAppClient;
import com.yennkasa.util.FileUtils;
import com.yennkasa.util.GcmUtils;
import com.yennkasa.util.MediaUtils;
import com.yennkasa.util.TypeFaceUtil;
import com.yennkasa.util.UiHelpers;
import com.yennkasa.util.ViewUtils;
import com.rey.material.widget.CheckBox;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.realm.Realm;

/**
 * A simple {@link Fragment} subclass.
 */
public class FeedBackFragment extends Fragment {


    private static final String ADD_SYSTEM_INFO = "addSystemInfo";
    private static final String SUBJECT = "subject";
    private static final String MESSAGE = "message";
    public static final String ATTACHMENTS = "attachments";
    private EditText feedbackBody;
    private View.OnClickListener onClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {

            String feedbackBody = FeedBackFragment.this.feedbackBody.getText().toString().trim();
            FeedBackFragment.this.feedbackBody.setText("");
            if (TextUtils.isEmpty(feedbackBody)) {
                UiHelpers.showErrorDialog(getActivity(), getString(R.string.error_feedback_body_empty));
                return;
            }

            String title = subjectEt.getText().toString().trim();
            if (TextUtils.isEmpty(title)) {
                title = "no subject";
            } else if (title.length() > 40) {
                UiHelpers.showErrorDialog(getActivity(), getString(R.string.error_feedback_subject_too_lonng));
                return;
            }
            subjectEt.setText("");
            JSONObject reportObject = new JSONObject();
            Realm userRealm = User.Realm(getContext());
            try {
                reportObject.put("body", feedbackBody);
                if (checkBox.isChecked()) {
                    reportObject.put("device", Build.DEVICE);
                    reportObject.put("model", Build.MODEL);
                    reportObject.put("manufacturer", Build.MANUFACTURER);
                    reportObject.put("reportedBy", UserManager.getMainUserId(userRealm));
                    reportObject.put("time", new Date());
                    reportObject.put("pairapVersion", BuildConfig.VERSION_NAME);
                    reportObject.put("pairapVersionCode", BuildConfig.VERSION_CODE);
                    reportObject.put("apiVersion", Build.VERSION.SDK_INT);
                    reportObject.put("hasGooglePlayServices", GcmUtils.hasGcm());
                    reportObject.put("title", title);

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        reportObject.put("arc", TextUtils.join(",", Build.SUPPORTED_ABIS));
                    } else {
                        //noinspection deprecation
                        String[] abi = {
                                Build.CPU_ABI,
                                Build.CPU_ABI2
                        };
                        reportObject.put("arc", TextUtils.join(",", abi));
                    }
                    reportObject.put("locale", Locale.getDefault().getDisplayCountry());
                }

                Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:team.yennkasa.android@gmail.com"));
                intent.putExtra(Intent.EXTRA_TEXT, reportObject.toString());
                intent.putExtra(Intent.EXTRA_SUBJECT, title);
                try {
                    startActivity(intent);
                    throw new ActivityNotFoundException();
                } catch (ActivityNotFoundException e) {
                    PairAppClient.sendFeedBack(reportObject, attachments);
                    UiHelpers.showToast(R.string.feedback_sent_successfully);
                }
            } catch (JSONException e) {
                throw new RuntimeException(e.getCause());
            } finally {
                userRealm.close();
            }
        }
    };
    private EditText subjectEt;
    private ArrayList<String> attachments = new ArrayList<>();
    private CheckBox checkBox;

    public FeedBackFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        setRetainInstance(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        //noinspection ConstantConditions
        View view = inflater.inflate(R.layout.fragment_feed_back, container, false);

        Button submit = (Button) view.findViewById(R.id.bt_submit);
        ViewUtils.setTypeface(submit, TypeFaceUtil.ROBOTO_REGULAR_TTF);
        submit.setOnClickListener(onClickListener);
        subjectEt = (EditText) view.findViewById(R.id.et_feedback_subject);
        feedbackBody = ((EditText) view.findViewById(R.id.et_feedback_body));
        checkBox = ((CheckBox) view.findViewById(R.id.cb_checked));
        ViewUtils.setTypeface(submit, TypeFaceUtil.ROBOTO_REGULAR_TTF);
        ViewUtils.setTypeface(subjectEt, TypeFaceUtil.ROBOTO_REGULAR_TTF);
        TextView addDeviceInfo = ((TextView) view.findViewById(R.id.tv_add_device_info));
        ViewUtils.setTypeface(addDeviceInfo, TypeFaceUtil.ROBOTO_REGULAR_TTF);
        if (savedInstanceState != null) {
            String title = savedInstanceState.getString(SUBJECT);
            if (title != null) {
                subjectEt.setText(title);
            }
            title = savedInstanceState.getString(MESSAGE);
            if (title != null) {
                feedbackBody.setText(title);
            }
            checkBox.setChecked(savedInstanceState.getBoolean(ADD_SYSTEM_INFO, false));
            List<String> saved = savedInstanceState.getStringArrayList(ATTACHMENTS);
            if (saved != null && !saved.isEmpty()) {
                attachments = new ArrayList<>(saved);
            }
        }
        return view;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK && requestCode == ChatActivity.PICK_PHOTO_REQUEST) {
            String path = FileUtils.resolveContentUriToFilePath(data.getData());
            if (path != null) {
                if (attachments.size() > 5) {
                    UiHelpers.showPlainOlDialog(getActivity(), getString(R.string.max_attachment_reached));
                    return;
                }
                if (!MediaUtils.isImage(path)) {
                    UiHelpers.showToast(R.string.not_a_bitmap);
                    return;
                }
                if (new File(path).length() > FileUtils.ONE_MB * 8) {
                    UiHelpers.showToast(R.string.error_file_too_large);
                    return;
                }
                attachments.add(path);
                UiHelpers.showToast(getString(R.string.attachment_added_feedback));
            } else {
                UiHelpers.showToast(getString(R.string.attach_failed_feedback));
            }
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_feedback, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_attach) {
            Intent attachIntent;
            attachIntent = new Intent(Intent.ACTION_GET_CONTENT);
            attachIntent.setType("image/*");
            attachIntent.addCategory(Intent.CATEGORY_DEFAULT);
            startActivityForResult(attachIntent, ChatActivity.PICK_PHOTO_REQUEST);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(ADD_SYSTEM_INFO, checkBox.isChecked());
        outState.putString(SUBJECT, subjectEt.getText().toString());
        outState.putString(MESSAGE, feedbackBody.getText().toString());
        outState.putStringArrayList(ATTACHMENTS, attachments);
        super.onSaveInstanceState(outState);
    }
}
