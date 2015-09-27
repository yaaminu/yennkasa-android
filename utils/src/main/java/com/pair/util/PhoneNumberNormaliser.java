package com.pair.util;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

import java.util.regex.Pattern;


/**
 * @author Null-Pointer on 7/25/2015.
 */
public class PhoneNumberNormaliser {
    //rough pattern - 00********** or +*********** or 011********** or 166************* (166 is special for dialing us numbers from thailand)
    // any char that is not either + or digit is considered non-dialable
    private static final Pattern NON_DIALABLE_PATTERN = Pattern.compile("[^\\d]");
    private static String TAG = PhoneNumberNormaliser.class.getSimpleName();

    private PhoneNumberNormaliser() {
        throw new IllegalStateException("cannot instantiate");
    }

    public static String toIEE(String phoneNumber, String defaultRegion) throws NumberParseException {
        if (phoneNumber == null) {
            throw new IllegalArgumentException("phoneNumber is null!");
        }
        if (defaultRegion == null) {
            throw new IllegalArgumentException("defaultRegion is null!");
        }
        phoneNumber = cleanNonDialableChars(phoneNumber);
        PhoneNumberUtil utils = PhoneNumberUtil.getInstance();
        Phonenumber.PhoneNumber number = utils.parse(phoneNumber, defaultRegion);
        return number.getCountryCode() + "" /*convert to  string*/ + number.getNationalNumber();
    }

    public static boolean isIEE_Formatted(String phoneNumber, String region) {
        PhoneNumberUtil util = PhoneNumberUtil.getInstance();
        phoneNumber = cleanNonDialableChars(phoneNumber);
        if (!phoneNumber.startsWith("+") && phoneNumber.startsWith("00") && phoneNumber.startsWith("011")) {
            if (!phoneNumber.startsWith(util.getNddPrefixForRegion(region, true)))
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
        number = cleanNonDialableChars(number);
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
        StringBuilder ret = new StringBuilder(phoneNumber.length());
        for (int i = 0; i < phoneNumber.length(); i++) {
            char theChar = phoneNumber.charAt(i);
            if (Character.isDigit(theChar)) {
                ret.append(theChar);
            }
        }
        if (wasIEEFormatted) {
            ret = ret.insert(0, '+');
        }
        return ret.toString();
    }

    public static String getCCC(String userCountryISO) {
        if (userCountryISO == null) throw new IllegalArgumentException("user country iso is null");
        PhoneNumberUtil util = PhoneNumberUtil.getInstance();
        int ccc = util.getCountryCodeForRegion(userCountryISO);
        return ccc + "";
    }

    public static String toLocalFormat(String phoneNumber, String userCountry) {
        final PhoneNumberUtil util = PhoneNumberUtil.getInstance();
        phoneNumber = cleanNonDialableChars(phoneNumber);
        try {
            return util.formatOutOfCountryCallingNumber((util.parse("+" + phoneNumber, null)), userCountry);
        } catch (NumberParseException e) {
            PLog.e(TAG, e.getMessage(), e.getCause());
            if (BuildConfig.DEBUG) {
                throw new RuntimeException("invalid user id");
            }
            return phoneNumber;
        }
    }

    public static String getTrunkPrefix(String countryCode) {
        PhoneNumberUtil util = PhoneNumberUtil.getInstance();
        return util.getNddPrefixForRegion(countryCode, true);
    }
}
