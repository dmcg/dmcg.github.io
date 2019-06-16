---
title: Failure is not an Option - Functional Error Handling in Kotlin. Part 6 - What Should You Do While Waiting for the Standard Result Type
layout: post
tags: [Kotlin, Failure is not an Option]
---
This is Part 6 in a series looking at functional error handling in Kotlin. The parts are

* [Part 1 - Exceptions](failure-is-not-an-option-part-1.html)
* [Part 2 - Either](failure-is-not-an-option-part-2.html)
* [Part 3 - Result and Fold](failure-is-not-an-option-part-3.html)
* [Part 4 - Either v Exception](failure-is-not-an-option-part-4.html)
* [Part 5 - Embracing Either](failure-is-not-an-option-part-5.html)
* [Part 6 - What Should You Do While Waiting for the Standard Result Type](failure-is-not-an-option-part-6.html)

It has been over a year since I wrote Part 5, and quite a lot has happened in that time. Arrow's [Try](https://arrow-kt.io/docs/arrow/core/try/) has matured but, as I read it, is now being deprecated as their approved way of representing errors. The Kotlin Standard Library has gained a [Result](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-result/index.html) type that we [aren't allowed to return](https://github.com/Kotlin/KEEP/blob/master/proposals/stdlib/result.md#limitations), and [Result4k](https://github.com/npryce/result4k) became useable by dint of renaming its types `Success` and `Failure` rather than `Ok` and `Err`.

So if you want to pick a functional error handling type for your project, not much has become clearer in 12 months!

I can however report one experimental result. I migrated a multi-person-month codebase from a result type based on Arrow [Either](https://arrow-kt.io/docs/arrow/core/either/) to a hand-rolled algebraic data type

```kotlin
sealed class Result<out R>

data class Success<out R>(val value: R) : Result<R>()
data class Failure(val exception: Exception) : Result<Nothing>()
```

The refactoring took less than a day and introduced no errors (that I found ;-). This is good news, because it is an existence proof that, at least for my style of programming, you could pick one result type for your codebase and change your mind with little pain.

This of course only applies if you can change the clients of your API. Library writers I think should still just throw exceptions unless they have a compelling reason not to.

Based on talking to Andrey Breslav at KotlinConf I suspect that Jetbrains are just being ultra-cautious, and that restrictions on the use of the standard `Result` will be removed as they gain confidence in it. It's worth noting though that the implementation is an inline class, not an algebraic data type. What this means in practice is that you cannot switch on the type. Instead of writing

```kotlin
val thang = when (result) {
    is Success -> "win"
    is Failure -> "fail"
}
```

you have to write

```kotlin
val thang = when {
    result.isSuccess -> "win"
    result.isFailure -> "fail"
    else -> error("Could contracts sort this out?")
}
```

which is obviously yuk, so if you want to have an easy migration to the standard type when it is allowed as a return type, I would learn to love fold

```kotlin
val thang = result.fold(
    onSuccess -> { "win" },
    onFailure -> { "fail" }
)
```

which you can use with both an ADT and the standard type, although note that the latter has the `onSuccess` parameter first in contrast to most Either-based results, so to future-proof as well as for clarity I would make sure to name both arguments.

## Next Time

I hadn't actually meant for this to be the subject of Part 6, but in the end I thought that a review was appropriate given the amount of time that has passed. This has been a bit off the cuff though, so I'd love to know if you agree or have a different perspective.

I have been thinking a lot about the broader topic of error handling recently, as [Nat Pryce](http://www.natpryce.com/) and I have proposed a session based on our experiences in this area for KotlinConf 2019. With luck the next episode can get around to what I had planned to write about - avoiding errors.

