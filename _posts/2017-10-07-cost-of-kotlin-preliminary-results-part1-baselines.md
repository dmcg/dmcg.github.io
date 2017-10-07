---
title: The Cost of Kotlin Language Features - Preliminary Results Part 1 - Baselines

layout: post

---
Following on from my last post [Lessons Learned Writing Java and Kotlin Microbenchmarks](benchmarks.html) this presents my preliminary results examining *The Cost of Kotlin Language Features* in  preparation for my presentation at [KotlinConf](http://kotlinconf.com) in November. 

The code behind the investigation is all available [on GitHub](https://githhub.com/dmcg/kostings). There you will find [a framework](https://github.com/dmcg/kostings/tree/master/src/main/java/com/oneeyedmen/kostings) that uses [JMH](http://openjdk.java.net/projects/code-tools/jmh/) to run benchmarks, and then runs JUnit tests on the results to check assertions about the speed. The [actual benchmarks and tests](https://github.com/dmcg/kostings/tree/master/src/main/java/costOfKotlin) that I'm running are also there, along with [benchmark results](https://github.com/dmcg/kostings/tree/master/results) and the [sets of those results that are used in the tests](https://github.com/dmcg/kostings/tree/master/canonical-results). The separation allows me to reject results that are suspect for any of the reasons discussed in my previous post.

Note that, since that post, [John Nolan](https://twitter.com/johnsnolan) has helped greatly with the statistics of comparing benchmarks and contributed the statistical comparators that the framework uses.

With the caveat that this is reporting on work in progress, so the results are subject to better measurement and review, let's dive straight in and look at the baseline tests. 

First Java

```java
public class JavaBaseline {
    @Benchmark
    public void baseline(StringState state, Blackhole blackhole) {
        blackhole.consume(state);
    }
}
```

and then Kotlin

```kotlin
open class KotlinBaseline {
    @Benchmark
    fun baseline(state: StringState, blackhole: Blackhole) {
        blackhole.consume(state)
    }

    @Test
    fun `java is quicker but not by much`() {
        assertThat(JavaBaseline::baseline, probablyFasterThan(this::baseline, byAFactorOf = 0.005))
        assertThat(JavaBaseline::baseline, ! probablyFasterThan(this::baseline, byAFactorOf = 0.01))
    }
}
```

In the `@Test` we see assertions about the relative performance of the `@Benchmark` methods. The test shows that the Java code is quicker by between 0.5 % and 1%, with the default Confidence Interval of 95%.

Here is a sample benchmark run shown graphically (the error bars are the mean error with 99.9% confidence).

![A Sample Baseline Run](assets/baselines-f1-w10-m500-run2.png)

Given that the Kotlin and Java look functionally identical, you might expect that we shouldn't be able to detect any difference in the runtime of the code. There is a subtle difference though, revealed when we examine the bytecode emitted by the Java compiler.

```
public baseline(LcostOfKotlin/strings/StringState;Lorg/openjdk/jmh/infra/Blackhole;)V
  @Lorg/openjdk/jmh/annotations/Benchmark;()
   L0
    LINENUMBER 12 L0
    ALOAD 2
    ALOAD 1
    INVOKEVIRTUAL org/openjdk/jmh/infra/Blackhole.consume (Ljava/lang/Object;)V
   L1
    LINENUMBER 13 L1
    RETURN
```

compared to that from the Kotlin compiler

```
  public final baseline(LcostOfKotlin/strings/StringState;Lorg/openjdk/jmh/infra/Blackhole;)V
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
    LINENUMBER 14 L1
    ALOAD 2
    ALOAD 1
    INVOKEVIRTUAL org/openjdk/jmh/infra/Blackhole.consume (Ljava/lang/Object;)V
   L2
    LINENUMBER 15 L2
    RETURN
```
  
The Kotlin compiler always checks that arguments passed to non-nullable parameters are, in fact, not null. I suppose that in a pure Kotlin world these could be dispensed with, and there is a compiler flag to disable them, but the reasoning goes that any old Java code could be invoking your method with nulls, so it's better to find this out as early as possible. 

This price is paid for invocation of every public Kotlin method, so it's as well that the check is relatively cheap. In this case you have to run the benchmarks for many iterations before you can see a statistically significant effect.

In the rest of this series I'll rarely compare Java results directly to Kotlin, but when I do, it's worth remembering that at least some of any slowdown detected will be due to parameter null checks rather than the rest of the benchmark method.

I hope to have the next installment of this series, covering string interpolation, published in the next few days. 
 




 




  



