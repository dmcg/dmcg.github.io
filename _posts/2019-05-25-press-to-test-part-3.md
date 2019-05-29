---
title: Press to Test - Test Driven Development in Android Part 3
layout: post
---

This is Part 3 in a series documenting my experiences learning Android development in Kotlin. The code is available to follow along on [GitHub](https://github.com/dmcg/PressToTest).

In [Part 1](press-to-test-part-1.html) I got a simple UI toy up and running, with tests running via [Espresso](https://developer.android.com/training/testing/espresso) in an emulator.

In [Part 2](press-to-test-part-2.html) I used [Roboletric](http://robolectric.org/) to get (a lightly refactored version of) the same tests running in a local JVM rather than the emulator.

In this episode I was *going* to look at writing unit tests for the interaction. But there was some feedback on the Kotlin Test Slack channel for Part 1 that said "It's unfortunate that he kind of blurred the scope of testing, waiting for 3 seconds for the snackbar to disappear." This criticism is well founded - these randomish waits for things to happen lead to brittle tests where we we can't trust that failures are actually problems. In Part 2 I compounded the problem in the Robolectric tests, although to be fair I did it in the name of consistency.

To recap - the issue is that the UI displays a message in a Snackbar, which automatically hides. Instead of waiting for the view to disappear, the tests just sleep for 3 seconds and then check that it has gone. The problem is compounded because on the emulator this happens automatically, whereas in Robolectric we have to idle the shadow looper (whatever that means) to induce it.

In reality, I think that in the early stages of real project I would be happy enough to leave the wait in the tests and see if there were actually problems. But this whole project is an academic exercise, so I suppose that we should see if we can address this issue, for academic interest.


## ConditionWatcher

In Part 2 I wrote "I'm pretty sure that if I understood what [this article](https://medium.com/azimolabs/wait-for-it-idlingresource-and-conditionwatcher-602055f32356) is saying I could find a common solution that does not have a fixed wait time," so I went off and read [that article](https://medium.com/azimolabs/wait-for-it-idlingresource-and-conditionwatcher-602055f32356). There was a lot of information about how Roboelectric can be told that things are ready for its next interaction, and then code for a [ConditionWatcher](https://github.com/AzimoLabs/ConditionWatcher) that basically tests a condition periodically until [either it is met or a timeout occurs](https://github.com/AzimoLabs/ConditionWatcher/blob/1ef420bb0ed496ff0bb6c574b22576ee9fe3998d/conditionwatcher/src/main/java/com/azimolabs/conditionwatcher/ConditionWatcher.java)

So I pull the ConditionWatcher jar into the project and try it out. 

```kotlin
    @Test
    fun clicking_button_shows_temporary_BOOM_message() {

        onView(snackBarMatcher).check(doesNotExist())

        onView(buttonMatcher).perform(click())
        onView(snackBarMatcher).check(isDisplayed())

        ConditionWatcher.waitForCondition(object: Instruction() {
            override fun getDescription() = "Snackbar has gone"

            override fun checkCondition(): Boolean = try {
                onView(snackBarMatcher).check(doesNotExist())
                true
            } catch (x: AssertionFailedError) {
                false
            }
        })
        onView(snackBarMatcher).check(doesNotExist())
    }
```

You can see there a nasty interface mismatch between our way of asserting the state of our GUI through Espresso's `onView(matcher).check(viewAssertion)`, which throw `AssertionFailedError` if they are not satisfied, and ConditionWatcher's condition, which returns `false` if it is not satisfied. But it's not a fatal problem, just an irritation, and this code does pass when we run it in the emulator.

When we run it in a local JVM under Robolectric though it doesn't pass, because as we previously found out, in order to make the Snackbar hide we have to poke the `ShadowLooper`. What we need is something like ConditionWatcher but which does this poking as well as waiting. While we're at it it would be nice to be able to set different timeouts and poll intervals per call, and to wait for a condition without creating an `Instruction` object.

So I spend a happy 90 minutes or so writing my own `Waiter` class.

## Waiter

```kotlin
data class Waiter(
    val defaultDescription: String = "something",
    val defaultTimeoutMillis: Long = 60000,
    val defaultPollMillis: Long = 250,
    val twiddler: () -> Unit = {}
) {
    fun waitFor(
        description: String? = null,
        timeoutMillis: Long = -1,
        pollMillis: Long = -1,
        condition: () -> Boolean
    ) {
        val resolvedDescription = description ?: defaultDescription
        val resolvedTimeoutMillis = if (timeoutMillis >= 0 ) timeoutMillis else defaultTimeoutMillis
        val resolvedPollMillis = if (pollMillis >= 0 ) pollMillis else defaultPollMillis

        val endT = System.currentTimeMillis() + resolvedTimeoutMillis
        while (!condition()) {
            if (System.currentTimeMillis() > endT)
                throw TimeoutException("Timeout waiting for $resolvedDescription after more than $timeoutMillis ms")
            Thread.sleep(resolvedPollMillis)
            twiddler()
        }
    }

    inline fun <reified T: Throwable> waitForNo(
        description: String? = null,
        timeoutMillis: Long = -1,
        pollMillis: Long = -1,
        crossinline block: () -> Unit
    ) {
        waitFor(description, timeoutMillis, pollMillis) {
            try {
                block()
                true
            } catch (t: Throwable) {
                if (t is T) false else throw t
            }
        }
    }
}
```

and yes, I did write it [test first](https://github.com/dmcg/PressToTest/blob/4aa0002ce5b6328dbf742494a9ef593ecb93df38/app/src/test/java/com/oneeyedmen/presstotest/WaiterTests.kt).

To be honest, this is probably overkill, but then it is an academic exercise, so I might be forgiven for stretching my coding legs a little. `waitForNo` is a bit suss, but is there to integrate with Espresso thus

```kotlin
@RunWith(AndroidJUnit4::class)
abstract class AcceptanceTests(private val waiter: Waiter) {

    @get:Rule
    val activityRule = ActivityTestRule(MainActivity::class.java)

    val button = onView(withId(R.id.button))
    val snackBar get() = onView(
        allOf(
            withId(android.support.design.R.id.snackbar_text),
            withText("BOOM!")
        )
    )

    @Test
    fun button_message_changes_on_pressing() {
        button.check(matchesIsDisplayed(withText("PRESS TO TEST")))

        button.perform(Finger.pressAndHold())
        button.check(matchesIsDisplayed(withText("RELEASE TO DETONATE")))

        button.perform(Finger.release())
        button.check(matchesIsDisplayed(withText("PRESS TO TEST")))
    }

    @Test
    fun clicking_button_shows_temporary_BOOM_message() {
        snackBar.check(doesNotExist())

        button.perform(click())
        snackBar.check(matchesIsDisplayed())

        waiter.waitForNo<AssertionFailedError>("Snackbar gone") {
            snackBar.check(doesNotExist())
        }
    }
}

private fun matchesIsDisplayed(matcher: Matcher<View> = Matchers.any(View::class.java)) = matches(
    allOf(
        ViewMatchers.isDisplayed(),
        matcher
    )
)
```

In `androidTest` - we don't have to pander to Robolectric

```kotlin
class InstrumentedAcceptanceTests : AcceptanceTests(Waiter())
``` 

Whereas in `test` we need a waiter that pokes Robolectric every time around its loop.

```kotlin
class InternalAcceptanceTests : AcceptanceTests(robolectricWaiter)

private val robolectricWaiter = Waiter(twiddler = { ShadowLooper.runMainLooperToNextTask() })
```

There we have it, we can run the same tests on the emulator and locally, and they will both wait for the snackbar to disappear of its own accord, or fail with a `TimeoutException` after a minute. 

## Wrap Up

Engineering is the balance between getting things right and getting things done. The code is now a more 'right' in that it doesn't just sleep, but I wonder if `runMainLooperToNextTask` will result in the Robolectric scheduler getting out of sync with the time that we have waited for the condition to hold. Without an actual problem though - sufficient unto the day is the evil thereof.

[Next time](press-to-test-part-4.html) I'll get back to trying to write some proper unit tests.

