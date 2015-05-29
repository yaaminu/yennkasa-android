package com.pair.pairapp;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.pair.data.User;
import com.pair.util.GcmHelper;
import com.pair.util.UiHelpers;
import com.pair.util.UserManager;

/**
 * Created by Null-Pointer on 5/28/2015.
 */
public class LoginFragment extends Fragment {
    private EditText phoneNumberEt, passwordEt;
    private Button loginButton;
    private boolean busy = false;
    private View progressView;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.login_fragment, container, false);
        phoneNumberEt = (EditText) view.findViewById(R.id.phone_number_field);
        passwordEt = (EditText) view.findViewById(R.id.passwordField);
        loginButton = (Button) view.findViewById(R.id.loginButton);
        progressView = view.findViewById(R.id.progressView);
        TextView tv = (TextView) view.findViewById(R.id.tv_signup);

        tv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getFragmentManager().beginTransaction().replace(R.id.container, new SignupFragment()).addToBackStack(null).commit();
            }
        });

        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                attemptLogin();
            }
        });
        return view;
    }

    private void attemptLogin() {
        GcmHelper.register(getActivity(), new GcmHelper.GCMRegCallback() {
            @Override
            public void done(Exception e, String regId) {
                if (e == null) {
                    if (busy) {
                        return;
                    }
                    busy = true;
                    User user = new User();
                    user.set_id(UiHelpers.getFieldContent(phoneNumberEt));
                    user.setPassword(UiHelpers.getFieldContent(passwordEt));
                    user.setGcmRegId(regId);
                    progressView.setVisibility(View.VISIBLE);
                    UserManager.getInstance(getActivity().getApplication()).logIn(user, loginCallback);
                } else {
                    UiHelpers.showErrorDialog(getActivity(), e.getMessage());
                }
            }
        });
    }

    private UserManager.LoginCallback loginCallback = new UserManager.LoginCallback() {
        public void done(Exception e) {
            progressView.setVisibility(View.GONE);
            busy = false;
            if (e == null) {
                startActivity(new Intent(getActivity(),MainActivity.class));
                getActivity().finish();
            } else {
                String message = e.getMessage();
                //this is necessary because retrofit sometime throw exceptions with no message
                if ((message == null) || (message.isEmpty())) {
                    message = "an unknown error occurred";
                }
                Toast.makeText(getActivity(), message, Toast.LENGTH_LONG).show();
            }
        }
    };
}
