---
title: Press to Test - Test Driven Development in Android Part 5
layout: post
---

This is Part 5 in a series documenting my experiences learning Android development in Kotlin. The code is available to follow along on [GitHub](https://github.com/dmcg/PressToTest).

In [Part 1](press-to-test-part-1.html) I got a simple UI toy up and running, with tests running via [Espresso](https://developer.android.com/training/testing/espresso) in an emulator.

In [Part 2](press-to-test-part-2.html) I used [Roboletric](http://robolectric.org/) to get (a lightly refactored version of) the same tests running in a local JVM rather than the emulator.

[Part 3](press-to-test-part-3.html) was spent working out how to wait for a condition (the snack bar disappearing) on both the emulator and Robolectric tests.

In [Part 4](press-to-test-part-4.html) I spent a whole day trying to write a single unit test that was sufficiently decoupled from the Android runtime to run without Robolectric.

This time I've learned not to try to predict what will happen! But my aim is to add unit tests for the 'Boom' message and to try to simplify the tests, perhaps by making use of supplied support classes.

## The Code So Far

Here's the code to date - if you haven't already you could read [Part 4](press-to-test-part-4.html) to see how it got that way.

```kotlin
class PressToTestTests {

    var buttonText = "DEFAULT"
    val viewModel = ViewModel { buttonText = it }

    @Test
    fun `button message changes on pressing`() {
        assertEquals("Press to Test", buttonText)

        viewModel.onTouchAction(MotionEvent.ACTION_DOWN)
        assertEquals("Release to Detonate", buttonText)

        viewModel.onTouchAction(MotionEvent.ACTION_UP)
        assertEquals("Press to Test", buttonText)
    }
}

class ViewModel(
    private val onButtonTextChanged: (String) -> Unit
) {

    private val defaultText = "Press to Test"
    private val pressedText = "Release to Detonate"

    var buttonText: String by Delegates.observable(defaultText) { _, _, newValue ->
        onButtonTextChanged(newValue)
    }

    init {
        // required to sync the view on creation
        buttonText = defaultText
    }

    fun onTouchAction(actionCode:Int) {
        when (actionCode) {
            MotionEvent.ACTION_DOWN -> buttonText = pressedText
            MotionEvent.ACTION_UP -> buttonText = defaultText
        }
    }

    val onTouchListener = View.OnTouchListener { v, event ->
        onTouchAction(event.actionMasked)
        false
    }
}

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val viewModel = ViewModel(onButtonTextChanged = button::setText)
        button.setOnTouchListener(viewModel.onTouchListener)

        button.setOnClickListener { view ->
            Snackbar.make(view, getString(R.string.explosion), Snackbar.LENGTH_SHORT)
                .setAction("Action", null).show()
        }
    }
}
```

## Data Binding or More Tests?

After completing this I spend a couple of hours overall reading up on [Data Binding](https://developer.android.com/topic/libraries/data-binding/index.html). It looks like I'll have to modify quite a bit of the app to use it, and probably work my way through [a codelab](https://codelabs.developers.google.com/codelabs/android-databinding) in order to understand what the documentation is talking about. I decide to press on without it for now, if only because working my way through documentation doesn't provide blog content.

## So, One More Test

The unit test that we are missing is that a snackbar should show "Boom" when the button is clicked. Given all the fun we had trying to test with a `Button` I work on the principle that we should avoid any direct reference to snackbars in the `ViewModel` and its tests. So I pull the same stunt as before - hide the code-to-make-the-boom behind a function passed into the `ViewModel`'s constructor.

```kotlin
class PressToTestTests {

    var buttonText = "DEFAULT"
    var boomCount = 0
    val viewModel = ViewModel(
        onButtonTextChanged = { buttonText = it },
        boom = { boomCount++ }
    )

    @Test
    fun `clicking button sets off the explosion`() {
        assertEquals(0, boomCount)

        viewModel.onClick()
        assertEquals(1, boomCount)
    }
}
```

expose a couple of methods on the `ViewModel`, one we can call from our tests as it doesn't need an event (which we can't create in unit tests), and one that gives access to an event listener

```kotlin
class ViewModel(
    private val onButtonTextChanged: (String) -> Unit,
    private var goBoom: () -> Unit
) {

    fun onClick() {
        goBoom()
    }

    val onClickListener = View.OnClickListener { onClick() }
}
```

and then install into our activity

```kotlin
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val viewModel = ViewModel(
            onButtonTextChanged = button::setText,
            goBoom = this::boom)
        button.setOnTouchListener(viewModel.onTouchListener)
        button.setOnClickListener(viewModel.onClickListener)
    }

    private fun boom() {
        Snackbar.make(button, getString(R.string.explosion), Snackbar.LENGTH_SHORT)
            .setAction("Action", null).show()
    }
}
```

Now that I know not to drive down closed roads, this is a quick and easy, runs as a unit test in milliseconds, and passes our acceptance tests. Woohoo!

## Review

I have a bit of a tidy up, moving installation of `ViewModel` with `Button` to a `ViewModel` constructor and then review the code.

```kotlin
class PressToTestTests {

    var buttonText = "DEFAULT"
    var boomCount = 0

    val viewModel = ViewModel(
        defaultText = "Press to Test",
        pressedText = "Release to Detonate",
        onButtonTextChanged = { buttonText = it },
        goBoom = { boomCount++ }
    )

    @Test
    fun `button message changes on pressing`() {
        assertEquals("Press to Test", buttonText)

        viewModel.onTouchAction(MotionEvent.ACTION_DOWN)
        assertEquals("Release to Detonate", buttonText)

        viewModel.onTouchAction(MotionEvent.ACTION_UP)
        assertEquals("Press to Test", buttonText)
    }

    @Test
    fun `clicking button sets off the explosion`() {
        assertEquals(0, boomCount)

        viewModel.onClick()
        assertEquals(1, boomCount)

        viewModel.onClick()
        assertEquals(2, boomCount)
    }
}
```

```kotlin
class ViewModel(
    private val defaultText: String,
    private val pressedText: String,
    private val onButtonTextChanged: (String) -> Unit,
    private var goBoom: () -> Unit
) {
    private var buttonText: String by Delegates.observable(defaultText) { _, _, newValue ->
        onButtonTextChanged(newValue)
    }

    constructor(
        button: Button,
        defaultText: String,
        pressedText: String,
        goBoom: () -> Unit

    ) : this(
        defaultText = defaultText,
        pressedText = pressedText,
        onButtonTextChanged = button::setText,
        goBoom = goBoom
    ) {
        button.setOnTouchListener { _, event ->
            onTouchAction(event.actionMasked)
            false
        }
        button.setOnClickListener {
            onClick()
        }
    }

    init {
        // sync the view on creation
        buttonText = defaultText
    }

    @VisibleForTesting
    internal fun onTouchAction(actionCode:Int) {
        when (actionCode) {
            MotionEvent.ACTION_DOWN -> buttonText = pressedText
            MotionEvent.ACTION_UP -> buttonText = defaultText
        }
    }

    @VisibleForTesting
    internal fun onClick() {
        goBoom()
    }
}
```

```kotlin
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ViewModel(
            button = button,
            defaultText = getString(R.string.default_button_label),
            pressedText = getString(R.string.pressed_button_label),
            goBoom = this::boom
        )
    }

    private fun boom() {
        Snackbar.make(button, getString(R.string.explosion), Snackbar.LENGTH_SHORT)
            .setAction("Action", null).show()
    }
}
```

Actually that isn't bad. The test is nice and simple and doesn't rely on any awkward Android classes, `MainActivity` is clean, but the relationship between the `ViewModel`, the button and the snackbar is nicely explicit. The nastiness is hidden away in in a secondary constructor of `ViewModel`, and that looks worse than it is because of Kotlin's awkward constructor delegation layout and my desire to fit the code on mobile screens.

Philosophically I like that `ViewModel` is now self-contained and asks only to be created with strings, a button and the `goBoom` effect. Overall, given the constraints on unit testing, this may be the least-worst solution. Which is least-bad-enough for me. 
 
## Wrap Up

This hasn't been a full day's work, but I'm going to stop this post here, as we now have complete acceptance and unit test coverage of the app, albeit hand-crafted. I'd still like to try out that Data Binding though, so I'll spend the rest of the day working through that codelab so that I can refactor my cobbling [tomorrow](press-to-test-part-6.html).
