---
title: Test Driven to Specification with Minutest - Part 1
layout: post
---
This is the first in a mini-series on my new test library, [Minutest](https://github.com/dmcg/minutest). I'll post the
other installments over the next few days.

Minutest allows the writing of tests in nested contexts, in a [Spec](http://rspec.info/)-style. But I find that, unless
the problem is very well, erm, specified, then specs are hard to write. In Test Driven Development we're often exploring
the shape of a solution in a way that doesn't fit well with specs, at least until we're done.

At the same time the tests that have helped us test drive functionality may not be ideal to communicate to future
developers what our code does, or to support safe refactoring of that code.

The ideal situation then would be for our test framework to support us as we TDD our way to some code, and then refactor
the tests to a more formal specification. That is the subject of this series.

## The Problem

The functionality that I'm going to implement was inspired by a question on the
[Kotlin Slack](https://kotlinlang.slack.com/archives/C0922A726/p1549403911046300). It boils down to partitioning a list
into other lists, rather like the standard `fun <T> Iterable<T>.partition(predicate: (T) -> Boolean): Pair<List<T>, List<T>>`
but for more than one predicate and more than two output lists.

## First Test

This is TDD, so we had better start with a test. In Minutest it looks like this.

```kotlin
class PartitionTests : JUnit5Minutests {
    fun tests() = rootContext<Unit>() {
        test("check I can run a test") {}
    }
}
```

We can point to this in IntelliJ and run it - if all is well we are ready to get on with the job proper.

## Driving an Interface

Now let's sketch out the form of the function that we want to implement by creating an example.

```kotlin
fun tests() = rootContext<Unit>() {
    test("explore the interface") {
        val items = listOf(-1, 0, 1, 2, 3)
        val predicates = listOf(
            Context2::isNegative,
            Context2::isZero,
            Context2::isPositive
        )
        val result = items.partition(predicates)
        assertEquals(listOf(listOf(-1), listOf(0), listOf(1, 2, 3)), result)
    }
}
```

    This won't compile until we add our predicates and a stub implementation.

```kotlin
fun isNegative(x: Int) = x < 0

fun isZero(x: Int) = x == 0
fun isPositive(x: Int) = x > 0

fun <T> Iterable<T>.partition(predicates: List<(T) -> Boolean>): List<List<T>> = TODO()
```

Now it compiles but fails with the `kotlin.NotImplementedError` from our `TODO`

## Driving an Implementation

I'm not clever enough to get this working in one step, so I'm going to step back and try just a degenerate case. We can
keep the first test but `SKIP` it for now, and then add a test that I can get to pass.

```kotlin
fun tests() = rootContext<Unit>() {
    SKIP - test("explore the interface") {
        val items = listOf(-1, 0, 1, 2, 3)
        val predicates = listOf(::isNegative, ::isZero, ::isPositive)
        val result = items.partition(predicates)
        assertEquals(listOf(listOf(-1), listOf(0), listOf(1, 2, 3)), result)
    }
    test("no predicates") {
        val items = listOf(-1, 0, 1, 2, 3)
        val predicates = emptyList<(Int) -> Boolean>()
        val result = items.partition(predicates)
        assertEquals(emptyList<List<Int>>(), result)
    }
}
```

This has the twin advantages of being a case that we are going to have to test for, and being easy to implement.

```kotlin
fun <T> Iterable<T>.partition(predicates: List<(T) -> Boolean>): List<List<T>> = emptyList()
```

Woo-hoo, a passing test.

OK, now lets add another easy test.

```kotlin
test("predicates but no items") {
    val items = emptyList<Int>()
    val predicates = listOf(::isNegative, ::isZero, ::isPositive)
    val result = items.partition(predicates)
    assertEquals(listOf(emptyList<Int>(), emptyList(), emptyList()), result)
}
```

This fails because we need to return a list for each predicate - I can do that.

```kotlin
fun <T> Iterable<T>.partition(predicates: List<(T) -> Boolean>): List<List<T>> =
    predicates.map { emptyList<T>() }
```

We now have two passing tests, and one skipped until I'm ready to step up. I suppose that had better be now. Reinstate the failing test and just go for it.

```kotlin
fun tests() = rootContext<Unit>() {
    test("items and predicates") {
        val items = listOf(-1, 0, 1, 2, 3)
        val predicates = listOf(::isNegative, ::isZero, ::isPositive)
        val result = items.partition(predicates)
        assertEquals(listOf(listOf(-1), listOf(0), listOf(1, 2, 3)), result)
    }
    test("no predicates") {
        val items = listOf(-1, 0, 1, 2, 3)
        val predicates = emptyList<(Int) -> Boolean>()
        val result = items.partition(predicates)
        assertEquals(emptyList<List<Int>>(), result)
    }
    test("predicates but no items") {
        val items = emptyList<Int>()
        val predicates = listOf(::isNegative, ::isZero, ::isPositive)
        val result = items.partition(predicates)
        assertEquals(listOf(emptyList<Int>(), emptyList(), emptyList()), result)
    }
}
```

My approach is to group the items by whether they match the predicate.

Firstly I'll add a function body buy some time,

```kotlin
fun <T> Iterable<T>.partition(predicates: List<(T) -> Boolean>): List<List<T>> {
    return predicates.map { emptyList<T>() }
}
```

then sketch out the intermediate.

```kotlin
fun <T> Iterable<T>.partition(predicates: List<(T) -> Boolean>): List<List<T>> {
    val grouped: Map<(T) -> Boolean, List<T>> = this.groupBy { item ->
        predicates.firstMatch(item)
    }
    return predicates.map { emptyList<T>() }
}

private fun <T> Iterable<(T) -> Boolean>.firstMatch(item: T): (T) -> Boolean = TODO()
```

Now `firstMatch` is just `find`, except that returns a nullable. Hmmm, I suppose that is the case where there isn't a
predicate to match an item. We can relax the type of the keys in our map to get it to compile

```kotlin
fun <T> Iterable<T>.partition(predicates: List<(T) -> Boolean>): List<List<T>> {
    val grouped: Map<((T) -> Boolean)?, List<T>> = this.groupBy { item ->
        predicates.firstMatch(item)
    }
    return predicates.map { emptyList<T>() }
}

private fun <T> Iterable<(T) -> Boolean>.firstMatch(item: T): ((T) -> Boolean)? =
    this.find { predicate -> predicate(item) }
```

There should be one value of `grouped` for each of our predicates - so that this type-checks

```kotlin
fun <T> Iterable<T>.partition(predicates: List<(T) -> Boolean>): List<List<T>> {
    val grouped: Map<((T) -> Boolean)?, List<T>> = this.groupBy { item ->
        predicates.firstMatch(item)
    }
    return grouped.values.toList()
}

```

## Almost There?

Running the tests, `items and predicates` passes, so we did something right, but the other two fail, so they've proved
worthwhile too. `no predicates` reports:

```
Expected :[]
Actual   :[[-1, 0, 1, 2, 3]]
```

while `predicates but no items` says:

```
Expected :[[], [], []]
Actual   :[]
```

It's time to actually think...

...

and come to the conclusion that in both these cases we're not returning a list for each predicate. Previously we `map`ped
over the predicates to create a list for each in the output.

Let's do that, fetching the predicate's own entry from the groups, and noting that the null-safety prevents us from
just indexing and instead makes us take the right decision to return an empty list if there are no items matching
the predicate.

```kotlin
fun <T> Iterable<T>.partition(predicates: List<(T) -> Boolean>): List<List<T>> {
    val grouped: Map<((T) -> Boolean)?, List<T>> = this.groupBy { item ->
        predicates.firstMatch(item)
    }
    return predicates.map { grouped.getOrDefault(it, emptyList()) }
}

```

Triumph - our tests pass - let's review.

## Working Code


```kotlin
class PartitionTests : JUnit5Minutests {
    fun tests() = rootContext<Unit>() {
        test("items and predicates") {
            val items = listOf(-1, 0, 1, 2, 3)
            val predicates = listOf(::isNegative, ::isZero, ::isPositive)
            val result = items.partition(predicates)
            assertEquals(listOf(listOf(-1), listOf(0), listOf(1, 2, 3)), result)
        }
        test("no predicates") {
            val items = listOf(-1, 0, 1, 2, 3)
            val predicates = emptyList<(Int) -> Boolean>()
            val result = items.partition(predicates)
            assertEquals(emptyList<List<Int>>(), result)
        }
        test("predicates but no items") {
            val items = emptyList<Int>()
            val predicates = listOf(::isNegative, ::isZero, ::isPositive)
            val result = items.partition(predicates)
            assertEquals(listOf(emptyList<Int>(), emptyList(), emptyList()), result)
        }
    }
}

fun <T> Iterable<T>.partition(predicates: List<(T) -> Boolean>): List<List<T>> {
    val grouped: Map<((T) -> Boolean)?, List<T>> = this.groupBy { item ->
        predicates.firstMatch(item)
    }
    return predicates.map { grouped.getOrDefault(it, emptyList()) }
}

private fun <T> Iterable<(T) -> Boolean>.firstMatch(item: T): ((T) -> Boolean)? =
    this.find { predicate -> predicate(item) }
```

Now if this code was just part of a test, or maybe some throwaway script, I'd probably stop there. But otherwise there
are too many loose ends for me to feel comfortable that I know the way that the code would behave in different situations.
I like the implementation - it's declarative and the types feel good, but you'd still have to puzzle out the edge cases;
 and if that's the case, then any refactoring might change the behaviour. Which in something like `partition` could have
ripple-effects through any dependent code.

## Adding More Tests

Looking through our test names, we can see a lack of precision. If we fix that we can see that we are missing the case
of no items and no predicates. This is just the sort of edge case that could happen and return an unexpected value -
lets check it.

```kotlin
fun tests() = rootContext<Unit>() {
    test("items and predicates") {
        val items = listOf(-1, 0, 1, 2, 3)
        val predicates = listOf(::isNegative, ::isZero, ::isPositive)
        val result = items.partition(predicates)
        assertEquals(listOf(listOf(-1), listOf(0), listOf(1, 2, 3)), result)
    }
    test("some items no predicates") {
        val items = listOf(-1, 0, 1, 2, 3)
        val predicates = emptyList<(Int) -> Boolean>()
        val result = items.partition(predicates)
        assertEquals(emptyList<List<Int>>(), result)
    }
    test("some predicates no items") {
        val items = emptyList<Int>()
        val predicates = listOf(::isNegative, ::isZero, ::isPositive)
        val result = items.partition(predicates)
        assertEquals(listOf(emptyList<Int>(), emptyList(), emptyList()), result)
    }
    test("no predicates no items") {
        val items = emptyList<Int>()
        val predicates = emptyList<(Int) -> Boolean>()
        val result = items.partition(predicates)
        assertEquals(emptyList<List<Int>>(), result)
    }
}
```

Thankfully that passes, giving us some confidence that our algorithm is sound.

Now what about items that slip through the predicate net? We can predict that they won't be returned in any of the lists.
Is this the behaviour that we want?

The alternative would be to return these items in another list at the end of the result, so that the result is one list
larger than the size of the predicates list. I dislike this because of the asymmetry, and because
(I predict but haven't proved) if we want a catch-all we can simply pass in a last predicate that always returns `true`.

Finally we already know that predicates that match no item are represented by an empty list in the output - but let's
write a test to prove it so that our future selves don't have to think it through.

## Our TDD Solution

Our final tests for today's installment are:

```kotlin
class PartitionTests : JUnit5Minutests {
    fun tests() = rootContext<Unit>() {
        test("every item matches a predicate and every predicate matches an item") {
            val items = listOf(-1, 0, 1, 2, 3)
            val predicates = listOf(::isNegative, ::isZero, ::isPositive)
            val result = items.partition(predicates)
            assertEquals(listOf(listOf(-1), listOf(0), listOf(1, 2, 3)), result)
        }
        test("an item matches no predicate") {
            val items = listOf(-1, 0, 1, 2, 3)
            val predicates = listOf(::isZero, ::isPositive)
            val result = items.partition(predicates)
            assertEquals(listOf(listOf(0), listOf(1, 2, 3)), result)
        }
        test("a predicate matches no item") {
            val items = listOf(0, 1, 2, 3)
            val predicates = listOf(::isNegative, ::isZero, ::isPositive)
            val result = items.partition(predicates)
            assertEquals(listOf(listOf(), listOf(0), listOf(1, 2, 3)), result)
        }
        test("some items no predicates") {
            val items = listOf(-1, 0, 1, 2, 3)
            val predicates = emptyList<(Int) -> Boolean>()
            val result = items.partition(predicates)
            assertEquals(emptyList<List<Int>>(), result)
        }
        test("some predicates no items") {
            val items = emptyList<Int>()
            val predicates = listOf(::isNegative, ::isZero, ::isPositive)
            val result = items.partition(predicates)
            assertEquals(listOf(emptyList<Int>(), emptyList(), emptyList()), result)
        }
        test("no predicates no items") {
            val items = emptyList<Int>()
            val predicates = emptyList<(Int) -> Boolean>()
            val result = items.partition(predicates)
            assertEquals(emptyList<List<Int>>(), result)
        }
    }
}
```

If I didn't have a Part 2 I think that I'd probably remove some of duplication from this code before checking it in. But
it's much like many of my TDD tests before Minutest - OK, probabably good enough, but lacking some rigour and expressiveness.

In [Part 2](test-driven-to-specification-with-minutest-part2.html) we will look at refactoring this code to be more
spec-like. With luck it will be more expressive and less duplicated while covering more of the behaviour of our
implementation.

