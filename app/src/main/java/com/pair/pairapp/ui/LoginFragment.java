package com.pair.pairapp.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.pair.data.User;
import com.pair.pairapp.MainActivity;
import com.pair.pairapp.R;
import com.pair.pairapp.SetUpActivity;
import com.pair.util.Config;
import com.pair.util.GcmHelper;
import com.pair.util.UiHelpers;
import com.pair.util.UserManager;
import com.pair.workers.ContactSyncService;

/**
 * @author by Null-Pointer on 5/28/2015.
 */
@SuppressWarnings("ConstantConditions FieldCanBeLocal")
public class LoginFragment extends Fragment {
    private Button loginButton;
    public static final String TAG = LoginFragment.class.getSimpleName();
    private EditText passwordEt;
    private AutoCompleteTextView phoneNumberEt;
    private boolean busy = false;

    public LoginFragment(){}

    @Override
    public void onAttach(Activity activity) {
        setRetainInstance(true);
        super.onAttach(activity);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View view = inflater.inflate(R.layout.login_fragment, container, false);
        phoneNumberEt = (AutoCompleteTextView) view.findViewById(R.id.et_phone_number_field);
        new UiHelpers.AutoCompleter(getActivity(), phoneNumberEt).execute();//enable autocompletion
        passwordEt = (EditText) view.findViewById(R.id.et_passwordField);
        loginButton = (Button) view.findViewById(R.id.bt_loginButton);
        TextView tv = (TextView) view.findViewById(R.id.tv_signup);

        tv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (busy)
                    return;
                getFragmentManager().beginTransaction().replace(R.id.container, new SignupFragment(), SetUpActivity.f_TAG).commit();
            }
        });
        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(TextUtils.isEmpty(passwordEt.getText().toString())
                        ||TextUtils.isEmpty(phoneNumberEt.getText().toString()))
                {
                    return;
                }
                attemptLogin();
            }
        });
        return view;
    }

    private void attemptLogin() {
        if (busy) {
            return;
        }
        busy = true;
        GcmHelper.register(getActivity(), new GcmHelper.GCMRegCallback() {
            @Override
            public void done(Exception e, String regId) {
                if (e == null) {
                    User user = ((SetUpActivity)getActivity()).registeringUser;
                    user.set_id(UiHelpers.getFieldContent(phoneNumberEt));
                    user.setPassword(UiHelpers.getFieldContent(passwordEt));
                    user.setGcmRegId(regId);
                    SignupFragment.goToVerificationFragment(LoginFragment.this,SetUpActivity.ACTION_LOGIN);
                } else {
                    busy = false;
                    UiHelpers.showErrorDialog(getActivity(), e.getMessage());
                }
            }
        });
    }
}
