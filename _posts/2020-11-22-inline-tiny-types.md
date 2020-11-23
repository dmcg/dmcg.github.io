---
title: Inline Tiny Types With Validation
layout: post
tags: [Kotlin, Failure is not an Option]
---

It’s been a long time since my last post.
This is largely because my writing efforts have been directed towards a book!
Nat Pryce (co-author of the excellent [GOOS](http://www.growing-object-oriented-software.com/)) and I are hard at work on _Java to Kotlin, A Refactoring Guidebook_, due to be published in 2021 by O’Reilly.
You can read our work in progress on [O'Reilly Online Learning](https://learning.oreilly.com/library/view/java-to-kotlin/9781492082262/).

Nat and I are both fans of [tiny-types](https://darrenhobbs.com/2007/04/11/tiny-types/).
The basic idea here is to have a separate type for individual domain concepts rather than just using (usually) Strings. So we might define `GivenName` and `FamilyName` types so that a `Customer` can be

```kotlin
data class Customer(
    val id: CustomerId,
    val givenName: GivenName,
    val familyName: FamilyName
)
```


Compared to raw strings, we can't mix up the order of our given and family names when we create a customer, nor can we accidentally pass an `OrderId` where we need a `CustomerId`.

Here's a typical tiny type in Kotlin:

```kotlin
data class GivenName(val value: String)
```

As you can imagine, tiny types are a bit of a pain in Java, where we have to define `equals`, `hashCode` and `toString` by hand, but Kotlin helps them make economic sense.
For bonus points, we can even have our types behave like a `CharSequence` (the closest interface to `String`.

```kotlin
data class FamilyName(val value: String): CharSequence by value
```

This lets us write, for example

```kotlin
familyName.isNotBlank()
```

because `isNotBlank` is helpfully defined on `CharSequence` not `String`.

On the subject of non-blank-ness, it would be really helpful if we were prevented from creating a blank `CustomerId`.
That would go a long way to avoiding the errors we get when functions are partial - only defined on some of their inputs (see [Failure is not an Option, Part 7 - Avoiding Failure](failure-is-not-an-option-part-7.html)).

We can prevent the creation of an invalid `CustomerId` by checking the value in an `init` block.

```kotlin
data class CustomerId(val value: String): CharSequence by value {
    init {
        require(value.isNotBlank())
    }
}
```

Now we may get an error when we try to create a `CustomerId`, but if we have an instance, we can rely on the fact that it isn't blank.

Given how easy they are to write in Kotlin, about the only drawback with tiny types is we actually end up with 2 objects to represent one value.
That's the initial `String`, and the data class wrapper around the string.
The extra object probably consumes an extra 16 bytes these days, which is quite an overhead if we have a lot of wrapped strings.
Potentially worse is the issue of cache coherency, because the initial string was probably created in the bowels of some JDBC code and may end up a long way in memory from its wrapper.
So calling a method on a tiny type reference may involve fetching it into cache, and then the value that it is wrapping.

Kotlin [Inline Classes](https://kotlinlang.org/docs/reference/inline-classes.html), whilst still experimental, are aimed at removing this overhead.

```kotlin
inline class GivenName(val value: String)
```

In most circumstances the compiler arranges for instances of inline classes to be passed around as just the single contained value, so that we don't have the overhead of creating an extra wrapper instance.
This should be perfect for tiny types.

There's one major problem though, we can't easily validate inline classes.

```kotlin
inline class CustomerId(val value: String) {
    init { // Error: Inline class cannot have an initializer block
        require(value.isNotBlank())
    }
}
```

`init` blocks are disallowed because Java code is permitted to call Kotlin methods that take inline classes, passing the base type (`String` above).
In this case the `init` block won't have been called, so users of inline classes can't know that validation will have been performed.
So `init` blocks are disallowed.

For the same reason the primary constructor must be public.
In fact, every route to validating an inline class is [deliberately blocked](https://discuss.kotlinlang.org/t/about-init-blocks-in-inline-classes/11824) because it might not have been taken when called from Java.

If you have a pure-Kotlin codebase, or just only ever call down into Java rather than back into Kotlin, this is a  shame.
It would be really nice to use inline classes to implement validated tiny types.
Can we find a way to make this work?

What we need to do is either make sure that calling `CustomerId("")` throws an exception, or prevent callers from calling the `CustomerId(val value: String)` at all, instead making them go through some factory which can perform pre-validation.

We can't throw an exception from the constructor because we can't have an init block, but there is a sneaky way to achieve the second, at least for pure Kotlin code -
make the value another inline class.

```kotlin
inline class CustomerId(private val __value: __DontMakeMe) {
    val value get(): String = __value.value
}

inline class __DontMakeMe(val value: String)
```

Now, in Kotlin, we can't call the constructor with a `String`:

```kotlin
val id = CustomerId("42") // Type mismatch: inferred type is String but __DontMakeMe was expected
```

We _can_ still call it, but there's enough warning that we are subverting things:

```kotlin
val id = CustomerId(__DontMakeMe("42"))
```

Now we can create a factory function to validate a `String` and create a  `CustomerId` with it.

```kotlin
fun customerIdOf(s: String): CustomerId? =
    when {
        s.isNotBlank() -> CustomerId(__DontMakeMe(s))
        else -> null
    }
```

Looking at the bytecode, we end up calling a static 'constructor' function for both `CustomerId` and `__DontMakeMe` here, but both just check that their parameter is not null.

I've chosen to return a nullable `CustomerId?` from `customerIdOf` so that callers know the call might fail, rather than having to read the code or documentation to see that it might throw an exception.

Inline classes can implement interfaces, although not through delegation at present.

```kotlin
inline class FamilyName(private val __value: __DontMakeMe): CharSequence {
    val value get(): String = __value.value

    override val length get() = value.length

    override fun get(index: Int) = value.get(index)

    override fun subSequence(startIndex: Int, endIndex: Int) =
        value.subSequence(startIndex, endIndex)
}

fun familyNameOf(s: String): FamilyName? =
    when {
        s.isNotBlank() -> FamilyName(__DontMakeMe(s))
        else -> null
    }
```

We can also define `toString` in order to mask sensitive information

```kotlin
inline class CardNumber(private val __value: __DontMakeMe) {
    val value get(): String = __value.value

    override fun toString() = "************" + value.substring(12)
}

fun cardNumberOf(s: String): CardNumber? =
    when {
        s.length == 16 && s.all(Char::isDigit) -> CardNumber(__DontMakeMe(s))
        else -> null
    }
```

As I've said, this is a subversion of the inline class initialisation rules and won't be safe if you accept calls in from Java.
I'm also not sure how marshalling technologies like Jackson or kotlinx.serialisation will interact with the technique.
For pure Kotlin codebases though I think it's worth a try, as tiny types can significantly improve the readability and safety of our code.

Come back soon, because I'll present a tiny tiny types framework using phantom types to reduce the amount of boilerplate code required.

Postscript - there is some discussion of other ways to achieve validation on the [reddit discussion](https://www.reddit.com/r/Kotlin/comments/jzevz4/inline_tiny_types_with_validation/).

