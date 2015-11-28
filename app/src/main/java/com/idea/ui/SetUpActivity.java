package com.idea.ui;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;

import com.idea.Errors.ErrorCenter;
import com.idea.PairApp;
import com.idea.data.ContactSyncService;
import com.idea.data.UserManager;
import com.idea.messenger.PairAppClient;
import com.idea.pairapp.R;
import com.idea.util.Config;
import com.idea.util.UiHelpers;
import com.rey.material.app.DialogFragment;


public class SetUpActivity extends PairAppBaseActivity implements VerificationFragment.Callbacks,
        ChooseDisplayPictureFragment.Callbacks, LoginFragment.Callbacks {

    static final int UNKNOWN = -1, LOGIN_STAGE = 0, VERIFICATION_STAGE = 1, DP_STAGE = 2, COMPLETE = 3;
    private static final String STAGE = "staSKDFDge", SETUP_PREFS_KEY = "setuSLFKA", OUR_TAG = "ourTag";
    int attempts = 0;
    private int stage = UNKNOWN;
    private DialogFragment progressDialog;
    private String TAG = SetUpActivity.class.getSimpleName();
    private final UserManager.CallBack loginOrSignUpCallback = new UserManager.CallBack() {
        @Override
        public void done(Exception e) {
            UiHelpers.dismissProgressDialog(progressDialog);
            if (e == null) {
                stage = VERIFICATION_STAGE;
                next();
            } else {
                String message = e.getMessage();
                if ((message == null) || (message.isEmpty())) {
                    message = getString(R.string.an_error_occurred);
                }
                ErrorCenter.reportError(TAG, message);
            }
        }
    };

    private static SharedPreferences getSharedPreferences() {
        return Config.getPreferences(SETUP_PREFS_KEY);
    }

    protected static boolean isEveryThingOk() {
        return getStage() == COMPLETE;
    }

    protected static int getStage() {
        return UserManager.getInstance().getIntPref(STAGE, UNKNOWN);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.set_up_activity);
        progressDialog = UiHelpers.newProgressDialog();
    }

    @Override
    protected void onResume() {
        super.onResume();
        stage = userManager.getIntPref(STAGE, UNKNOWN);
        next();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveState();
    }

    private Object saveState() {
        return userManager.putStandAlonePref(STAGE, stage);
    }

    private void next() {
        saveState();
        Fragment fragment;// = getSupportFragmentManager().findFragmentById(R.id.container);
        if (stage == UNKNOWN) {
            if (isUserLoggedIn()) {
                if (isUserVerified()) {
                    stage = DP_STAGE;
                } else {
                    stage = VERIFICATION_STAGE;
                    return;
                }
            } else {
                stage = LOGIN_STAGE;
            }
        }
        fragment = findFragment();
        addFragment(fragment);
    }

    @NonNull
    private Fragment findFragment() {
        Fragment fragment = getSupportFragmentManager().findFragmentByTag(OUR_TAG + stage);
        if (fragment != null) {
            return fragment;
        }
        switch (stage) {
            case DP_STAGE:
                fragment = new ChooseDisplayPictureFragment();
                break;
            case VERIFICATION_STAGE:
                fragment = new VerificationFragment();
                break;
            case LOGIN_STAGE:
                //fall through
            default:
                fragment = new LoginFragment();
                break; //redundant but safe
        }
        return fragment;
    }

    private void addFragment(Fragment fragment) {
        getSupportFragmentManager().beginTransaction().replace(R.id.container, fragment, OUR_TAG + stage).commit();
    }

    private void doGoBackToLogin() {
        progressDialog.show(getSupportFragmentManager(), null);
        UserManager.getInstance().reset(new UserManager.CallBack() {
            @Override
            public void done(Exception e) {
                UiHelpers.dismissProgressDialog(progressDialog);
                if (e == null) {
                    stage = LOGIN_STAGE;
                    next();
                } else {
                    ErrorCenter.reportError(TAG, e.getMessage());
                }
            }
        });
    }

    @Override
    public void onVerified() {
        stage = DP_STAGE;
        next();
    }

    @Override
    public void onBackToCLogIn() {
        doGoBackToLogin();
    }

    private void completeSetUp() {
        if (stage != COMPLETE) {
            next();
            return;
        }
        getActivityPreferences().edit().putInt(STAGE, COMPLETE).commit();
        PairApp.enableComponents();
        ContactSyncService.syncIfRequired(this);
        PairAppClient.startIfRequired(this);
        UiHelpers.gotoMainActivity(this);
    }

    @Override
    public void onDp(final String newDp) {
        if (newDp.equals(getCurrentUser().getDP())) {
            stage = COMPLETE;
            completeSetUp();
        } else if (attempts++ > 3) {
            ErrorCenter.reportError(TAG, getString(R.string.permanently_disconnected));
        } else {
            doChangeDp(newDp);
        }
    }

    private void doChangeDp(final String newDp) {
        progressDialog.show(getSupportFragmentManager(), null);
        userManager.changeDp(newDp, new UserManager.CallBack() {
            @Override
            public void done(Exception e) {
                UiHelpers.dismissProgressDialog(progressDialog);
                if (e != null) {
                    try {
                        UiHelpers.
                                showErrorDialog(SetUpActivity.this, e.getMessage(),
                                        getString(R.string.try_again), getString(android.R.string.ok), new UiHelpers.Listener() {
                                            @Override
                                            public void onClick() {
                                                onDp(newDp);
                                            }
                                        }, null);
                    } catch (Exception ignored) {

                    }
                } else {
                    stage = COMPLETE;
                    completeSetUp();
                }
            }
        });
    }

    @Override
    public void onCancelled() {
        stage = COMPLETE;
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
    public int placeHolderDp() {
        return R.drawable.user_avartar;
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

    @Override
    public SharedPreferences getActivityPreferences() {
        return SetUpActivity.getSharedPreferences();
    }
}
