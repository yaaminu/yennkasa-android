package com.pair.util;

import android.util.Log;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import com.pair.data.UserManager;
import com.pair.pairapp.BuildConfig;

import java.util.regex.Pattern;

/**
 * @author Null-Pointer on 7/25/2015.
 */
public class PhoneNumberNormaliser {
    private static String TAG = PhoneNumberNormaliser.class.getSimpleName();

    private PhoneNumberNormaliser() {
        throw new IllegalStateException("cannot instantiate");
    }

    //rough pattern - 00********** or +*********** or 011********** or 166************* (166 is special for dialing us numbers from thailand)
    // any char that is not either + or digit is considered non-dialable
    private static final Pattern NON_DIALABLE_PATTERN = Pattern.compile("[^\\d]");

    public static String toIEE(String phoneNumber, String defaultRegion) throws NumberParseException {
        if (phoneNumber == null) {
            throw new IllegalArgumentException("phoneNumber is null!");
        }
        if (defaultRegion == null) {
            throw new IllegalArgumentException("defaultRegion is null!");
        }
        PhoneNumberUtil utils = PhoneNumberUtil.getInstance();
        Phonenumber.PhoneNumber number = utils.parse(phoneNumber, defaultRegion);
        return number.getCountryCode() + "" /*convert to  string*/ + number.getNationalNumber();
    }

    public static boolean isIEE_Formatted(String phoneNumber, String region) {
        // FIXME: 8/23/2015 test this `if condition`. this could be a bug!
        PhoneNumberUtil util = PhoneNumberUtil.getInstance();
        if (!phoneNumber.startsWith("+") && phoneNumber.startsWith("00") && phoneNumber.startsWith("011")) {
            phoneNumber = "+" + phoneNumber; // numbers like 233 20 4441069 will be parsed with no exception.
        }
        try {
            return util.isValidNumber(util.parse(phoneNumber, null));
        } catch (NumberParseException e) {
            return false;
        }
    }

    public static boolean isValidPhoneNumber(String number, String countryIso) {
        PhoneNumberUtil util = PhoneNumberUtil.getInstance();
        try {
            return util.isValidNumber(util.parse(number, countryIso));
        } catch (NumberParseException e) {
            return false;
        }
    }

    public static String cleanNonDialableChars(String phoneNumber) {
        if (phoneNumber == null) {
            throw new IllegalArgumentException("phone number is null!");
        }
        boolean wasIEEFormatted = phoneNumber.indexOf('+') != -1;
        String ret = NON_DIALABLE_PATTERN.matcher(phoneNumber).replaceAll("");
        if (wasIEEFormatted) {
            ret = "+" + ret;
        }
        return ret;
    }

    public static String getCCC(String userCountryISO) {
        if (userCountryISO == null) throw new IllegalArgumentException("user country iso is null");
        PhoneNumberUtil util = PhoneNumberUtil.getInstance();
        int ccc = util.getCountryCodeForRegion(userCountryISO);
        return ccc + "";
    }

    public static String toLocalFormat(String phoneNumber) {
        final PhoneNumberUtil util = PhoneNumberUtil.getInstance();
        try {
            return util.formatOutOfCountryCallingNumber((util.parse("+" + phoneNumber, null)), UserManager.getInstance().getUserCountryISO());
        } catch (NumberParseException e) {
            Log.e(TAG, e.getMessage(), e.getCause());
            if (BuildConfig.DEBUG) {
                throw new RuntimeException("invalid user id");
            }
            return phoneNumber;
        }
    }

    public static String getTrunkPrefix(String gh) {
        PhoneNumberUtil util = PhoneNumberUtil.getInstance();
        return util.getNddPrefixForRegion(gh, true);
    }
}
