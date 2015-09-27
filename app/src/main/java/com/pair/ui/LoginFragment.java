package com.pair.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
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
import com.pair.pairapp.BuildConfig;
import com.pair.pairapp.R;
import com.pair.util.CLog;
import com.pair.util.Config;
import com.pair.util.FormValidator;
import com.pair.util.PhoneNumberNormaliser;
import com.pair.util.UiHelpers;
import com.pair.view.MyTextWatcher;
import com.rey.material.app.DialogFragment;
import com.rey.material.widget.Spinner;
import com.rey.material.widget.TextView;

import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Locale;

import io.realm.Realm;

/**
 * @author by Null-Pointer on 5/28/2015.
 */
@SuppressWarnings("ConstantConditions FieldCanBeLocal")
public class LoginFragment extends Fragment {
    public static final String TAG = LoginFragment.class.getSimpleName();
    public static final String LOCALE_KEY = "locale";
    private Button loginButton;
    private EditText usernameEt, phoneNumberEt;
    private Realm realm;
    private boolean isCLoggingIn = true;
    private Spinner spinner;
    private String userName, phoneNumber, userCountry;
    private FormValidator validator;
    private Callbacks callback;
    private DialogFragment progressDialog;
    private Spinner.OnItemSelectedListener onItemSelectedListener = new Spinner.OnItemSelectedListener() {
        @Override
        public void onItemSelected(Spinner parent, View view, int position, long id) {
            phoneNumberEt.setEnabled(position > 0);
            String countryCode;
            if (position > 0) {
                countryCode = ((Country) spinner.getAdapter().getItem(position)).getIso2letterCode();
                phoneNumberEt.addTextChangedListener(new MyTextWatcher(countryCode));
            } else {
                phoneNumberEt.addTextChangedListener(new MyTextWatcher());
            }

        }
    };


