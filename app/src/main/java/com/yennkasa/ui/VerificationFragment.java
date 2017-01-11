package com.yennkasa.ui;


import android.app.ProgressDialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import com.yennkasa.Errors.ErrorCenter;
import com.yennkasa.R;
import com.yennkasa.data.UserManager;
import com.yennkasa.data.VerificationHelper;
import com.yennkasa.messenger.FireBaseInstantIDService;
import com.yennkasa.util.Config;
import com.yennkasa.util.Event;
import com.yennkasa.util.EventBus;
import com.yennkasa.util.PLog;
import com.yennkasa.util.TaskManager;
import com.yennkasa.util.ThreadUtils;
import com.yennkasa.util.TypeFaceUtil;
import com.yennkasa.util.UiHelpers;
import com.yennkasa.util.ViewUtils;

import butterknife.ButterKnife;
import butterknife.OnTextChanged;
import io.realm.Realm;

import static com.yennkasa.data.UserManager.VERIFICAITON_CODE_RECIEVED;
import static com.yennkasa.data.VerificationHelper.VERIFICATION_FAILED;
import static com.yennkasa.data.VerificationHelper.VERIFICATION_INITIATED;
import static com.yennkasa.data.VerificationHelper.VERIFICATION_SUCCESS;

/**
 * A simple {@link Fragment} subclass.
 */
