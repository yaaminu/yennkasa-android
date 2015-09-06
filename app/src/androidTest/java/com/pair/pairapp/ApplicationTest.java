package com.pair.pairapp;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Log;

/**
 * <a href="http://d.android.com/tools/testing/testing_android.html">Testing Fundamentals</a>
 */
public class ApplicationTest extends AndroidTestCase {
    public static final String TAG = ApplicationTest.class.getSimpleName();

    @SmallTest
    public void testRun() throws Exception {
        Log.i(TAG, "test running");
    }
}