---
title: Failure is not an Option - Functional Error Handling in Kotlin. Part 7 - Avoiding Failure
layout: post
tags: [Kotlin, Failure is not an Option]
---
This is Part 7 in a series looking at functional error handling in Kotlin. The parts are

* [Part 1 - Exceptions](failure-is-not-an-option-part-1.html)
* [Part 2 - Either](failure-is-not-an-option-part-2.html)
* [Part 3 - Result and Fold](failure-is-not-an-option-part-3.html)
* [Part 4 - Either v Exception](failure-is-not-an-option-part-4.html)
* [Part 5 - Embracing Either](failure-is-not-an-option-part-5.html)
* [Part 6 - What Should You Do While Waiting for the Standard Result Type](failure-is-not-an-option-part-6.html)
* [Part 7 - Avoiding Failure](failure-is-not-an-option-part-7.html)

Today I'd like to talk about avoiding error handling.

If I was cleverer than I am, this would have been the first part of this series. But I was seduced by the techniques of error handling and, as is often the case, not listening to what pain was telling me about my situation. It has taken 12 months of reading and thinking to realise what some people (hi Barry) were telling me a year ago. In my defence, I have not seen the advice in this post spelled out so directly, although I may just not have understood it when I saw it. My hope is that even if this is just another blog post saying the same thing as many others, it will increase the chances that people come to this information when they are ready to receive it.

So

As we've seen from the other posts in this series, handling errors is tedious and itself error-prone - and we could all do with less tedium and fewer errors. Exceptions are less tedious in code than other techniques, but as they allow us to sweep possible problems under the carpet, we either get less reliable code, or the tedium of lifting a lot of carpets. Result types are tedious in code, but potentially more reliable. Checked exceptions were a valiant effort to get the best of both worlds, but in the end foundered on the rock of first-class functions.

Errors are particularly pernicious in code because they are transitive. If a function relies on code that can fail then it either has to have a strategy for succeeding despite the failure of its compatriot, or it is itself subject to failure. Pretty soon any moderately complex functionality has a combinatorial explosion of ways that it can fail. We then end up adding code to reason with the possible errors just so that a function can fail with a error that makes sense to its callers. And that error handling code itself is subject to errors!

If we rely on exceptions for our error handling then the net effect is that most complex code paths can effectively throw any exception, and without checked exceptions we have easy no way to work out which. And even if we do work it out, then just swapping out one function for another can invalidate the analysis. Exactly the same thing is true for a Result type but with more complicated return types and control flow. (Actually there is another approach using sealed classes to represent errors within a bounded context. We'll cover that in another post - it can reduce the types of errors that have to be considered, but doesn't change their transitive nature.)

So what do we do? Mostly we suck it up. We catch exceptions at the top level and log and return an error, and/or we accept that every non-trivial function will return `Either<R, Exception>` and handle *that* at the top level in the same way. At some boundaries we try to deal with errors we know might happen in a sensible way, and then we run our systems and add code to deal with the other problems that we actually see.

I don't think that this can ever change completely, but it would be good if it applied to less of our codebase. Our aim should be to reduce the number of functions that can fail. If in particular we can do that for lower-level functions, then the higher level functions that call them will also become less subject to failure, which in time should significantly reduce the proportion of functions that have to consider error cases at all. The boundary of code that is always subject to failure will rise up through the layers. This seems like a far better outcome than my previous experience, which was that learning better error handling techniques just allowed me to tolerate the pain all over, instead of encouraging me to expand the pain-free zones.

With the aim of reducing with number of our functions that can fail in mind, what are the reasons that a function can fail?

## Type of Errors

Here are some basic categories of errors that can occur in a program.

### General Environmental Failures

These are things that can just happen, but shouldn't if things are not very badly broken. We might run out of memory, or find that a referenced method is not present because required libraries are not linked. All bets are off in these circumstances - they are represented by throwing a subclass of `Error` and we should probably just let the program die.

### External Failures

Generally represented on the JVM by `IOException` - these represent a failure external to our program. Maybe a function tried to read from a file that no longer exists, or write to a network socket that has died.

In practice the prevalence of external errors varies widely even when they can occur in principle. Both reading from an open file and writing to a socket *could* fail, but files on our local filesystem are rarely deleted while we are reading them. In contrast we should assume that any network call will fail at some point. If a function is likely to fail in this way, we need to plan for it. If we need to plan for it, it would be nice if we had been told that it was likely to happen, either by declaring a thrown exception or returning a result type.

Also in practice, functions that can fail with `IOException` are generally 'impure' in a functional programming sense - their result doesn't just depend on the value of their arguments. I'll have to talk about this topic another time, but one way to deal with this impurity is to push such functions to the edges of our systems. If we manage to push it to the top edge, then most of our system layers will not have to deal with any such error, but this is a hard trick to pull off.

For now then let's just assume that many, but by no means all, of our functions will be subject to failure with an `IOException`, either because they read from external systems, or invoke functions that are themselves subject to failure with an `IOException`.

### Partial Functions

The other common reason that a function may not be able to return normally (where abnormally includes returning an `Error` as part of a `Result` type) is that it is only able to give an output for a restricted range of its inputs - a partial function. A good example from previous episodes was

```kotlin
fun parseInt(s: String): Int = SOMECODE()
```

This is only able to return an `Int` for a special subset of all strings that might be passed in. An even more common example is

```kotlin
class Array<T> {
    operator fun get(index: Int): T = SOMECODE()
}
```

