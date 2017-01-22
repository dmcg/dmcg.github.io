---
layout: post
title: Test Driven Development v Testing Part 3 - Approval Tests
---

This isn't the post I thought I was going to write for Part 3 [last week](/tdd-v-testing-part2.html), but it occurred to me that I could reasonably TDD FizzBuzz with [Approval Tests](http://approvaltests.com/) too, so here goes. 

Approval Testing is basically the application of Golden Master Testing to in-development rather than legacy code. You really don't need any test infrastructure to do this, but as I wrote some, we're going to use it!

[Okey-doke](https://github.com/dmcg/okey-doke) is a library that integrates with JUnit via a Rule.
 
```java
public class FizzBuzzApprovalsTests {    
    @Rule public ApprovalsRule approver = ApprovalsRule.usualRule(); 
}
```

The nature of Approval Testing is that we just write code and approve its output if it meets our expectations. So let's add a test using the approver and see what happens.

```java
    @Test
    public void test() {
        approver.assertApproved(fizzBuzz(1));
    }
```

Nothing can happen until the code compiles, and approvals testing is all about moving fast, so let's just add an implementation that covers most cases.

 ```java
    public String fizzBuzz(int i) {
        return String.valueOf(i);
    }
```

Running this gives a test failure 

```
java.lang.AssertionError: 
Expected :null
Actual   :1
```

which is telling us that nothing was expected (there is not yey any approved output for this test). A file `FizzBuzzApprovalsTests.test.actual` is created with the actual output, viz

```
1
```

Okeydoke also tells you what to do if you want to do to approve the output

```
To approve...
cp '/Users/duncan/Documents/Work/website-jekyll/site/FizzBuzz/src/com/oneeyedmen/play/FizzBuzzApprovalsTests.test.actual' '/Users/duncan/Documents/Work/website-jekyll/site/FizzBuzz/src/com/oneeyedmen/play/FizzBuzzApprovalsTests.test.approved'
```

Do we want to approve? Well, the output of `1` is right for the case we've considered, so let's copy the actual to the approved file as suggested and run the tests again. This time they pass as they are the same. We could add the `FizzBuzzApprovalsTests.test.approved` to version control as it is effectively part of our test sources.

Now we could write another test to cover other numbers, but with Approval Tests it's easy to check a lot of cases at once, so let's check 1 ... 31 by building a string with the results and checking that

```java
    @Test
    public void test() {
        approver.assertApproved(
            IntStream.range(1, 32).mapToObj(this::fizzBuzz).collect(joining(","))
        );
    }
```

which fails

```
org.junit.ComparisonFailure: 
Expected :1
Actual   :1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31
```

but it is what we want to we approve it, and run the tests again just to make sure they pass now.

Now instead of writing another test we can just add to our implementation.

```java
    public String fizzBuzz(int i) {
        if (i % 3 == 0) return "Fizz";
        return String.valueOf(i);
    }
```

and run the test

```
org.junit.ComparisonFailure: 
Expected :1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31
Actual   :1,2,Fizz,4,5,Fizz,7,8,Fizz,10,11,Fizz,13,14,Fizz,16,17,Fizz,19,20,Fizz,22,23,Fizz,25,26,Fizz,28,29,Fizz,31
```

Hmmm, it's getting difficult to see what's going on there, as the expected and actual are out of sync on the line. As this is deliberate practice, I'm going to stash the production change and fix the output format first.

```java
IntStream.range(1, 32).mapToObj(this::fizzBuzz).collect(joining("\n"))
```

Running the test this time we can't see a diff in the console, but crucially IntelliJ says

```
org.junit.ComparisonFailure:  <Click to see difference>
```

Clicking to see the difference gives us a diff view showing something like

```
Expected                    Actual
1,2,3,4,5,6,7,8,9,10,11..   1 
                            2 
                            3 
                            4 
...                        
```

which was the point of the formatting change, so we approve, then unstash the production change to give a failing test with the diff

```
Expected                    Actual       
1                           1
2                           2
3                           Fizz
4                           4
5                           5
6                           Fizz
7                           7
...
```

which is going to let us make a lot more sense of the results. So we approve, and then write more production code.

```java
    public String fizzBuzz(int i) {
        if (i % 3 == 0) return "Fizz";
        if (i % 5 == 0) return "Buzz";
        return String.valueOf(i);
    }
```
This causes another approvals failure

```
Expected                    Actual
1                           1
2                           2
Fizz                        Fizz
4                           4
5                           Buzz
Fizz                        Fizz
7                           7
8                           8
Fizz                        Fizz
10                          Buzz
11                          11
Fizz                        Fizz
13                          13
14                          14
Fizz                        Fizz
16                          16
...
```

which is closer, so we approve that too. One last step

```java
    public String fizzBuzz(int i) {
        if (i % 3 == 0) return "Fizz";
        if (i % 5 == 0) return "Buzz";
        if (i % 15 == 0) return "FizzBuzz";
        return String.valueOf(i);
    }
```

and we run the tests, which pass. A quick check-in, and we're home in time for tea and medals.
 
Luckily my pair, maybe it was you, points out that they shouldn't have passed, as the approved file still shows Fizz at 15 rather than FizzBuzz. Complacency is the main problem I have with Approval tests, but luckily we hadn't pushed, so our embarrassment is local. A quick fix 

```java
    public String fizzBuzz(int i) {
        if (i % 15 == 0) return "FizzBuzz";
        if (i % 3 == 0) return "Fizz";
        if (i % 5 == 0) return "Buzz";
        return String.valueOf(i);
    }
```

gives the desired change

```
Expected                    Actual
1                           1
2                           2
Fizz                        Fizz
4                           4
5                           Buzz
Fizz                        Fizz
7                           7
8                           8
Fizz                        Fizz
10                          Buzz
11                          11
Fizz                        Fizz
13                          13
14                          14
Fizz                        FizzBuzz
16                          16
17                          17
Fizz                        Fizz
19                          19
20                          Buzz
Fizz                        Fizz
22                          22
23                          23
Fizz                        Fizz
25                          Buzz
26                          26
Fizz                        Fizz
28                          28
29                          29
Fizz                        FizzBuzz
31                          31
```

which we approve and then amend commit. I think we got away with it.

Compared to the example-based tests in [Part 1](/tdd-v-testing-part1.html), or the theories of [Part 2](/tdd-v-testing-part2.html), our final test is very simple

```java
    @Test
    public void test() {
        approver.assertApproved(
            IntStream.range(1, 32).mapToObj(this::fizzBuzz).collect(joining("\n"))
        );
    }
```

In fact it makes no sense until you look into `FizzBuzzApprovalsTests.test.approved` and see

```
1
2
Fizz
4
Buzz
Fizz
7
8
Fizz
Buzz
11
Fizz
13
14
FizzBuzz
16
17
Fizz
19
Buzz
Fizz
22
23
Fizz
Buzz
26
Fizz
28
29
FizzBuzz
31
```

Does this make enough sense, given that we have to read it separately from the test? Maybe not, especially given that my previous encouragement to make tests more communicative after the TDD. Let's finish by making that approved file more explicit

```java
IntStream.range(1, 32).mapToObj((i) ->  i + "\t = \t" + fizzBuzz(i)).collect(joining("\n"))
```

```
1	 = 	1
2	 = 	2
3	 = 	Fizz
4	 = 	4
5	 = 	Buzz
6	 = 	Fizz
7	 = 	7
8	 = 	8
9	 = 	Fizz
10	 = 	Buzz
11	 = 	11
12	 = 	Fizz
13	 = 	13
14	 = 	14
15	 = 	FizzBuzz
16	 = 	16
17	 = 	17
18	 = 	Fizz
19	 = 	19
20	 = 	Buzz
21	 = 	Fizz
22	 = 	22
23	 = 	23
24	 = 	Fizz
25	 = 	Buzz
26	 = 	26
27	 = 	Fizz
28	 = 	28
29	 = 	29
30	 = 	FizzBuzz
31	 = 	31
```

Once you're in the flow with Approval tests they can be very productive, especially when you don't need the help of writing a test to work out what the next step is. I suppose we're skipping the test driving and going straight to the proof-against-regression bit of testing. They are also splendid for getting existing code without tests under test quickly and cheaply, although I suppose that is just Golden Master Testing.
 
 The main downside is that we can't look directly in the test to see examples of what the code does - we have to look in the approved file. Those examples may also fail to be good documentation to allow us to understand what the code does - in this case they'd do for another programmer, but not for an 8 year-old. Or maybe vice-versa, I'm often wrong.
  
One application where they really do shine is where we are incrementally improving an algorithm and stopping when it is good enough. This time last year I was parsing a list of strings into first name, given name, title etc. Given a corpus of 2000 names I could approve each improvement and know when a change had led to better or worse results very quickly. Example-based tests would have become unwieldy very quickly in comparison. They can be also be a cheap warning when other things have changed too - I once wrote an Approval Test for the contents of our deployed lib directory so that we didn't accidentally upgrade dependencies when making changes to the unfathomable Maven build. 
  
I still think that there's another post to come in this series. I'll let you know on [Twitter](https://twitter.com/duncanmcg) when it arrives.
 

























