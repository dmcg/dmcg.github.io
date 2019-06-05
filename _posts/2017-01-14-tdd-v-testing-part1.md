---
layout: post
title: Test Driven Development v Testing Part 1 - Example Tests
tags: [Java, Testing]
---

Dominic Fox wrote a very good post recently titled [How to TDD FizzBuzz with JUnit Theories](https://opencredo.com/tdd-fizzbuzz-junit-theories/). In it he wrote *"For a long time I was of the opinion that a) FizzBuzz couldn’t be meaningfully TDD’d, and b) this illustrated a common pitfall with TDD."*


As it happens I attended an excellent workshop led by Jon Jagger at [XP2016](http://xp2016.org/cfp/Agenda.html#PC5T). We test-drove FizzBuzz several times as different pairs, coming up with different approaches to the testing and the solution.

A typical session went something like this.

First write a test.

```java
    @Test public void test() {
        assertEquals("1", fizzBuzz(1));
    }
```

That doesn't compile, so let's add the function.

```java
    public String fizzBuzz(int i) {
        return null;
    }
```

That fails, so let's add the simplest implementation.

```java
    public String fizzBuzz(int i) {
        return "1";
    }
```

This succeeds, but seems like cheating. Let's write a test to prove it.

```java
    @Test public void test() {
        assertEquals("1", fizzBuzz(1));
        assertEquals("2", fizzBuzz(2));
    }
```

That fails, so we can write some production code,

```java
    public String fizzBuzz(int i) {
        return String.valueOf(i);
    }
```
and maybe rename the test.

```java
    @Test public void test_other_numbers() {
        assertEquals("1", fizzBuzz(1));
        assertEquals("2", fizzBuzz(2));
    }
```

OK, now for Fizz. Baby steps - this is deliberate practice.

```java
    @Test public void fizz() {
        assertEquals("Fizz", fizzBuzz(3));
    }
```

Fix the failing test with the simplest fix.

```java
    public String fizzBuzz(int i) {
        if (i == 3) return "Fizz";
        return String.valueOf(i);
    }
```

Call the cheating by adding a breaking assertion,

```java
    @Test public void fizz_for_multiples_of_three() {
        assertEquals("Fizz", fizzBuzz(3));
        assertEquals("Fizz", fizzBuzz(6));
    }
```

which we can then fix.

```java
    public String fizzBuzz(int i) {
        if (i % 3 == 0) return "Fizz";
        return String.valueOf(i);
    }
```

Buzz now. Let's take 2 steps forward this time - 

```java
    @Test public void buzz_for_multiples_of_fice() {
        assertEquals("Buzz", fizzBuzz(5));
        assertEquals("Buzz", fizzBuzz(10));
    }
```

which fails, and we'll go straight to the complicated implementation. 

```java
    public String fizzBuzz(int i) {
        if (i % 3 == 0) return "Fizz";
        if (i % 5 == 0) return "Buzz";
        return String.valueOf(i);
    }
```

Now there's just one lingering doubt in the back of our minds that we can explore with a test, viz

```java
    @Test public void fizzbuzz_for_multiples_of_three_and_five() {
        assertEquals("FizzBuzz", fizzBuzz(15));
        assertEquals("FizzBuzz", fizzBuzz(30));
    }
```

The simplest fix is probably this

```java
    public String fizzBuzz(int i) {
        if (i % 15 == 0) return "FizzBuzz";
        if (i % 3 == 0) return "Fizz";
        if (i % 5 == 0) return "Buzz";
        return String.valueOf(i);
    }
```

but personally I think that this is a better, if less efficient, expression of the intent

```java
    public String fizzBuzz(int i) {
        String result = "";
        if (i % 3 == 0) result = result + "Fizz";
        if (i % 5 == 0) result = result + "Buzz";
        return result == "" ? String.valueOf(i) : result;
    }
```

and yes, I am being deliberately provocative with the `==` ;-)

Let's have a look at those tests all together.

```java
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
```

and compare them to those that Dominic said *"contain the logic of the implementation, only in an obscure and intractable form."*

```java
    @Test
    public void multiplesOfThreeButNotFiveAreFizz() {
        for (int i = 1; i <= 100; i++) {
            if ((i % 3 == 0) && !(i % 5 == 0)) {
                assertEquals("Fizz", unit.apply(i));
            }
        }
    }
     
    @Test
    public void multiplesOfFiveButNotThreeAreBuzz() {
        for (int i = 1; i <= 100; i++) {
            if (!(i % 3 == 0) && (i % 5 == 0)) {
                assertEquals("Buzz", unit.apply(i));
            }
        }
    }
     
    public void multiplesOfThreeAndFiveAreFizzBuzz() {
        for (int i = 1; i <= 100; i++) {
            if ((i % 3 == 0) && (i % 5 == 0)) {
                assertEquals("FizzBuzz", unit.apply(i));
            }
        }
    } 
// ...and so on
```

or this, which he said *"feels wildly unsatisfactory ... It’s hard to see this as really rigorously testing anything."*

```java
    @Test
    public void testSomeJudiciouslyChosenValues() {
        assertEquals("Fizz", unit.apply(3));
        assertEquals("4", unit.apply(4));
        assertEquals("Buzz", unit.apply(5));
        assertEquals("FizzBuzz", unit.apply(15));
        // just in case
        assertEquals("FizzBuzz", unit.apply(30));
    }
```

Are any of these tests good enough? Which ones provide best coverage and communication? I'll leave you to mull that over while I [ride my bike](https://www.strava.com/activities/831826153).

<hr />

OK, I'm back. What was your verdict? Would you be happy with any of those tests for FizzBuzz?

For me, we can discount `testSomeJudiciouslyChosenValues` easily, but only because it doesn't go out of its way to describe the system under test. Using judiciously chosen values to demonstrate and verify the behaviour of a system isn't of itself a bad thing. If it was, pulling on this thread would unravel pretty much all of Behaviour Driven Design.

Looking at the set beginning with `multiplesOfThreeButNotFiveAreFizz`, I agree with Dominic - they are pretty obscure and intractable. But then, they were probably designed to show theories in a good light, so perhaps they are a straw man. The fact is that testing the first 100 numbers is arbitrary and misses the opportunity to communicate the fact that FizzBuzz has period of 15. A sane implementation can be verified in far fewer examples, and an insane implementation, one that looks at the test and goes out of it's way to break it, could throw `UnsupportedOperationException` at 101, or return "Banana" at random with a probability of 1:10000.

The traditional goal of unit tests is to prevent accidental breakage during modifications of this or other code - regression. Well-written tests can also serve to demonstrate what the code does - communication. In TDD they have another job, to guide the implementation. I would argue that the tests we wrote actually TDDing FizzBuzz here fulfil the three roles of regression, communication and guiding the implementation pretty well. They helped us write the code, the names tell us what the code does, and the examples exemplify the names.

There have been times when the tests that I have written to guide the implementation of a system have not communicated well, or have holes that might allow regressions to slip through (what is `fizzBuzz(0)`?). Before we consider the job done, we should look at the tests that helped us drive the design and judge them against the regression and communication criteria. If they fall short, then add examples, refactor the tests, maybe even recast them as theories. Don't expect the test artifact of TDD to be perfect first time, because [TDD Is About Design, Not Testing](http://www.drdobbs.com/tdd-is-about-design-not-testing/229218691).

Tune in later for [Part 2](/tdd-v-testing-part2.html), where I'll repeat TDD FizzBuzz using JUnit theories from the outset.






