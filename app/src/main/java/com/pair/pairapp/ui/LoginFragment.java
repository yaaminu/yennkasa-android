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
import android.widget.Button;
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
@SuppressWarnings("ConstantConditions FieldCanBeLocal")
public class LoginFragment extends Fragment{
    private Button loginButton;
    public static final String TAG = LoginFragment.class.getSimpleName();
    private EditText passwordEt;
    private EditText phoneNumberEt;
    private ProgressDialog progressDialog;
    private Realm realm;

    public LoginFragment() {
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
        realm = Realm.getInstance(getActivity());
        View view = inflater.inflate(R.layout.login_fragment, container, false);
        phoneNumberEt = (EditText) view.findViewById(R.id.et_phone_number_field);
        passwordEt = (EditText) view.findViewById(R.id.et_passwordField);
        loginButton = (Button) view.findViewById(R.id.bt_loginButton);
        Spinner spinner = ((Spinner) view.findViewById(R.id.sp_ccc));
        final CountriesListAdapter adapter = new CountriesListAdapter(getActivity(), realm.where(Country.class).findAllSorted("name"));
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(0);
        TextView tv = (TextView) view.findViewById(R.id.tv_signup);
        tv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Fragment fragment = getFragmentManager().findFragmentByTag(SetUpActivity.SIGNUP_FRAG);
                if (fragment == null) {
                    fragment = new SignupFragment();
                }
                getFragmentManager().beginTransaction().replace(R.id.container, fragment, SetUpActivity.SIGNUP_FRAG).commit();
            }
        });
        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (TextUtils.isEmpty(passwordEt.getText().toString())
                        || TextUtils.isEmpty(phoneNumberEt.getText().toString())) {
                    return;
                }
                attemptLogin();
            }
        });
        return view;
    }

    private void attemptLogin() {
        final String phoneNumber = phoneNumberEt.getText().toString().trim(),
                password = passwordEt.getText().toString().trim(),
                ccc = ((Country) ((Spinner) getView().findViewById(R.id.sp_ccc))
                        .getSelectedItem())
                        .getIso2letterCode();
        if (!PhoneNumberNormaliser.isValidPhoneNumber(phoneNumber, ccc)) {

            final DialogInterface.OnClickListener okListener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    doAttemptLogin(phoneNumber, password, ccc);
                }
            };
            UiHelpers.showErrorDialog(getActivity(),
                    R.string.st_invalid_phone_number_title,
                    R.string.st_invalid_phone_number_message,
                    R.string.st_understand,
                    android.R.string.cancel, okListener, null);
            return;
        }
        doAttemptLogin(phoneNumber, password, ccc);
    }

    private void doAttemptLogin(String phoneNumber, String password, String ccc) {
        progressDialog = new ProgressDialog(getActivity());
        progressDialog.setMessage(getString(R.string.st_please_wait));
        progressDialog.setCancelable(false);
        progressDialog.show();
        UserManager.getInstance().logIn(getActivity(), phoneNumber, password, ccc, new UserManager.CallBack() {
            @Override
            public void done(Exception e) {
                progressDialog.dismiss();
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
}
