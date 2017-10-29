---
title: The Cost of Kotlin Language Features - Preliminary Results Part 3 - Invocation

layout: post

---
This is Part 3 in a series examining *The Cost of Kotlin Language Features* in preparation for my presentation at [KotlinConf](http://kotlinconf.com) in November. The series consists of 

* [Lessons Learned Writing Java and Kotlin Microbenchmarks](benchmarks.html)
* [Part 1 - Baselines](cost-of-kotlin-preliminary-results-part1-baselines.html)
* [Part 2 - Strings](cost-of-kotlin-preliminary-results-part2-strings.html)
* [Part 3 - Invocation](cost-of-kotlin-preliminary-results-part3-invocation.html)
* [Part 4 - Nullable Primitives](cost-of-kotlin-preliminary-results-part-4-nullable-primitives.html)
* [Part 5 - Properties](cost-of-kotlin-preliminary-results-part-5-properties.html)

I'm publishing these results ahead of KotlinConf to give an opportunity for peer-review, so please do give me your feedback about the content, experimental method, code and conclusions. If you're reading this before November 2017 it isn't too late to save me from making a fool of myself in person, rather than just on the Internet. As ever the current state of the code to run the benchmarks is available for inspection and comment [on GitHub](https://github.com/dmcg/kostings).
 
In this post I look at the basic building block of functional programming - the first-class function. This allows us to treat a unit of computation as just another reference, to be passed around and then invoked when needed. I haven't (yet?) compared the Kotlin performance to the equivalent Java - here I just compare different Kotlin code.

The function we're going to invoke is simple

```kotlin
fun aFunction(i: Int) = 2 * i
```

and our baseline is invoking it directly.

```kotlin
open class Invoking {

    @Benchmark
    fun baseline(intState: IntState) : Int {
        return aFunction(intState.randomInt)
    }
}
```

[In this case, I'm not consuming the result in a [JMH](http://openjdk.java.net/projects/code-tools/jmh/) blackhole as previously, but instead returning it to the framework, which promises to make sure that the result is referenced so that the benchmark computation has to be performed. Also note that, nervous of other processor optimisations, I use a random variable rather than a fixed value for each benchmark execution when dealing with primitives.] 
 
The simplest thing we can do is to create a function reference and invoke that, rather than invoking the function directly.

```kotlin
    @Benchmark
    fun invoke_via_reference(intState: IntState) : Int {
        return (::aFunction)(intState.randomInt)
    }
```

This turns out to be between 1% and 2% slower than the baseline. 

```kotlin
    @Test
    fun `invoke via reference is slower than plain invocation`() {
        assertThat(this::baseline, probablyFasterThan(this::invoke_via_reference, byAFactorOf = 0.01))
        assertThat(this::baseline, ! probablyFasterThan(this::invoke_via_reference, byAFactorOf = 0.02))
    }
```

This result is surprising in two ways.

1.  As far as I can see, the compiler could compile invoke_via_reference to the same code as the baseline.
2.  Given that it hasn't; < 2% is really not much slower.

So let's fall back to the bytecode

```java
public final baseline(LcostOfKotlin/primitives/IntState;)I
  @Lorg/openjdk/jmh/annotations/Benchmark;()
    @Lorg/jetbrains/annotations/NotNull;() // invisible, parameter 0
   L0
    ALOAD 1
    LDC "intState"
    INVOKESTATIC kotlin/jvm/internal/Intrinsics.checkParameterIsNotNull (Ljava/lang/Object;Ljava/lang/String;)V
   L1
    LINENUMBER 16 L1
    ALOAD 1
    INVOKEVIRTUAL costOfKotlin/primitives/IntState.getRandomInt ()I
    INVOKESTATIC costOfKotlin/invoking/InvokingKt.aFunction (I)I
    IRETURN
    
public final invoke_via_reference(LcostOfKotlin/primitives/IntState;)I
  @Lorg/openjdk/jmh/annotations/Benchmark;()
    @Lorg/jetbrains/annotations/NotNull;() // invisible, parameter 0
   L0
    ALOAD 1
    LDC "intState"
    INVOKESTATIC kotlin/jvm/internal/Intrinsics.checkParameterIsNotNull (Ljava/lang/Object;Ljava/lang/String;)V
   L1
    LINENUMBER 22 L1
    GETSTATIC costOfKotlin/invoking/Invoking$invoke_via_reference$1.INSTANCE : LcostOfKotlin/invoking/Invoking$invoke_via_reference$1;
    CHECKCAST kotlin/jvm/functions/Function1
    ALOAD 1
    INVOKEVIRTUAL costOfKotlin/primitives/IntState.getRandomInt ()I
    INVOKESTATIC java/lang/Integer.valueOf (I)Ljava/lang/Integer;
    INVOKEINTERFACE kotlin/jvm/functions/Function1.invoke (Ljava/lang/Object;)Ljava/lang/Object;
    CHECKCAST java/lang/Number
    INVOKEVIRTUAL java/lang/Number.intValue ()I
    IRETURN
```

Instead of just `INVOKESTATIC costOfKotlin/invoking/InvokingKt.aFunction`, `invoke_via_reference` has additional calls to get the instance of the function as a function type, check that it is the right type, box the parameter because there are no primitive specialisations of the function types, invoke the function through its interface with the boxed parameter, and finally unbox the result. All this costs just ~1.5% throughput.

[Writing this I realise that if the benchmark tax (calls to `checkParamaterIsNotNull` and `IntState.getRandomInt`) is high, it reduces the apparent cost of the invocations we actually want to measure. In this case I don't believe that they are - in particular note that the random int is calculated outside the benchmark not on demand.]

Anyhoo; the point of first class functions is to be passed around. Let's simulate that by passing them to something whose job it is to invoke them.

```kotlin
inline fun <T> applier(t: T, f: (T) -> T) = f(t)
```

Now we can benchmark lambdas and function references.

```kotlin
    @Benchmark
    fun passed_as_lambda(intState: IntState) : Int {
        return applier(intState.randomInt) { aFunction(it ) }
    }

    @Benchmark
    fun passed_as_function_reference(intState: IntState) : Int {
        return applier(intState.randomInt, ::aFunction)
    }
```

For the data that I've gathered, these are statistically indistinguishable from the baseline!

Again, there are two reasons for that exclamation mark. Let's look at the bytecode.

```java
public final passed_as_lambda(LcostOfKotlin/primitives/IntState;)I
  @Lorg/openjdk/jmh/annotations/Benchmark;()
    @Lorg/jetbrains/annotations/NotNull;() // invisible, parameter 0
   L0
    ALOAD 1
    LDC "intState"
    INVOKESTATIC kotlin/jvm/internal/Intrinsics.checkParameterIsNotNull (Ljava/lang/Object;Ljava/lang/String;)V
   L1
    LINENUMBER 27 L1
    ALOAD 1
    INVOKEVIRTUAL costOfKotlin/primitives/IntState.getRandomInt ()I
    ISTORE 2
   L2
    LINENUMBER 66 L2
    ILOAD 2
    ISTORE 3
   L3
    LINENUMBER 27 L3
    ILOAD 3
    INVOKESTATIC costOfKotlin/invoking/InvokingKt.aFunction (I)I
   L4
   L5
    NOP
   L6
    IRETURN
```

[`passed_as_function_reference` is identical [*mutatis mutandis*](https://www.merriam-webster.com/dictionary/mutatis%20mutandis)]

Pretentious! Moi? Anyhoo; because of the wonders of inline functions, we haven't had to pay the price of boxing and unboxing our int, nor of invoking a function type through its interface. If you ignore the line numbers and loading and storing - this is the same code as the baseline. Which is remarkable.

And yet it isn't the same code as the baseline, because of the loading and storing, which are setting things up on the JVM stack to be used in the function. In the baseline the result of INVOKEVIRTUAL of `getRandomInt` puts the argument to INVOKESTATIC `aFunction` straight onto the operand stack. In this code the result of `getRandomInt` is pulled from the stack into a local variable 2, which is pushed onto the stack in order to be stored in local variable 3, which is pushed onto the stack in order to be used by `aFunction`. This faffing is a bit surprising, as is the fact that I can't measure its cost in terms of benchmark speed.

I think that the local variable hokey-cokey is required because we expect to be able to set a breakpoint in the `applier` and see the values of its parameters, although that *may* only explain one of the in-outs. My inability to detect any time taken in the dance may be because these are very fast instructions to execute, or it may be that HotSpot detects that they are not necessary and optimises them away. Your virtual machine mileage may vary.   

My brain hurts now, so just one more benchmark before lunch.

```kotlin
val aBlock: (Int) -> Int = { 2 * it }

    @Benchmark
    fun passed_as_value_of_function_type(intState: IntState) : Int {
        return applier(intState.randomInt, aBlock)
    }
```

Here we invoke via a variable of function type, rather than directly with a lambda or function reference. In this case the speed is slower than the baseline and the other `applier` applications by 1-2%. Why?

```java
  public final passed_as_value_of_function_type(LcostOfKotlin/primitives/IntState;)I
  @Lorg/openjdk/jmh/annotations/Benchmark;()
    @Lorg/jetbrains/annotations/NotNull;() // invisible, parameter 0
   L0
    ALOAD 1
    LDC "intState"
    INVOKESTATIC kotlin/jvm/internal/Intrinsics.checkParameterIsNotNull (Ljava/lang/Object;Ljava/lang/String;)V
   L1
    LINENUMBER 37 L1
    ALOAD 1
    INVOKEVIRTUAL costOfKotlin/primitives/IntState.getRandomInt ()I
    INVOKESTATIC java/lang/Integer.valueOf (I)Ljava/lang/Integer;
    ASTORE 2
    INVOKESTATIC costOfKotlin/invoking/InvokingKt.getABlock ()Lkotlin/jvm/functions/Function1;
    ASTORE 3
   L2
    LINENUMBER 68 L2
    ALOAD 3
    ALOAD 2
    INVOKEINTERFACE kotlin/jvm/functions/Function1.invoke (Ljava/lang/Object;)Ljava/lang/Object;
   L3
    CHECKCAST java/lang/Number
    INVOKEVIRTUAL java/lang/Number.intValue ()I
    IRETURN
```

Here we pay the price of getting the value of `aBlock`, and, because it is a function type and we are using a primitive, boxing and unboxing in order to call it, via its interface. This is this interesting because it shows that, for inline functions, the cost of the function depends not just on its definition, but also the code into which it is inlined. 

  



