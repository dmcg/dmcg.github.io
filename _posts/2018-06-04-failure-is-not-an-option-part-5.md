---
title: Failure is not an Option - Functional Error Handling in Kotlin. Part 5 - Embracing Either
layout: post
tags: [Kotlin, Failure is not an Option]
---
This is Part 5 in a series looking at functional error handling in Kotlin. The parts are

* [Part 1 - Exceptions](failure-is-not-an-option-part-1.html)
* [Part 2 - Either](failure-is-not-an-option-part-2.html)
* [Part 3 - Result and Fold](failure-is-not-an-option-part-3.html)
* [Part 4 - Either v Exception](failure-is-not-an-option-part-4.html)
* [Part 5 - Embracing Either](failure-is-not-an-option-part-5.html)
* [Part 6 - What Should You Do While Waiting for the Standard Result Type](failure-is-not-an-option-part-6.html)
* [Part 7 - Avoiding Failure](failure-is-not-an-option-part-7.html)

In this episode we'll look at an case where the functional style has some definite benefits over using exceptions, and then go on to try to solve a problem that you will come across that is less easily finessed.

## Encapsulating failure

Let’s start this episode with the case where using the functional Either type has some real advantages over exceptions.

Let’s say that I’m processing a potentially huge file, translating every line to a number that I want to sum.

```kotlin
fun sumLines(reader: BufferedReader): Long =
    reader.lineSequence().map(Integer::parseInt).fold(0L, Long::plus)
```

(Ordinarily we could call `.sum()` on the sequence, but that returns an Int which may overflow too quickly, so here I accumulate to a Long)

We run this for 5 minutes before discovering that some lines are corrupt, so that parseInt throws, aborting the whole process. Sigh. An expedient approach is just to catch the exception, log in place, and substitute a 0.

```kotlin
fun sumLines(reader: BufferedReader): Long =
    reader.lineSequence()
        .map {
            try {
                Integer.parseInt(it)
            } catch (e: Exception) {
                System.err.println(e.message)
                0
            }
        }
        .fold(0L, Long::plus)
```

I might be happy with that for a one-off solution, but this hard-codes our error handling and, as is sometimes the case with exceptions, hides the happy path which is the point of the algorithm.

Remembering our functional definition of parseInt

```kotlin
fun parseInt(s: String): Either<Exception, Int> = resultOf { Integer.parseInt(s) }
```

we *could* delay our gratification by creating a sequence of Either<Exception, Int>, and then fold over that.

```kotlin
fun sumLines(reader: BufferedReader): Long {
    val ints: Sequence<Either<Exception, Int>> = reader.lineSequence().map(::parseInt)
    return ints.fold(0L) { acc, intResult ->
        intResult.fold(
            { exception -> System.err.println(exception.message); acc },
            { result -> acc + result }
        )
    }
}
```

Hmmm, I'm not sure that is much better, especially with those two nested folds that really don't mean the same thing unless you close your eyes and imagine very hard. The least we can do is to de-nest them

```kotlin
fun sumLines(reader: BufferedReader): Long {
    val ints: Sequence<Int> = reader.lineSequence()
        .map(::parseInt)
        .map { intResult ->
            intResult.fold(
                { exception ->
                    System.err.println(exception.message);
                    0
                },
                { result -> result }
            )
        }
    return ints.fold(0L, Long::plus)
}
```

We can make things better by writing a little extension to allow us to not use Either.fold when we just want to use the value

```kotlin
inline fun <L, R> Either<L, R>.orElse(whenLeft: (L) -> R): R = when(this) {
    is Left -> whenLeft(this.l)
    is Right -> this.r
}
```

so now we have

```kotlin
fun sumLines(reader: BufferedReader): Long {
    val ints: Sequence<Int> = reader.lineSequence()
        .map(::parseInt)
        .map {
            it.orElse {
                exception -> System.err.println(exception.message)
                0
            }
        }
    return ints.fold(0L, Long::plus)
}
```

and we can pull that idea up a level

```kotlin
fun <L, R> Sequence<Either<L, R>>.eachOrElse(whenLeft: (L) -> R): Sequence<R> = this.map {
    it.orElse(whenLeft)
}
```

leaving us with the the still-a-bit-ugly

```kotlin
fun sumLines(reader: BufferedReader): Long {
    val ints: Sequence<Either<Exception, Int>> = reader.lineSequence().map(::parseInt)
    return ints.eachOrElse( { exception -> System.err.println(exception) ; 0 }).fold(0L, Long::plus)
}
```

Now though we have at least successfully separated what to do in the case of an error from our fundamental flow, allowing us to move the responsibility to the caller

```kotlin
fun sumLines(reader: BufferedReader, onError: (Exception) -> Int): Long =
    reader.lineSequence().map(::parseInt).eachOrElse(onError).fold(0L, Long::plus)

fun sumLines(reader: BufferedReader): Long =
    sumLines(reader) { exception ->
        System.err.println(exception)
        0
    }
```

This is typical of the functional programming approach - push all the nastiness to the outside of the system, leaving nice pure referentially-transparent core algorithms. Now that we've had that inspiration, we could of course do the same thing with exception-based code

```kotlin
fun sumLines(reader: BufferedReader, onError: (Exception) -> Int): Long =
    reader.lineSequence()
        .map {
            try {
                Integer.parseInt(it)
            } catch (e: Exception) {
                onError(e)
            }
        }
        .fold(0L, Long::plus)
```

