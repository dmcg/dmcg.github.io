---
title: The Cost of Kotlin Language Features - Preliminary Results Part 2 - Strings

layout: post

---
This is Part 2 in a series examining *The Cost of Kotlin Language Features* in preparation for my presentation at [KotlinConf](http://kotlinconf.com) in November. The series consists of 

* [Lessons Learned Writing Java and Kotlin Microbenchmarks](benchmarks.html)
* [Part 1 - Baselines](cost-of-kotlin-preliminary-results-part1-baselines.html)
* [Part 2 - Strings](cost-of-kotlin-preliminary-results-part2-strings.html)
* [Part 3 - Invocation](cost-of-kotlin-preliminary-results-part3-invocation.html)
* [Part 4 - Nullable Primitives](cost-of-kotlin-preliminary-results-part-4-nullable-primitives.html)


I'm publishing these results ahead of KotlinConf to give an opportunity for peer-review, so please do give me your feedback about the content, experimental method, code and conclusions. If you're reading this before November 2017 it isn't too late to save me from making a fool of myself in person, rather than just on the Internet.
 
In this post I look at String interpolation, the language feature that allows you to embed Kotlin expressions inside strings. 

Let's build the string `Hello World` from its greeting and subject, in Java first.

```java
public class JavaStrings {

    @Benchmark
    public void baseline(StringState state, Blackhole blackhole) {
        blackhole.consume(state.getHello());
        blackhole.consume(state.getWorld());
    }

    @Benchmark
    public void concat(StringState state, Blackhole blackhole) {
        blackhole.consume(state.getHello() + " " + state.getWorld());
    }

}
```

Here we just concatenate two strings passed into the benchmark, separated by a space. See [Part 1](2017-10-08-cost-of-kotlin-preliminary-results-part1-baselines.html) for an explanation of the `state` and `blackhole` parameters.

In Kotlin we could write

```kotlin
open class KotlinStrings {

    @Benchmark
    fun baseline(state: StringState, blackhole: Blackhole) {
        blackhole.consume(state.hello)
        blackhole.consume(state.world)
    }

    @Benchmark
    fun concat(state: StringState, blackhole: Blackhole) {
        blackhole.consume("${state.hello} ${state.world}")
    }
}
```

Graphically the throughput for a particular test run looks like this

![A Sample Strings Run](assets/strings-f1-w10-m500-run2.png)

A visual inspection suggests that the Kotlin version may be as quick as the Java one, and indeed, for the data I have, I cannot tell them apart.

```kotlin
    @Test
    fun `cannot detect the difference between Java and Kotlin`() {
        assertThat(JavaStrings::concat, ! probablyDifferentTo(KotlinStrings::concat))
    }
```

In other words, "We cannot say that (the mean throughput of the Java version is different than the mean throughput of the Kotlin version for a 95% confidence)".

Whilst this is true for the data that I have, I suspect that it is not true to say that there is no cost to Kotlin's string interpolation. Examining the bytecode for the Java (v 1.8.0_131) version we see.

```java
  public concat(LcostOfKotlin/strings/StringState;Lorg/openjdk/jmh/infra/Blackhole;)V
  @Lorg/openjdk/jmh/annotations/Benchmark;()
   L0
    LINENUMBER 18 L0
    ALOAD 2
    NEW java/lang/StringBuilder
    DUP
    INVOKESPECIAL java/lang/StringBuilder.<init> ()V
    ALOAD 1
    INVOKEVIRTUAL costOfKotlin/strings/StringState.getHello ()Ljava/lang/String;
    INVOKEVIRTUAL java/lang/StringBuilder.append (Ljava/lang/String;)Ljava/lang/StringBuilder;
    LDC " "
    INVOKEVIRTUAL java/lang/StringBuilder.append (Ljava/lang/String;)Ljava/lang/StringBuilder;
    ALOAD 1
    INVOKEVIRTUAL costOfKotlin/strings/StringState.getWorld ()Ljava/lang/String;
    INVOKEVIRTUAL java/lang/StringBuilder.append (Ljava/lang/String;)Ljava/lang/StringBuilder;
    INVOKEVIRTUAL java/lang/StringBuilder.toString ()Ljava/lang/String;
    INVOKEVIRTUAL org/openjdk/jmh/infra/Blackhole.consume (Ljava/lang/Object;)V
   L1
    LINENUMBER 19 L1
    RETURN
```

while the Kotlin (v 1.1.4) has

```java
  public final concat(LcostOfKotlin/strings/StringState;Lorg/openjdk/jmh/infra/Blackhole;)V
  @Lorg/openjdk/jmh/annotations/Benchmark;()
     [null checks, see Part 1]
   L1
    LINENUMBER 21 L1
    ALOAD 2
    NEW java/lang/StringBuilder
    DUP
    INVOKESPECIAL java/lang/StringBuilder.<init> ()V
    LDC ""
    INVOKEVIRTUAL java/lang/StringBuilder.append (Ljava/lang/String;)Ljava/lang/StringBuilder;
    ALOAD 1
    INVOKEVIRTUAL costOfKotlin/strings/StringState.getHello ()Ljava/lang/String;
    INVOKEVIRTUAL java/lang/StringBuilder.append (Ljava/lang/String;)Ljava/lang/StringBuilder;
    BIPUSH 32
    INVOKEVIRTUAL java/lang/StringBuilder.append (C)Ljava/lang/StringBuilder;
    ALOAD 1
    INVOKEVIRTUAL costOfKotlin/strings/StringState.getWorld ()Ljava/lang/String;
    INVOKEVIRTUAL java/lang/StringBuilder.append (Ljava/lang/String;)Ljava/lang/StringBuilder;
    INVOKEVIRTUAL java/lang/StringBuilder.toString ()Ljava/lang/String;
    INVOKEVIRTUAL org/openjdk/jmh/infra/Blackhole.consume (Ljava/lang/Object;)V
   L2
    LINENUMBER 22 L2
    RETURN
```

Both create a StringBuilder and then append the parts of the string, but the Kotlin compiler has appended a vestigial empty string representing the parts of the template string before the first substitution. As it is hard to believe that appending an empty string to a StringBuffer is free, the Kotlin code must be slower, just not, in this case, detectably so.

As a aside, both the Kotlin and the Java compilers miss the trick of initialising the StringBuilder with the first part of the concatenation - both cases should really generate code equivalent to  

```java
new StringBuilder(state.getHello()).append(state.getWorld())
``` 

Apart from the un-needed empty string, I found no places where Kotlin string interpolation had any cost over vanilla Java. Indeed where strings are known constant at compile time, they are composed then.

```kotlin
    fun `the compiler optimizes this to a constant`() = "${"hello"} ${"world"}"

    fun `and even this`() = "${"${"hello" + " " + "world"}"}"
``` 

both compile to 

```java
    LDC "hello world"
    ARETURN
```

although 

```kotlin
private val hello = "hello"
private val world = "world"

fun `this is not a constant expression`() = "$hello $world"
```




  



