package com.pair.util;

import android.net.Uri;
import android.telephony.PhoneNumberUtils;

import com.pair.pairapp.BuildConfig;
import com.pair.pairapp.MainActivity;
import com.pair.util.FileHelper;

import junit.framework.TestCase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.*;
import org.robolectric.annotation.Config;

import java.io.File;
import java.lang.String;

import io.realm.internal.Util;

/**
 * @author Null-Pointer on 6/24/2015.
 */
@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = BuildConfig.class)
public class FileHelperTest extends TestCase {
    Uri uri;
    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {

    }

    @Test
    public void testGetOutputUri() throws Exception {
    }

    @Test
    public void testResolveContentUriToFilePath() throws Exception {

    }

    @Test
    public void testGetMimeType() throws Exception {

    }

    @Test
    public void testGetExtension() throws Exception {

    }

    @Test
    public void testSave() throws Exception {

    }
}