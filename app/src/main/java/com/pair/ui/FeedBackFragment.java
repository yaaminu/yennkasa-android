package com.pair.ui;


import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.pair.data.UserManager;
import com.pair.messenger.PairAppClient;
import com.pair.pairapp.R;
import com.pair.util.UiHelpers;
import com.rey.material.widget.CheckBox;
import com.rey.material.widget.EditText;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;
import java.util.Locale;

/**
 * A simple {@link Fragment} subclass.
 */
public class FeedBackFragment extends Fragment {


    private View.OnClickListener onClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            //noinspection ConstantConditions
            EditText titleEt = ((EditText) getView().findViewById(R.id.et_feedback_subject)),
                    subject = ((EditText) getView().findViewById(R.id.et_feedback_body));
            CheckBox checkBox = ((CheckBox) getView().findViewById(R.id.cb_checked));

            String feedbackBody = subject.getText().toString().trim();
            subject.setText("");
            if (TextUtils.isEmpty(feedbackBody)) {
                UiHelpers.showErrorDialog(((PairAppBaseActivity) getActivity()), getString(R.string.error_feedback_body_empty));
                return;
            }

            String title = titleEt.getText().toString().trim();
            titleEt.setText("");
            if (TextUtils.isEmpty(title)) {
                title = "no subject";
            }
            JSONObject reportObject = new JSONObject();
            try {
                reportObject.put("body", feedbackBody);
                reportObject.put("title", title);
                if (checkBox.isChecked()) {
                    reportObject.put("device", Build.DEVICE);
                    reportObject.put("model", Build.MODEL);
                    reportObject.put("manufacturer", Build.MANUFACTURER);
                    reportObject.put("reportedBy", UserManager.getMainUserId());
                    reportObject.put("time", new Date());
                    reportObject.put("apiVersion", Build.VERSION.SDK_INT);
                    reportObject.put("locale", Locale.getDefault().getDisplayCountry());
                }
                PairAppClient.sendFeedBack(reportObject); //async
                UiHelpers.showToast(getString(R.string.feedback_sent_successfully));
            } catch (JSONException e) {
                throw new RuntimeException(e.getCause());
            }
        }
    };

    public FeedBackFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        //noinspection ConstantConditions
        View view = inflater.inflate(R.layout.fragment_feed_back, container, false);

        view.findViewById(R.id.bt_submit).setOnClickListener(onClickListener);

        return view;
    }


}
