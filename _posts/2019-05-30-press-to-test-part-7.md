---
title: Press to Test - Test Driven Development in Android Part 7
layout: post
tags: [PressToTest, Testing, Android, Kotlin]
---

This is the final part in a series documenting my experiences learning Android development in Kotlin. The code is available to follow along on [GitHub](https://github.com/dmcg/PressToTest).

In [Part 1](press-to-test-part-1.html) I got a simple UI toy up and running, with tests running via [Espresso](https://developer.android.com/training/testing/espresso) in an emulator.

In [Part 2](press-to-test-part-2.html) I used [Roboletric](http://robolectric.org/) to get (a lightly refactored version of) the same tests running in a local JVM rather than the emulator.

[Part 3](press-to-test-part-3.html) was spent working out how to wait for a condition (the snack bar disappearing) on both the emulator and Robolectric tests.

In [Part 4](press-to-test-part-4.html) and [Part 5](press-to-test-part-5.html) I added unit tests that can run without the ten-second Robolectric tax by introducing something that I *think* is a ViewModel, but doesn't look anything like Google's examples.

[Part 6](press-to-test-part-6.html) was spent using [Data Binding](https://developer.android.com/topic/libraries/data-binding) to wire up the ViewModel in a way that I think is probably more like the [Android Architecture Components](https://developer.android.com/topic/libraries/architecture) have in mind.

This final episode tries to draw some conclusions from the journey to date.

## But First, Some Swing

Between 1998 and 2010 most of my development work was in [Swing](https://en.wikipedia.org/wiki/Swing_(Java)), a now deprecated though still working cross-platform GUI toolkit. 

I thought that it would be instructive to reimplement PressToTest, test-first, in Swing. I won't take you through the process, but here is the result.

```kotlin
class PressToTestTests {

    var boomCount = 0
    val button = JButton().apply {
        PressToTestModel(this, "Press to Test", "Release to Detonate") {
            boomCount++
        }
    }

    @Test
    fun `button message changes on pressing`() {
        assertEquals("Press to Test", button.text)

        button.dispatchEvent(mouseEvent(MOUSE_PRESSED))
        assertEquals("Release to Detonate", button.text)

        button.dispatchEvent(mouseEvent(MOUSE_RELEASED))
        assertEquals("Press to Test", button.text)
    }

    @Test
    fun `clicking button sets off the explosion`() {
        assertEquals(0, boomCount)

        button.doClick()
        assertEquals(1, boomCount)

        button.doClick()
        assertEquals(2, boomCount)
    }

    private fun mouseEvent(eventType: Int) = MouseEvent(button, eventType, currentTimeMillis(), 0, 0, 0, 0, false)
}
```

```kotlin
class PressToTestModel(
    private val button: JButton,
    private val defaultMessage: String,
    private val pressedMessage: String,
    private val action: () -> Unit
) {

    init {
        button.text = defaultMessage
        button.addActionListener {  action() }
        button.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                button.text = pressedMessage
            }
            override fun mouseReleased(e: MouseEvent) {
                button.text = defaultMessage
            }
        })
    }
}
```

```kotlin
fun main() {
    val button = JButton().apply {
        PressToTestModel(this, "Press to Test", "Release to Detonate") {
            JOptionPane.showMessageDialog(this, "Boom")
        }
    }

    JFrame("Press to Test").apply {
        contentPane.add(button)
        pack()
        setLocationRelativeTo(null)
        defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        isVisible = true
    }
}
```

That's it. No XML, no 20,000 line Java source files generated from that XML, no Java source files generated to implement the data binding from that XML, no Gradle plugins to make sure that all that generation is done, and no IDE plugins to make sure that when I drill into the generated code I end up looking at the XML that it came from.

If I wanted a more acceptance-level test I could invoke `main` from a JUnit test, find the button by ID or text and manipulate it using a [Robot](https://docs.oracle.com/javase/8/docs/api/java/awt/Robot.html).

Now this is a disingenuous comparison. Swing had the luxury (and curse) of rendering the UI itself, so that almost everything is pure Java. Android also has a more complicated app lifecycle model, so that managing consistency of state between a long-running application and a short-running activity is (necessarily?) complicated. And we shouldn't forget that Swing assumes that it is running on a desktop-class OS, where Android is mobile - although the cheapest current Android phones are a class above the desktop that Swing was targeting in 1998.

## Could Do Better

What I have discovered in this journey is that, even with its constraints, Android doesn't really seem to try hard enough to make unit testing its UIs easy. 

A GUI decoupling pattern like [MVVM](https://en.wikipedia.org/wiki/Model%E2%80%93view%E2%80%93viewmodel) should allow us to test our view logic without having real UI components, by working in terms of interfaces and data-binding. Our Android `ViewModel` is able to implement the `OnTouchListener` and `OnClickListener` interfaces to be notified of changes, and bind its `buttonText` `LiveModel` to the button's text, all without being aware of `Button`. In this way we can test the `ViewModel` without having to create an actual `Button`, which we can only do on an 'actual' device. All the pieces to implement this pattern are in place.

As I found though, if you then try to test this setup, you find that you cannot create the events that are propagated through the interfaces - in particular `MotionEvent` - in your tests. Introducing the Robolectric fake Android API can solve this problem, but only at the expense of horrible test startup times.

There is another problem with Robolectric, which is that in order to use it, you have to run your tests with the `RobolectricTestRunner` (maybe not directly, the `AndroidJUnit4` test runner delegates to Robolectric for unit tests). Each test can only have one runner, which prevents the use of other [interesting testing techniques](https://github.com/dmcg/minutest). The `RobolectricTestRunner` is also intimately tied to JUnit 4, and Google are [in no hurry](https://github.com/robolectric/robolectric/issues/3477) to fix that. To be fair, they are caught a bit between a rock and a hard place - they *could* fix the single test runner issue by reimplementing Robolectric support as a [JUnit Rule](https://github.com/junit-team/junit4/wiki/rules), but these aren't well supported by JUnit 5. Sigh.

Given the difficulties that I found using Robolectric (slow startup, having to poke it to advance time, having to kick it to acknowledge the activity lifecycle, cannot integrate with other testrunners) I'm really not sure that it is worth the effort over a combination of the more completely decoupled unit tests that I ended up writing and running code on the simulator. Except that the simulator would be a challenge to run as part of a server-hosted continuous integration setup, which is probably the reason that people persist with Robolectric.   

On the plus side, one of the reasons that Robolectric seems a marginal gain is that running acceptances tests against a real app running in a simulator proves to be so simple and reliable. So kudos to Google for Espresso and the tooling around it.
 
## Wrap Up

I think that this toy example has run its course. Thanks to all those who provided feedback. 

What about you? Do you agree with my conclusions, or do you have a different experience? Maybe there are tricks that I have missed? If so, please do let me know, either in the comments below, via [Twitter](https://twitter.com/duncanmcg) or [email](http://oneeyedmen.com/mailto:duncan@oneeyedmen.com). Thank you.


