package com.pair.pairapp.ui;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.pair.adapter.CountriesListAdapter;
import com.pair.data.Country;
import com.pair.data.UserManager;
import com.pair.pairapp.Config;
import com.pair.pairapp.MainActivity;
import com.pair.pairapp.R;
import com.pair.pairapp.SetUpActivity;
import com.pair.util.PhoneNumberNormaliser;
import com.pair.util.UiHelpers;
import com.pair.workers.ContactSyncService;

import io.realm.Realm;

/**
 * @author by Null-Pointer on 5/28/2015.
 */
public class SignupFragment extends Fragment {

    private EditText passWordEt, userNameEt;
    private EditText phoneNumberEt;
    private Realm realm;
    private View.OnClickListener gotoLogin = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Fragment fragment = getFragmentManager().findFragmentByTag(SetUpActivity.LOGIN_FRAG);
            if (fragment == null) {
                fragment = new LoginFragment();
            }
            getFragmentManager().beginTransaction().replace(R.id.container, fragment, SetUpActivity.LOGIN_FRAG).commit();
        }
    };
    private ProgressDialog pDialog;

    public SignupFragment() {
    }

    @Override
    public void onAttach(Activity activity) {
        setRetainInstance(true);
        super.onAttach(activity);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View view = inflater.inflate(R.layout.signup_fragment, container, false);
        passWordEt = (EditText) view.findViewById(R.id.et_passwordField);
        phoneNumberEt = (EditText) view.findViewById(R.id.et_phone_number_field);
        userNameEt = (EditText) view.findViewById(R.id.usernameField);
        Spinner spinner = ((Spinner) view.findViewById(R.id.sp_ccc));
        realm = Realm.getInstance(getActivity());
        final CountriesListAdapter adapter = new CountriesListAdapter(getActivity(), realm.where(Country.class).findAllSorted("name"));
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        final TextView tv = (TextView) view.findViewById(R.id.tv_login);
        tv.setClickable(true);
        tv.setOnClickListener(gotoLogin);
        view.findViewById(R.id.signupButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (TextUtils.isEmpty(passWordEt.getText().toString())
                        || TextUtils.isEmpty(phoneNumberEt.getText().toString())
                        || TextUtils.isEmpty(userNameEt.getText().toString())) {
                    return;
                }
                attemptSignUp();
            }
        });
        return view;
    }

    private void attemptSignUp() {
        final String phoneNumber = phoneNumberEt.getText().toString().trim(),
                name = userNameEt.getText().toString().trim(),
                password = passWordEt.getText().toString().trim();
        @SuppressWarnings("ConstantConditions")
        final Spinner spinner = (Spinner) getView().findViewById(R.id.sp_ccc);
        final String ccc = ((Country) spinner.getSelectedItem()).getIso2letterCode();
        if (!PhoneNumberNormaliser.isValidPhoneNumber(phoneNumber, ccc)) {
            final DialogInterface.OnClickListener okListener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    doAttemptSignUp(phoneNumber, name, password, ccc);
                }
            };
            UiHelpers.showErrorDialog(getActivity(),
                    R.string.st_invalid_phone_number_title,
                    R.string.st_invalid_phone_number_message,
                    R.string.st_understand,
                    android.R.string.cancel, okListener, null);
            return;
        }
        doAttemptSignUp(phoneNumber, name, password, ccc);
    }

    private void doAttemptSignUp(String phoneNumber, String name, String password, String ccc) {
        pDialog = new ProgressDialog(getActivity());
        pDialog.setMessage(getString(R.string.st_please_wait));
        pDialog.setCancelable(false);
        pDialog.show();
        UserManager.getInstance().signUp(getActivity(), name, phoneNumber, password, ccc, new UserManager.CallBack() {
            @Override
            public void done(Exception e) {
                pDialog.dismiss();
                if (e == null) {
                    Config.enableComponents();
                    ContactSyncService.start(Config.getApplicationContext());
                    startActivity(new Intent(getActivity(), MainActivity.class));
                    getActivity().finish();
                } else {
                    String message = e.getMessage();
                    if ((message == null) || (message.isEmpty())) {
                        message = "an unknown error occurred";
                    }
                    UiHelpers.showErrorDialog(getActivity(), message);
                }
            }
        });
    }

    @Override
    public void onDestroy() {
        realm.close();
        super.onDestroy();
    }
}
