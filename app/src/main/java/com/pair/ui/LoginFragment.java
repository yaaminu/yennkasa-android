package com.pair.ui;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import com.pair.adapter.CountriesListAdapter;
import com.pair.data.Country;
import com.pair.data.UserManager;
import com.pair.pairapp.R;
import com.pair.util.FormValidator;
import com.pair.util.PhoneNumberNormaliser;
import com.pair.util.UiHelpers;
import com.rey.material.widget.Spinner;
import com.rey.material.widget.TextView;

import io.realm.Realm;

/**
 * @author by Null-Pointer on 5/28/2015.
 */
@SuppressWarnings("ConstantConditions FieldCanBeLocal")
public class LoginFragment extends Fragment {
    private Button loginButton;
    public static final String TAG = LoginFragment.class.getSimpleName();
    private EditText usernameEt, phoneNumberEt;
    private Realm realm;
    private boolean isLoggingIn = true;
    private Spinner spinner;
    private String userName, phoneNumber, userCountry;
    private FormValidator validator;
    private Callbacks callback;
    private View.OnClickListener listener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (v.getId() == R.id.bt_loginButton) {
                validateAndContinue();
            } else if (v.getId() == R.id.tv_signup) {
                toggleSignUpLogin(((TextView) v));
            } else {
                throw new AssertionError();
            }
        }
    };

    public LoginFragment() {
    }

    @Override
    public void onAttach(Context activity) {
        super.onAttach(activity);
        setRetainInstance(true);

        try {
            callback = (Callbacks) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException("Activity" + activity.getClass().getSimpleName() + " must implement interface" + Callbacks.class.getName());
        }
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        realm = Country.REALM(getActivity());
        View view = inflater.inflate(R.layout.login_fragment, container, false);
        phoneNumberEt = (EditText) view.findViewById(R.id.et_phone_number_field);
        loginButton = (Button) view.findViewById(R.id.bt_loginButton);
        usernameEt = (EditText) view.findViewById(R.id.et_username);
        spinner = ((Spinner) view.findViewById(R.id.sp_ccc));

        validator = new FormValidator();
        validator.addStrategy(phoneNumberStrategy)
                .addStrategy(usernameStrategy);

        final CountriesListAdapter adapter = new CountriesListAdapter(getActivity(), realm.where(Country.class).findAllSorted("name"));
        adapter.setDropDownViewResource(R.layout.country_spinner_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(0);
        TextView tv = (TextView) view.findViewById(R.id.tv_signup);
        tv.setOnClickListener(listener);
        loginButton.setOnClickListener(listener);
        return view;
    }

    private void validateAndContinue() {
        phoneNumber = phoneNumberEt.getText().toString().trim();
        userCountry = ((Country) spinner.getSelectedItem()).getIso2letterCode();
        userName = usernameEt.getText().toString().trim();

        if (userCountryStrategy.validate() && usernameStrategy.validate() && phoneNumberStrategy.validate()) {
            attemptLoginOrSignUp();
        }
    }

    private void toggleSignUpLogin(TextView v) {
        if (isLoggingIn) {
            isLoggingIn = false;
            v.setText(R.string.st_already_have_an_account);
            usernameEt.setVisibility(View.VISIBLE);
            usernameEt.requestFocus();
            loginButton.setText(R.string.sign_up_button_label);
        } else {
            isLoggingIn = true;
            v.setText(R.string.dont_have_an_account_sign_up);
            usernameEt.setVisibility(View.GONE);
            phoneNumberEt.requestFocus();
            loginButton.setText(R.string.log_in_button_label);
        }
    }


    private void attemptLoginOrSignUp() {
        doAttemptLogin();
    }

    private void doAttemptLogin() {
        if (isLoggingIn) {
            callback.onLogin(phoneNumber, userCountry);
        } else {
            callback.onSignUp(userName, phoneNumber, userCountry);
        }
    }

    private void showRequiredFieldDialog(String field) {
        UiHelpers.showErrorDialog((PairAppBaseActivity) getActivity(), getString(R.string.required_field_error, field));
    }

    //    Pattern userNamePattern = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]{1,12}");
    private FormValidator.ValidationStrategy userCountryStrategy = new FormValidator.ValidationStrategy() {
        @Override
        public boolean validate() {
            int position = spinner.getSelectedItemPosition();
            if (position == 0) {
                spinner.requestFocus();
                showRequiredFieldDialog(getString(R.string.country));
                return false;
            }
            return true;
        }
    }, usernameStrategy = new FormValidator.ValidationStrategy() {
        @Override
        public boolean validate() {
            if (isLoggingIn) return true;
            if (TextUtils.isEmpty(userName)) {
                showRequiredFieldDialog(getString(R.string.username_hint));
                usernameEt.requestFocus();
                return false;
            } else {
                Pair<String, String> errorNamePair = UserManager.getInstance().isValidUserName(userName);
                if (errorNamePair.second != null) {
                    UiHelpers.showErrorDialog((PairAppBaseActivity) getActivity(), errorNamePair.second);
                    usernameEt.requestFocus();
                    return false;
                }
                userName = errorNamePair.first;
                return true;
            }
        }
    }, phoneNumberStrategy = new FormValidator.ValidationStrategy() {
        @Override
        public boolean validate() {
            if (TextUtils.isEmpty(phoneNumber)) {
                showRequiredFieldDialog(getString(R.string.phone_hint));
                phoneNumberEt.requestFocus();
                return false;
            }
            if (!PhoneNumberNormaliser.
                    isValidPhoneNumber(phoneNumber, userCountry)) {
                final UiHelpers.Listener okListener = new UiHelpers.Listener() {
                    @Override
                    public void onClick() {
                        doAttemptLogin();
                    }
                };
                UiHelpers.showErrorDialog((PairAppBaseActivity) getActivity(),
                        getString(R.string.st_invalid_phone_number_message, phoneNumber).toUpperCase(),
                        getString(R.string.yes).toUpperCase(),
                        getString(android.R.string.cancel).toUpperCase(), okListener, null);
                phoneNumberEt.requestFocus();
                return false;
            }
            return true;
        }
    };

    interface Callbacks {
        void onLogin(String phoneNumber, String userIsoCountry);

        void onSignUp(String userName, String phoneNumber, String userIsoCountry);
    }
}

