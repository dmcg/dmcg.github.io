---
title: Growing a Kotlin Domain Specific Language
layout: post
---

In [my last post](/jmock-and-kotlin.html) we extended JMock, which is an existing Java
[DSL](http://martinfowler.com/bliki/DomainSpecificLanguage.html). In this post we'll look at writing a DSL from scratch.

The domain for this language is acceptance testing. Of course there is already a DSL for this - [Gherkin](https://github.com/cucumber/cucumber/wiki/Gherkin)
the language that is parsed and executed by Cucumber. Gerkin is nicely understandable by our customers but it is an external DSL - basically text,
and that means that we don't have tools to refactor and analyze it.

What we're going to write is an internal DSL in Kotlin that will *output* Gerkin as it is running the test. That way our customers
can approve the test, but IntelliJ will provide the tooling.

I'll cut to the chase and show a test

``` kotlin
class SearchTests : AcceptanceTest(
    asA = "author named James",
    want = "to have a journal suggested for me")
{
    val james = Actor.with("James", driver, scenario)

    @Test fun `Search returns some results`() {
        given(james).loadsTheHomePage()
        then(james).shouldSee(::`the results table`, isntThere)
        wheN(james).searchesForTitle("Philosophy and Education")
        then(james).shouldSee(::`the results table`, withTopTableItem("Studies in Philosophy and Education"))
    }

    @Test fun `Selecting a journal takes you to the resubmission page`() {
        given(james).loadsTheHomePage()
        wheN (james) {
            searchesForTitle("Philosophy and Education")
            selectsTheJournal("Studies in Philosophy and Education")
        }
        then(james).shouldSee(::`the page`, isTheResubmissionPage)
    }
}
```

and here's the output of the test.

```
Feature: Search Tests
    As author named James
    I want to have a journal suggested for me

    Scenario: Search returns some results
        Given James loads the home page
        Then James sees the results table isn't there
        When James searches for title "Philosophy and Education"
        Then James sees the results table has top table row with "Studies in Philosophy and Education"

    Scenario: Selecting a journal takes you to the resubmission page
        Given James loads the home page
        When James searches for title "Philosophy and Education"
        When James selects the journal named "Studies in Philosophy and Education"
        Then James sees the page is the resubmission page
```

I won't pretend that I got here in one step, but here's how the DSL is implemented.

`AcceptanceTest `





