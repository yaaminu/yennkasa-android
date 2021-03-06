package com.yennkasa.ui;

import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.yennkasa.BuildConfig;
import com.yennkasa.Errors.ErrorCenter;
import com.yennkasa.R;
import com.yennkasa.Yennkasa;
import com.yennkasa.data.UserManager;
import com.yennkasa.messenger.SmsReciever;
import com.yennkasa.messenger.YennkasaClient;
import com.yennkasa.util.Config;
import com.yennkasa.util.GcmUtils;
import com.yennkasa.util.PLog;
import com.yennkasa.util.TypeFaceUtil;
import com.yennkasa.util.UiHelpers;
import com.yennkasa.util.ViewUtils;

import butterknife.ButterKnife;
import butterknife.OnClick;
import io.realm.Realm;

import static android.Manifest.permission.ACCESS_NETWORK_STATE;
import static android.Manifest.permission.CAMERA;
import static android.Manifest.permission.MODIFY_AUDIO_SETTINGS;
import static android.Manifest.permission.READ_CONTACTS;
import static android.Manifest.permission.READ_PHONE_STATE;
import static android.Manifest.permission.RECEIVE_SMS;
import static android.Manifest.permission.RECORD_AUDIO;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;


public class SetUpActivity extends PairAppBaseActivity implements VerificationFragment.Callbacks,
        ChooseDisplayPictureFragment.Callbacks, LoginFragment.Callbacks {

    public static final int REQUEST_CODE_GET_COUNTRY = 10001;
    static final int UNKNOWN = -1, LOGIN_STAGE = 0, VERIFICATION_STAGE = 1, DP_STAGE = 2, COMPLETE = 3;
    private static final String STAGE = "staSKDFDge", SETUP_PREFS_KEY = "setuSLFKA", OUR_TAG = "ourTag";
    public static final int PERMISSION_REQUEST_CODE = 101;
    private static final String[] permissions = new String[]{WRITE_EXTERNAL_STORAGE, RECEIVE_SMS, READ_CONTACTS, RECORD_AUDIO, CAMERA, MODIFY_AUDIO_SETTINGS, READ_PHONE_STATE, ACCESS_NETWORK_STATE};
    int attempts = 0;
    private int stage = UNKNOWN;
    private ProgressDialog progressDialog;
    private String TAG = SetUpActivity.class.getSimpleName();
    private final UserManager.CallBack loginOrSignUpCallback = new UserManager.CallBack() {
        @Override
        public void done(Exception e) {
            progressDialog.dismiss();
            if (e == null) {
                Yennkasa.enableComponent(SmsReciever.class);
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
        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage(getString(R.string.st_please_wait));
        progressDialog.setCancelable(false);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
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
        addFragment(findFragment());
    }

    @NonNull
    private Fragment findFragment() {

        Fragment fragment = getSupportFragmentManager().findFragmentByTag(OUR_TAG + stage);
        if (fragment != null) {
            return fragment;
        }

        switch (stage) {
            case DP_STAGE:
                Yennkasa.disableComponent(SmsReciever.class);
                fragment = new ChooseDisplayPictureFragment();
                break;
            case VERIFICATION_STAGE:
                Yennkasa.enableComponent(SmsReciever.class);
                fragment = new VerificationFragment();
                break;
            case LOGIN_STAGE:
                fragment = new LoginFragment();
                break;
            default:
                fragment = new IntroFragment();
                break; //redundant but safe
        }
        return fragment;
    }

    private void addFragment(Fragment fragment) {
        getSupportFragmentManager().beginTransaction().replace(R.id.container, fragment, OUR_TAG + stage).commitAllowingStateLoss();
    }

    private void doGoBackToLogin() {
        progressDialog.show();
        UserManager.getInstance().reset(userRealm, new UserManager.CallBack() {
            @Override
            public void done(Exception e) {
                progressDialog.dismiss();
                if (e == null) {
                    Yennkasa.disableComponent(SmsReciever.class);
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
        Yennkasa.disableComponent(SmsReciever.class);
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
        Yennkasa.enableComponents();
        YennkasaClient.startIfRequired(this);
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
        progressDialog.show();
        userManager.changeDp(userRealm, newDp, new UserManager.CallBack() {
            @Override
            public void done(Exception e) {
                progressDialog.dismiss();
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
    public void onSignUp(String userName, String phoneNumber, String userIsoCountry, String city) {
        progressDialog.show();
        userManager.signUp(userName, phoneNumber, userIsoCountry, city, loginOrSignUpCallback);
    }

    @Override
    public SharedPreferences getActivityPreferences() {
        return SetUpActivity.getSharedPreferences();
    }

    public static class IntroFragment extends Fragment {

        long created = 0;
        private String TAG = "IntroFragment";

        @OnClick({R.id.agree_and_continue, R.id.terms})
        void onclick(View v) {
            switch (v.getId()) {
                case R.id.agree_and_continue:
                    goToNext();
                    break;
                case R.id.terms:
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse("http://yennkasa.com/terms"));
                    try {
                        startActivity(intent);
                    } catch (ActivityNotFoundException e) {
                        PLog.d(TAG, "this is strange no browser");
                        UiHelpers.showErrorDialog(getActivity(), "You have no browser on you phone, you may install one from the play store");
                    }
                    break;
                default:
                    throw new AssertionError();
            }
        }

        @Nullable
        @Override
        public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            View view = inflater.inflate(R.layout.fragment_splash, container, false);
            ButterKnife.bind(this, view);
            view.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View view, MotionEvent motionEvent) {
                    if (SystemClock.uptimeMillis() - created > 2000) {
                        runnable.run();
                    }
                    return true;
                }

            });
            TextView appname = (TextView) view.findViewById(R.id.tv_app_name),
                    appVersion = ((TextView) view.findViewById(R.id.tv_app_version));
            appVersion.setText(BuildConfig.VERSION_NAME);
            ViewUtils.setTypeface(appname, TypeFaceUtil.two_d_font);
            ViewUtils.setTypeface(appVersion, TypeFaceUtil.ROBOTO_REGULAR_TTF);

            TextView copyRight = ((TextView) view.findViewById(R.id.copy_right));
            ViewUtils.setTypeface(copyRight, TypeFaceUtil.two_d_font);
            showWelcomAlert();
            return view;
        }

        private void showWelcomAlert() {
            new AlertDialog.Builder(getContext())
                    .setMessage(R.string.welcome_text)
                    .setPositiveButton(android.R.string.ok, null)
                    .setCancelable(false)
                    .create().show();
        }

        @Override
        public void onResume() {
            super.onResume();
            created = SystemClock.uptimeMillis();
            new Handler().postDelayed(runnable, 5000);
        }

        private void goToNext() {
            SetUpActivity activity = (SetUpActivity) getActivity();
            if (activity != null && activity.stage == UNKNOWN) {
                activity.stage = LOGIN_STAGE;
                activity.next();
            }
        }

        private final Runnable runnable = new Runnable() {
            @Override
            public void run() {
                if (getContext() == null) return;
                requestForPermission();
            }
        };

        private void requestForPermission() {
            requestPermissions(permissions, PERMISSION_REQUEST_CODE);
        }

        @Override
        public void onRequestPermissionsResult(int requestCode,
                                               @NonNull String permissions[], @NonNull int[] grantResults) {
            switch (requestCode) {
                case PERMISSION_REQUEST_CODE: {
                    if (grantResults.length == permissions.length) {
                        if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                            //we cannot continue without file system access
                            showDialogAndKillApp();
                            return;
                        }
                        if (grantResults[1] != PackageManager.PERMISSION_GRANTED) {
                            explainReason(RECEIVE_SMS);
                        } else {
                            showTermsView();
                        }
                    } else {
                        showDialogAndKillApp();
                    }
                }
                break;
                default:
                    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            }
        }

        private void showTermsView() {
            // TODO: 1/9/17 animate
            ViewUtils.hideViews(ButterKnife.findById(getView(), R.id.splash_view));
            ViewUtils.showViews(ButterKnife.findById(getView(), R.id.terms_view));
        }


        private void explainReason(String receiveSms) {
            if (shouldShowRequestPermissionRationale(receiveSms)) {
                new AlertDialog.Builder(getContext())
                        .setMessage(R.string.recieve_sms_explanation)
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                requestForPermission();
                            }
                        }).setNegativeButton(android.R.string.cancel, null)
                        .setCancelable(false)
                        .show();
            } else {
                showTermsView();
            }
        }

        private void showDialogAndKillApp() {
            new AlertDialog.Builder(getContext())
                    .setMessage(getString(R.string.reason_for_file_system_required, getString(R.string.app_name)))
                    .setTitle(R.string.permission_denied_title)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            getActivity().finish();
                        }
                    })
                    .setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                            getActivity().finish();
                        }
                    }).create().show();
        }
    }


    @Override
    protected void showMessage() {
        if (stage != UNKNOWN) {
            int message = 0;
            if (!GcmUtils.hasGcm()) {
                message = R.string.no_gcm_error_message;
            } else if (GcmUtils.gcmUpdateRequired()) {
                message = R.string.gcm_update_required_prompt;
            }
            if (message != 0) {
                final int tmp = message;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        UiHelpers.showStopAnnoyingMeDialog(SetUpActivity.this,
                                "gcmUnavialble" + TAG, R.string.stop_annoying_me, tmp, R.string.i_know, android.R.string.cancel, null, null);
                    }
                });
            }
        }
    }

    @Override
    public Realm getRealm() {
        return userRealm;
    }
}
