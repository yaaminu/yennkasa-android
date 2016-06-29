package com.pairapp.util;

import android.os.Looper;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.rule.PowerMockRule;

import static com.pairapp.util.Echo.echo;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * author Null-Pointer on 1/20/2016.
 */
@PowerMockIgnore({"org.mockito.*", "org.robolectric.*", "android.*"})
@PrepareForTest({Looper.class})
public class ThreadUtilsTest {

    @Rule
    public PowerMockRule rule = new PowerMockRule();

    Thread mainThreadMock;
    private Looper mockLooper;

    @Before
    public void setUp() throws Exception {
        mainThreadMock = new Thread("mainthread");
        mockStatic(Looper.class);
        mockLooper = mock(Looper.class);
        when(Looper.getMainLooper()).thenReturn(mockLooper);
        when(mockLooper.getThread()).thenReturn(mainThreadMock);
    }

    @Test
    public void testIsMainThread() throws Exception {
        echo("testing isMainThread()");
        echo("must return false when currentThread != Looper.getMainLooper().getThread()");
        assertFalse(ThreadUtils.isMainThread());
        echo("must return true when currentThread == Looper.getMainLooper().getThread()");
        when(mockLooper.getThread()).thenReturn(Thread.currentThread());
        assertTrue(ThreadUtils.isMainThread());
    }

    @Test
    public void testEnsureNotMain() throws Exception {
        echo("ensureNotMain() must throw %s when current thread == Looper.getMainLooper().getThread()", IllegalStateException.class.getName());
        try {
            when(mockLooper.getThread()).thenReturn(Thread.currentThread());
            ThreadUtils.ensureNotMain();
            fail(String.format("must throw %s", IllegalStateException.class.getName()));
        } catch (IllegalStateException e) {
            echo("correctly threw %s", e.getClass().getName());
        }
        echo("ensureNotMain() must not throw  when current thread != Looper.getMainLooper().getThread()");
        try {
            when(mockLooper.getThread()).thenReturn(mainThreadMock);
            ThreadUtils.ensureNotMain();
            echo("passed");
        } catch (IllegalStateException e) {
            fail("must not throw");
        }
    }

    @Test
    public void testEnsureMain() throws Exception {
        echo("ensureMain() must throw %s when current thread != Looper.getMainLooper().getThread()", IllegalStateException.class.getName());
        try {
            when(mockLooper.getThread()).thenReturn(mainThreadMock);
            ThreadUtils.ensureMain();
            fail(String.format("must throw %s", IllegalStateException.class.getName()));
        } catch (IllegalStateException e) {
            echo("correctly threw %s", e.getClass().getName());
        }
        echo("ensureMain() must not throw  when current thread == Looper.getMainLooper().getThread()");
        try {
            when(mockLooper.getThread()).thenReturn(Thread.currentThread());
            ThreadUtils.ensureMain();
            echo("passed");
        } catch (IllegalStateException e) {
            fail("must not throw");
        }
    }
}
