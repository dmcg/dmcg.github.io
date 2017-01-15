package com.oneeyedmen.play;

import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

@RunWith(Theories.class)
public class FizzBuzzTheoryTests {

    @DataPoints
    public static final int[] numbers = IntStream.range(1, 31).toArray();

    @Theory
    public void is_not_null(int i) {
        assertNotNull(fizzBuzz(i));
    }

    @Theory
    public void starts_with_fizz_when_divisble_by_3(int i) {
        assumeTrue(i % 3 == 0);
        assertTrue(fizzBuzz(i).matches("Fizz.*"));
    }

    @Theory
    public void ends_with_buzz_when_divisble_by_5(int i) {
        assumeTrue(i % 5 == 0);
        assertTrue(fizzBuzz(i).matches(".*Buzz"));
    }

    @Theory
    public void is_fizzbuzz_when_divisible_by_15(int i) {
        assumeTrue(i % 15 == 0);
        assertEquals("FizzBuzz", fizzBuzz(i));
    }

    @Theory
    public void is_string_of_number_for_other_numbers(int i) {
        assumeTrue(i % 3 != 0 && ((i % 5) != 0));
        assertEquals(String.valueOf(i), fizzBuzz(i));
    }

    public String fizzBuzz(int i) {
        if (i % 15 == 0) return "FizzBuzz";
        if (i % 3 == 0) return "Fizz";
        if (i % 5 == 0) return "Buzz";
        return String.valueOf(i);
    }

}
