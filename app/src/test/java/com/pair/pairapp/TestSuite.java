package com.pair.pairapp;

import com.pair.util.FileHelper;

import org.junit.Test;


public class TestSuite {

    @Test
    public void testGetOutputUri() throws Exception {
        FileHelper.getOutputUri(0);
    }

}