public class VerificationFragment extends Fragment {
    private static final String TAG = VerificationFragment.class.getSimpleName();
    private static final String KEY_TOKEN_SENT = "tokenSent" + TAG;
    public static final String VERIFICATION_TOKEN = "verificationToken";
    private ProgressDialog progressDialog;
    private EditText etVerification;
    private Callbacks callback;
    private final View.OnClickListener listener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (v.getId() == R.id.bt_resend_token) {
                sendToken();
            } else if (v.getId() == R.id.bt_change_number) {
                changeNumber();
            }
        }
    };

    private void changeNumber() {
        Config.getApplicationWidePrefs().edit().remove(KEY_TOKEN_SENT).apply();
        callback.onBackToCLogIn();
    }

    private TextView buttonChangeNumber;
    private TextView resendToken;
    private TextView notice;

    private void sendToken() {
        etVerification.setText("");
        progressDialog.show();
        helper = new VerificationHelper(UserManager.getMainUserId(callback.getRealm()));
        helper.sendVerificationToken();
    }

    public VerificationFragment() {
        // Required empty public constructor
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        setRetainInstance(true);
        try {
            callback = (Callbacks) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.getClass().getName() + " must implement Callbacks interface");
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EventBus.getDefault().register(eventsListener, VERIFICAITON_CODE_RECIEVED,
                VERIFICATION_FAILED, VERIFICATION_INITIATED,
                VERIFICATION_SUCCESS);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_verification, container, false);
        ButterKnife.bind(this, view);
        buttonChangeNumber = (TextView) view.findViewById(R.id.bt_change_number);
        buttonChangeNumber.setOnClickListener(listener);
        ViewUtils.setTypeface(buttonChangeNumber, TypeFaceUtil.ROBOTO_REGULAR_TTF);

        resendToken = (TextView) view.findViewById(R.id.bt_resend_token);
        resendToken.setOnClickListener(listener);
        ViewUtils.setTypeface(resendToken, TypeFaceUtil.ROBOTO_REGULAR_TTF);

        etVerification = ((EditText) view.findViewById(R.id.et_verification));
        ViewUtils.setTypeface(etVerification, TypeFaceUtil.ROBOTO_REGULAR_TTF);

        notice = (TextView) view.findViewById(R.id.tv_verification_notice);
        ViewUtils.setTypeface(notice, TypeFaceUtil.ROBOTO_LIGHT_TTF);
        setUpViews(buttonChangeNumber, resendToken, notice);
        progressDialog = new ProgressDialog(getContext());
        progressDialog.setMessage(getString(R.string.st_please_wait));
        progressDialog.setCancelable(false);
        if (savedInstanceState != null) {
            String token = savedInstanceState.getString(VERIFICATION_TOKEN);
            if (token != null) {
                etVerification.setText(token);
            }
        }
        return view;
    }

    @OnTextChanged(R.id.et_verification)
    void onTextChanged(Editable text) {
        if (text.toString().trim().length() == 5) {
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    doVerifyUser();
                }
            }, 500);
        }
    }

    @Nullable
    VerificationHelper helper;
    private final EventBus.EventsListener eventsListener = new EventBus.EventsListener() {
        @Override
        public int threadMode() {
            return EventBus.MAIN;
        }

        @Override
        public void onEvent(EventBus yourBus, Event event) {
            String tag = event.getTag().toString();
            switch (tag) {
                case VERIFICAITON_CODE_RECIEVED:
                    if (event.getData() != null) {
                        String code = event.getData().toString().trim();
                        if (!TextUtils.isEmpty(code)) {
                            etVerification.setText(code);
                        }
                    }
                    break;
                case VERIFICATION_INITIATED:
                    if (progressDialog.isShowing()) {
                        progressDialog.hide();
                    }
                    Config.getApplicationWidePrefs().edit().putBoolean(KEY_TOKEN_SENT, true).apply();
                    setUpViews(buttonChangeNumber, resendToken, notice);
                    break;
                case VERIFICATION_FAILED:
                    //noinspection ThrowableResultOfMethodCallIgnored,ConstantConditions
                    UiHelpers.showErrorDialog(getActivity(), event.getError().getMessage());
                    if (progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }
                    break;
                case VERIFICATION_SUCCESS:
                    progressDialog.setCancelable(false);
                    if (!progressDialog.isShowing()) {
                        progressDialog.show();
                    }
                    UserManager.getInstance().verifyUser(callback.getRealm(), "token-unused",
                            FireBaseInstantIDService.getInstanceID(), new UserManager.CallBack() {
                                @Override
                                public void done(Exception e) {
                                    progressDialog.dismiss();
                                    if (e == null) {
                                        callback.onVerified();
                                    } else {
                                        UiHelpers.showErrorDialog(getActivity(), e.getMessage());
                                    }
                                }
                            });
                    break;
                default:
                    throw new AssertionError();
            }

            if (event.isSticky()) {
                yourBus.removeStickyEvent(event);
            }
            event.recycle();
        }

        @Override
        public boolean sticky() {
            return true;
        }
    };


    @Override
    public void onDestroy() {
        EventBus.getDefault().unregister(VERIFICAITON_CODE_RECIEVED, eventsListener);
        EventBus.getDefault().unregister(VERIFICATION_INITIATED, eventsListener);
        EventBus.getDefault().unregister(VERIFICATION_FAILED, eventsListener);
        EventBus.getDefault().unregister(VERIFICATION_SUCCESS, eventsListener);
        super.onDestroy();
    }

    private void setUpViews(TextView buttonVerify, TextView resendToken, TextView notice) {

        boolean tokenSent = Config.getApplicationWidePrefs().getBoolean(KEY_TOKEN_SENT, false);
        if (tokenSent) {
            ViewUtils.showViews(etVerification);
        } else {
            ViewUtils.hideViews(etVerification);
        }
        notice.setText(tokenSent ? R.string.st_send_verification_notice : R.string.send_token_notice);
        resendToken.setText(tokenSent ? R.string.st_send_again : R.string.send_token);
    }

    private void doVerifyUser() {
        final String code = etVerification.getText().toString().trim();
        if (TextUtils.isEmpty(code) || code.length() < 4 && !TextUtils.isDigitsOnly(code)) {
            UiHelpers.showErrorDialog(getActivity(), getString(R.string.token_invalid));
        } else {
            progressDialog.show();
            updateProgress(getString(R.string.verifying_account_stage_1));
            TaskManager.executeNow(new Runnable() {
                @Override
                public void run() {
                    String token = fetchFirebaseInstanceID(); //might block
                    if (token == null) {
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                ErrorCenter.reportError(TAG, getString(R.string.st_unable_to_connect));
                                progressDialog.dismiss();
                            }
                        });
                    } else {
                        updateProgress(getString(R.string.verifying_account_stage_2));
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (helper == null) {
                                    helper = new VerificationHelper(UserManager.getMainUserId(callback.getRealm()));
                                }
                                helper.verify(code);
                            }
                        });
                    }
                }
            }, true);

        }

    }

    private void updateProgress(final String message) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                progressDialog.setMessage(message);
            }
        });
    }

    @Nullable
    private String fetchFirebaseInstanceID() {
        ThreadUtils.ensureNotMain();
        int attempts = 0;
        while (attempts++ < 4) {
            try {
                String token = FireBaseInstantIDService.getInstanceID();
                if (token == null) {
                    long time = 3000L * attempts;
                    Thread.sleep(time);
                    continue;
                }
                return token;
            } catch (InterruptedException e) {
                PLog.e(TAG, e.getMessage(), e);
            }
        }
        return null;
    }


    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putString(VERIFICATION_TOKEN, etVerification.getText().toString());
        super.onSaveInstanceState(outState);
    }

    public interface Callbacks {
        void onVerified();

        void onBackToCLogIn();

        Realm getRealm();
    }
}
