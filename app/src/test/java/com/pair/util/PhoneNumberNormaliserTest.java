package com.pair.util;

import android.telephony.PhoneNumberUtils;

import com.pair.pairapp.BuildConfig;
import com.pair.workers.PhoneNumberNormaliser;

import junit.framework.TestCase;

import org.apache.tools.ant.types.Assertions;
import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.junit.Assert.assertSame;

import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.robolectric.internal.bytecode.InstrumentingClassLoader;

import java.util.regex.Pattern;

/**
 * @author Null-Pointer on 7/25/2015.
 */

public class PhoneNumberNormaliserTest extends TestCase {
    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }


    @Test
    public void testRelplaceNonDialable()throws Exception{
        assertEquals("0204441069",PhoneNumberNormaliser.replaceNonDialable("020-444 10 69"));
        assertEquals("+0204441069",PhoneNumberNormaliser.replaceNonDialable("+  020-444 10 69"));
        assertEquals("0204441069",PhoneNumberNormaliser.replaceNonDialable(" (020)-444 10 69"));
        assertEquals("0204441069",PhoneNumberNormaliser.replaceNonDialable("020w(444) 10 69"));
        assertEquals("+233204441069",PhoneNumberNormaliser.replaceNonDialable("(+233) 204441069"));
        try {
            PhoneNumberNormaliser.replaceNonDialable(null);
        }catch (Exception e){
            assertSame(IllegalArgumentException.class,e.getClass());
        }
    }
    @Ignore
    public void testNormalise()throws Exception{
        try {
            PhoneNumberNormaliser.normalise(null,"+233");
        } catch (Exception e) {
          assertSame(IllegalArgumentException.class,e.getClass());
        }
        try {
            PhoneNumberNormaliser.normalise("0266349205",null);
        } catch (Exception e) {
            assertSame(IllegalArgumentException.class,e.getClass());
        }
        try {
            PhoneNumberNormaliser.normalise("0266349205","233");
        } catch (Exception e) {
            assertSame(IllegalArgumentException.class,e.getClass());
        }

        assertEquals("+233266349205", PhoneNumberNormaliser.normalise("0266349205", "+233"));
        assertEquals("+233266349205", PhoneNumberNormaliser.normalise("(026)-(634)9205", "+233"));
        assertEquals("+233266349205", PhoneNumberNormaliser.normalise("00233266349205", "+233"));
        assertEquals("+233266349205", PhoneNumberNormaliser.normalise("011233266349205", "+233"));
        assertEquals("+233266349205", PhoneNumberNormaliser.normalise("+233266349205", "+233"));
        assertEquals("+233266349205", PhoneNumberNormaliser.normalise("(+233)266349205", "+233"));
        assertEquals("+233266349205", PhoneNumberNormaliser.normalise("166233266349205", "+233"));
    }



}
