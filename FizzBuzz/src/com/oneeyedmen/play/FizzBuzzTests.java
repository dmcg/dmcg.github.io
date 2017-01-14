package com.oneeyedmen.play;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FizzBuzzTests {

    @Test public void test_other_numbers() {
        assertEquals("1", fizzBuzz(1));
        assertEquals("2", fizzBuzz(2));
    }

    @Test public void fizz_for_multiples_of_three() {
        assertEquals("Fizz", fizzBuzz(3));
        assertEquals("Fizz", fizzBuzz(6));
    }

    @Test public void buzz_for_multiples_of_five() {
        assertEquals("Buzz", fizzBuzz(5));
        assertEquals("Buzz", fizzBuzz(10));
    }

    @Test public void fizzbuzz_for_multiples_of_three_and_five() {
        assertEquals("FizzBuzz", fizzBuzz(15));
        assertEquals("FizzBuzz", fizzBuzz(30));
    }

    @Test public void what_about_zero() {
        assertEquals("FizzBuzz", fizzBuzz(0));
    }

//    public String fizzBuzz(int i) {
//        if (i % 15 == 0) return "FizzBuzz";
//        if (i % 3 == 0) return "Fizz";
//        if (i % 5 == 0) return "Buzz";
//        return String.valueOf(i);
//    }
//
    public String fizzBuzz(int i) {
        String result = "";
        if (i % 3 == 0) result = result + "Fizz";
        if (i % 5 == 0) result = result + "Buzz";
        return result == "" ? String.valueOf(i) : result;
    }
}
