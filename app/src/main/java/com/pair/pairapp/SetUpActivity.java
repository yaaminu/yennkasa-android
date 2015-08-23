package com.pair.pairapp;

import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;

import com.pair.data.Country;
import com.pair.data.UserManager;
import com.pair.pairapp.ui.LoginFragment;
import com.pair.pairapp.ui.VerificationFragment;
import com.pair.util.GcmUtils;
import com.pair.util.UiHelpers;
import com.rey.material.app.DialogFragment;

import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Locale;

import io.realm.Realm;


public class SetUpActivity extends ActionBarActivity {

    private String TAG = SetUpActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (GcmUtils.checkPlayServices(this)) {
            setContentView(R.layout.set_up_activity);
            Realm realm = Country.REALM(this);
            boolean countriesSetup = realm.where(Country.class).count() > 0;
            realm.close();
            if (!countriesSetup) {
                setUpCountriesTask.execute();
            } else {
                addFragment();
            }
            //add login fragment
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        GcmUtils.checkPlayServices(this);
    }

    private void addFragment() {
        Fragment fragment;// = getSupportFragmentManager().findFragmentById(R.id.container);
        if (UserManager.getInstance().isUserLoggedIn()) {
            if (UserManager.getInstance().isUserVerified()) {
                throw new RuntimeException("user logged and verified "); // FIXME: 7/31/2015 remove this
            }
            fragment = new VerificationFragment();
        } else {
            fragment = new LoginFragment();
        }
        getSupportFragmentManager().beginTransaction().replace(R.id.container, fragment, null).commit();
    }

    DialogFragment pDialog;
    private AsyncTask<Void, Void, Void> setUpCountriesTask = new AsyncTask<Void, Void, Void>() {
        @Override
        protected void onPreExecute() {
            pDialog = UiHelpers.newProgressDialog();
            pDialog.show(getSupportFragmentManager(), null);
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
                    final String localisedName = locale.getDisplayCountry().trim();
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
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            pDialog.dismiss();
            addFragment();
        }

    };
}
