package com.pair.pairapp.ui;


import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.pair.data.User;
import com.pair.pairapp.MainActivity;
import com.pair.pairapp.R;
import com.pair.pairapp.SetUpActivity;
import com.pair.util.Config;
import com.pair.util.UiHelpers;
import com.pair.util.UserManager;
import com.pair.workers.ContactSyncService;

/**
 * A simple {@link Fragment} subclass.
 */
public class VerificationFragment extends Fragment {


    User registeringUser;
    ProgressDialog progressDialog;
    public VerificationFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        registeringUser = ((SetUpActivity) getActivity()).registeringUser;
        if (registeringUser == null) {
            throw new IllegalStateException("SetUpActivity.registering#User cannot be null at this time");
        }
        final View view = inflater.inflate(R.layout.fragment_verification, container, false);
        final Button b = ((Button) view.findViewById(R.id.bt_complete_setup));
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EditText et = ((EditText) view.findViewById(R.id.et_verification));
                final String code = et.getText().toString();
                if (!TextUtils.isEmpty(code)) {
                    progressDialog = new ProgressDialog(getActivity());
                    progressDialog.setMessage(getString(R.string.st_please_wait));
                    progressDialog.setCancelable(false);
                    progressDialog.show();
                    completeSetUp(code);
                }
            }
        });
        view.findViewById(R.id.bt_cancel_verification).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((SetUpActivity)getActivity()).verificationCancelled();
            }
        });
        view.findViewById(R.id.bt_send_verification).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UserManager.getInstance().generateAndSendVerificationToken(registeringUser.get_id());
                v.setVisibility(View.GONE);
                view.findViewById(R.id.bt_cancel_verification).setVisibility(View.GONE);
                ((TextView) view.findViewById(R.id.tv_verification_notice)).setText(R.string.st_verification_notice);
                view.findViewById(R.id.et_verification).setVisibility(View.VISIBLE);
                b.setVisibility(View.VISIBLE);
            }
        });
        return view;
    }

    private void completeSetUp(String code) {
        String action = getArguments().getString(SetUpActivity.ACTION);
        if (action == null) {
            throw new IllegalArgumentException("action cannot be null");
        }
        String ccc = ((SetUpActivity) getActivity()).CCC;
        if (action.equals(SetUpActivity.ACTION_LOGIN)) {
            UserManager.getInstance().logIn(registeringUser,ccc,code, loginOrSignUpCallback);
        } else if (action.equals(SetUpActivity.ACTION_SIGN_UP)) {
            UserManager.getInstance().signUp(registeringUser,ccc,code, loginOrSignUpCallback);
        } else {
            throw new IllegalArgumentException("unknown action: " + action);
        }
    }

    private UserManager.CallBack loginOrSignUpCallback = new UserManager.CallBack() {
        public void done(Exception e) {
            progressDialog.dismiss();
            if (e == null) {
                Config.enableComponents();
                ContactSyncService.start(getActivity());
                startActivity(new Intent(getActivity(), MainActivity.class));
                getActivity().finish();
            } else {
                String message = e.getMessage();
                if ((message == null) || (message.isEmpty())) {
                    message = "an unknown error occurred";
                }
                UiHelpers.showErrorDialog(getActivity(),message);
            }
        }
    };
}
