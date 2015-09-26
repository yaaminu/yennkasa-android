package com.pair.ui;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.telephony.PhoneNumberUtils;
import android.text.Editable;
import android.text.Selection;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import com.google.i18n.phonenumbers.AsYouTypeFormatter;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.pair.adapter.CountriesListAdapter;
import com.pair.data.Country;
import com.pair.data.UserManager;
import com.pair.pairapp.R;
import com.pair.util.FormValidator;
import com.pair.util.PhoneNumberNormaliser;
import com.pair.util.UiHelpers;
import com.rey.material.widget.Spinner;
import com.rey.material.widget.TextView;

import java.util.Locale;

import io.realm.Realm;
import io.realm.RealmResults;

/**
 * @author by Null-Pointer on 5/28/2015.
 */
@SuppressWarnings("ConstantConditions FieldCanBeLocal")
public class LoginFragment extends Fragment {
    public static final String TAG = LoginFragment.class.getSimpleName();
    private Button loginButton;
    private EditText usernameEt, phoneNumberEt;
    private Realm realm;
    private boolean isLoggingIn = true;
    private Spinner spinner;
    private String userName, phoneNumber, userCountry;
    private FormValidator validator;
    private Callbacks callback;
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
                        doAttemptLogin();
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


    // although this works i am not confident it will work in all cases as a result am using
    // the android implementation

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
                toggleSignUpLogin(((TextView) v));
            } else {
                throw new AssertionError();
            }
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

        validator = new FormValidator();
        validator.addStrategy(phoneNumberStrategy)
                .addStrategy(usernameStrategy);

        final CountriesListAdapter adapter = new CountriesListAdapter(getActivity(), realm.where(Country.class).findAllSorted(Country.FIELD_NAME));
        adapter.setDropDownViewResource(R.layout.country_spinner_item);
        //quick hit text
        new AsyncTask<Void, Void, Void>() {
            int position = -1;

            @Override
            protected Void doInBackground(Void... params) {
                Realm realm = Country.REALM(getActivity());
                RealmResults<Country> countries = realm.where(Country.class).findAllSorted(Country.FIELD_NAME);
                String defaultCC = Locale.getDefault().getCountry();
                for (int i = 0; i < countries.size(); i++) {
                    //it is the same query that we are using. our countries assets remains unchanged once it is loaded
                    Country country = countries.get(i);
                    if (country.getIso2letterCode().equals(defaultCC)) {
                        //our countries adapter uses a hack that makes it's dateset inconsistent with the actual dataset
                        //so we have to add one to the position we had.
                        position = i + 1;
                        break;
                    }
                }
                realm.close();
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                if (spinner.getSelectedItemPosition() == 0 && position != -1) {
                    spinner.setSelection(position);
                }
            }
        }.execute();
        spinner.setAdapter(adapter);
        spinner.setSelection(0);
        spinner.setOnItemSelectedListener(onItemSelectedListener);
        TextView tv = (TextView) view.findViewById(R.id.tv_signup);
        tv.setOnClickListener(listener);
        loginButton.setOnClickListener(listener);
        return view;
    }

    private void validateAndContinue() {
        phoneNumber = phoneNumberEt.getText().toString().trim();
        userCountry = ((Country) spinner.getSelectedItem()).getIso2letterCode();
        userName = usernameEt.getText().toString().trim();

        if (userCountryStrategy.validate() && usernameStrategy.validate() && phoneNumberStrategy.validate()) {
            attemptLoginOrSignUp();
        }
    }

    private void toggleSignUpLogin(TextView v) {
        if (isLoggingIn) {
            isLoggingIn = false;
            v.setText(R.string.st_already_have_an_account);
            usernameEt.setVisibility(View.VISIBLE);
            usernameEt.requestFocus();
            loginButton.setText(R.string.sign_up_button_label);
        } else {
            isLoggingIn = true;
            v.setText(R.string.dont_have_an_account_sign_up);
            usernameEt.setVisibility(View.GONE);
            phoneNumberEt.requestFocus();
            loginButton.setText(R.string.log_in_button_label);
        }
    }


    private void attemptLoginOrSignUp() {
        doAttemptLogin();
    }

    private void doAttemptLogin() {
        if (isLoggingIn) {
            callback.onLogin(phoneNumber, userCountry);
        } else {
            callback.onSignUp(userName, phoneNumber, userCountry);
        }
    }

    private void showRequiredFieldDialog(String field) {
        UiHelpers.showErrorDialog((PairAppBaseActivity) getActivity(), getString(R.string.required_field_error, field));
    }

    interface Callbacks {
        void onLogin(String phoneNumber, String userIsoCountry);

        void onSignUp(String userName, String phoneNumber, String userIsoCountry);
    }

    /**
     * from the android source code {@link android.telephony.PhoneNumberFormattingTextWatcher}
     * God bless open source software!
     */
    private class MyTextWatcher implements TextWatcher {

        private boolean mSelfChange = false;

        private boolean mStopFormatting;

        private AsYouTypeFormatter mFormatter;

        public MyTextWatcher() {
            this(Locale.getDefault().getCountry());
        }


        public MyTextWatcher(String countryCode) {
            if (countryCode == null) throw new IllegalArgumentException();
            mFormatter = PhoneNumberUtil.getInstance().getAsYouTypeFormatter(countryCode);
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count,
                                      int after) {
            if (mSelfChange || mStopFormatting) {
                return;
            }
            // If the user manually deleted any non-dialable characters, stop formatting
            if (count > 0 && hasSeparator(s, start, count)) {
                stopFormatting();
            }
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            if (mSelfChange || mStopFormatting) {
                return;
            }
            // If the user inserted any non-dialable characters, stop formatting
            if (count > 0 && hasSeparator(s, start, count)) {
                stopFormatting();
            }
        }

        @Override
        public synchronized void afterTextChanged(Editable s) {
            if (mStopFormatting) {
                // Restart the formatting when all texts were clear.
                mStopFormatting = !(s.length() == 0);
                return;
            }
            if (mSelfChange) {
                // Ignore the change caused by s.replace().
                return;
            }
            String formatted = reformat(s, Selection.getSelectionEnd(s));
            if (formatted != null) {
                int rememberedPos = mFormatter.getRememberedPosition();
                mSelfChange = true;
                s.replace(0, s.length(), formatted, 0, formatted.length());
                // The text could be changed by other TextWatcher after we changed it. If we found the
                // text is not the one we were expecting, just give up calling setSelection().
                if (formatted.equals(s.toString())) {
                    Selection.setSelection(s, rememberedPos);
                }
                mSelfChange = false;
            }
        }

        private String reformat(CharSequence s, int cursor) {
            // The index of char to the leftward of the cursor.
            int curIndex = cursor - 1;
            String formatted = null;
            mFormatter.clear();
            char lastNonSeparator = 0;
            boolean hasCursor = false;
            int len = s.length();
            for (int i = 0; i < len; i++) {
                char c = s.charAt(i);
                if (PhoneNumberUtils.isNonSeparator(c)) {
                    if (lastNonSeparator != 0) {
                        formatted = getFormattedNumber(lastNonSeparator, hasCursor);
                        hasCursor = false;
                    }
                    lastNonSeparator = c;
                }
                if (i == curIndex) {
                    hasCursor = true;
                }
            }
            if (lastNonSeparator != 0) {
                formatted = getFormattedNumber(lastNonSeparator, hasCursor);
            }
            return formatted;
        }

        private String getFormattedNumber(char lastNonSeparator, boolean hasCursor) {
            return hasCursor ? mFormatter.inputDigitAndRememberPosition(lastNonSeparator)
                    : mFormatter.inputDigit(lastNonSeparator);
        }

        private void stopFormatting() {
            mStopFormatting = true;
            mFormatter.clear();
        }

        private boolean hasSeparator(final CharSequence s, final int start, final int count) {
            for (int i = start; i < start + count; i++) {
                char c = s.charAt(i);
                if (!PhoneNumberUtils.isNonSeparator(c)) {
                    return true;
                }
            }
            return false;
        }
    }
}

