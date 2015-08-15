package com.pair.pairapp.ui;


import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import com.pair.data.UserManager;
import com.pair.pairapp.Config;
import com.pair.pairapp.MainActivity;
import com.pair.pairapp.R;
import com.pair.util.UiHelpers;
import com.pair.workers.ContactSyncService;
import com.rey.material.app.DialogFragment;

/**
 * A simple {@link Fragment} subclass.
 */
public class VerificationFragment extends Fragment {
    DialogFragment progressDialog;

    public VerificationFragment() {
        // Required empty public constructor
    }

    private final View.OnClickListener listener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (v.getId() == R.id.bt_verify) {
                doVerifyUser();
            } else if (v.getId() == R.id.bt_resend_token) {
                resendToken();
            } else if (v.getId() == R.id.tv_back_to_login) {
                backToLogin();
            }
        }
    };

    private void backToLogin() {
        progressDialog.show(getFragmentManager(), null);
        UserManager.getInstance().reset(new UserManager.CallBack() {
            @Override
            public void done(Exception e) {
                progressDialog.dismiss();
                if (e == null) {
                    startActivity(new Intent(getActivity(), MainActivity.class));
                    getActivity().finish();
                } else {
                    UiHelpers.showErrorDialog(getActivity(), e.getMessage());
                }
            }
        });

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_verification, container, false);
        view.findViewById(R.id.bt_verify).setOnClickListener(listener);
        view.findViewById(R.id.bt_resend_token).setOnClickListener(listener);
        view.findViewById(R.id.tv_back_to_login).setOnClickListener(listener);
        progressDialog = UiHelpers.newProgressDialog();
        return view;
    }

    private void doVerifyUser() {
        @SuppressWarnings("ConstantConditions") EditText et = ((EditText) getView().findViewById(R.id.et_verification));
        final String code = et.getText().toString();
        et.setText("");
        if (TextUtils.isEmpty(code)) {
            UiHelpers.showErrorDialog(getActivity(), getString(R.string.token_required));
        } else {
            progressDialog.show(getFragmentManager(), null);
            UserManager.getInstance().verifyUser(code, callBack);
        }
    }

    private void resendToken() {
        UserManager.getInstance().resendToken(new UserManager.CallBack() {
            @Override
            public void done(Exception e) {
                progressDialog.dismiss();
                if (e == null) {
                    completeSetUp();
                } else {
                    UiHelpers.showErrorDialog(getActivity(), e.getMessage());
                }
            }
        });
    }

    private UserManager.CallBack callBack = new UserManager.CallBack() {
        public void done(Exception e) {
            progressDialog.dismiss();
            if (e == null) {
                completeSetUp();
            } else {
                String message = e.getMessage();
                if ((message == null) || (message.isEmpty())) {
                    message = "an unknown error occurred";
                }
                UiHelpers.showErrorDialog(getActivity(), message);
            }
        }
    };

    private void completeSetUp() {
        Config.enableComponents();
        ContactSyncService.start(getActivity());
        startActivity(new Intent(getActivity(), MainActivity.class));
        getActivity().finish();
    }
}