Which of these implementations is better is open to debate. Pretty much every programmer will be able to read the try version and work out what it does after a while, and I think that it will be more efficient. When you're used to the declarative style though the eachOrElse version reveals the intention without having to run your brain as a virtual machine. These days I'd probably go with eachOrElse until I needed the performance.

Before we go on, eagle-eyed readers might spot a source of failure that I had missed. Did you?

I'll give you a couple of minutes.



What I'd failed to spot is that, if this were Java, calls to any methods on a Reader would declare an IOException, and we'd be forced to consider that reading every line might fail. Here the flow of control is inverted, so that the line sequence pokes strings at our code, but even so, every time it does the file could have been deleted or the network gone away. So map can still throw an IOException that we are not revealing by returning Either<Exception, Long>. Our function should be declared as

```kotlin
fun sumLines(reader: BufferedReader, onError: (Exception) -> Int): Either<Exception, Long> = resultOf {
    reader.lineSequence().map(::parseInt).eachOrElse(onError).fold(0L, Long::plus)
}
```

Frankly this has depressed me a bit. Even when I'm trying to demonstrate how an Either type can help with error handling in Kotlin, I mess it up. I suppose that we're actually no worse off than Python or C# or almost any other language developers though - we do the best we can, sometimes we make mistakes, and when we find them we do our best to fix them.

## Dealing with Control Flow

If you try this functional style of error handling, sooner or later you'll run into places where, well, things get icky. Let's interpret three strings as ints and add them, the old-fashioned way, with exceptions

```kotlin
fun addAsInts(s1: String, s2: String, s3: String): Either<Exception, Int> = resultOf {
    Integer.parseInt(s1) + Integer.parseInt(s2) + Integer.parseInt(s3)
}
```

Now how about with our functional parseInt and map / flatMap?

```kotlin
fun addAsInts(s1: String, s2: String, s3: String): Either<Exception, Int> =
    parseInt(s1).flatMap { i1 ->
        parseInt(s2).flatMap { i2 ->
            parseInt(s3).map { i3 ->
                i1 + i2 + i3
            }
        }
    }
```

"Really Duncan - that's better than exceptions?" I hear you cry.

No, I can't say that it is; not with a straight face anyway. We can make things a bit better if we're prepared to use our orElse and early returns

```kotlin
fun addAsInts(s1: String, s2: String, s3: String): Either<Exception, Int> = resultOf {
    parseInt(s1).orElse { return Left(it) } +
        parseInt(s2).orElse { return Left(it) } +
        parseInt(s3).orElse { return Left(it) }
}
```

which in turn can be sweetened a bit with

```kotlin
inline fun <L, R> Either<L, R>.onLeft(abortWith: (Left<L>) -> Nothing): R = when(this) {
    is Left -> abortWith(this)
    is Right -> this.r
}
```

to give us

```kotlin
fun addAsInts(s1: String, s2: String, s3: String): Either<Exception, Int> = resultOf {
    parseInt(s1).onLeft { return it } +
        parseInt(s2).onLeft { return it } +
        parseInt(s3).onLeft { return it }
}
```

but frankly my functional programmer friends hate early returns (which also complicate referential transparency) as much as they hate exceptions, so this is frowned upon.

What is required here is what Haskell calls [do-notation](https://en.wikibooks.org/wiki/Haskell/do_notation), which is a way to sequence expressions only evaluating the next if the previous didn't 'fail'. This is of course the role of exceptions and/or early returns in other languages.

Functional programmers value referential transparency so much that Scala programmers are resigned to using their [`for-comprehensions`](https://stackoverflow.com/questions/10441559/scala-equivalent-of-haskells-do-notation-yet-again) to solve this problem, and [Arrow](http://arrow-kt.io/docs/patterns/monad_comprehensions/) bends Kotlin's coroutines to the same end. With Arrow I could write

```kotlin
fun parseInt(s: String): Try<Int> = Try {
    Integer.parseInt(s)
}

fun addAsInts(s1: String, s2: String, s3: String): Try<Int>  =
    Try.monad().binding {
        val i1: Int = parseInt(s1).bind()
        val i2: Int = parseInt(s2).bind()
        val i3: Int = parseInt(s3).bind()
        i1 + i2 + i3
    }.ev()
```

(or at least I could until they changed the API again :-(

Personally I'd rather keep things on a level that I can understand, and maybe even implement myself. So I'd be inclined to define

```kotlin
fun <R> Either<Exception, R>.orThrow(): R = when(this) {
    is Left -> throw this.l
    is Right -> this.r
}
```

and use it like this

```kotlin
fun addAsInts(s1: String, s2: String, s3: String): Either<Exception, Int> = resultOf {
    parseInt(s1).orThrow() +
        parseInt(s2).orThrow() +
        parseInt(s3).orThrow()
}
```

Now I'm really not enough of a functional programmer to argue that this is as functionally-pure as do-notation, but from the trenches where I stand I can't see the difference. Combining exceptions with an Either type in this way at least plays to the strengths of the JVM in exception handling, but you may prefer Arrow, even if examining the bytecode it produces is enough to crash IntelliJ.

I think that's enough for today. In the next installment I hope to make some recommendations for how an API might document its failure modes in a manner that combines the best of checked exceptions and Either types.

