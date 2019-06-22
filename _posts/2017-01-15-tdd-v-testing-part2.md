---
layout: post
title: Test Driven Development v Testing Part 2 - Theory Tests
tags: [Java, Testing, TDD v Testing]
---

As promised at the end of [Part 1](/tdd-v-testing-part1.html), I'll now go through the steps of TDDing FizzBuzz with JUnit theories, inspired by Dominic Fox's excellent post titled [How to TDD FizzBuzz with JUnit Theories](https://opencredo.com/tdd-fizzbuzz-junit-theories/). My reasons for doing this are
  
1. To compare theories with the 'traditional' example-based process shown in Part&nbsp;1.

2. Because informative though Dominic's post was, it didn't actually show any TDD, just some tests that may or may not have been the end result of some test driving.

3. To better understand JUnit theories myself.

So, let's go.

There is more scaffolding and less IntelliJ handholding when writing a JUnit theory test, but I guess we'll get used to it. Actually I'm just going to copy Dominic's setup, but using 31 as the upper bound of the values that we're going to check, as anything more is just a waste.

```java
@RunWith(Theories.class)
public class FizzBuzzTheoryTests {
    @DataPoints
    public static final int[] numbers = IntStream.range(1, 31).toArray();
}
```

Now what is our first test? In Part 1 this was easy, we just had to assert something about the result for number `1`.  With theories it's harder, because we have to assert something that is true for all numbers, will drive our implementation, and allows us to make a very small change. The best I can come up with is

```java
    @Theory
    public void is_not_null(int i) {
        assertNotNull(fizzBuzz(i));
    }
```

which is at least uncontentious and, through its failure to compile, makes us add our first line of implementation.

```java
    public String fizzBuzz(int i) {
        return "";
    }
```

This passes straight away, so we can add another theory to drive more code.

```java
    @Theory
    public void is_string_of_number(int i) {
        assertEquals(Integer.valueOf(i), fizzBuzz(i));
    }
```

I must say that this step left me feeling a bit icky. Firstly, we have done exactly what Dominic complained about in his article - defined the code to be written in the test. I suppose that we could find another expression of the same thing, `"" + i` maybe. Or taken Dominic's approach and just specified that the result should have only digits, at the expense of under-specifying. Let's just say that this is a toy example, for real code there may be a way of specifying the result that is less complicated than the production code.

Also, we've written something that we know is not going to be true later on - this test is only going to be valid for a subset of the ints. We could fix that now, but my inner pair is eager to see a green bar, so we go ahead and write the code

```java
    public String fizzBuzz(int i) {
        return String.valueOf(i);
    }
```

which passes, and then go back and retrofit the assumption.

```java
    @Theory
    public void is_string_of_number_for_other_numbers(int i) {
        assumeTrue(i % 3 != 0 && i % 5 != 0);
        assertEquals(String.valueOf(i), fizzBuzz(i));
    }
```

which continues to pass. Now for 3's

```java
    @Theory
    public void is_fizz_when_divisible_by_3(int i) {
        assumeTrue(i % 3 == 0);
        assertEquals("Fizz", fizzBuzz(i));
    }
```

leading to

```java
    public String fizzBuzz(int i) {
        if (i % 3 == 0) return "Fizz";
        return String.valueOf(i);
    }
```

and 5's

```java
    @Theory
    public void is_buzz_when_divisible_by_5(int i) {
        assumeTrue(i % 5 == 0);
        assertEquals("Buzz", fizzBuzz(i));
    }
```

leading to 

```java
    public String fizzBuzz(int i) {
        if (i % 3 == 0) return "Fizz";
        if (i % 5 == 0) return "Buzz";
        return String.valueOf(i);
    }
```

which still fails! 

Now this is the interesting part of the theories TDD. At this stage in Part 2 we had to add a test ourselves to drive out the case of numbers divisible by 15. This time, our naive implementation fails the is_buzz_when_divisible_by_5 test in this case. So a point to the more formal specification, but now we have a few things to do to get to a passing case.
  
We can try just fixing the code

```java
    public String fizzBuzz(int i) {
        if (i % 15 == 0) return "FizzBuzz";
        if (i % 3 == 0) return "Fizz";
        if (i % 5 == 0) return "Buzz";
        return String.valueOf(i);
    }
```

but that leads to 2 broken tests! As this is deliberate practice, we should probably back out the production change and fix the tests first, Mikado-style.  

```java
    @Theory
    public void starts_with_fizz_when_divisible_by_3(int i) {
        assumeTrue(i % 3 == 0);
        assertTrue(fizzBuzz(i).matches("Fizz.*"));
    }

    @Theory
    public void ends_with_buzz_when_divisible_by_5(int i) {
        assumeTrue(i % 5 == 0);
        assertTrue(fizzBuzz(i).matches(".*Buzz"));
    }
```

My internal pair and I had a long discussion over this. Should we test for the simple contains, or starts/ends with? In the end we plumped for the start and end, as I felt that they expressed the problem better. The tests are still broken of course, and for longer than he was comfortable with, but at least we know the fix is easy.
  
```java
    public String fizzBuzz(int i) {
        if (i % 15 == 0) return "FizzBuzz";
        if (i % 3 == 0) return "Fizz";
        if (i % 5 == 0) return "Buzz";
        return String.valueOf(i);
    }
```

Phew. Are we done? I'm pretty sure that `is_not_null` is now superfluous, so deleting that and reordering to tell a story we have

```java
    @Theory
    public void starts_with_fizz_when_divisible_by_3(int i) {
        assumeTrue(i % 3 == 0);
        assertTrue(fizzBuzz(i).matches("Fizz.*"));
    }

    @Theory
    public void ends_with_buzz_when_divisible_by_5(int i) {
        assumeTrue(i % 5 == 0);
        assertTrue(fizzBuzz(i).matches(".*Buzz"));
    }

    @Theory
    public void is_string_of_number_for_other_numbers(int i) {
        assumeTrue(i % 3 != 0 && i % 5 != 0);
        assertEquals(String.valueOf(i), fizzBuzz(i));
    }
```

By the standards of Part 1, then I think that yes, we are done. Our tests will guard against regression, they make good documentation; they might be broken by a determined editor. Personally, they feel a little unfinished though. Perhaps because of their formality, you realise that they don't fully specify the behaviour - you couldn't re-implement the code given the test because nothing actually specifies what `fizzBuzz(15)` should return. So I'd end up adding
 
```java
    @Theory
    public void is_fizzbuzz_when_divisible_by_15(int i) {
        assumeTrue(i % 15 == 0);
        assertEquals("FizzBuzz", fizzBuzz(i));
    }
```

which seems a bit arbitrary, but every other formulation ended up worse. Dominic had a different set of theories, but his also suffer from not actually allowing you to re-implement the code from the spec. 

Let's remind ourselves what the tests from Part 1 looked like.

```java
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

   @Test public void test_other_numbers() {
        assertEquals("1", fizzBuzz(1));
        assertEquals("2", fizzBuzz(2));
    }
```

What have we learnt? For me, TDDing with theories was a lot less comfortable than TDDing with examples, but then I have a lot more experience with the former. In this case I'm not convinced that either my theories or Dominic's are better tests than the example tests, but they do appeal to the mathematical side of me. Comparing them you can see that the previous tests were a bit sloppy, in particular we should have had names like `fizz_for_multiples_of_three_that_arent_also_multiples_of_five`. 

Which tests better express the rules of FizzBuzz? I think that humans probably value examples over formality, and mathematicians vice versa, so perhaps it depends which way you lean. To me the theories feel like I need to read between the lines, to infer behaviour that should be evident, but maybe I'm just bad at writing them. 

I can better imagine writing theories after the TDD cycle, to better specify and catch regressions. Some algorithms and data structures would benefit from theories to verify that pre and post conditions apply during their TDD. I suppose I'd like to think that any concurrent data structures I use were specified and tested in this mathematical style, but perhaps documented with example tests.

If you like this post, or even if you don't, then you should read Nat Pryce's [article](http://natpryce.com/articles/000807.html) about using property tests, closely related to JUnit Theories, to TDD another old favourite, the Diamond Kata. 

And re-reading Dominic's post in the light of my trying the theories, I think that there is a [Part 3](/tdd-v-testing-part3.html) of this series coming, so follow me on [Twitter](https://twitter.com/duncanmcg) if you want to know when it is published.

