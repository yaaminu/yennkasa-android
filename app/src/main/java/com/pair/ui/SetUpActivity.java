package com.pair.ui;

import android.content.res.Configuration;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;

import com.pair.Errors.ErrorCenter;
import com.pair.PairApp;
import com.pair.data.Country;
import com.pair.data.UserManager;
import com.pair.messenger.PairAppClient;
import com.pair.pairapp.BuildConfig;
import com.pair.pairapp.R;
import com.pair.util.Config;
import com.pair.util.UiHelpers;
import com.pair.workers.ContactSyncService;
import com.rey.material.app.DialogFragment;

import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

import io.realm.Realm;


public class SetUpActivity extends PairAppBaseActivity implements VerificationFragment.Callbacks,
        ChooseDisplayPictureFragment.Callbacks, LoginFragment.Callbacks {

    private String TAG = SetUpActivity.class.getSimpleName();
    DialogFragment progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.set_up_activity);
        progressDialog = UiHelpers.newProgressDialog();

        //we need to do all the time to automatically handle configuration changes see setupCountriesTask#doInBackGround
        setUpCountries();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setUpCountries();
    }

    private void setUpCountries() {
        Realm realm = Country.REALM(this);
        long countries = realm.where(Country.class).count();
        realm.close();
        if (countries < 240) {
            setUpCountriesTask.execute();
        } else {
            addFragment();
        }
    }

    private void addFragment() {
        Fragment fragment;// = getSupportFragmentManager().findFragmentById(R.id.container);
        if (isUserLoggedIn()) {
            if (isUserVerified()) {
                throw new RuntimeException("user logged and verified "); // FIXME: 7/31/2015 remove this
            }
            fragment = new VerificationFragment();
        } else {
            fragment = new LoginFragment();
        }
        addFragment(fragment);
    }

    private void addFragment(Fragment fragment) {
        getSupportFragmentManager().beginTransaction().replace(R.id.container, fragment, null).commit();
    }

    private AsyncTask<Void, Void, Void> setUpCountriesTask = new AsyncTask<Void, Void, Void>() {
        @Override
        protected void onPreExecute() {
            progressDialog.show(getSupportFragmentManager(), null);
        }

        @Override
        protected Void doInBackground(Void... params) {
            Realm realm = Country.REALM(SetUpActivity.this);
            try {
                // TODO: 8/4/2015 change this and pass the input stream directly to realm
                JSONArray array = new JSONArray(IOUtils.toString(getAssets().open("countries.json"), Charsets.UTF_8));
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
                    Log.i(TAG, country.toString());
                }
            } catch (IOException | JSONException e) {
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, e.getMessage(), e.getCause());
                } else {
                    Log.e(TAG, e.getMessage());
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
            addFragment();
            UiHelpers.showToast(Config.deviceArc() + "  " + Config.supportsCalling());
        }

    };

    private void doGoBackToLogin() {
        progressDialog.show(getSupportFragmentManager(), null);
        UserManager.getInstance().reset(new UserManager.CallBack() {
            @Override
            public void done(Exception e) {
                UiHelpers.dismissProgressDialog(progressDialog);
                if (e == null) {
                    addFragment(new LoginFragment());
                } else {
                    ErrorCenter.reportError(TAG, e.getMessage());
                }
            }
        });
    }

    @Override
    public void onVerified() {
        addFragment(new ChooseDisplayPictureFragment());
        PairApp.enableComponents();
        ContactSyncService.syncIfRequired(this);
        PairAppClient.startIfRequired(this);
    }

    @Override
    public void onBackToLogIn() {
        doGoBackToLogin();
    }

    private void completeSetUp() {
        UiHelpers.gotoMainActivity(this);
    }

    int attempts = 0;

    @Override
    public void onDp(final String newDp) {
        if (attempts++ > 3) {
            ErrorCenter.reportError(TAG, getString(R.string.permanently_disconnected));
            return;
        }
        if (newDp != null && new File(newDp).exists()) {
            progressDialog.show(getSupportFragmentManager(), null);
            userManager.changeDp(newDp, new UserManager.CallBack() {
                @Override
                public void done(Exception e) {
                    UiHelpers.dismissProgressDialog(progressDialog);
                    if (e != null) {
                        try {
                            UiHelpers.
                                    showErrorDialog(SetUpActivity.this, e.getMessage(),
                                            getString(R.string.try_again), getString(R.string.later), new UiHelpers.Listener() {
                                                @Override
                                                public void onClick() {
                                                    onDp(newDp);
                                                }
                                            }, null);
                        } catch (Exception ignored) {

                        }
                    } else {
                        completeSetUp();
                    }
                }
            });
        }
    }

    @Override
    public void onCancelled() {
        completeSetUp();
    }

    @Override
    public boolean allowCancelling() {
        return true;
    }

    @Override
    public CharSequence noDpNotice() {
        return getString(R.string.choose_dp_help_friends_recognise_you);
    }

    @Override
    public String defaultDp() {
        return isUserLoggedIn() ? getCurrentUser().getDP() : null;
    }

    @Override
    public void onLogin(String phoneNumber, String userIsoCountry) {
        progressDialog.show(getSupportFragmentManager(), "");
        userManager.logIn(phoneNumber, userIsoCountry, loginOrSignUpCallback);
    }

    @Override
    public void onSignUp(String userName, String phoneNumber, String userIsoCountry) {
        progressDialog.show(getSupportFragmentManager(), "");
        userManager.signUp(userName, phoneNumber, userIsoCountry, loginOrSignUpCallback);
    }

    private final UserManager.CallBack loginOrSignUpCallback = new UserManager.CallBack() {
        @Override
        public void done(Exception e) {
            UiHelpers.dismissProgressDialog(progressDialog);
            if (e == null) {
                addFragment(new VerificationFragment());
            } else {
                String message = e.getMessage();
                if ((message == null) || (message.isEmpty())) {
                    message = getString(R.string.an_error_occurred);
                }
                ErrorCenter.reportError(TAG, message);
            }
        }
    };
}