    // although this works i am not confident it will work in all cases as a result am using
    // the android implementation
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
            if (isCLoggingIn) return true;
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
                        doAttemptCLogin();
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
    /* private class MyTextWatcher implements TextWatcher {
         boolean selfChanged = false;

         AsYouTypeFormatter formatter;

         MyTextWatcher(String countryCode) {
             formatter = PhoneNumberUtil.getInstance().
                     getAsYouTypeFormatter(countryCode);
         }

         @Override
         public void beforeTextChanged(CharSequence s, int start, int count, int after) {

         }

         @Override
         public void onTextChanged(CharSequence s, int start, int before, int count) {

         }

         @Override
         public void afterTextChanged(Editable s) {
             if (selfChanged) {
                 phoneNumberEt.setSelection(s.length());
                 selfChanged = false;
                 return;
             }
             formatter.clear();

             if (s.length() == 0) {
                 return;
             }
             //lets keep things simple
             String phoneNumber = "", content = phoneNumberEt.getText().toString().trim();
             content = PhoneNumberNormaliser.cleanNonDialableChars(content);
             for (int i = 0; i < content.length(); i++) {
                 phoneNumber = formatter.inputDigit(content.charAt(i));
             }
             phoneNumber = phoneNumber.trim();
             if (!phoneNumber.isEmpty()) {
                 selfChanged = true;
                 phoneNumberEt.setText(phoneNumber);
                 //we will advance the cursor to the end in the next run look above
             }
         }
     }
 */
    private View.OnClickListener listener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (v.getId() == R.id.bt_loginButton) {
                validateAndContinue();
            } else if (v.getId() == R.id.tv_signup) {
                toggleSignUpCLogin(((TextView) v));
            } else {
                throw new AssertionError();
            }
        }
    };
    private CountriesListAdapter countriesSpinnerAdapter;
    private AsyncTask<Void, Void, Void> setUpCountriesTask = new AsyncTask<Void, Void, Void>() {
        @Override
        protected void onPreExecute() {
            progressDialog.show(getFragmentManager(), null);
        }

        @Override
        protected Void doInBackground(Void... params) {
            Realm realm = Country.REALM(getActivity());
            try {
                // TODO: 8/4/2015 change this and pass the input stream directly to realm
                JSONArray array = new JSONArray(IOUtils.toString(getActivity().getAssets().open("countries.json"), Charsets.UTF_8));
                JSONObject cursor;
                Locale locale;
                realm.beginTransaction();
                realm.clear(Country.class);

                for (int i = 0; i < array.length(); i++) {
                    cursor = array.getJSONObject(i);
                    if (cursor.optString(Country.FIELD_CCC, "").isEmpty()) {
                        continue; //cleans up the assets file
                    }
                    final String isoCode = cursor.getString(Country.FIELD_ISO_2_LETTER_CODE);
                    locale = new Locale("", isoCode);
                    String localisedName = locale.getDisplayCountry().trim();
                    if (localisedName.equalsIgnoreCase(isoCode)) {
                        localisedName = cursor.getString(Country.FIELD_NAME) + " (" + localisedName + ")";
                    }
                    Country country = new Country();
                    country.setName(localisedName.isEmpty() ? cursor.getString(Country.FIELD_NAME) : localisedName);
                    country.setCcc(cursor.getString(Country.FIELD_CCC));
                    country.setIso2letterCode(isoCode);
                    realm.copyToRealm(country);
                }
                realm.commitTransaction();
                for (Country country : realm.where(Country.class).findAll()) {
                    CLog.i(TAG, country.toString());
                }
            } catch (IOException | JSONException e) {
                if (BuildConfig.DEBUG) {
                    CLog.e(TAG, e.getMessage(), e.getCause());
                } else {
                    CLog.e(TAG, e.getMessage());
                }
            } finally {
                realm.close();
            }
//            SystemClock.sleep(10000);
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            progressDialog.dismiss();
            setUpSpinner();
            UiHelpers.showToast(Config.deviceArc() + "  " + Config.supportsCalling());
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
        progressDialog = UiHelpers.newProgressDialog();
        validator = new FormValidator();
        validator.addStrategy(phoneNumberStrategy)
                .addStrategy(usernameStrategy);
        setUpSpinner();
        TextView tv = (TextView) view.findViewById(R.id.tv_signup);
        tv.setOnClickListener(listener);
        loginButton.setOnClickListener(listener);
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        setUpCountriesAndContinue();
    }

    @Override
    public void onPause() {
        super.onPause();
        callback.getActivityPreferences().edit().putString(LOCALE_KEY, Locale.getDefault().getCountry()).commit();
    }

    private void setUpCountriesAndContinue() {
        //we need to do all the time to automatically handle configuration changes see setupCountriesTask#doInBackGround
        String userLocale = callback.getActivityPreferences().getString(LOCALE_KEY, Locale.getDefault().getCountry());
        boolean reloadAssets = !userLocale.equals(Locale.getDefault().getCountry());
        if (reloadAssets) {
            setUpCountriesTask.execute();
        } else {
            Realm realm = Country.REALM(getActivity());
            long countries = realm.where(Country.class).count();
            realm.close();
            if (countries < 240) {
                setUpCountriesTask.execute();
            }
        }
    }

    private void validateAndContinue() {
        phoneNumber = phoneNumberEt.getText().toString().trim();
        userCountry = ((Country) spinner.getSelectedItem()).getIso2letterCode();
        userName = usernameEt.getText().toString().trim();

        if (userCountryStrategy.validate() && usernameStrategy.validate() && phoneNumberStrategy.validate()) {
            attemptCLoginOrSignUp();
        }
    }

    private void toggleSignUpCLogin(TextView v) {
        if (isCLoggingIn) {
            isCLoggingIn = false;
            v.setText(R.string.st_already_have_an_account);
            usernameEt.setVisibility(View.VISIBLE);
            usernameEt.requestFocus();
            loginButton.setText(R.string.sign_up_button_label);
        } else {
            isCLoggingIn = true;
            v.setText(R.string.dont_have_an_account_sign_up);
            usernameEt.setVisibility(View.GONE);
            phoneNumberEt.requestFocus();
            loginButton.setText(R.string.log_in_button_label);
        }
    }

    private void attemptCLoginOrSignUp() {
        doAttemptCLogin();
    }

    private void doAttemptCLogin() {
        if (isCLoggingIn) {
            callback.onCLogin(phoneNumber, userCountry);
        } else {
            callback.onSignUp(userName, phoneNumber, userCountry);
        }
    }

    private void showRequiredFieldDialog(String field) {
        UiHelpers.showErrorDialog((PairAppBaseActivity) getActivity(), getString(R.string.required_field_error, field));
    }

    private void setUpSpinner() {
        countriesSpinnerAdapter = new CountriesListAdapter(getActivity(), realm.where(Country.class).findAllSorted(Country.FIELD_NAME));
        countriesSpinnerAdapter.setDropDownViewResource(R.layout.country_spinner_item);
        spinner.setAdapter(countriesSpinnerAdapter);
        spinner.setSelection(0);
        spinner.setOnItemSelectedListener(onItemSelectedListener);
    }

    interface Callbacks {
        void onCLogin(String phoneNumber, String userIsoCountry);

        void onSignUp(String userName, String phoneNumber, String userIsoCountry);

        SharedPreferences getActivityPreferences();
    }
}

