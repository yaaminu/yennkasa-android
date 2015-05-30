package com.pair.pairapp;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.pair.data.User;
import com.pair.util.FormValidator;
import com.pair.util.GcmHelper;
import com.pair.util.UiHelpers;
import com.pair.util.UserManager;

/**
 * Created by Null-Pointer on 5/28/2015.
 */
public class SignupFragment extends Fragment {

    private EditText passWordEt,userNameEt;
    private AutoCompleteTextView phoneNumberEt;
    private FormValidator validator;
    private boolean busy;
    private View progressView;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.signup_fragment, container, false);
        passWordEt = (EditText) view.findViewById(R.id.passwordField);
        phoneNumberEt = (AutoCompleteTextView) view.findViewById(R.id.phone_number_field);
        new UiHelpers.AutoCompleter(getActivity(),phoneNumberEt).execute(); //enable autocompletion
        userNameEt = (EditText) view.findViewById(R.id.usernameField);
        progressView = view.findViewById(R.id.progressView);

        final TextView tv = (TextView) view.findViewById(R.id.tv_login);
        tv.setOnClickListener(gotoLogin);
        view.findViewById(R.id.signupButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //TODO validate all fields before proceeding
                attemptSignUp();
            }
        });
        return view;
    }

    private View.OnClickListener gotoLogin = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (busy) //trying to login, see {code attemptSignUp}
                return;
            getFragmentManager().popBackStackImmediate();
        }
    };

    private void attemptSignUp() {
        if (busy) {
            return;
        }
        busy = true;
        GcmHelper.register(getActivity(), new GcmHelper.GCMRegCallback() {
            @Override
            public void done(Exception e, String regId) {
                if (e == null) {
                    User user = new User();
                    user.set_id(UiHelpers.getFieldContent(phoneNumberEt));
                    user.setPassword(UiHelpers.getFieldContent(passWordEt));
                    user.setName(UiHelpers.getFieldContent(userNameEt));
                    user.setGcmRegId(regId);
                    progressView.setVisibility(View.VISIBLE);
                    UserManager.getInstance(getActivity().getApplication()).signUp(user, signUpCallback);
                } else {
                    busy = false;
                    UiHelpers.showErrorDialog(getActivity(), e.getMessage());
                }
            }
        });
    }


    private UserManager.SignUpCallback signUpCallback = new UserManager.SignUpCallback() {
        public void done(Exception e) {
            progressView.setVisibility(View.GONE);
            busy = false;
            if (e == null) {
                startActivity(new Intent(getActivity(), MainActivity.class));
                getActivity().finish();
            } else {
                String message = e.getMessage();
                if ((message == null) || (message.isEmpty())) {
                    message = "an unknown error occurred";
                }
                Toast.makeText(getActivity(), message, Toast.LENGTH_LONG).show();
            }
        }
    };
}
