package com.yennkasa.messenger;

import com.yennkasa.util.PLog;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author aminu on 11/18/2016.
 */
public class ZlibCompressorTest {
    ZlibCompressor compressor = new ZlibCompressor();

    @BeforeClass
    public static void setUp() {
        PLog.setLogLevel(PLog.LEVEL_NONE);
    }

    @Test
    public void compress() throws Exception {
        byte[] input = "Hello world is an example input".getBytes();
        System.out.println("size before " + input.length);
        byte[] compressed = compressor.compress(input);
        System.out.println("size after " + compressed.length);
        byte[] sameInput = compressor.decompress(compressed);
        Assert.assertArrayEquals(input, sameInput);
    }

    @Test
    public void decompress() throws Exception {
        byte[] input = "Hello world is an example input".getBytes();
        System.out.println("size before " + input.length);
        byte[] compressed = compressor.compress(input);
        System.out.println("size after " + compressed.length);
        byte[] sameInput = compressor.decompress(compressed);
        Assert.assertArrayEquals(input, sameInput);
    }

}
