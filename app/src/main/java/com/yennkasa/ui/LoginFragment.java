package com.yennkasa.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.google.i18n.phonenumbers.NumberParseException;
import com.yennkasa.BuildConfig;
import com.yennkasa.R;
import com.yennkasa.data.Country;
import com.yennkasa.data.UserManager;
import com.yennkasa.util.Config;
import com.yennkasa.util.FormValidator;
import com.yennkasa.util.GenericUtils;
import com.yennkasa.util.PLog;
import com.yennkasa.util.PhoneNumberNormaliser;
import com.yennkasa.util.TaskManager;
import com.yennkasa.util.ThreadUtils;
import com.yennkasa.util.UiHelpers;
import com.yennkasa.view.MyTextWatcher;

import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Locale;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.OnTextChanged;
import butterknife.OnTouch;
import io.realm.Realm;

/**
 * @author by Null-Pointer on 5/28/2015.
 */
@SuppressWarnings("ConstantConditions FieldCanBeLocal")
public class LoginFragment extends Fragment {
    public static final String TAG = LoginFragment.class.getSimpleName();
    public static final String LOCALE_KEY = "locale";
    public static final String USER_NAME = "userName";
    public static final String USER_ID = "userId";
    public static final String COUNTRY = "country";
    private static final String SAVED_COUNTRY = "saved.country";
    @Nullable
    public static Intent results;

    @Bind(R.id.et_user_city)
    EditText userCity;

    @Bind(R.id.bt_loginButton)
    Button loginButton;

    @Bind(R.id.tv_ccc)
    TextView countryCCC;
    @Bind(R.id.et_username)
    TextView usernameEt;
    @Bind(R.id.et_phone_number_field)
    TextView phoneNumberEt;

    @Bind(R.id.problems_logging_in)
    TextView signUpLoginNotice;

    @Bind(R.id.tv_app_version)
    TextView version;

    private Realm countriesRealm;
    private String userName, phoneNumber, userCountry;
    private FormValidator validator;
    private Callbacks callback;
    private ProgressDialog progressDialog;

