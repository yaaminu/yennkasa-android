package com.idea.ui;


import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import com.idea.Errors.ErrorCenter;
import com.idea.data.UserManager;
import com.idea.pairapp.R;
import com.idea.util.Config;
import com.idea.util.TypeFaceUtil;
import com.idea.util.UiHelpers;
import com.idea.util.ViewUtils;
import com.rey.material.app.DialogFragment;

/**
 * A simple {@link Fragment} subclass.
 */
public class VerificationFragment extends Fragment {
    private static final String TAG = VerificationFragment.class.getSimpleName();
    private static final String KEY_TOKEN_SENT = "tokenSent" + TAG;
    private DialogFragment progressDialog;
    private EditText etVerification;
    private Callbacks callback;
    private final View.OnClickListener listener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (v.getId() == R.id.bt_verify) {
                boolean tokenSent = Config.getApplicationWidePrefs().getBoolean(KEY_TOKEN_SENT, false);
                if (tokenSent) {
                    doVerifyUser();
                } else {
                    sendToken();
                }
            } else if (v.getId() == R.id.bt_resend_token) {
                resendToken();
            } else if (v.getId() == R.id.tv_back_to_login) {
                callback.onBackToCLogIn();
            }
        }
    };
    private TextView buttonVerify;
    private TextView resendToken;
    private TextView notice;

    private void sendToken() {
        progressDialog.show(getFragmentManager(), null);
        UserManager.getInstance().sendVerificationToken(new UserManager.CallBack() {
            @Override
            public void done(Exception e) {
                UiHelpers.dismissProgressDialog(progressDialog);
                if (e != null) {
                    ErrorCenter.reportError(TAG, e.getMessage());
                } else {
                    Config.getApplicationWidePrefs().edit().putBoolean(KEY_TOKEN_SENT, true).apply();
                    setUpViews(buttonVerify, resendToken, notice);
                }
            }
        });
    }

    public VerificationFragment() {
        // Required empty public constructor
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        setRetainInstance(true);
        try {
            callback = (Callbacks) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.getClass().getName() + " must implement Callbacks interface");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_verification, container, false);
        buttonVerify = (TextView) view.findViewById(R.id.bt_verify);
        buttonVerify.setOnClickListener(listener);
        ViewUtils.setTypeface(buttonVerify, TypeFaceUtil.ROBOTO_REGULAR_TTF);

        resendToken = (TextView) view.findViewById(R.id.bt_resend_token);
        resendToken.setOnClickListener(listener);
        ViewUtils.setTypeface(resendToken, TypeFaceUtil.ROBOTO_REGULAR_TTF);

        android.widget.TextView backToLogin = (android.widget.TextView) view.findViewById(R.id.tv_back_to_login);
        backToLogin.setOnClickListener(listener);
        ViewUtils.setTypeface(backToLogin, TypeFaceUtil.DROID_SERIF_BOLD_TTF);

        etVerification = ((EditText) view.findViewById(R.id.et_verification));
        ViewUtils.setTypeface(etVerification, TypeFaceUtil.ROBOTO_REGULAR_TTF);

        notice = (TextView) view.findViewById(R.id.tv_verification_notice);
        ViewUtils.setTypeface(notice, TypeFaceUtil.ROBOTO_REGULAR_TTF);
        ViewUtils.setTypeface((TextView) view.findViewById(R.id.copy_right), TypeFaceUtil.two_d_font);
        setUpViews(buttonVerify, resendToken, notice);
        progressDialog = UiHelpers.newProgressDialog();
        return view;
    }

    private void setUpViews(TextView buttonVerify, TextView resendToken, TextView notice) {
        boolean tokenSent = Config.getApplicationWidePrefs().getBoolean(KEY_TOKEN_SENT, false);
        notice.setText(tokenSent ? R.string.st_send_verification_notice:R.string.send_token_notice);
        if (tokenSent) {
            ViewUtils.showViews(resendToken, etVerification);
        } else {
            ViewUtils.hideViews(resendToken, etVerification);
        }
        buttonVerify.setText(tokenSent ? R.string.verify : R.string.send_token);
    }

    private void doVerifyUser() {
        final String code = etVerification.getText().toString();
        etVerification.setText("");
        if (TextUtils.isEmpty(code)) {
            UiHelpers.showErrorDialog((PairAppBaseActivity) getActivity(), getString(R.string.token_required));
        } else {
            progressDialog.show(getFragmentManager(), null);
            UserManager.getInstance().verifyUser(code, new UserManager.CallBack() {
                @Override
                public void done(Exception e) {
                    UiHelpers.dismissProgressDialog(progressDialog);
                    if (e != null) {
                        ErrorCenter.reportError(TAG, e.getMessage());
                    } else {
                        callback.onVerified();
                    }
                }
            });
        }

    }

    private void resendToken() {
        progressDialog.show(getFragmentManager(), null);
        UserManager.getInstance().resendToken(new UserManager.CallBack() {
            @Override
            public void done(Exception e) {
                UiHelpers.dismissProgressDialog(progressDialog);
                if (e != null) {
                    ErrorCenter.reportError(TAG, e.getMessage());
                }
            }
        });
    }

    public interface Callbacks {
        void onVerified();

        void onBackToCLogIn();
    }
}
