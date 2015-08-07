package com.pair.pairapp;

import com.pair.util.FileUtils;

import org.junit.Test;


public class TestSuite {

    @Test
    public void testGetOutputUri() throws Exception {
        FileUtils.getOutputUri(0);
    }

}

