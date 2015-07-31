package com.pair.util;

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
        }catch (Exception e){
            //correct
        }
        assertEquals("+233266349205", PhoneNumberNormaliser.toIEE("0266349205", "GH"));
        assertEquals("+233266349205", PhoneNumberNormaliser.toIEE("(026)-(634)9205", "GH"));
        assertEquals("+233266349205", PhoneNumberNormaliser.toIEE("00233266349205", "GH"));
        assertEquals("+233266349205", PhoneNumberNormaliser.toIEE("+233266349205", "GH"));
        assertEquals("+233266349205", PhoneNumberNormaliser.toIEE("(+233)266349205", "GH"));
        assertEquals("+233266349205", PhoneNumberNormaliser.toIEE("(233)266349205", "GH"));
    }

    @Test
    public void testIsEE_Fomatted() throws Exception {
        assertTrue(PhoneNumberNormaliser.isIEE_Formatted("+233266349205"));
        assertFalse(PhoneNumberNormaliser.isIEE_Formatted("0266349205"));
        assertTrue(PhoneNumberNormaliser.isIEE_Formatted("+233-266349205"));
    }

}
