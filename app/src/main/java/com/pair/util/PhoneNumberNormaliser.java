package com.pair.util;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

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
    private static final Pattern GLOBAL_NUMBER_PATTERN = Pattern.compile("^(00|011|166)"),
            NON_DIALABLE_PATTERN = Pattern.compile("[^\\d]");

    public static String toIEE(String phoneNumber, String defaultCountryCallingCode) throws NumberParseException {
        if (phoneNumber == null) {
            throw new IllegalArgumentException("phoneNumber is null!");
        }
        if (defaultCountryCallingCode == null) {
            throw new IllegalArgumentException("defaultCountryCallingCode is null!");
        }
        PhoneNumberUtil utils = PhoneNumberUtil.getInstance();
        Phonenumber.PhoneNumber number = utils.parse(phoneNumber, defaultCountryCallingCode);
        return "+" + number.getCountryCode() + number.getNationalNumber();
    }

    public static boolean isIEE_Formatted(String phoneNumber) {
        PhoneNumberUtil util = PhoneNumberUtil.getInstance();
        try {
            return util.isValidNumber(util.parse(phoneNumber,null));
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
}
