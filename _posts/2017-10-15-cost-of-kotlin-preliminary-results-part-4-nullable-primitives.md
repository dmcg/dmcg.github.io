---
title: The Cost of Kotlin Language Features - Preliminary Results Part 4 - Nullable Primitives
layout: post
tags: [Kotlin, Cost of Kotlin Language Features]
---
This is Part 4 in a series examining *The Cost of Kotlin Language Features* in preparation for my presentation at [KotlinConf](http://kotlinconf.com) in November. The series consists of 

* [Lessons Learned Writing Java and Kotlin Microbenchmarks](benchmarks.html)
* [Part 1 - Baselines](cost-of-kotlin-preliminary-results-part1-baselines.html)
* [Part 2 - Strings](cost-of-kotlin-preliminary-results-part2-strings.html)
* [Part 3 - Invocation](cost-of-kotlin-preliminary-results-part3-invocation.html)
* [Part 4 - Nullable Primitives](cost-of-kotlin-preliminary-results-part-4-nullable-primitives.html)
* [Part 5 - Properties](cost-of-kotlin-preliminary-results-part-5-properties.html)

I'm publishing these results ahead of KotlinConf to give an opportunity for peer-review, so please do give me your feedback about the content, experimental method, code and conclusions. If you're reading this before November 2017 it isn't too late to save me from making a fool of myself in person, rather than just on the Internet. As ever the current state of the code to run the benchmarks is available for inspection and comment [on GitHub](https://github.com/dmcg/kostings).
 
This post looks at the cost of nullability. My aim with this batch wasn't to measure individual aspects of nullability so much as to simply model typical usage, so I picked adding to a nullable Int as an example.  
   
As usual we start with a baseline, consuming a constant expression passed in to the benchmark

```kotlin
open class KotlinPrimitives {

    @Benchmark
    fun _1_baseline(state: IntState, blackhole: Blackhole) {
        blackhole.consume(state._41)
    }
}
```

And now a take that and add one to it

```kotlin
    @Benchmark
    fun _2_sum(state: IntState, blackhole: Blackhole) {
        blackhole.consume(state._41 + 1)
    }
```

Irritatingly, for the data that I have, that addition isn't statistically detectable.

```kotlin
    @Test
    fun `kotlin sum is not detectable`() {
        assertThat(this::_1_baseline, ! probablyDifferentTo(this::_2_sum))
    }
```

Looking at the bytecode we see that the problem is that the code that we want to measure is swamped by code that we don't want to measure, but has to be there to make the measurements possible.

```java
  public final _2_sum(LcostOfKotlin/primitives/IntState;Lorg/openjdk/jmh/infra/Blackhole;)V
  @Lorg/openjdk/jmh/annotations/Benchmark;()
    @Lorg/jetbrains/annotations/NotNull;() // invisible, parameter 0
    @Lorg/jetbrains/annotations/NotNull;() // invisible, parameter 1
   L0
    ALOAD 1
    LDC "state"
    INVOKESTATIC kotlin/jvm/internal/Intrinsics.checkParameterIsNotNull (Ljava/lang/Object;Ljava/lang/String;)V
    ALOAD 2
    LDC "blackhole"
    INVOKESTATIC kotlin/jvm/internal/Intrinsics.checkParameterIsNotNull (Ljava/lang/Object;Ljava/lang/String;)V
   L1
    LINENUMBER 22 L1
    ALOAD 2
    ALOAD 1
    INVOKEVIRTUAL costOfKotlin/primitives/IntState.get_41 ()I
    ICONST_1
    IADD
    INVOKEVIRTUAL org/openjdk/jmh/infra/Blackhole.consume (I)V
   L2
    LINENUMBER 23 L2
    RETURN
```

That `ICONST_1 IADD` hidden in the middle is what we want to measure! Ah well. I could do better by not using the `Blackhole`, and by arranging for field access to the `IntState._41`, and switching off null-checks (ideally we'd have a way to do that for just this method with an annotation), but, well, I have a more detectable fish to fry.

```kotlin
    @Benchmark
    fun _3_sum_nullable(state: IntState, blackhole: Blackhole) {
        blackhole.consume(state.nullable_41!! + 1)
    }
    
    @Test
    fun `sum nullable is slower`() {
        assertThat(this::_2_sum, probablyFasterThan(this::_3_sum_nullable))
        assertThat(this::_2_sum, ! probablyFasterThan(this::_3_sum_nullable, byAFactorOf = 0.001))
    }    
```

Adding a not-null !! assertion is statistically slower (when testing a value that is never null), but by less than 0.1%. In the bytecode we see the cost of the nullability

```java
    DUP
    IFNONNULL L2
    INVOKESTATIC kotlin/jvm/internal/Intrinsics.throwNpe ()V
   L2
    INVOKEVIRTUAL java/lang/Integer.intValue ()I
```

is a check for `Integer != null` and fetching the Int out of its box.

What if we don't know that Int can't be null? We can use the Elvis operator to substitute 0 for null. 

```kotlin
    @Benchmark
    fun _4_sum_always_null(state: IntState, blackhole: Blackhole) {
        blackhole.consume(state.nullInt ?: 0 + 1)
    }

    @Test
    fun `sum always null is slower`() {
        assertThat(this::_2_sum, probablyFasterThan(this::_4_sum_always_null))
        assertThat(this::_2_sum, ! probablyFasterThan(this::_4_sum_always_null, byAFactorOf = 0.001))
    }
```

Uh huh - again, slower, but by less than 0.1% when our value is always null. Here's the bytecode

```java
    DUP
    IFNULL L2
    INVOKEVIRTUAL java/lang/Integer.intValue ()I
    GOTO L3
   L2
    POP
    ICONST_1
   L3
    INVOKEVIRTUAL org/openjdk/jmh/infra/Blackhole.consume (I)V

```

Argghh! 

Can you see the problem?

It took me a while.

We aren't doing the addition at all any more. The Elvis operator has a lower precedence than addition. So the expression we're measuring turns out to be

```kotlin
    state.nullInt ?: (0 + 1)
```

and the compiler is smart enough to know that `0 + 1 = 1` for all values of 0 and 1, so it just substitutes `ICONST_1`.

Sigh.

I'll have to fix that an re-run the benchmarks (which takes several hours), but in the meantime, luckily, I already have some more results.

For a long time I couldn't detect any statistically significant different between the versions with and without null checks. I formed a hypothesis that, when the value being checked was always null or not null, [branch prediction](https://en.wikipedia.org/wiki/Branch_predictor) in either HotSpot or the processor was eating the cost of the check. So I measured with `Int?`s that were randomly null or not

```kotlin
    @Benchmark
    fun _5_sum_50_50_nullable(state: IntState, blackhole: Blackhole) {
        blackhole.consume(state.`50 50 NullableInt` ?: 0 + 1)
    }

    @Benchmark
    fun _6_sum_90_10_nullable(state: IntState, blackhole: Blackhole) {
        blackhole.consume(state.`90 10 NullableInt` ?: 0 + 1)
    }
```

It turns out that branch prediction isn't detectable

```kotlin
    @Test
    fun `branch_prediction is undetectable_50_50`() {
        assertThat(this::_4_sum_always_null, ! probablyDifferentTo(this::_5_sum_50_50_nullable))
    }

    @Test
    fun `branch_prediction is undetectable_90_10`() {
        assertThat(this::_4_sum_always_null, ! probablyDifferentTo(this::_6_sum_90_10_nullable))
    }
```

but I suppose that [Speculative Execution](https://en.wikipedia.org/wiki/Speculative_execution) may play a part in the throughput of null checks.  









