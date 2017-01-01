package com.yennkasa.util;

import android.app.Application;
import android.content.Context;

import org.junit.Test;
import org.mockito.Matchers;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * author Null-Pointer on 1/16/2016.
 */
@PowerMockIgnore({"org.mockito.*", "org.robolectric.*", "android.*"})
public class GenericUtilsTest {

    @Test
    public void testEnsureNotNull() throws Exception {
        try {
            GenericUtils.ensureNotNull((String[]) null);
            fail("must throw");
        } catch (IllegalArgumentException ignored) {

        }
        try {
            GenericUtils.ensureNotNull("fafaf", new Object(), null, null);
            fail("must throw");
        } catch (IllegalArgumentException ignored) {

        }
        try {
            GenericUtils.ensureNotNull(null, null, null);
            fail("must throw");
        } catch (IllegalArgumentException ignored) {

        }
        try {
            GenericUtils.ensureNotNull(new Object(), null);
            fail("must throw");
        } catch (IllegalArgumentException ignored) {

        }
        GenericUtils.ensureNotNull("foo");
    }


    @Test
    public void testEnsureNotNull1() throws Exception {
        final String message = "must not be null";
        try {
            GenericUtils.ensureNotNull(null, message);
            fail("must throw");
        } catch (IllegalArgumentException ignored) {
            assertEquals(message, ignored.getMessage());
        }
        try {
            GenericUtils.ensureNotNull("foo", null);
            fail("must throw");
        } catch (IllegalArgumentException ignored) {

        }
        try {
            GenericUtils.ensureNotNull(null, null);
            fail("must throw");
        } catch (IllegalArgumentException ignored) {

        }
    }

    @Test
    public void testIsEmpty() throws Exception {
        assertTrue(GenericUtils.isEmpty(null));
        assertTrue(GenericUtils.isEmpty("  "));
        assertTrue(GenericUtils.isEmpty(" "));
        assertTrue(GenericUtils.isEmpty(""));
        assertFalse(GenericUtils.isEmpty(" m         a "));
        assertFalse(GenericUtils.isEmpty("fooobar"));
    }

    @Test
    @PrepareForTest
    public void testGetString() throws Exception {
        //power mock required
        Application application = PowerMockito.mock(Application.class);
        Context context = PowerMockito.mock(Context.class);
        PowerMockito.when(application.getApplicationContext()).thenReturn(context);
        int num = Matchers.anyInt();
        PowerMockito.when(context.getString(num)).thenReturn("sample string");
        Config.init(application);
        assertEquals("sample string", GenericUtils.getString(num));
    }

    @Test
    public void testEnsureNotEmpty() throws Exception {
        try {
            GenericUtils.ensureNotEmpty((String[]) null);
            fail("must throw");
        } catch (IllegalArgumentException ignored) {

        }
        try {
            GenericUtils.ensureNotEmpty("");
            fail("must throw");
        } catch (IllegalArgumentException ignored) {

        }
        try {
            GenericUtils.ensureNotEmpty("faf", null, "");
            fail("must throw");
        } catch (IllegalArgumentException ignored) {

        }
        try {
            GenericUtils.ensureNotEmpty("fafaf", "", null);
            fail("must throw");
        } catch (IllegalArgumentException ignored) {

        }
    }

    @Test
    public void testEnsureConditionsValid() throws Exception {
        GenericUtils.ensureConditionTrue(true, "must be true");
        try {
            GenericUtils.ensureConditionTrue(false, "must be true");
            fail("must throw");
        } catch (IllegalArgumentException ignored) {
        }
    }

    @Test
    public void testCapitalise() throws Exception {
        String test1 = "some String to be Capitalised FOR SeRiousNess",
                cap1 = "Some String To Be Capitalised For Seriousness",
                test2 = "SOME TEXT FOR TESTING",
                cap2 = "Some Text For Testing",
                test3 = "some text for testing",
                cap3 = "Some Text For Testing",
                test4 = "some&&   teXt %%for tYpE9900 TesTing",
                cap4 = "Some&&   Text %%for Type9900 Testing";

        assertEquals("", GenericUtils.capitalise(""));
        assertEquals("", GenericUtils.capitalise(null));
        assertEquals(" ", GenericUtils.capitalise(" "));
        assertEquals(cap1, GenericUtils.capitalise(test1));
        assertEquals(cap2, GenericUtils.capitalise(test2));
        assertEquals(cap3, GenericUtils.capitalise(test3));
        assertEquals(cap4, GenericUtils.capitalise(test4));
    }

    @Test
    public void testIsCapitalised() throws Exception {
        String textString1 = "Capitalised Strings Are Fun",
                 textString2 = "unCapitalised STringS suck",
                textString3 = "One",
                textString4 = "one";
        assertTrue(GenericUtils.isCapitalised(textString1));
        assertFalse(GenericUtils.isCapitalised(textString2));
        assertTrue(GenericUtils.isCapitalised(textString3));
        assertFalse(GenericUtils.isCapitalised(textString4));
    }
}
