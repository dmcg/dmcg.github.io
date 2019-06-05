---
title: My New Test Model
layout: post
tags: [Kotlin, Minutest, Testing]
---

## Abstract

I examine the strengths and weaknesses of the The xUnit and Spec testing models in terms of expressiveness, extensibility and their ability to reuse test code. I then propose a revised model which, by treating fixtures as a first-class abstraction, significantly improves on the existing frameworks.

## Introduction

The xUnit family of testing frameworks was started by Kent Beck with [SUnit](http://swing.fit.cvut.cz/projects/stx/doc/online/english/tools/misc/testfram.htm) in 1989. It defines tests as methods of a class, and the test fixture as fields of that class. This model is simple and well suited to [Test Driven Development](https://en.wikipedia.org/wiki/Test-driven_development), but in my experience is neither very expressive nor easily extensible.

Spec (for specification) testing introduced a more expressive language in order be readable by less technical people as part of [Behaviour Driven Development](https://en.wikipedia.org/wiki/Behavior-driven_development). Its model is also more extensible than xUnit's, but at the expense of significant complication in the management of test fixtures.

As a long time JUnit (and occasional RSpec, unittest and Jasmine) user, neither model fully meets all my needs. I write tests to drive designs, document code, demonstrate system behaviour, prevent regressions and find existing bugs; but existing test tools don't to allow me to flow between these roles, or to reuse test code in different modes.

This paper explores the difficulties that I have with the xUnit and Spec models and proposes a revised model that works significantly better for me.

## Fixtures

A lot of the discussion that follows will revolve around test fixtures. What are they?

[Wikipedia](https://en.wikipedia.org/wiki/Test_fixture) says:

> A test fixture is something used to consistently test some item, device, or piece of software. Test fixtures can be found when testing electronics, software and physical devices.

The [JUnit 4 Wiki](https://github.com/junit-team/junit4/wiki/test-fixtures) says:

> A test fixture is a fixed state of a set of objects used as a baseline for running tests. The purpose of a test fixture is to ensure that there is a well known and fixed environment in which tests are run so that results are repeatable.

So a test fixture is something to give us consistency / repeatability. In software we try where possible to create a fixture that encapsulates all the state that can affect the result of running the test, or is affected by the running of the test. That way, by creating a fresh fixture for each test, we can prevent one test run from affecting a later one.

There is a notable difference between a physical test fixture and a software test fixture. When testing a physical part it is mounted *in* the fixture - they are separate. When testing a software object, then the fixture will generally create that object - the subject under test will be a property of the fixture. This isn't a hard and fast rule though; in particular when testing stateless objects, stand-alone functions, or external services.

## xUnit

The xUnit lineage of test frameworks began with SUnit. SUnit's model was to compose a fixture from the instance variables of a class, so that independent fixtures can be created by creating a new instance of the class. Tests are then written as methods on that class, and can reference the fixture as their own instance variables.

This model was carried forward to JUnit, NUnit and all the xUnits. Here is a (pedagogically stilted) JUnit test, written in Kotlin, for a function to move the contexts of one mutable list into another.

```kotlin
class MoveIntoTests {

    // the subjects under test
    lateinit var source: MutableList<String>
    lateinit var destination: MutableList<String>

    @Test
    fun `empty list to empty list`() {
        source = emptyMutableList()
        destination = emptyMutableList()
        moveInto(source, destination)
        assertEquals(emptyMutableList(), destination)
    }

    @Test
    fun `empty list to non-empty list`() {
        source = emptyMutableList()
        destination = aNonEmptyList.toMutableList()
        moveInto(source, destination)
        assertEquals(aNonEmptyList, destination)
    }

    @Test
    fun `non-empty list to empty list`() {
        source = aNonEmptyList.toMutableList()
        destination = emptyMutableList()
        moveInto(source, destination)
        assertEquals(aNonEmptyList, destination)
    }

    @Test
    fun `non-empty list to non-empty list`() {
        source = aNonEmptyList.toMutableList()
        destination = aNonEmptyListToo.toMutableList()
        moveInto(source, destination)
        assertEquals(aNonEmptyListToo + aNonEmptyList, destination)
    }

    @AfterEach
    fun `source should be empty`() {
        assertEquals(emptyMutableList(), source)
    }
}

private var aNonEmptyList = listOf("apple")
private var aNonEmptyListToo = listOf("banana", "cherry")
```

Here the two subjects under test (`source` and `destination`) are properties of the test class and hence form the fixture. To run the tests, JUnit collects all the methods annotated with `@Test` and, for each, it creates an instance of the test class and then invokes the particular test method. In this way our tests can mutate the components of the fixture safe in the knowledge that the next test will have a fresh copy.

`nonEmptyList1` and `nonEmptyList2` might ordinarily be properties and so also part of the fixture. In this case I have moved them into the top level scope to emphasise that, as immutable collections of immutable values, they can be shared between tests without cross-contamination of state.

This is a nice simple model, but it has some limitations.

## xUnit Tests are Defined by Methods

Having tests defined as methods means that more tests need more methods - or special treatment. Consider repeating a test a number of times - the test framework has to provide a mechanism for this, as we can't (in most static languages) define test methods at runtime. JUnit 5 has a [@RepeatedTest](https://junit.org/junit5/docs/current/user-guide/#writing-tests-repeated-tests) annotation to allow us to do this. If we want to check that `empty list to empty list` *really* behaves as expected we can write something like

```kotlin
@RepeatedTest(value = 100, name = "attempt {currentRepetition}")
fun `empty list to empty list`() {
    source = emptyMutableList()
    destination = emptyMutableList()
    moveInto(source, destination)
    assertEquals(emptyMutableList(), destination)
}
```

but `RepeatedTest`is a facility that the JUnit authors had to provide - I couldn't implement it myself. I know how to repeat things in Kotlin, but every time I want to repeat tests in JUnit I have to look up how to cast this magic spell.

How about repeating a test with different parameters? Again there is an annotation [@ParameterizedTest](https://junit.org/junit5/docs/current/user-guide/#writing-tests-parameterized-tests), and again it is very much a special case rather than something that I as a test author could write given the model of the test framework.

Even something as simple as *not* running a test requires framework support. JUnit 5 ends up with an `@Disabled` annotation, plus `@DisabledOnOs`, `@DisabledIfSystemProperty`, `@DisabledIfEnvironmentVariable`, `@DisabledIf` - which can evaluate Javascript expressions but not (out of the box) the language in which I am actually writing the tests, and finally an entire `ExecutionCondition` extension API.

These limitations are caused by using methods as the way of defining tests, without providing any good way to treat them as first-class units of execution. There have been attempts to address this - JUnit 4 introduced [TestRules](https://junit.org/junit4/javadoc/4.12/org/junit/rules/TestRule.html) -

> an alteration in how a test method, or set of test methods, is run and reported.

and [Theories](https://junit.org/junit4/javadoc/4.12/org/junit/experimental/theories/Theories.html)

> The Theories runner allows to test a certain functionality against a subset of an infinite set of data points.

but these are both object-oriented knives in what should be a functional gunfight. The most promising avenue is JUnit 5's [Dynamic Tests](https://junit.org/junit5/docs/current/user-guide/#writing-tests-dynamic-tests); unfortunately this appears to be a completely different execution model to the rest of the framework and so does not integrate well with 'normal' tests.

## Only One Fixture Setup per xUnit Class

Another limitation with the xUnit model is that every test in a class shares the same fixture setup code. In our example `source` and `destination` need to be initialised differently for each test, so each test has to do that initialisation. This leads to the ugliness of the `lateinit` - our way of telling Kotlin that someone will populate that value [dreckly](https://www.urbandictionary.com/define.php?term=dreckly). An alternative is to create a different test class for each initial fixture state, or to [nest classes](https://junit.org/junit5/docs/current/user-guide/#writing-tests-nested) to express context. Both are not the standard model and take significant effort to maintain.

This might not seem like a major problem, but tests are often trying to do the difficult job of communicating to a human how a complicated system will behave in different circumstances. When that communication is through examples it is important for the human to be able to see the commonality and difference between the examples without expending precious mental bandwidth.

The quest for tests so expressive that product owners could understand what they are telling us about the behaviour of our systems motivated the creation of another test framework family, the Specs.

## Spec(ification)s Bring Structure

Spec-like frameworks, beginning I believe with [RSpec](http://rspec.info/), allow us to demonstrate behaviour in different contexts. In the Kotlin framework [Spek](https://spekframework.org) our previous example might look like this.

```kotlin
object MoveIntoSpec : Spek({

    describe("moveInto") {
        context("empty list to empty list") {
            val source = emptyMutableList()
            val destination = emptyMutableList()

            it("should not change source or destination") {
                moveInto(source, destination)
                assertEquals(emptyMutableList(), destination)
                assertEquals(emptyMutableList(), source)
            }
        }

        context("empty list to non-empty list") {
            val source = emptyMutableList()
            val destination = aNonEmptyList.toMutableList()

            it("should not change source or destination") {
                moveInto(source, destination)
                assertEquals(aNonEmptyList, destination)
                assertEquals(emptyMutableList(), source)
            }
        }

        context("non-empty list to empty list") {
            val source = aNonEmptyList.toMutableList()
            val destination = emptyMutableList()

            it("should move source items into destination") {
                moveInto(source, destination)
                assertEquals(aNonEmptyList, destination)
                assertEquals(emptyMutableList(), source)
            }
        }

        context("non-empty list to non-empty list") {
            val source = aNonEmptyList.toMutableList()
            val destination = aNonEmptyListToo.toMutableList()

            it("should move source items into destination") {
                moveInto(source, destination)
                assertEquals(aNonEmptyListToo + aNonEmptyList, destination)
                assertEquals(emptyMutableList(), source)
            }
        }
    }
})
```

Look at how the variable parts of the fixture, `source` and `destination`, agree with the context description. We have named a context and expressed it in code. This structure is difficult to impose with xUnit frameworks, but is the point of specs. xUnit only allows a single fixture setup and then we're straight into tests - here different contexts can set up the fixtures in different ways before their tests are run. Specs allow the Given of Given When Then to be expressed in the context structure.

## Specs are Dynamic

A convenient side-effect of the Spec model is that those `context` and `it` blocks are evaluated at runtime, which means that in Spek, if I want to be *really* sure that moving an empty list into an empty list results in an empty list, I can just write plain old code to repeatedly create tests.

```kotlin
context("empty list to empty list") {
    val source = emptyMutableList()
    val destination = emptyMutableList()

    (1..100).forEach { run ->
        it("should not change source or destination attempt $run") {
            moveInto(source, destination)
            assertEquals(emptyMutableList(), destination)
            assertEquals(emptyMutableList(), source)
        }
    }
}
// Note that we will see later that this test is logically flawed
```

Parameterised tests are almost as easy to write in Spec-like frameworks. As we will see later - no need to look up what magic annotations your framework authors have conjured up - just define a source of parameter values and create contexts or tests on the fly for whatever combinations you like. Compared to xUnit our tests (and contexts) have been promoted to first-class status, so that we can bring the tools of functional programming to bear.

There are problems hiding in those contexts though.

## Spec Fixture Lifecycle

Your obsessive-compulsive side might think that there should be two levels of nested contexts in this example, so that we can better express their commonality and difference. As someone who just had to look up whether to hyphenate obsessive-compulsive, I agree.

```kotlin
object MoveIntoNestedSpec : Spek({
    describe("moveInto") {
        context("empty source") {
            val source = emptyMutableList()

            context("empty destination") {
                val destination = emptyMutableList()

                it("should not change destination") {
                    moveInto(source, destination)
                    assertEquals(emptyMutableList(), destination)
                }
            }
            context("non-empty destination") {
                val destination = aNonEmptyList.toMutableList()

                it("should not change destination") {
                    moveInto(source, destination)
                    assertEquals(aNonEmptyList, destination)
                }
            }

            afterEach {
                // source should always be emptied
                assertEquals(emptyMutableList(), source)
            }
        }

        context("non-empty source") {
            val source = aNonEmptyList.toMutableList()

            context("empty destination") {
                val destination = emptyMutableList()

                it("should contain just the source") {
                    moveInto(source, destination)
                    assertEquals(aNonEmptyList, destination)
                }
            }

            context("non-empty destination") {
                val destination = aNonEmptyListToo.toMutableList()

                it("should contain destination plus source") {
                    moveInto(source, destination)
                    assertEquals(aNonEmptyListToo + aNonEmptyList, destination)
                }
            }

            afterEach {
                assertEquals(emptyMutableList(), source)
            }
        }
    }
})
```

Ah - that's so much better.

It's such a shame that it doesn't work.

The problem is that those `source` variables are shared between the child contexts and are mutable. In the `non-empty source` context, `val source = nonEmptyList1.toMutableList()` is executed only once, when the contexts are being defined, before running the tests. So `should contain destination plus source` finds that `source` has been cleared out by the previous test and there is nothing to add.

There are two common solutions to this problem in Spec-like tests. The first is the use of `before` blocks to initialise mutable fixture state

```kotlin
context("non-empty source") {
    lateinit var source: MutableList<String>
    beforeEach {
        source = aNonEmptyList.toMutableList()
    }
    //...

    context("non-empty destination") {
        lateinit var destination: MutableList<String>
        beforeEach {
            destination = aNonEmptyListToo.toMutableList()
        }

        it("should contain destination plus source") {
            moveInto(source, destination)
            assertEquals(aNonEmptyListToo + aNonEmptyList, destination)
        }
    }
}
```

The second is the use of a special lazy construct - Spek calls this `memoized`

```kotlin
context("non-empty source") {
    val source by memoized { aNonEmptyList.toMutableList() }

    //...
    context("non-empty destination") {
        val destination by memoized { aNonEmptyListToo.toMutableList() }

        it("should contain destination plus source") {
            moveInto(source, destination)
            assertEquals(aNonEmptyListToo + aNonEmptyList, destination)
        }
    }
}
```

The `beforeEach` method is tedious, error-prone, and doesn't sit well with an immutable, functional style. Initialisation blocks like memoized are nicer, but force users to internalise the test lifecycle and differentiate between the handling of mutable and immutable state. Everything can appear to be fine until you add a test, or reorder what is there, and the tests that were passing now fail.

## Spec Fixture Scope

Another problem with the Spec model is that tests rely on receiving the fixture properties in their lexical scope. As a result extracting common code and reusing it between tests is hard, at least in statically typed languages. If you try to extract `it("should contain destination plus source")` for use in another test you'll find it is tied into place by `source` and `destination`. In xUnit, where the fixture has a type (the type of the test class), we routinely extract a supertype with the fixture properties and methods that rely on these properties. Concrete classes can then inherit from this to share fixtures, setup, tests and utilities. We can't do that in the Spec model, so have to fall back on framework-provided [special cases](https://spekframework.github.io/spek/docs/latest/#_subjects).

## The Best of Both Worlds

In summary: xUnit has a simple fixture model that we can understand and reuse, but limited facilities for expressing fixture variations, and poor support for manipulating tests as first class constructs; Specs address these limitations of xUnit, but at the expense of a complicated and not easily reusable fixture model.

What if we could combine the nested scopes and programmatic test generation of the Spec model with the simple and predictable fixtures of the xUnit system? We would then be able to execute business-friendly specs and security-friendly fuzz testing. We could run a test for every file in a directory of examples or skip a test every Tuesday afternoon without recourse to the framework manual. And we would be able to combine test contracts into suites of tests for concrete implementations.

## Try this One Weird Trick

The key to these benefits is to promote the fixture to a first-class abstraction, separate from the tests.

There are two aspects to this promotion. The first aspect is type, the second lifecycle. In xUnit the type of the fixture is the type of the test class, and they share the same lifecycle. In Specs, the fixture lifecycle is decoupled from the test lifecycle, but there is no separate fixture type.

Let's see what happens when we extract a fixture type in our nested context Spek test.

```kotlin
object MoveIntoNestedFixtureSpec : Spek({

    // Create a fixture class with all our mutable test state
    data class Fixture(
        val source: MutableList<String> = emptyMutableList(),
        val destination: MutableList<String> = emptyMutableList()
    )

    describe("moveInto") {
        //...
        context("non-empty source") {
            val fixture by memoized {
                Fixture(aNonEmptyList.toMutableList())
            }

            context("empty destination") {
                it("should contain just the source") {
                    moveInto(fixture.source, fixture.destination)
                    assertEquals(aNonEmptyList, fixture.destination)
                }
            }

            context("non-empty destination") {
                val fixture by memoized {
                    fixture.copy(destination = aNonEmptyListToo.toMutableList())
                    // ↑ this fixture is the parent fixture
                }
                it("should contain destination plus source") {
                    moveInto(fixture.source, fixture.destination)
                    assertEquals(aNonEmptyListToo + aNonEmptyList, fixture.destination)
                }
            }

            afterEach {
                assertEquals(emptyMutableList(), fixture.source)
            }
        }
    }
})
```

In exchange for explicitly creating and managing the fixture down the context tree, we have a simple rule - if a test references a mutable thing, either in setup or execution, initialise that thing in the fixture and initialise the fixture in a `by memoized` block.

Now that the fixture has its own type, you can also see that the tests and common setup code can be extracted and reused, as they now depend only on `Fixture` (and constants).

The downside of this approach is the irritation of not having access to the fixture properties without qualification - `fixture.source.moveInto(fixture.destination)` gets old pretty quickly compared to having the properties directly in scope. If we could solve that problem then this model could meet our design goal of having the flexibility and expressiveness of a Spec with the predictable model and fixture reuse of an xUnit.

## Introducing Minutest

[Minutest](https://github.com/dmcg/minutest) is a Kotlin library created to explore this approach. It takes advantage of a Kotlin language feature that allows the value of the implicit receiver `this` to be provided by framework code. Because fixtures are special, in that there will only be one instance per test, Minutest allows the test developer to specify how to create, mutate and translate the single instance in contexts, and then returns it to the tests as the implicit receiver.

Let's look at our contexts in Minutest.

```kotlin
class MoveIntoMinutests : JUnit5Minutests {

    data class Fixture(
        val source: MutableList<String> = emptyMutableList(),
        val destination: MutableList<String> = emptyMutableList()
    )

    fun tests() = rootContext<Fixture> // Contexts are parameterised by the fixture type
    {
        context("empty source") {

            // The fixture block is run once per test and must return an instance of the fixture
            fixture {
                Fixture(emptyMutableList(), emptyMutableList())
            }

            context("empty destination") {
                test("should not be changed") {
                    // here 'this' is the fixture inherited from the 'empty source'
                    moveInto(source, destination)
                    assertEquals(emptyMutableList(), destination)
                }
            }

            context("non-empty destination") {

                // the deriveFixture block translates from the parent fixture
                deriveFixture {
                    parentFixture.copy(destination = aNonEmptyList.toMutableList())
                }

                test("should not be changed") {
                    moveInto(source, destination)
                    assertEquals(aNonEmptyList, destination)
                }
            }

            after {
                // after blocks also have the fixture as 'this'
                assertEquals(emptyMutableList(), source)
            }
        }

        context("non-empty source") {
            fixture {
                Fixture(aNonEmptyList.toMutableList())
            }

            context("empty destination") {
                test("should contain just the source") {
                    moveInto(source, destination)
                    assertEquals(aNonEmptyList, destination)
                }
            }

            context("non-empty destination") {
                deriveFixture {
                    parentFixture.copy(destination = aNonEmptyListToo.toMutableList())
                }

                test("should contain destination plus source") {
                    moveInto(source, destination)
                    assertEquals(aNonEmptyListToo + aNonEmptyList, destination)
                }
            }

            after {
                assertEquals(emptyMutableList(), source)
            }
        }
    }
}
```

Here the `fixture` block is called by the framework once for each test, so that stateful fixtures will not contaminate future tests. `deriveFixture` allows a context to change the fixture from its parent. All `fixture`, `test` and `before` and `after` blocks have access to the value of the fixture in their context as `this`.

This model has proved to be both expressive and flexible. As a Spec, Minutest allows easy manipulation of contexts and tests, so that we can quickly refactor the previous example to a parameterised test without having to look up how to use a special annotation.

```kotlin
class MoveIntoParameterisedMinutests : JUnit5Minutests {

    data class Fixture(
        val source: MutableList<String> = emptyMutableList(),
        val destination: MutableList<String> = emptyMutableList()
    ) {
        override fun toString() = "$source and $destination"
    }

    val scenarios = listOf(
        Fixture(emptyMutableList(), emptyMutableList()),
        Fixture(aNonEmptyList.toMutableList(), emptyMutableList()),
        Fixture(emptyMutableList(), aNonEmptyListToo.toMutableList()),
        Fixture(aNonEmptyList.toMutableList(), aNonEmptyListToo.toMutableList())
    )

    fun tests() = rootContext<Fixture> {
        scenarios.forEach { scenario ->
            context("Given $scenario") {
                fixture {
                    scenario
                }
                test("moves items from source to destination") {
                    val originalSource = source.toList()
                    val originalDestination = destination.toList()
                    moveInto(source, destination)
                    assertEquals(originalDestination + originalSource, destination)
                    assertEquals(emptyMutableList(), source)
                }
            }

            willRun(
                "root",
                "  Given [] and []",
                "    moves items from source to destination",
                "  Given [apple] and []",
                "    moves items from source to destination",
                "  Given [] and [banana, cherry]",
                "    moves items from source to destination",
                "  Given [apple] and [banana, cherry]",
                "    moves items from source to destination"
            )
        }
    }
}
```

This is of course overkill for our little example, but the ability to compose tests, contexts and fixtures allows us to reuse them all in creative ways. Let's generalise our previous example to any mutable collection

```kotlin
data class Fixture(
    val source: MutableCollection<String>,
    val destination: MutableCollection<String>
)

fun ContextBuilder<Fixture>.supportsMoveTo(
    collectionOf: (List<String>) -> MutableCollection<String>
) {
    context("empty source") {
        //...
    }
    context("non-empty source") {
        fixture {
            Fixture(collectionOf(aNonEmptyList), collectionOf(emptyList()))
        }

        context("empty destination") {
            test("should contain just the source") {
                moveInto(source, destination)
                assertEquals(collectionOf(aNonEmptyList), destination)
            }
        }

        context("non-empty destination") {
            deriveFixture {
                parentFixture.copy(destination = collectionOf(aNonEmptyListToo))
            }

            test("should contain destination plus source") {
                moveInto(source, destination)
                assertEquals(collectionOf(aNonEmptyListToo + aNonEmptyList), destination)
            }
        }

        after {
            assertEquals(collectionOf(emptyList()), source)
        }
    }
}

/*-
We can use this contract to test with different `MutableCollection`s by a specifying the concrete factory `collectionOf`
-*/

class ArrayListTests : JUnit5Minutests {
    fun tests() = rootContext<Fixture> {
        supportsMoveTo { content -> ArrayList(content) }
    }
}

class LinkedListTests : JUnit5Minutests {
    fun tests() = rootContext<Fixture> {
        supportsMoveTo { content -> LinkedList(content) }
    }
}

class SetTests : JUnit5Minutests {
    fun tests() = rootContext<Fixture> {
        supportsMoveTo { content -> LinkedHashSet(content) }
    }
}
```

## Other Features

Minutest users are still exploring ways to exploit this revised test model to make tests more expressive and comprehensive. Other features that become simple to implement include:

* [Property based testing](http://oneeyedmen.com/property-based-testing-with-minutest.html)
* Sharing fixtures between test runs
* Immutable fixtures
* [Mapping from one fixture type to another](https://github.com/dmcg/minutest/blob/master/docs/fixtures.md#changing-fixture-type) to expand or contract the focus of tests.
* Logging test execution
* [Refactoring](http://oneeyedmen.com/test-driven-to-specification-with-minutest-part1.html) - for example flat TDD tests to a Spec or property-based tests.

## Other Implementations

Minutest is a Kotlin library that integrates with JUnit 5 (and experimentally JUnit 4) to run its tests and provide assertions. While some of the expressiveness in the test specification requires Kotlin language features, I believe that a similar model could be implemented in other languages. In fact a similar, albeit dynamically typed, fixture model is available for [Jasmine](https://jasmine.github.io/2.8/introduction.html#section-The_%3Ccode%3Ethis%3C/code%3E_keyword).

## Conclusions

By giving the fixture its own type, the problems of fixture lifecycle and reuse inherent in the Spec model can be mitigated. A framework such as Minutest that embraces this approach can reap further rewards in test expression, productivity and flexibility.


