---
title: Failure is not an Option - Functional Error Handling in Kotlin. Part 2 - Either
layout: post
tags: [Kotlin, Failure is not an Option]
---
This is Part 2 in a series looking at functional error handling in Kotlin. The parts are

* [Part 1 - Exceptions](failure-is-not-an-option-part-1.html)
* [Part 2 - Either](failure-is-not-an-option-part-2.html)
* [Part 3 - Result and Fold](failure-is-not-an-option-part-3.html)
* [Part 4 - Either v Exception](failure-is-not-an-option-part-4.html)
* [Part 5 - Embracing Either](failure-is-not-an-option-part-5.html)

In this second episode I’ll look at why functional programming tends to avoid exceptions, and what it uses instead.

A distinguishing feature of functional programming is Referential Transparency. When this applies an expression may be transparently replaced by the result of its evaluation. So if I write

```kotlin
val secondsIn24hours = 60 * 60 * 24
```

then I can replace 60 * 60 with 3600 or 60 * 24 with 1440 without affecting the results, in fact the compiler may decide to replace the whole expression with 86400 for us. In contrast

```kotlin
val dayLengthInHours = secondsIn(today()) / 60.0 / 60 / 24
```

is not referentially transparent, because today() will yield a different result than it did yesterday, and any day may have have a leap second applied.

Some say that exceptions break referential transparency. I’m not convinced that it isn’t possible to unify the two, but I would accept that exceptions muddy the referential transparency waters. Why should we care? Because referential transparency makes it a lot easier to reason about the behaviour of a program, which in turn leads to fewer errors and more opportunities to refactor and optimise.

If we want these things, then how should we signal and recover from errors? A time-honoured solution is to return a special value, often null. Let’s say that we want to parse a string into an integer. We could define

```kotlin
fun parseInt(s: String): Int? = TODO()
```

In Kotlin our clients would know that the result was `Int?`, and would explicitly be forced to deal with the case where the string did not represent an int, so this would be a fine solution. In Java it would be less fine, as there is no way to make the client consider null. In this case we could (for Java 8+) define

```java
public static Optional<Integer> parseInt(String s)
```

where `Optional<T>` has been provided for just this purpose of signalling nullability.

Note that the standard Java function is

```java
public static int parseInt(String s) throws NumberFormatException
```

The problem here is that NumberFormatException is an unchecked exception, so that clients are not forced deal with it. Was that a good decision? I’d say in retrospect not in this case, but we’ll return to the problem of partial functions later.

If we only care to know that something was amiss, and not the details, then a nullable or optional result is a good solution. But what if we want to convey where our parsing failed - what was the first character that wasn’t a digit? The NumberFormatException thrown by Java is able to carry that information (it doesn’t, but ho-hum). How can we replicate that behaviour in a functional way? In other words, how do we return either the error, or an integer?

The answer, as they say, is in the question. We define a type Either, which can hold one of two types, but only one at a time.

```kotlin
sealed class Either<out L, out R>

data class Left<out L>(val l: L) : Either<L, Nothing>()

data class Right<out R>(val r: R) : Either<Nothing, R>()
```

For no good reason that I can see, the convention is that Right is used for a result, Left for an error. If we stick to this convention we could define

```kotlin
fun parseInt(s: String): Either<String, Int> = try {
    Right(Integer.parseInt(s))
} catch (exception: Exception) {
    Left(exception.message ?: "No message")
}
```

How would we use this? As it is a sealed class, `when` expressions and smart casting work really nicely to let us write things like

```kotlin
val result: Either<String, Int> = parseInt(readLine() ?: "")
when (result) {
    is Right -> println("Your number was ${result.r}")
    is Left -> println("I couldn't read your number because ${result.l}")
}
```

which admittedly is pathologically not functional, but gives the general idea. By returning an Either we force our clients to deal with the fact that we may have failed - in effect we have reproduced some of checked exceptions in a functional form. To embrace this style you make all your functions return Either and when they in turn invoke something that could fail, pass on any failure or unwrap the success and act on it.

```kotlin
fun doubleString(s: String): Either<String, Int> {
    val result: Either<String, Int> = parseInt(s)
    return when (result) {
        is Right -> Right(2 * result.r)
        is Left -> result
    }
}
```

Whilst using `when` to unwrap an Either is cute, it quickly gets old, so we write

```kotlin
inline fun <L, R1, R2> Either<L, R1>.map(f: (R1) -> R2): Either<L, R2> =
    when (this) {
        is Right -> Right(f(this.r))
        is Left -> this
    }
```

which allows us to write the previous function as

```kotlin
fun doubleString(s: String): Either<String, Int> = parseInt(s).map { 2 * it }
```

Why is that function called `map` and not `invokeUnlessLeft`? Well if you squint you may be able to see that it is kind of the same thing as `List.map`. Practice that squinting, because we are now going to define

```kotlin
inline fun <L, R1, R2> Either<L, R1>.flatMap(f: (R1) -> Either<L, R2>): Either<L, R2> =
    when (this) {
        is Right -> f(this.r)
        is Left -> this
    }
```

This unpacks our value and uses it to invoke a function that in turn might fail (as it returns Either). What can we do with that? Well lets say we want to read from a Reader and print double the result.

```kotlin
fun BufferedReader.eitherReadLine(): Either<String, String> =
    try {
        val line = this.readLine()
        if (line == null)
            Left("No more lines")
        else
            Right(line)
    } catch (x: IOException) {
        Left(x.message ?: "No message")
    }


fun doubleNextLine(reader: BufferedReader): Either<String, Int> = reader.eitherReadLine().flatMap { doubleString(it) }
```

This code will return a Left with the failure if `eitherReadLine` fails, otherwise it will return the result of `doubleString`, which may itself be either a Left for failure or a Right with the final int result. In this way a chain of map and or flatMap calls acts like a series of expressions which might throw an exception - the first failure aborts the rest of the computation.

Frankly, if you come from an OO background this style does take some getting used to. In my experience no amount of reading helps - you just have to knuckle down and start writing code this way until it becomes less painful. There are other helper functions that we can write to make it less verbose and perhaps easier to read and reason with, but the approach is still not without cost compared to just throwing and managing exceptions.

The [next episode](failure-is-not-an-option-part-3.html) will look at more details of functional error handling and how to unify it with exceptions.

