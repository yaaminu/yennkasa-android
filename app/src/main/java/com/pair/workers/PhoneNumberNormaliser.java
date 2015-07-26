package com.pair.workers;

import android.telephony.PhoneNumberUtils;

import java.util.regex.Pattern;

/**
 * @author Null-Pointer on 7/25/2015.
 */
public class PhoneNumberNormaliser {
    private PhoneNumberNormaliser() {
        throw new IllegalStateException("cannot instantiate");
    }

    //rough pattern - 00********** or +*********** or 011********** or 166************* (166 is special for dialing us numbers from thailand)
    // any char that is not either + or digit is considered non-dialable
    private static final Pattern GLOBAL_NUMBER_PATTERN = Pattern.compile("^(00|011|166)"),
            NON_DIALABLE_PATTERN = Pattern.compile("[^\\d]");

    public static String normalise(String phoneNumber, String defaultCountryCallingCode) {
        if (phoneNumber == null) {
            throw new IllegalArgumentException("phoneNumber is null!");
        }
        if (phoneNumber.length() < 7) {
            throw new IllegalArgumentException("phone number is too short");
        }
        if (defaultCountryCallingCode == null) {
            throw new IllegalArgumentException("defaultCountryCallingCode is null!");
        }
        if (!defaultCountryCallingCode.startsWith("+") || defaultCountryCallingCode.length() < 2) {
            throw new IllegalArgumentException("invalid ccc: " + defaultCountryCallingCode);
        }
        phoneNumber = replaceNonDialable(phoneNumber);
        if(phoneNumber.startsWith("+")){
            return phoneNumber;
        }
        if (GLOBAL_NUMBER_PATTERN.matcher(phoneNumber).find()) {
            return GLOBAL_NUMBER_PATTERN.matcher(phoneNumber).replaceFirst("+");
        }

        phoneNumber = phoneNumber.substring(1);
        return defaultCountryCallingCode + phoneNumber;
    }

    public static String replaceNonDialable(String phoneNumber) {
        if(phoneNumber == null){
            throw new IllegalArgumentException("phone number is null!");
        }
        boolean wasIEEFormatted = phoneNumber.indexOf('+') != -1;
        String ret= NON_DIALABLE_PATTERN.matcher(phoneNumber).replaceAll("");
        if(wasIEEFormatted){
            ret = "+"+ret;
        }
        return ret;
    }
}
