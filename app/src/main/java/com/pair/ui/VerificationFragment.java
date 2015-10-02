package com.pair.ui;


import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import com.pair.Errors.ErrorCenter;
import com.pair.data.UserManager;
import com.pair.pairapp.R;
import com.pair.util.TypeFaceUtil;
import com.pair.util.UiHelpers;
import com.pair.util.ViewUtils;
import com.rey.material.app.DialogFragment;

/**
 * A simple {@link Fragment} subclass.
 */
public class VerificationFragment extends Fragment {
    private static final String TAG = VerificationFragment.class.getSimpleName();
    private DialogFragment progressDialog;
    private EditText etVerification;
    private Callbacks callback;
    private final View.OnClickListener listener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (v.getId() == R.id.bt_verify) {
                doVerifyUser();
            } else if (v.getId() == R.id.bt_resend_token) {
                resendToken();
            } else if (v.getId() == R.id.tv_back_to_login) {
                callback.onBackToCLogIn();
            }
        }
    };

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
        android.widget.TextView buttonVerify = (android.widget.TextView) view.findViewById(R.id.bt_verify);
        buttonVerify.setOnClickListener(listener);
        ViewUtils.setTypeface(buttonVerify, TypeFaceUtil.ROBOTO_REGULAR_TTF);

        android.widget.TextView resendToken = (android.widget.TextView) view.findViewById(R.id.bt_resend_token);
        resendToken.setOnClickListener(listener);
        ViewUtils.setTypeface(resendToken, TypeFaceUtil.ROBOTO_REGULAR_TTF);

        android.widget.TextView backToLogin = (android.widget.TextView) view.findViewById(R.id.tv_back_to_login);
        backToLogin.setOnClickListener(listener);
        ViewUtils.setTypeface(backToLogin, TypeFaceUtil.DROID_SERIF_BOLD_TTF);

        etVerification = ((EditText) view.findViewById(R.id.et_verification));
        ViewUtils.setTypeface(etVerification, TypeFaceUtil.ROBOTO_REGULAR_TTF);

        ViewUtils.setTypeface((TextView) view.findViewById(R.id.tv_verification_notice), TypeFaceUtil.ROBOTO_REGULAR_TTF);
        ViewUtils.setTypeface((TextView) view.findViewById(R.id.copy_right), TypeFaceUtil.two_d_font);

        progressDialog = UiHelpers.newProgressDialog();
        return view;
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
