package com.pair.util;

import junit.framework.TestCase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.File;

/**
 * @author Null-Pointer on 6/24/2015.
 */
public class FileHelperTest extends TestCase {

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {

    }

    @Test
    public void testGetOutputUri() throws Exception {
        File rootDir = new File("/mnt/sdcard/");
        rootDir.mkdirs();
        File file = Mockito.mock(File.class);
        Mockito.when(file.getName()).thenReturn("fake stub");
        assertEquals("mocked", "fake stub", file.getName());
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