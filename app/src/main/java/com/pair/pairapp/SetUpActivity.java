package com.pair.pairapp;

import android.app.ProgressDialog;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;

import com.pair.data.Country;
import com.pair.data.User;
import com.pair.pairapp.ui.LoginFragment;

import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;

import java.io.IOException;

import io.realm.Realm;


public class SetUpActivity extends ActionBarActivity {


    public static final String ACTION = "ac";
    public static final String LOGIN_FRAG = "login",SIGNUP_FRAG = "signup";
    public User registeringUser;
    public String CCC;
    private String TAG = SetUpActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.set_up_activity);
        Realm realm = Realm.getInstance(this);
        boolean countriesSetup = realm.where(Country.class).count() >= 243;
        realm.close();
        if(!countriesSetup){
             setUpCountriesTask.execute();
        }
        registeringUser = new User();
        registeringUser.setType(User.TYPE_NORMAL_USER);
        //add login fragment
        addFragment();
    }

    private void addFragment() {
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.container);
        if (fragment == null) {
            fragment = new LoginFragment();
        }
        String fTag = fragment instanceof LoginFragment?LOGIN_FRAG:SIGNUP_FRAG;
        getSupportFragmentManager().beginTransaction().replace(R.id.container, fragment, fTag).commit();
    }

    public void verificationCancelled(){
        addFragment();
    }

    ProgressDialog pDialog;
    private AsyncTask<Void,Void,Void> setUpCountriesTask = new AsyncTask<Void, Void, Void>() {
        @Override
        protected void onPreExecute() {
            pDialog = new ProgressDialog(SetUpActivity.this);
            pDialog.setMessage(getString(R.string.st_please_wait));
            pDialog.setCancelable(false);
            pDialog.show();
        }

        @Override
        protected Void doInBackground(Void... params) {
            Realm realm = Realm.getInstance(SetUpActivity.this);
            try {
                realm.beginTransaction();
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
        }
    };
}