    private FormValidator.ValidationStrategy userCountryStrategy = new FormValidator.ValidationStrategy() {
        @Override
        public boolean validate() {
            String ccc = countryCCC.getText().toString().trim();
            if (GenericUtils.isEmpty(ccc)) {
                countryCCC.requestFocus();
                // TODO: 12/15/16 take user directly to countries activity
                showRequiredFieldDialog(getString(R.string.country));
                return false;
            }
            return true;
        }
    }, usernameStrategy = new FormValidator.ValidationStrategy() {
        @Override
        public boolean validate() {
            if (TextUtils.isEmpty(userName)) {
                UiHelpers.showErrorDialog(getActivity(), getString(R.string.username_required));
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
    private FormValidator.ValidationStrategy cityStrategy = new FormValidator.ValidationStrategy() {
        @Override
        public boolean validate() {
            city = userCity.getText().toString().trim();
            if (city.length() == 1) {
                UiHelpers.showErrorDialog(getActivity(), getString(R.string.city_name_too_short));
                return false;
            }
            if (city.length() >= 2) {
                if (city.matches("[\\w| \\-']{2,}")) {
                    return true;
                } else {
                    UiHelpers.showErrorDialog(getActivity(), getString(R.string.city_name_invalid));
                    return false;
                }
            }
            //city name is optional
            return true;
        }
    };
    private String city;

    @SuppressLint("SetTextI18n")
    private void initialiseUserCountryCCC() {
        if (countryCCC.getText().toString().trim().isEmpty()) {
            TelephonyManager manager = ((TelephonyManager) getActivity().getSystemService(Context.TELEPHONY_SERVICE));
            String ccc = PhoneNumberNormaliser.getCCC(manager.getSimCountryIso());
            if (GenericUtils.isEmpty(ccc)) {
                countryCCC.setText("+000");
            } else {
                countryCCC.setText("+" + ccc);
            }
        }
    }

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
    MyTextWatcher watcher;
    static final MyTextWatcher dummyWatcher = new MyTextWatcher();

    @OnTextChanged(R.id.tv_ccc)
    void onTextChanged(Editable text) {
        if (!GenericUtils.isEmpty(text)) {
            Country country = countriesRealm.where(Country.class)
                    .equalTo(Country.FIELD_CCC, text.toString().substring(1)).findFirst();
            if (country != null) {
                watcher = new MyTextWatcher(country.getIso2letterCode());
            } else {
                watcher = dummyWatcher;
            }
        } else {
            watcher = dummyWatcher;
        }
        phoneNumberEt.removeTextChangedListener(watcher);
        phoneNumberEt.addTextChangedListener(watcher);
    }

    //the ontouch callback seems to be called multiple times
    //so we set a flag to handle this anomaly. always reset in
    //onActivityResult()
    boolean selectingCountry = false;

    @OnTouch(R.id.tv_ccc)
    boolean touch(View v) {
        if (!selectingCountry) {
            selectingCountry = true;
            Intent intent = new Intent(getContext(), CountryLists.class);
            startActivityForResult(intent, SetUpActivity.REQUEST_CODE_GET_COUNTRY);
        }
        return true;
    }

    @OnClick({R.id.bt_loginButton, R.id.problems_logging_in})
    void onClick(View v) {
        if (v.getId() == R.id.bt_loginButton) {
            validateAndContinue();
        } else if (v.getId() == R.id.problems_logging_in) {
            // TODO: 12/23/2015 redirect user to my website
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("http://yennkasa.com/support?p=login&platform=android_" + Build.VERSION.SDK_INT + "&clientVersion=" + BuildConfig.VERSION_NAME + "&locale=" + Locale.getDefault().getCountry()));
            try {
                startActivity(intent);
            } catch (ActivityNotFoundException e) {
                PLog.d(TAG, "this is strange no browser");
                UiHelpers.showErrorDialog(getActivity(), "You have no browser on you phone, you may install one from the play store");
            }
        } else {
            throw new AssertionError();
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        countriesRealm = Country.REALM(getActivity());
        validator = new FormValidator();
        validator.addStrategy(phoneNumberStrategy)
                .addStrategy(usernameStrategy)
                .addStrategy(cityStrategy);
    }

    @SuppressLint("SetTextI18n")
    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.login_fragment, container, false);
        ButterKnife.bind(this, view);
        progressDialog = new ProgressDialog(getActivity());
        progressDialog.setMessage(getString(R.string.st_please_wait));
        progressDialog.setCancelable(false);

        if (savedInstanceState != null) {
            String savedCountry = savedInstanceState.getString(COUNTRY);
            if (savedCountry != null) {
                String savedCountryCCC = savedInstanceState.getString(SAVED_COUNTRY);
                if (!GenericUtils.isEmpty(savedCountry)) {
                    countryCCC.setText(savedCountryCCC);
                }
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
        if (phoneNumberEt.getText().toString().trim().isEmpty()) {
            phoneNumberEt.setText(PhoneNumberNormaliser.getUserPhoneNumber(getContext()));
        }
        initialiseUserCountryCCC();
        version.setText(BuildConfig.VERSION_NAME);
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        setUpCountriesAndContinue();
    }

    @SuppressLint("CommitPrefEdits")
    @Override
    public void onPause() {
        super.onPause();
        callback.getActivityPreferences().edit().putString(LOCALE_KEY, Locale.getDefault().getCountry()).commit();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (results != null) { //quick fix for some platforms where result intent is not set.
            resultCode = Activity.RESULT_OK;
            requestCode = SetUpActivity.REQUEST_CODE_GET_COUNTRY;
            data = results;
        }
        if (requestCode == SetUpActivity.REQUEST_CODE_GET_COUNTRY) {
            selectingCountry = false;
            if (resultCode == Activity.RESULT_OK) {
                String ccc = data.getStringExtra(Country.FIELD_CCC);
                GenericUtils.ensureNotEmpty(ccc);
                countryCCC.setText("+" + ccc);
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void setUpCountriesAndContinue() {
        //we need to do all the time to automatically handle configuration changes see setupCountriesTask#doInBackGround
        String userLocale = callback.getActivityPreferences().getString(LOCALE_KEY, Locale.getDefault().getCountry());
        boolean reloadAssets = !userLocale.equals(Locale.getDefault().getCountry());
        if (reloadAssets) {
            loadCountries();
        } else {
            Realm realm = Country.REALM(getActivity());
            try {
                long countries = realm.where(Country.class).count();
                if (countries < 240) {
                    loadCountries();
                } else {
                    initialiseUserCountryCCC();
                }
            } finally {
                realm.close();
            }
        }
    }

    private void loadCountries() {
        progressDialog.show();
        TaskManager.executeNow(new Runnable() {
            @Override
            public void run() {
                if (ThreadUtils.isMainThread()) {
                    initialiseUserCountryCCC();
                    progressDialog.dismiss();
                } else {
                    doLoadCountriesInBackground();
                    TaskManager.executeOnMainThread(this);
                }
            }
        }, false);
    }

    private void doLoadCountriesInBackground() {
        Realm realm = Country.REALM(Config.getApplicationContext());
        try {
            // TODO: 8/4/2015 change this and pass the input stream directly to countriesRealm
            JSONArray array = new JSONArray(IOUtils.toString(getActivity().getAssets().open("countries.json"), Charsets.UTF_8));
            JSONObject cursor;
            Locale locale;
            realm.beginTransaction();
            realm.delete(Country.class);
            realm.commitTransaction();
            realm.beginTransaction();
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
        } catch (IOException | JSONException e) {
            if (BuildConfig.DEBUG) {
                PLog.e(TAG, e.getMessage(), e.getCause());
            } else {
                PLog.e(TAG, e.getMessage());
            }
        } finally {
            realm.close();
        }
    }


    private void validateAndContinue() {
        phoneNumber = phoneNumberEt.getText().toString().trim();

        String countryCCC = this.countryCCC.getText().
                toString();
        userCountry = "";
        if (!GenericUtils.isEmpty(countryCCC)) {
            Country country = countriesRealm.where(Country.class)
                    .equalTo(Country.FIELD_CCC, countryCCC.substring(1)).findFirst();
            if (country != null) {
                userCountry = country.getIso2letterCode();
            }
        }
        userName = usernameEt.getText().toString().trim();
        city = userCity.getText().toString().trim();
        if (userCountryStrategy.validate() && usernameStrategy.validate() && phoneNumberStrategy.validate() && cityStrategy.validate()) {
            attemptLoginOrSignUp();
        }
    }

    private void attemptLoginOrSignUp() {
        doAttemptCLogin();
    }

    private void doAttemptCLogin() {
        try {
            new AlertDialog.Builder(getContext())
                    .setMessage(getString(R.string.number_confirm, "+" + PhoneNumberNormaliser.toIEE(phoneNumber, userCountry)))
                    .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            callback.onSignUp(userName, phoneNumber, userCountry, city);
                        }
                    }).setNegativeButton(android.R.string.no, null)
                    .create().show();
        } catch (NumberParseException e) {
            throw new RuntimeException(e);
        }
    }

    private void showRequiredFieldDialog(String field) {
        UiHelpers.showErrorDialog(getActivity(), getString(R.string.required_field_error, field));
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        String country = countryCCC.getText().toString().trim();
        outState.putString(SAVED_COUNTRY, country);
        outState.putString(USER_NAME, usernameEt.getText().toString());
        outState.putString(USER_ID, phoneNumberEt.getText().toString());
        super.onSaveInstanceState(outState);
    }

    interface Callbacks {

        void onSignUp(String userName, String phoneNumber, String userIsoCountry, String city);

        SharedPreferences getActivityPreferences();
    }
}

