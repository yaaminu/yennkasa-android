package com.pairapp.pairapp;

import com.pairapp.util.FileUtils;

import org.junit.Test;


public class TestSuite {

    @Test
    public void testGetOutputUri() throws Exception {
        FileUtils.getOutputUri(0);
    }

}

