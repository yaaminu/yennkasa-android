package com.pairapp.util;

import com.google.i18n.phonenumbers.PhoneNumberUtil;

import junit.framework.TestCase;

import org.junit.Ignore;
import org.junit.Test;

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
    public void testRelplaceNonDialable() throws Exception {
        assertEquals("0204441069", PhoneNumberNormaliser.cleanNonDialableChars("020-444 10 69"));
        assertEquals("+0204441069", PhoneNumberNormaliser.cleanNonDialableChars("+  020-444 10 69"));
        assertEquals("0204441069", PhoneNumberNormaliser.cleanNonDialableChars(" (020)-444 10 69"));
        assertEquals("0204441069", PhoneNumberNormaliser.cleanNonDialableChars("020w(444) 10 69"));
        assertEquals("+233204441069", PhoneNumberNormaliser.cleanNonDialableChars("(+233) 204441069"));
        try {
            PhoneNumberNormaliser.cleanNonDialableChars(null);
            fail("must throw");
        } catch (Exception e) {
            assertSame(IllegalArgumentException.class, e.getClass());
        }
    }

    @Ignore
    public void testNormalise() throws Exception {
        try {
            PhoneNumberNormaliser.toIEE(null, "+233");
            fail("must throw");
        } catch (Exception e) {
            assertSame(IllegalArgumentException.class, e.getClass());
        }
        try {
            PhoneNumberNormaliser.toIEE("0266349205", null);
            fail("must throw");
        } catch (Exception e) {
            assertSame(IllegalArgumentException.class, e.getClass());
        }
        try {
            PhoneNumberNormaliser.toIEE("0266349205", "233");
            fail("must throw");
        } catch (Exception e) {
            //correct
        }
        final String expected = "233266349205";
        assertEquals(expected, PhoneNumberNormaliser.toIEE("0266349205", "GH"));
        assertEquals(expected, PhoneNumberNormaliser.toIEE("(026)-(634)9205", "GH"));
        assertEquals(expected, PhoneNumberNormaliser.toIEE("00233266349205", "GH"));
        assertEquals(expected, PhoneNumberNormaliser.toIEE("+233266349205", "GH"));
        assertEquals(expected, PhoneNumberNormaliser.toIEE("(+233)266349205", "GH"));
        assertEquals(expected, PhoneNumberNormaliser.toIEE("(233)266349205", "GH"));
    }

    @Test
    public void testIsEE_Fomatted() throws Exception {
        assertTrue(PhoneNumberNormaliser.isIEE_Formatted("+233266349205", "GH"));
        assertTrue(PhoneNumberNormaliser.isIEE_Formatted("+233-266349205", "GH"));
        assertTrue(PhoneNumberNormaliser.isIEE_Formatted("+233266349205", "GH"));
  }

    @Test
    public void testIsValidPhoneNumber() throws Exception{
        assertTrue(PhoneNumberNormaliser.isValidPhoneNumber("0266349205", "GH"));
        assertTrue(PhoneNumberNormaliser.isValidPhoneNumber("0204441069", "GH"));
        assertFalse(PhoneNumberNormaliser.isValidPhoneNumber("0766349205", "GH"));
        assertTrue(PhoneNumberNormaliser.isValidPhoneNumber("0266349205", "GH"));
        assertTrue(PhoneNumberNormaliser.isValidPhoneNumber("0236349205", "GH"));
        assertFalse(PhoneNumberNormaliser.isValidPhoneNumber("0766349205", "GH"));
        assertTrue(PhoneNumberNormaliser.isValidPhoneNumber("002348033557792", "GH"));
    }
    @Test
    public void testToLocalNumber() throws Exception {
        System.out.println(PhoneNumberUtil.getInstance().parse("+2330266564229", "GH"));
        System.out.print(PhoneNumberNormaliser.getTrunkPrefix("GH"));
    }

    @Test
    public void testGetTrunkPrefex() throws Exception {
        assertEquals("0", PhoneNumberNormaliser.getTrunkPrefix("GH"));
        assertEquals("1", PhoneNumberNormaliser.getTrunkPrefix("US"));
        assertEquals("8", PhoneNumberNormaliser.getTrunkPrefix("RU"));
    }
}
