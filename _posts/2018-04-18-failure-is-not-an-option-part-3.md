---
title: Failure is not an Option - Functional Error Handling in Kotlin. Part 3 - Result and Fold
layout: post
tags: [Kotlin, Failure is not an Option]
---
This is Part 3 in a series looking at functional error handling in Kotlin. The parts are

* [Part 1 - Exceptions](failure-is-not-an-option-part-1.html)
* [Part 2 - Either](failure-is-not-an-option-part-2.html)
* [Part 3 - Result and Fold](failure-is-not-an-option-part-3.html)
* [Part 4 - Either v Exception](failure-is-not-an-option-part-4.html)
* [Part 5 - Embracing Either](failure-is-not-an-option-part-5.html)

In this third episode I’ll look at some of the pros and cons of using an Either type for functional error handling.

If you were ahead of the game in the last installment you'll have noticed that we mostly caught exceptions and translated them to a Either.Left to signify failure.

```kotlin
fun parseInt(s: String): Either<String, Int> = try {
    Right(Integer.parseInt(s))
} catch (exception: Exception) {
    Left(exception.message ?: "No message")
}
```

You may further have said to yourself that it seemed a shame to loose all that nice stack-tracey information that Java went out of it's way to gather and use to create the exception. You may even have thought that we should have said

```kotlin
fun parseInt(s: String): Either<Exception, Int> = try {
    Right(Integer.parseInt(s))
} catch (exception: Exception) {
    Left(exception)
}
```

If so, I think you're right. Let's define a helper function to remove some of the boilerplate and refactor.

```kotlin
inline fun <R> resultOf(f: () -> R): Either<Exception, R> = try {
    Right(f())
} catch (e: Exception) {
    Left(e)
}

fun parseInt(s: String): Either<Exception, Int> = resultOf { Integer.parseInt(s) }

fun BufferedReader.eitherReadLine(): Either<Exception, String> = resultOf {
    this.readLine()
}

fun doubleString(s: String): Either<Exception, Int> = parseInt(s).map { 2 * it }

fun doubleNextLine(reader: BufferedReader): Either<Exception, Int> =
    reader.eitherReadLine().flatMap { doubleString(it) }
```

Either<Exception, R> is such a common pattern that you may be tempted give it a special name. Arrow calls it [Try](http://arrow-kt.io/docs/datatypes/try/). There are various other libraries that provide the abstraction as a Result type, including a [proposal for the standard library](https://youtrack.jetbrains.com/issue/KT-18608).

There are a couple of big problems though with making a concrete type that locks in Left to be Exception.

The first problem is, should that be Exception, or Throwable? Arrow says Throwable, but I disagree. If we catch Throwable we are catching Errors, which `indicate serious problems that a reasonable application should not try to catch.` In fact the Scala code on which the Arrow Try is based does not catch Error. The JetBrains Stdlib proposal catches all Throwables, but it is designed to represent the way that an expression terminated, rather than being used for functional error handling.

The second big problem is that functions do not always fail with an exception. Especially if we're just validating parameters in local functions it seems overkill to create an exception, quite an expensive operation, just to in order to represent a failure. So we're probably going to want an Either<String, R> for these cases.

A different way of formalising the use of Either as Result is offered by [Result4K](https://github.com/npryce/result4k). This defines a sealed Result type with with Ok or Err subtypes, making the Right and Left cases clear. It's my favourite of the solutions I've seen so far, but then it is from the home team.

For now then, let's stick with our generic Either type and see what more we can do with it.

If there's one thing that categorises functional programming with monads (no I don't, but Either is) it's delayed gratification. By mapping and flatmapping we don't actually have to deal with errors at low levels - just pass their occurrence back to our callers. As with exceptions though, in the end someone will have to report failure.

Back in [Part 2](failure-is-not-an-option-part-2.html) we used `when` to switch on the type of Either

```kotlin
val result: Either<Exception, Int> = parseInt(readLine() ?: "")
when (result) {
    is Right -> println("Your number was ${result.r}")
    is Left -> println("I couldn't read your number because ${result.l}")
}
```

The formalisation of this on Either is called `fold`. It takes two functions and returns the result of calling the first if the Either is Left, the second if it is Right.
This lets us actually do something on the outside of our system
Again you have to squint quite hard to see the similarity between Either.fold and List.fold, but it turns out that functional programmers are so good at squinting that they have given it a special name - catamorphism.

Personally I hate fold. Sure it unwraps the values for you, but putting lambdas inside a function invocation is ugly, and you pretty much always need to name the arguments because you want the success case first, but that doesn't happen naturally when success is Right. I'll often fall back on the raw `when` formulation because it has the braces in the right place and reads naturally. I suppose the nastiness of fold at least encourages the user to only do it as a last resort, which is as it should be.

Before we finish this episode, let's just review what we have.


```kotlin
sealed class Either<out L, out R>

data class Left<out L>(val l: L) : Either<L, Nothing>()

data class Right<out R>(val r: R) : Either<Nothing, R>()

inline fun <L, R1, R2> Either<L, R1>.map(f: (R1) -> R2): Either<L, R2> =
    when (this) {
        is Right -> Right(f(this.r))
        is Left -> this
    }

inline fun <L, R1, R2> Either<L, R1>.flatMap(f: (R1) -> Either<L, R2>): Either<L, R2> =
    when (this) {
        is Right -> f(this.r)
        is Left -> this
    }

inline fun <L, R, T> Either<L, R>.fold(fl: (L) -> T, fr: (R) -> T): T =
    when (this) {
        is Right -> fr(this.r)
        is Left -> fl(this.l)
    }

inline fun <R> resultOf(f: () -> R): Either<Exception, R> =
    try {
        Right(f())
    } catch (e: Exception) {
        Left(e)
    }
```

There's an awful lot of usefulness packed into a few lines of code there - a testament to Kotlin. The nice thing about using data classes and extension methods is that I don't have to provide everything that you could want to do with Either as a method - you can add your own.

If you're beginning to experiment with functional error handling, I'd start with the definitions up above. If you find yourself doing something the same way more than twice, look to see if you add an extension method that expresses that operation. For example, I have

```kotlin
inline fun <L, R, T> Either<L, R>.assertSuccess(f: (R) -> T) =
    when (this) {
        is Right -> f(this.r)
        is Left -> error("Value was not a Right, but a Left of ${this.l}")
    }
```

which I use in test code in one of two ways

```kotlin
val result: Either<Exception, String> = someOperation()

result.assertSuccess {
    assertEquals("banana", it)
}

val rightValue: String = result.assertSuccess { it }
```

Once you've internalised some of the idioms, I'd then introduce a specific Result type, it can be it's own sealed class or a typealias for Either. Or you can import someone else's library and experiment with it.

This series isn't done yet. As you'll find out if you do go down this path, there are plenty of pain points still to be addressed!

