---
title: Property Based Testing With Minutest
layout: post
tags: [Kotlin, Minutest, Testing]
---
This is the bonus episode of a mini-series on my new test library, [Minutest](https://github.com/dmcg/minutest).

In [Part 1](test-driven-to-specification-with-minutest-part1.html) we looked at the Test Driven Development of an
extension function, `partition`.

In [Part 2](test-driven-to-specification-with-minutest-part2.html) we refactored the code into a
[Spec](http://rspec.info/) -style, with nested contexts giving a more formal specification of what the code does.

In this part I'll look at a different style of testing with Minutest -
[Property Based Testing](https://hypothesis.works/articles/what-is-property-based-testing/). Instead of constructing
example inputs and testing against expected outcomes, Property Based Testing throws many inputs at a system and checks
the outcomes against a set of properties that we can say should hold true independent of the exact input.

## The Code Under Test

Let's remind ourselves of the code we're testing.

```kotlin
/**
 * Partitions the receiver into lists which contain the items that match the input corresponding predicate.
 *
 * If multiple predicates match an item, the item is attributed to the first which matches.
 */
fun <T> Iterable<T>.partition(predicates: List<(T) -> Boolean>): List<List<T>> {
    val grouped: Map<((T) -> Boolean)?, List<T>> = this.groupBy { item ->
        predicates.firstMatch(item)
    }
    return predicates.map { grouped.getOrDefault(it, emptyList()) }
}

private fun <T> Iterable<(T) -> Boolean>.firstMatch(item: T): ((T) -> Boolean)? =
    this.find { predicate -> predicate(item) }
```

## What Are Our Properties?

The key to Property Based Testing is to find properties of outcomes that are true no matter what the input. For example,
the length of the output list will be the same as the length of the predicates, no matter what predicates are provided.
What other properties can you think of?

Yes, it isn't easy.

Let's park that for a while while we implement a bit of mechanism.

## Unrolled Tests

What we want to do is run the same tests for a set
of input values. We'll represent the inputs with a Fixture data class and write the following.

```kotlin
class PartitionPropertyTests : JUnit5Minutests {
    data class Fixture(
        val items: List<Int>,
        val predicates: List<(Int) -> Boolean>
    )

    fun tests() = rootContext<Fixture> {
        context("empty predicates") {
            fixture { Fixture(emptyList(), emptyList()) }
            test("result size equals predicates size") {
                assertEquals(predicates.size, items.partition(predicates).size)
            }
            test("something else that should be true for any fixure") {}
        }
        context("some predicates") {
            fixture { Fixture(emptyList(), listOf(::isNegative, ::isZero, ::isPositive)) }
            test("result size equals predicates size") {
                assertEquals(predicates.size, items.partition(predicates).size)
            }
            test("something else that should be true for any fixture") {}
        }
        // we could go on
    }
}
```

## Building Contexts In Code

Now obviously this doesn't scale. Luckily Minutest is _All Just Kotlin™_ so we can build those contexts in code.

```kotlin
val fixtures = listOf(
    Fixture(emptyList(), emptyList()),
    Fixture(emptyList(), listOf(::isNegative, ::isZero, ::isPositive))
)

fun tests() = rootContext<Fixture> {
    fixtures.forEach { fixture ->
        context("Inputs $fixture") {
            fixture { fixture }
            test("result size equals predicates size") {
                assertEquals(predicates.size, items.partition(predicates).size)
            }
            test("something else that should be true for any fixture") {}
        }
    }
}
```

## Scale Out and Down

You can probably see where this is going - all we have to do is flesh-out the set of fixtures and tests.

```kotlin
data class Fixture(
    val items: List<Int>,
    val predicates: List<(Int) -> Boolean>
) {
    fun result() = items.partition(predicates)
}

fun fixtures() = combinationsOf(
    listOf(
        emptyList(),
        listOf(1),
        listOf(-1, 0, 1, 2, 3),
        listOf(3, 2, 1, 0, -1),
        listOf(-1, -1, 0, 0, 1, 2, 3)
    ),
    listOf(
        emptyList(),
        listOf(::isZero),
        listOf(::isNegative, ::isZero, ::isPositive),
        //...
        listOf(::isPositive, ::isBiggerThan10)
    )
).map { (items, predicates) -> Fixture(items, predicates) }

fun tests() = rootContext<Fixture> {
    fixtures().forEach { fixture ->
        context("Inputs $fixture") {
            fixture { fixture }
            test("result size equals predicates size") {
                assertEquals(predicates.size, result().size)
            }
            test("first output has every item in input that matches first predicate") {
                if (predicates.isNotEmpty()) {
                    assertEquals(items.filter { predicates[0](it) }, result()[0])
                }
            }
            test("no item in output doesn't match the corresponding predicate") {
                predicates.zip(result()).forEach { (predicate, resultForPredicate) ->
                    assertTrue(resultForPredicate.filterNot(predicate).isEmpty())
                }
            }
            test("every output item is in the input") {
                val outputs = result().flatten().toSet()
                assertTrue((outputs - items).isEmpty())
            }
            // ...
        }
    }
}
```

I'm not an experienced Property Based Tester - I'm still finding my feet when it comes to finding good inputs and
properties that are minimal and expressive - but I do know that every time I thought of a new property, or came up with an
interesting new list of items or predicates, my confidence in the implementation increased. The testing was
exploratory, but with the advantages of automation and combinatorial growth. Not only that, but each new case increased
my confidence that any changes to the behaviour caused by refactoring would be caught by one of the hundreds of tests
generated this way.

## Generating Inputs

There are other ways of generating the inputs for Property Based Testing. One would be to generate random lists of items and
predicates rather than combinations of given ones. Another would be to start with some givens and mutate them, either
consistently or randomly. Both would give more confidence that we hadn't rigged the input, at the expense of having to
manage the randomness.

It's also worth noting that if we generate many more tests we may come across limitations in our test reporting tools.
I _think_ that you could use Minutest to generate only one outer context and one inner test in these circumstances, but
collect any failures from all fixtures into a single result per test. I'll write another post if I can.

## Putting It All Together

You are not alone if you couldn't read these tests and predict what the `partition` method actually did. Maybe I'm just not
good enough at specifying the properties - or maybe we do need human-centred examples in that role. Luckily there is
nothing to stop us having the examples for humans and the property based tests for regression. We can merge the specification
tests from last time with these, and run them both with Minutest - they can even share the same Fixture.


```kotlin
class PartitionTests : JUnit5Minutests {

    data class Fixture(
        val items: List<Int> = emptyList(),
        val predicates: List<(Int) -> Boolean> = emptyList()
    ) {
        fun result() = items.partition(predicates)

        fun assertResultIs(vararg expected: List<Int>) {
            assertEquals(expected.asList(), result())
        }
    }

    fun specs() = rootContext<Fixture> {
        fixture { Fixture() }

        context("some items") {
            withItems(-1, 0, 1, 2, 3)
            context("no predicates") {
                test("returns empty list") {
                    assertResultIs()
                }
            }
            context("everything matches something") {
                withPredicates(::isNegative, ::isZero, ::isPositive)
                test("returns a list for each predicate") {
                    assertResultIs(listOf(-1), listOf(0), listOf(1, 2, 3))
                }
            }
            context("a predicate doesn't match any item") {
                withPredicates(::isBiggerThan10, ::isNegative, ::isZero, ::isPositive)
                test("returns an empty list for that predicate") {
                    assertResultIs(emptyList(), listOf(-1), listOf(0), listOf(1, 2, 3))
                }
            }
            context("an item doesn't match any predicate") {
                withPredicates(::isNegative, ::isPositive)
                test("item is not in the returned lists") {
                    assertResultIs(listOf(-1), listOf(1, 2, 3))
                }
            }
            context("an item matches more than one predicate") {
                withPredicates(::isBiggerThan10, ::isNegative, ::isZero, ::isPositive, ::isBiggerThan1)
                test("item is assigned to the first matching predicate") {
                    assertResultIs(emptyList(), listOf(-1), listOf(0), listOf(1, 2, 3), emptyList())
                }
            }
            context("repeated predicates") {
                withPredicates(::isNegative, ::isNegative, ::isZero, ::isPositive)
                test("item is assigned to each predicate") {
                    assertResultIs(listOf(-1), listOf(-1), listOf(0), listOf(1, 2, 3))
                }
            }
            context("lambda predicates") {
                withPredicates({ x -> isNegative(x) }, { x -> isPositive(x) })
                test("item is assigned to each predicate") {
                    assertResultIs(listOf(-1), listOf(1, 2, 3))
                }
            }
            context("items in a different order") {
                withItems(3, 2, 1, 0, -1)
                withPredicates(::isNegative, ::isZero, ::isPositive)
                test("input order is preserved") {
                    assertResultIs(listOf(-1), listOf(0), listOf(3, 2, 1))
                }
            }
        }
        context("no items") {
            context("no predicates") {
                test("returns empty list") {
                    assertResultIs()
                }
            }
            context("some predicates") {
                withPredicates(::isNegative, ::isZero, ::isPositive)
                test("returns an empty list for each predicate") {
                    assertResultIs(emptyList(), emptyList(), emptyList())
                }
            }
        }
    }

    private fun fixtures() = combinationsOf(
        listOf(
            emptyList(),
            listOf(1),
            listOf(-1, 0, 1, 2, 3),
            listOf(3, 2, 1, 0, -1),
            listOf(-1, -1, 0, 0, 1, 2, 3)
        ),
        listOf(
            emptyList(),
            listOf(::isZero),
            listOf(::isNegative, ::isZero, ::isPositive),
            listOf(::isPositive, ::isZero, ::isNegative),
            listOf(::isZero, ::isZero, ::isPositive),
            listOf(::isZero, ::isZero, ::isPositive, ::isPositive),
            listOf(::isPositive, ::isBiggerThan1),
            listOf(::isPositive, ::isBiggerThan10)
        )
    ).map { (items, predicates) -> Fixture(items, predicates) }

    fun properties() = rootContext<Fixture> {
        fixtures().forEach { fixture ->
            context("Inputs $fixture") {
                fixture { fixture }
                test("result size equals predicates size") {
                    assertEquals(predicates.size, result().size)
                }
                test("first output has every item in input that matches first predicate") {
                    if (predicates.isNotEmpty()) {
                        assertEquals(items.filter { predicates[0](it) }, result()[0])
                    }
                }
                test("no item in output doesn't match the corresponding predicate") {
                    predicates.zip(result()).forEach { (predicate, resultForPredicate) ->
                        assertTrue(resultForPredicate.filterNot(predicate).isEmpty())
                    }
                }
                test("every output item is in the input") {
                    assertTrue((result().flatten().toSet() - items).isEmpty())
                }
                test("the order of items in each output is the same as the order in the input") {
                    result().forEach { listInResult ->
                        val correspondingInputItems = items.filter { listInResult.contains(it) }
                        assertEquals(correspondingInputItems, listInResult)
                    }
                }
                test("no item is in more than one output") {
                    // Outputs for the same predicate are considered the same output
                    result().forEach { listInResult ->
                        val otherItems = result().filterNot { it == listInResult }.flatten()
                        assertEquals(listInResult - otherItems, listInResult)
                    }
                }
                test("identical predicates result in the same output") {
                    val resultsBySamePredicate: Map<(Int) -> Boolean, List<List<Int>>> =
                        predicates.zip(result()).groupBy(
                            { (predicate, _) -> predicate }, { (_, resultForPredicate) -> resultForPredicate }
                        ).filterValues { it.size > 1 }
                    resultsBySamePredicate.values.forEach { resultForPredicate ->
                        assertTrue(resultForPredicate.size > 1)
                        assertTrue(resultForPredicate.itemsAreTheSame())
                    }
                }
            }
        }
    }
    private fun <T> Iterable<T>.itemsAreTheSame() = this.toSet().size == 1

    private fun TestContextBuilder<Fixture, Fixture>.withItems(vararg items: Int) =
        before_ { copy(items = items.asList()) }

    private fun TestContextBuilder<Fixture, Fixture>.withPredicates(vararg predicates: (Int) -> Boolean) =
        before_ { copy(predicates = predicates.asList()) }
}
```

## Conclusion

Property Based Tests allow us to specify the behaviour of code in a very different way to the usual examples or
specifications. They can allow easy working of edge-cases, increase our rigour and give increased robustness against
regressions, but may not communicate well with the users of our code. Combining specifications and property based tests
looks promising, especially where the tests can share infrastructure and setup.