Array indexing can only succeed if the index is contained in the array, which (in most but not all languages) cannot be known at compile time.

If a partial function is passed an input for which it is undefined it is polite for it to let us know - generally by throwing an exception, returning `null`, or returning an error code.

It is partial functions that I'd like to think about today, as in most systems they represent the majority of the errors that can occur.

## Healing Partial Functions

As this is a Kotlin post, it's worth noting every function that takes a reference and cannot cope with it being null is a partial function. Kotlin allows us to make those functions total by specifying that they cannot be invoked with a null argument.

Can we heal partial functions by changing the type of their parameters in other cases?

In [Part 5](failure-is-not-an-option-part-5.html) I looked at various implementations of

```kotlin
fun addAsInts(s1: String, s2: String, s3: String): Either<Exception, Int> = resultOf {
    Integer.parseInt(s1) + Integer.parseInt(s2) + Integer.parseInt(s3)
}
```


Adding integers represented as strings is a partial function because it is only defined where each of its inputs can be interpreted as an integer. Here it is made a total function by expanding its result type so that we can return a failure result in these cases - but now all callers have to deal with this result rather than a plain old `Int`. As Part 5 showed, that can lead to some pretty cryptic code, especially when we come to combining results from several partial functions.

Now the example was chosen to illustrate how to combine results, but we can also solve the partial function problem by simply changing the signature.

```kotlin
fun add(i1: Int, i2: Int, i3: Int): Int = i1 + i2 + i3
```


Now the function is total again, and we don't have to pollute our calling code with error handling.

Where the data is naturally a string in the first place (typed by the user perhaps) then we are going to have to convert to `Int` somewhere. We *could* do that just before we need to interpret it, but that makes every function above it in the call stack either partial or subject to the errors in the conversion. So in this case we would almost certainly convert to `Int` as soon as possible, especially as the earlier we convert the more context we have available to report errors. The alternative is trying to recreate this context from an exception or result code propagated from several layers down.

In other cases though it's easy to underestimate how much simpler this makes things and pass down strings that represent `URI`'s, or `File`s or `DateTime`s and hence make the functions that interpret them partial.

With a bit of work many otherwise partial functions can be made total. Integer division, for example, can fail.

```kotlin
// throws ArithmeticException if count is 0
fun average(sum: Int, count: Int) = sum / count
```

We can fix this `average` by introducing a type to represent a `NonZeroInt` and using it as a parameter. This makes the function total - it is now defined for all possible (runtime) values of its parameters.

```kotlin
data class NonZeroInt(val value: Int) {
    init {
        require(value != 0)
    }
}

fun average(sum: Int, count: NonZeroInt) = sum / count

// Can't fail because divisor.value can never be 0
operator fun Int.div(divisor: NonZeroInt) = this / divisor.value
```

Similarly, many List operations can fail on an empty list, but be made total on a non-empty list.

```kotlin
class NonEmptyList<T>(private val wrapped: List<T>) :
    List<T> by wrapped {
    init {
        require(wrapped.isNotEmpty())
        wrapped.first()
    }
}
```


That's all you need ensure that calling `first()`, `last()`, `reduce()` etc on a `NonEmptyList` parameter can't fail.

Note that this technique does not give compile-time checking that an integer is not 0, or that an input list is not empty. What it does do is to push the error handling up the call stack so that lower functions do not have to consider failure, which in turn will allow more intermediate functions to be total and error-handling-free. Whilst you have to define new types, these not only aid totality but also make our code more expressive. The major downside is the extra heap allocation required to hold the wrapper object, and the occasional dereferencing of the wrapped value.

## Panacea?

Of course this cannot solve all of our partial function problems.

Often a function is partial for a specific combination of its arguments - array dereferencing or regular expression substitution for example. In these cases we might gain advantages by moving the operation up the call stack, or it might be that that these make the program less clear and so less safe.

We sometimes see functions that are only total for other combinations of more than two arguments - a string which contains the characters of the two other integer arguments maybe. Often these are code smells and should be addressed as such - is this grouping a thing? If so we can encapsulate the related parameters as an object that we can validate on construction and pass down, with the now-total operation a method on that object.

Another complication is a value that has different validity for different functions. One function might be happy with a `NonBlankString`, another needs the same value to be an `ALL_UPPERCASE_STRING`. In these cases we may be able to combine the types into a single validated type, but it certainly adds complexity which may or may not be worth the cost.

The biggest challenge is perhaps functions where validating totality is expensive and the function is only called in a subset of possible interactions. If the value of complex regular expression substitution is only actually required once every other Tuesday, you may not want to pull its evaluation up the call stack so that it is invoked on every of 100,000 requests per day. In these cases discretion is required - functions are *allowed* to be partial after all - we're just trying to reduce the number that are.

## On the Other Hand

It is *possible* to program in an environment where (almost) every function is partial - this is the case in a dynamic language. Every parameter could have any type, and is subject to runtime failure (usually with an exception) when we discover that its actual type does not meet our expectations. Programming in these languages is a different process than when we have static types. For myself, I like the ability to reason about my code that static types give me, and feel that I may as well double-down and apply that to errors as well.

## Conclusion

I have been seduced by the idea that because some parts of my code must deal with errors, all parts might as well deal with errors. Judging by other code that I read, I don’t think I’m alone. Converting low level partial functions to total functions can propagate totality through our code, significantly reducing the amount of error handling code that we have to write.

Thanks to [Nat Pryce](http://natpryce.com) for reviewing this. Nat and I have proposed a session based on our experiences in this area for KotlinConf 2019.

