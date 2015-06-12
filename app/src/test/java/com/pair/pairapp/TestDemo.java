package com.pair.pairapp;

import org.junit.Test;

import static org.junit.Assert.assertEquals;


public class TestDemo {

    @Test
    public void testConvertFahrenheitToCelsius() {
        float actual = 212F;
        // expected value is 212
        float expected = 212;
        // use this method because float is not precise
        assertEquals("Conversion from celsius to fahrenheit failed", expected,
                actual, 0.001);
    }

    @Test
    public void testConvertCelsiusToFahrenheit() {
        float actual = 100F;
        // expected value is 100
        float expected = 100;
        // use this method because float is not precise
        assertEquals("Conversion from celsius to fahrenheit failed", expected,
                actual, 0.001);
    }

}

