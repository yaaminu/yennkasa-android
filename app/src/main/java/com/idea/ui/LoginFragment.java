package com.idea.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import com.idea.adapter.CountriesListAdapter;
import com.idea.data.Country;
import com.idea.data.UserManager;
import com.idea.pairapp.BuildConfig;
import com.idea.pairapp.R;
import com.idea.util.FormValidator;
import com.idea.util.PLog;
import com.idea.util.PhoneNumberNormaliser;
import com.idea.util.TypeFaceUtil;
import com.idea.util.UiHelpers;
import com.idea.util.ViewUtils;
import com.idea.view.MyTextWatcher;
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
    public static final String SPINER_POSITION = "spinerPosition";
    public static final String USER_NAME = "userName";
    public static final String USER_ID = "userId";
    public static final String LOGGIN_IN = "logginIn";
    public static final String COUNTRY = "country";
    private Button loginButton;
    private EditText usernameEt, phoneNumberEt;
    private Realm realm;
    private boolean isLoggingIn = true;
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
            if (isLoggingIn) return true;
            if (TextUtils.isEmpty(userName)) {
                showRequiredFieldDialog(getString(R.string.username_hint));
                usernameEt.requestFocus();
                return false;
            } else {
                android.support.v4.util.Pair<String, String> errorNamePair = UserManager.getInstance().isValidUserName(userName);
                if (errorNamePair.second != null) {
                    UiHelpers.showErrorDialog(getActivity(), errorNamePair.second);
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
                UiHelpers.showErrorDialog(getActivity(),
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
                toggleSignUpLogin();
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
                    PLog.i(TAG, country.toString());
                }
            } catch (IOException | JSONException e) {
                if (BuildConfig.DEBUG) {
                    PLog.e(TAG, e.getMessage(), e.getCause());
                } else {
                    PLog.e(TAG, e.getMessage());
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
        }

    };
    private TextView signUpLoginNotice;

    public LoginFragment() {
    }

    @Override
    public void onAttach(Context activity) {
        super.onAttach(activity);
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
        ViewUtils.setTypeface(phoneNumberEt, TypeFaceUtil.ROBOTO_REGULAR_TTF);

        loginButton = (Button) view.findViewById(R.id.bt_loginButton);
        ViewUtils.setTypeface(loginButton, TypeFaceUtil.ROBOTO_REGULAR_TTF);

        usernameEt = (EditText) view.findViewById(R.id.et_username);
        ViewUtils.setTypeface(usernameEt, TypeFaceUtil.ROBOTO_REGULAR_TTF);

        spinner = ((Spinner) view.findViewById(R.id.sp_ccc));
        progressDialog = UiHelpers.newProgressDialog();
        validator = new FormValidator();
        validator.addStrategy(phoneNumberStrategy)
                .addStrategy(usernameStrategy);
        setUpSpinner();
        signUpLoginNotice = (TextView) view.findViewById(R.id.tv_signup);
        ViewUtils.setTypeface(signUpLoginNotice, TypeFaceUtil.ROBOTO_REGULAR_TTF);
        signUpLoginNotice.setOnClickListener(listener);

        loginButton.setOnClickListener(listener);

        android.widget.TextView appName = ((android.widget.TextView) view.findViewById(R.id.tv_app_name));
        ViewUtils.setTypeface(appName, TypeFaceUtil.two_d_font);


        android.widget.TextView copyRight = ((android.widget.TextView) view.findViewById(R.id.copy_right));
        ViewUtils.setTypeface(copyRight, TypeFaceUtil.two_d_font);
        if (savedInstanceState != null) {
            String savedCountry = savedInstanceState.getString(COUNTRY);
            if (savedCountry != null) {
                int savedPosition = savedInstanceState.getInt(SPINER_POSITION, 0);
                if (savedPosition > 0 &&/*avoid out of bound during times the spinner's connect might have changed*/ spinner.getAdapter().getCount() > savedPosition) {
                    Country country = (Country) spinner.getAdapter().getItem(savedPosition);
                    if (country.getIso2letterCode().equals(savedCountry)) {
                        spinner.setSelection(savedPosition);
                    } else {
                        int itemsCount = spinner.getAdapter().getCount();
                        for (int i = 0; i < itemsCount; i++) {
                            Country c = ((Country) spinner.getAdapter().getItem(i));
                            if (c.getIso2letterCode().equals(savedCountry)) {
                                spinner.setSelection(i);
                                onItemSelectedListener.onItemSelected(spinner, null, i, -1);
                                break;
                            }
                        }
                    }
                }
            }
            isLoggingIn = savedInstanceState.getBoolean(LOGGIN_IN, true);
            if (isLoggingIn) {
                signUpLoginNotice.setText(R.string.dont_have_an_account_sign_up);
                usernameEt.setVisibility(View.GONE);
                phoneNumberEt.requestFocus();
                loginButton.setText(R.string.log_in_button_label);
            } else {
                signUpLoginNotice.setText(R.string.st_already_have_an_account);
                usernameEt.setVisibility(View.VISIBLE);
                usernameEt.requestFocus();
                loginButton.setText(R.string.sign_up_button_label);
            }
            String userName = savedInstanceState.getString(USER_NAME);
            if (userName != null) {
                usernameEt.setText(userName);
            }
            userName = savedInstanceState.getString(USER_ID);
            if (userName != null) {
                phoneNumberEt.setText(userName);
            }
        }

//        //re-use appName
//        appName = ((android.widget.TextView) view.findViewById(R.id.tv_app_name));
//        ViewUtils.setTypeface(appName, TypeFaceUtil.DROID_SERIF_REGULAR_TTF);

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

    private void toggleSignUpLogin() {
        if (isLoggingIn) {
            isLoggingIn = false;
            signUpLoginNotice.setText(R.string.st_already_have_an_account);
            usernameEt.setVisibility(View.VISIBLE);
            usernameEt.requestFocus();
            loginButton.setText(R.string.sign_up_button_label);
        } else {
            isLoggingIn = true;
            signUpLoginNotice.setText(R.string.dont_have_an_account_sign_up);
            usernameEt.setVisibility(View.GONE);
            phoneNumberEt.requestFocus();
            loginButton.setText(R.string.log_in_button_label);
        }
    }

    private void attemptCLoginOrSignUp() {
        doAttemptCLogin();
    }

    private void doAttemptCLogin() {
        if (isLoggingIn) {
            callback.onLogin(phoneNumber, userCountry);
        } else {
            callback.onSignUp(userName, phoneNumber, userCountry);
        }
    }

    private void showRequiredFieldDialog(String field) {
        UiHelpers.showErrorDialog(getActivity(), getString(R.string.required_field_error, field));
    }

    private void setUpSpinner() {
        countriesSpinnerAdapter = new CountriesListAdapter(getActivity(), realm.where(Country.class).findAllSorted(Country.FIELD_NAME));
        countriesSpinnerAdapter.setDropDownViewResource(R.layout.country_spinner_item);
        spinner.setAdapter(countriesSpinnerAdapter);
        spinner.setSelection(0);
        spinner.setOnItemSelectedListener(onItemSelectedListener);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        int selectedItemPosition = spinner.getSelectedItemPosition();
        outState.putInt(SPINER_POSITION, selectedItemPosition);
        outState.putString(USER_NAME, usernameEt.getText().toString());
        outState.putString(USER_ID, phoneNumberEt.getText().toString());
        outState.putBoolean(LOGGIN_IN, isLoggingIn);
        if (selectedItemPosition >= 1) {
            outState.putString(COUNTRY, ((Country) spinner.getAdapter().getItem(selectedItemPosition)).getIso2letterCode());
        }
        super.onSaveInstanceState(outState);
    }

    interface Callbacks {
        void onLogin(String phoneNumber, String userIsoCountry);

        void onSignUp(String userName, String phoneNumber, String userIsoCountry);

        SharedPreferences getActivityPreferences();
    }
}

