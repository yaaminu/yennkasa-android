package com.idea.messenger;

/**
 * a basic class that contains data sent as push with a
 * field that tells the purpose of the post
 *
 * @author Null-Pointer on 9/3/2015.
 */
class PushData {
    public final String whatAmI, actualDataInJson;

    static final String I_AM_A_MESSAGE = "MSG", i_AM_UPDATE = "UPDT", NEW_USER_JOINED = "NUJ";

    public PushData(String whatAmI, String actualDataInJson) {
        if (whatAmI == null || actualDataInJson == null) {
            throw new IllegalArgumentException("null!");
        }

        this.whatAmI = whatAmI;
        this.actualDataInJson = actualDataInJson;
    }
}
