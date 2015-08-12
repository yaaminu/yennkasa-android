package com.pair.pairapp;

import android.app.ProgressDialog;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;

import com.pair.data.Country;
import com.pair.data.UserManager;
import com.pair.pairapp.ui.LoginFragment;
import com.pair.pairapp.ui.SignupFragment;
import com.pair.pairapp.ui.VerificationFragment;
import com.pair.util.GcmUtils;

import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;

import java.io.IOException;

import io.realm.Realm;


public class SetUpActivity extends ActionBarActivity {


    public static final String ACTION = "ac",
            LOGIN_FRAG = "login",
            SIGNUP_FRAG = "signup",VERIFICATION_FRAGMENT = "vfrag";
    private String TAG = SetUpActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(GcmUtils.checkPlayServices(this)) {
            setContentView(R.layout.set_up_activity);
            Realm realm = Realm.getInstance(this);
            boolean countriesSetup = realm.where(Country.class).count() >= 243;
            realm.close();
            if (!countriesSetup) {
                setUpCountriesTask.execute();
            }else{
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
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.container);
        if (fragment == null) {
            if (UserManager.getInstance().isUserLoggedIn()) {
                if (UserManager.getInstance().isUserVerified()) {
                    throw new RuntimeException("user logged and verified "); // FIXME: 7/31/2015 remove this
                }
                fragment = new VerificationFragment();
            } else {
                fragment = new LoginFragment();
            }
        }
        String fTag;
        if (fragment instanceof LoginFragment) fTag = LOGIN_FRAG;
        else if (fragment instanceof SignupFragment) fTag = SIGNUP_FRAG;
        else fTag = VERIFICATION_FRAGMENT;
        getSupportFragmentManager().beginTransaction().replace(R.id.container, fragment, fTag).commit();
    }

    ProgressDialog pDialog;
    private AsyncTask<Void, Void, Void> setUpCountriesTask = new AsyncTask<Void, Void, Void>() {
        @Override
        protected void onPreExecute() {
            pDialog = new ProgressDialog(SetUpActivity.this);
            pDialog.setMessage(getString(R.string.initialising));
            pDialog.setCancelable(false);
            pDialog.show();
        }

        @Override
        protected Void doInBackground(Void... params) {
            Realm realm = Realm.getInstance(SetUpActivity.this);
            try {
                realm.beginTransaction();
                // TODO: 8/4/2015 change this and pass the input stream directly to realm
                realm.createAllFromJson(Country.class, IOUtils.toString(getAssets().open("countries.json"), Charsets.UTF_8));
                realm.commitTransaction();
            } catch (IOException e) {
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, e.getMessage(), e.getCause());
                } else {
                    Log.e(TAG, e.getMessage());
                }
                realm.cancelTransaction();
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
